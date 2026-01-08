package com.moodify.backend.controller;

import com.moodify.backend.dto.MoodRequest;
import com.moodify.backend.dto.MoodResponse;
import com.moodify.backend.service.MoodService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MoodController {

    private static final Logger logger = LoggerFactory.getLogger(MoodController.class);

    private final MoodService moodService;

    public MoodController(MoodService moodService) {
        this.moodService = moodService;
    }

    @PostMapping("/generate")
    public MoodResponse generateSongs(@RequestBody MoodRequest request) {
        logger.info("Received generate request: mood={}, era={}, language={}", request.getMood(), request.getEra(), request.getLanguage());

        return new MoodResponse(
                moodService.generateSongs(request)
        );
    }

    // Mock endpoint to return canned songs when Gemini is unavailable
    @PostMapping("/mock")
    public MoodResponse mockGenerate(@RequestBody(required = false) MoodRequest request) {
        logger.info("Mock generate called (used as fallback)");
        List<String> songs = List.of(
                "Tum Hi Ho - Arijit Singh | https://www.youtube.com/results?search_query=Tum+Hi+Ho+Arijit+Singh",
                "Pehla Nasha - Udit Narayan | https://www.youtube.com/results?search_query=Pehla+Nasha+Udit+Narayan",
                "Kal Ho Naa Ho - Sonu Nigam | https://www.youtube.com/results?search_query=Kal+Ho+Naa+Ho+Sonu+Nigam",
                "Channa Mereya - Arijit Singh | https://www.youtube.com/results?search_query=Channa+Mereya+Arijit+Singh",
                "Tujh Mein Rab Dikhta Hai - Roop Kumar Rathod | https://www.youtube.com/results?search_query=Tujh+Mein+Rab+Dikhta+Hai"
        );
        return new MoodResponse(songs);
    }



    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException ex) {
        logger.error("Unhandled exception:", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", ex.getMessage()));
    }
}
