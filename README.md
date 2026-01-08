# 🎶 Moodify – AI-Powered Music Recommendation Platform

Moodify is a full-stack web application that recommends songs based on a user’s **mood**, **music era**, **language**, and **feelings**.  
It combines a modern **Bootstrap frontend** with a **Spring Boot backend**, powered by **Google Gemini AI**.

> *Feel your mood. Find your music.*

---

## 🚀 Live Demo

- **Frontend (Netlify)**: https://moodify.netlify.app  
- **Backend (Railway)**: https://moodify-production-136a.up.railway.app

> ⚠️ Backend runs on Railway trial credits and may sleep or stop after credits expire.

---

## 🧩 Features

- 🎭 Mood selection (Happy, Sad, Romantic, Chill, etc.)
- 📀 Era selection (Old, 90s, 2000s, New)
- 🌐 Language support (Hindi, English, Bengali)
- 💬 Custom feeling input
- 🤖 AI-powered song recommendations (Gemini)
- ▶️ YouTube and Spotify search links for each song
- 🔁 Mock API fallback when AI quota is exceeded
- 📱 Fully responsive UI

---

## 🛠️ Tech Stack

### Frontend
- HTML5  
- CSS3  
- Bootstrap 5  
- JavaScript (Vanilla)  
- AOS (Animate on Scroll)

### Backend
- Java 17  
- Spring Boot  
- Spring WebFlux (WebClient)  
- REST APIs  
- Docker  
- Google Gemini API

### Deployment
- Frontend: **Netlify**
- Backend: **Railway**
- Version Control: **Git & GitHub**

---

## 📁 Project Structure


```
Moodify-Project/
│
├── frontend/
│   ├── index.html
│   ├── assets/
│       ├── css/
│       ├── js/
│       ├── images/
│       └── icons/
│
├── backend/
│   └── moodify-backend/
│       ├── src/main/java/com/moodify/backend/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── dto/
│       │   └── config/
│       ├── src/main/resources/
│       │   └── application.properties
│       └── pom.xml
│── Dockerfile
└── README.md
```

---

## 🔗 API Endpoints

### Generate Songs (AI)
POST /api/generate

**Request Body**
```json
{
  "mood": "Sad",
  "era": "90s",
  "language": "Bengali",
  "feeling": "Feeling nostalgic"
}
```

Mock Songs (Fallback)

```
POST /api/mock
```
Returns predefined songs when Gemini API is unavailable.

---

## Environment Variables
### Set the following variable in Railway:

```GEMINI_API_KEY=your_api_key_here```

application.properties

```gemini.api.key=${GEMINI_API_KEY} ```

---

## Docker Support
### Backend Dockerfile:

```
FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]
```

---

## Known Limitations
- Gemini API has quota limits (free tier)
- Railway backend runs on trial credits

---
