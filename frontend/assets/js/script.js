// CHANGE THIS WHEN SWITCHING ENVIRONMENTS
const API_BASE_URL = "https://moodify-production-136a.up.railway.app";
// const API_BASE_URL = "http://localhost:9090"; // for local testing


// Mood
const moodOptions = document.querySelectorAll(".mood-option");
let selectedMood = "";

moodOptions.forEach(option => {
  option.addEventListener("click", () => {
    moodOptions.forEach(o => o.classList.remove("selected"));
    option.classList.add("selected");
    selectedMood = option.getAttribute("data-mood");
  });
});

// Era
const eraOptions = document.querySelectorAll(".era-option");
let selectedEra = "";

eraOptions.forEach(option => {
  option.addEventListener("click", () => {
    eraOptions.forEach(o => o.classList.remove("selected"));
    option.classList.add("selected");
    selectedEra = option.getAttribute("data-era");
  });
});

// Language
const languageOptions = document.querySelectorAll(".language-option");
let selectedLanguage = "";

languageOptions.forEach(option => {
  option.addEventListener("click", () => {
    languageOptions.forEach(o => o.classList.remove("selected"));
    option.classList.add("selected");
    selectedLanguage = option.getAttribute("data-language");
  });
});

// Feeling
const feelingInput = document.getElementById("feelingInput");

// Generate Button
const generateButton = document.getElementById("generateBtn");

generateButton.addEventListener("click", () => {
  const feeling = feelingInput.value.trim();

  if (!selectedMood || !selectedEra || !selectedLanguage || !feeling) {
    alert("Please select mood, era, language and enter your feelings.");
    return;
  }

  generateButton.disabled = true;
  generateButton.innerText = "Generating...";

  fetch(`${API_BASE_URL}/api/generate`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      mood: selectedMood,
      era: selectedEra,
      language: selectedLanguage,
      feeling: feeling
    })
  })
    .then(response => {
      if (!response.ok) {
        throw new Error("Server error");
      }
      return response.json();
    })
    .then(data => {
      const resultsContainer = document.getElementById("resultsContainer");
      resultsContainer.innerHTML = "<h4>🎵 Recommended Songs for You:</h4>";

      const grid = document.createElement("div");
      grid.className = "moodify-grid";

      data.songs.forEach(song => {
        const parts = song.split(" | ");
        const title = parts[0] || song;
        const youTubeLink = parts[1] || '#';
        const spotifyLink = parts[2] || null;

        const card = document.createElement("div");
        card.className = "song-card";

        const titleEl = document.createElement("div");
        titleEl.className = "song-title";
        titleEl.textContent = title;

        const actions = document.createElement("div");
        actions.className = "song-actions";

        const ytBtn = document.createElement("a");
        ytBtn.href = youTubeLink;
        ytBtn.target = "_blank";
        ytBtn.className = "btn-yt";
        ytBtn.textContent = "YouTube";

        actions.appendChild(ytBtn);

        if (spotifyLink) {
          const spBtn = document.createElement("a");
          spBtn.href = spotifyLink;
          spBtn.target = "_blank";
          spBtn.className = "btn-spotify";
          spBtn.textContent = "Spotify";
          actions.appendChild(spBtn);
        }

        card.appendChild(titleEl);
        card.appendChild(actions);
        grid.appendChild(card);
      });

      resultsContainer.appendChild(grid);
    })
    .catch(async error => {
      console.error(error);
      // Try a backend mock fallback so the UI remains testable while Gemini is down
      try {
        const fallbackResp = await fetch(`${API_BASE_URL}/api/mock`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ mood: selectedMood, era: selectedEra, language: selectedLanguage, feeling: feeling })
        });
        if (fallbackResp.ok) {
          const data = await fallbackResp.json();
          const resultsContainer = document.getElementById("resultsContainer");
          resultsContainer.innerHTML = "<h4>🎵 Recommended Songs (mock):</h4>";

          const list = document.createElement("ul");
          list.className = "list-group moodify-results";

          data.songs.forEach(song => {
            const item = document.createElement("li");
            item.className = "list-group-item moodify-item";

            const [title, link] = song.split(" | ");
            item.innerHTML = `<a href="${link}" target="_blank" class="song-link">${title}</a>`;

            list.appendChild(item);
          });

          resultsContainer.appendChild(list);
          return;
        }
      } catch (e) {
        console.error("Mock fallback failed:", e);
      }

      alert("Failed to generate songs.");
    })
    .finally(() => {
      generateButton.disabled = false;
      generateButton.innerText = "🎶 Generate Songs";
    });
});
