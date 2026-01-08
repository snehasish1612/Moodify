package com.moodify.backend.dto;

import java.util.List;

public class MoodResponse {

    private List<String> songs;

    public MoodResponse(List<String> songs) {
        this.songs = songs;
    }

    public List<String> getSongs() {
        return songs;
    }

    public void setSongs(List<String> songs) {
        this.songs = songs;
    }
}
