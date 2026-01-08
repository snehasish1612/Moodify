package com.moodify.backend.service;

import com.moodify.backend.dto.MoodRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import org.springframework.core.io.buffer.DataBufferLimitException;

@Service
public class MoodService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Default model used for generation (simplified)
    private static final String DEFAULT_MODEL = "models/gemini-2.5-flash";

    // Lightweight cache to remember which song queries returned YouTube results (avoids repeated lookups)
    private final Map<String, Boolean> ytExistCache = new ConcurrentHashMap<>();

    // A simple HTTP client (used for quick existence checks against public sites)
    // Increase in-memory buffer size to allow reading YouTube result pages without DataBufferLimitException
    private final WebClient httpClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    private static final Logger logger = LoggerFactory.getLogger(MoodService.class);

    @Value("${gemini.api.key}")
    private String apiKey;

    private final WebClient webClient = WebClient.create(
            "https://generativelanguage.googleapis.com");

    public List<String> generateSongs(MoodRequest request) {

        String prompt = String.format("""
                Suggest 5 distinct %s %s songs in %s language.
                Mood: %s.
                IMPORTANT: Respond ONLY with five unique lines, each in this exact format:
                Song - Artist
                Do NOT include numbering, quotes, explanations, or any extra text — only the five lines.
                If you cannot find songs exactly matching the language/era, return the closest matches in that format.
                """,
                request.getEra(),
                request.getMood(),
                request.getLanguage(),
                request.getFeeling());

        String model = DEFAULT_MODEL;
        String response;
        try {
            response = callGenerateSimple(model, prompt);
        } catch (Exception e) {
            logger.error("Error while calling selected model ({}): {}", model, e.getMessage());
            throw new RuntimeException("Error contacting Gemini API (model=" + model + "): " + e.getMessage(), e);
        }

        if (response == null || response.isEmpty()) {
            logger.warn("Empty response from Gemini API");
            throw new RuntimeException("Empty response from Gemini API");
        }

        logger.debug("Gemini response: {}", response);
        return extractSongs(response);
    }

    /**
     * Return the models JSON from the Gemini models endpoint.
     * This is used internally for model discovery and selection.
     */


    private String callGenerateSimple(String model, String prompt) {
        String[] versions = new String[]{"/v1beta", "/v1"};
        String bodyContent = """
                {
                  "contents": [{ "parts": [{ "text": "%s" }] }]
                }
            """.formatted(prompt);

        for (String version : versions) {
            String pathContent = version + "/" + model + ":generateContent";
            try {
                logger.debug("Attempting POST {}", pathContent);
                String resp = webClient.post()
                        .uri(uriBuilder -> uriBuilder
                                .path(pathContent)
                                .queryParam("key", apiKey)
                                .build())
                        .header("Content-Type", "application/json")
                        .bodyValue(bodyContent)
                        .exchangeToMono(clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(bodyStr -> {
                                    if (clientResponse.statusCode().is2xxSuccessful()) return Mono.just(bodyStr);
                                    return Mono.error(new RuntimeException("API error (" + pathContent + "): " + clientResponse.statusCode() + " - " + bodyStr));
                                }))
                        .block();

                if (resp != null && !resp.isEmpty()) return resp;
            } catch (Exception e) {
                logger.warn("Attempt {} failed for model {}: {}", version, model, e.getMessage());
            }
        }

        throw new RuntimeException("All generation attempts failed for model " + model);
    }







    private static final List<String> FALLBACK_SONGS = List.of(
            "Tum Hi Ho - Arijit Singh",
            "Pehla Nasha - Udit Narayan",
            "Kal Ho Naa Ho - Sonu Nigam",
            "Channa Mereya - Arijit Singh",
            "Tujh Mein Rab Dikhta Hai - Roop Kumar Rathod"
    );

    private List<String> extractSongs(String response) {

        if (response == null || response.isEmpty()) {
            return FALLBACK_SONGS.stream().map(this::withYouTubeLink).toList();
        }

        // Try to extract generated textual content reliably from JSON responses (Gemini / Text-Bison)
        String generated = extractGeneratedText(response);

        // If the generated string itself contains embedded JSON fragments (e.g., '"text":"...') try to sanitize
        generated = sanitizeGeneratedTextIfNeeded(generated);

        // Split by newlines and normalize each candidate, dedupe while preserving order
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();

        for (String raw : generated.split("\\r?\\n")) {
            String s = raw.trim();
            if (s.isEmpty()) continue;
            // remove leading numbering like '1.' or '1)'
            s = s.replaceAll("^[0-9]+[.)]?\\s*", "");

            String normalized = normalizeSongLine(s);
            if (normalized != null && !normalized.isEmpty()) {
                // Quick existence check on YouTube to reduce hallucinatory suggestions
                try {
                    boolean exists = ytExistCache.computeIfAbsent(normalized.toLowerCase(Locale.ROOT), k -> youtubeHasResult(normalized));
                    if (exists) {
                        seen.add(normalized);
                    } else {
                        logger.info("Skipping candidate (no YouTube results): {}", normalized);
                    }
                } catch (Exception e) {
                    logger.warn("YouTube existence check failed for '{}': {}. Accepting it as fallback.", normalized, e.getMessage());
                    seen.add(normalized);
                }
                if (seen.size() >= 5) break;
            }
        }

        // If not enough, append fallbacks (but avoid duplicates)
        for (String f : FALLBACK_SONGS) {
            if (seen.size() >= 5) break;
            if (!seen.contains(f)) seen.add(f);
        }

        return seen.stream().limit(5).map(this::withYouTubeLink).toList();
    }

    private String extractGeneratedText(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            // 1) If 'candidates' array is present, extract text pieces
            if (root.has("candidates")) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode cand : root.withArray("candidates")) {
                    if (cand.has("content")) {
                        for (JsonNode part : cand.withArray("content")) {
                            if (part.has("text")) {
                                sb.append(part.get("text").asText()).append("\n");
                            } else {
                                // sometimes content entries are objects with nested 'text'
                                String maybe = part.toString();
                                sb.append(maybe).append("\n");
                            }
                        }
                    } else if (cand.has("output")) {
                        sb.append(cand.get("output").asText()).append("\n");
                    }
                }
                String out = sb.toString().trim();
                if (!out.isEmpty()) return out;
            }

            // 2) If 'outputs' array is present (different response shape)
            if (root.has("outputs")) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode out : root.withArray("outputs")) {
                    if (out.has("content")) {
                        for (JsonNode part : out.withArray("content")) {
                            if (part.has("text")) sb.append(part.get("text").asText()).append("\n");
                            else sb.append(part.toString()).append("\n");
                        }
                    } else if (out.has("text")) {
                        sb.append(out.get("text").asText()).append("\n");
                    }
                }
                String o = sb.toString().trim();
                if (!o.isEmpty()) return o;
            }

            // 3) As a fallback, pull all 'text' fields anywhere in the JSON
            java.util.List<String> texts = root.findValuesAsText("text");
            if (!texts.isEmpty()) {
                return String.join("\n", texts);
            }

        } catch (Exception e) {
            logger.warn("Failed to parse generated JSON, falling back to raw response: {}", e.getMessage());
        }

        // fallback: return raw string
        return response;
    }

    private String withYouTubeLink(String song) {
        try {
            // Use URL encoding for queries
            String query = java.net.URLEncoder.encode(song, java.nio.charset.StandardCharsets.UTF_8.toString());
            // YouTube: filter for videos (sp param helps prioritize video results)
            String youTube = "https://www.youtube.com/results?search_query=" + query + "&sp=EgIQAQ%3D%3D";
            // Spotify: open search in Spotify Web
            String spotify = "https://open.spotify.com/search/" + query;
            // Format: title | youtubeLink | spotifyLink
            return song + " | " + youTube + " | " + spotify;
        } catch (Exception e) {
            logger.warn("Failed to encode song query for links: {}", e.getMessage());
            String query = song.replace(" ", "+");
            return song + " | https://www.youtube.com/results?search_query=" + query + " | https://open.spotify.com/search/" + query;
        }
    }

    private String normalizeSongLine(String s) {
        // We expect a format like "Title - Artist" (dash used as separator). Try to be forgiving.
        if (s.contains(" - ")) {
            String[] parts = s.split(" - ", 2);
            String title = parts[0].trim();
            String artist = parts[1].trim();
            // If artist has extra commas listing many names, keep the first 1-2 names
            if (artist.contains(",")) {
                String[] artParts = artist.split(",");
                artist = artParts[0].trim();
            }
            return title + " - " + artist;
        }
        // fallback: if there's a dash without spaces
        if (s.contains("-")) {
            String[] parts = s.split("-", 2);
            String title = parts[0].trim();
            String artist = parts[1].trim();
            if (artist.contains(",")) {
                artist = artist.split(",")[0].trim();
            }
            return title + " - " + artist;
        }
        // Not in a parsable format - try to heuristically split last word(s) as artist (risky)
        String[] words = s.split(" ");
        if (words.length >= 2) {
            String artist = words[words.length - 1];
            String title = String.join(" ", java.util.Arrays.copyOf(words, words.length - 1));
            return title + " - " + artist;
        }
        return null;
    }

    /**
     * If the generated text appears to contain embedded JSON fragments such as
     * '"text":"My Immortal - ...\nBecause of You - ..."', try to parse that JSON
     * and extract the inner text fields (unescaped) so the UI shows clean song lines.
     */
    private String sanitizeGeneratedTextIfNeeded(String generated) {
        if (generated == null || generated.isEmpty()) return generated;

        // Quick heuristic: if it contains a "text" key or looks like JSON, attempt to parse
        if (generated.contains("\"text\"") || generated.trim().startsWith("{") || generated.trim().startsWith("[")) {
            try {
                JsonNode root = objectMapper.readTree(generated);
                // If it's a value node (a string with embedded JSON), try to find text fields anywhere
                java.util.List<String> texts = root.findValuesAsText("text");
                if (!texts.isEmpty()) {
                    return String.join("\n", texts);
                }
            } catch (Exception e) {
                // If parsing the whole string fails, try to regex-extract an inner "text":"..." value
                try {
                    java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\\"text\\\"\s*:\s*\\\"([\\s\\S]*?)\\\"");
                    java.util.regex.Matcher m = p.matcher(generated);
                    if (m.find()) {
                        String inner = m.group(1);
                        // Unescape JSON-style escape sequences by letting ObjectMapper read it as a JSON string
                        String unescaped = objectMapper.readValue("\"" + inner.replaceAll("\\\\\"", "\\\\\\\"") + "\"", String.class);
                        return unescaped;
                    }
                } catch (Exception e2) {
                    logger.warn("Failed to sanitize generated JSON fragment: {}", e2.getMessage());
                }
            }
        }
        // If nothing else, strip obvious JSON-like noise (e.g., leading '"text":')
        String cleaned = generated.replaceAll("\\\"text\\\"\s*:\s*\\\"", "").replaceAll("\\\"\\s*,?", "").trim();
        return cleaned;
    }

    /**
     * Quick heuristic to check whether a YouTube search for the given song returns any videos.
     * Uses a simple HTTP GET to the public results page and searches the HTML/initial data
     * for indications of video results. Results are cached by the caller.
     */
    private boolean youtubeHasResult(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            String url = "https://www.youtube.com/results?search_query=" + encoded + "&sp=EgIQAQ%3D%3D";
            String body = httpClient.get()
                    .uri(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));

            if (body == null || body.isEmpty()) return false;
            String lower = body.toLowerCase(Locale.ROOT);
            // Look for typical markers in the YouTube initial HTML/JSON: presence of video links or videoRenderer
            if (lower.contains("/watch?v=") || lower.contains("videorenderer") || (lower.contains("ytinitialdata") && lower.contains("video"))) {
                return true;
            }
            return false;
        } catch (Exception e) {
            // If response exceeded buffer limit but returned 200, assume YouTube likely has results (avoid false negatives)
            if (e instanceof DataBufferLimitException || (e.getMessage() != null && e.getMessage().contains("Exceeded limit on max bytes to buffer"))) {
                logger.info("youtubeHasResult(): response exceeded buffer limit, treating as likely present for '{}'", query);
                return true;
            }
            logger.warn("youtubeHasResult() failed for '{}': {}", query, e.getMessage());
            return false;
        }
    }
}
