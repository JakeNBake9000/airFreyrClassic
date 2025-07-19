package com.mycompany.airfreyr;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * A class to fetch YouTube Music links based on song metadata.
 * Uses the YouTube Data API v3 to search for music videos.
 */
public class YouTubeMusicLinkFetcher {
    
    private static final String YOUTUBE_API_BASE_URL = "https://www.googleapis.com/youtube/v3/search";
    private static final String YOUTUBE_MUSIC_BASE_URL = "https://music.youtube.com/watch?v=";
    private static final String YOUTUBE_BASE_URL = "https://www.youtube.com/watch?v=";
    
    private final String apiKey;
    private final HttpClient httpClient;
    private final Gson gson;
    
    /**
     * Constructor that requires a YouTube Data API key.
     * 
     * @param apiKey Your YouTube Data API v3 key
     */
    public YouTubeMusicLinkFetcher(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.gson = new Gson();
    }
    
    /**
     * Fetches the most relevant YouTube Music link for a given song.
     * 
     * @param songName The name of the song
     * @param artistName The artist name
     * @param albumName The album name (can be null)
     * @param lengthMillis The length of the song in milliseconds
     * @return The YouTube Music URL of the most relevant result, or null if not found
     * @throws IOException If there's an error with the HTTP request
     * @throws InterruptedException If the request is interrupted
     */
    public String getYouTubeMusicLink(String songName, String artistName, String albumName, long lengthMillis) 
            throws IOException, InterruptedException {
        
        if (songName == null || songName.trim().isEmpty() || 
            artistName == null || artistName.trim().isEmpty()) {
            throw new IllegalArgumentException("Song name and artist name cannot be null or empty");
        }
        
        // Build search query with audio-only preference
        String searchQuery = buildSearchQuery(songName, artistName, albumName);
        
        // Search YouTube
        List<SearchResult> searchResults = searchYouTube(searchQuery);
        
        if (searchResults.isEmpty()) {
            return null;
        }
        
        // Fetch video details including duration
        fetchVideoDetails(searchResults);
        
        // Find the best match
        SearchResult bestMatch = findBestMatch(searchResults, songName, artistName, albumName, lengthMillis);
        
        return bestMatch != null ? YOUTUBE_MUSIC_BASE_URL + bestMatch.videoId : null;
    }
    
    /**
     * Builds a search query string from the song metadata with audio preference.
     */
    private String buildSearchQuery(String songName, String artistName, String albumName) {
        StringBuilder query = new StringBuilder();
        query.append(artistName).append(" ").append(songName);
        
        if (albumName != null && !albumName.trim().isEmpty()) {
            query.append(" ").append(albumName);
        }
        
        // Add audio preference terms to help find audio-only versions
        query.append(" audio OR \"official audio\" OR \"full song\"");
        
        return query.toString();
    }
    
    /**
     * Searches YouTube using the Data API v3.
     */
    private List<SearchResult> searchYouTube(String query) throws IOException, InterruptedException {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = String.format("%s?part=snippet&q=%s&type=video&videoCategoryId=10&maxResults=10&key=%s",
                    YOUTUBE_API_BASE_URL, encodedQuery, apiKey);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                throw new IOException("YouTube API request failed with status: " + response.statusCode());
            }
            
            return parseSearchResults(response.body());
            
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL syntax", e);
        }
    }
    
    /**
     * Parses the JSON response from YouTube API.
     */
    private List<SearchResult> parseSearchResults(String jsonResponse) throws IOException {
        List<SearchResult> results = new ArrayList<>();
        
        JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
        JsonArray items = root.getAsJsonArray("items");
        
        if (items != null) {
            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                JsonObject id = item.getAsJsonObject("id");
                JsonObject snippet = item.getAsJsonObject("snippet");
                
                if (id != null && snippet != null) {
                    String videoId = id.get("videoId").getAsString();
                    String title = snippet.get("title").getAsString();
                    String channelTitle = snippet.get("channelTitle").getAsString();
                    String description = snippet.get("description").getAsString();
                    
                    results.add(new SearchResult(videoId, title, channelTitle, description));
                }
            }
        }
        
        return results;
    }
    
    /**
     * Finds the best matching video from search results.
     */
    private SearchResult findBestMatch(List<SearchResult> results, String songName, String artistName, 
                                     String albumName, long lengthMillis) {
        
        SearchResult bestMatch = null;
        int bestScore = -1;
        
        for (SearchResult result : results) {
            int score = calculateMatchScore(result, songName, artistName, albumName, lengthMillis);
            
            // Absolutely reject music videos and lyric videos (negative or very low scores)
            if (score < 0) {
                continue; // Skip this result entirely
            }
            
            if (score > bestScore) {
                bestScore = score;
                bestMatch = result;
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Calculates a match score for a search result.
     */
    private int calculateMatchScore(SearchResult result, String songName, String artistName, 
                                  String albumName, long lengthMillis) {
        int score = 0;
        String title = result.title.toLowerCase();
        String channelTitle = result.channelTitle.toLowerCase();
        String description = result.description.toLowerCase();
        
        // Check for song name in title
        if (title.contains(songName.toLowerCase())) {
            score += 10;
        }
        
        // Check for artist name in title or channel
        if (title.contains(artistName.toLowerCase()) || channelTitle.contains(artistName.toLowerCase())) {
            score += 8;
        }
        
        // Check for album name
        if (albumName != null && !albumName.trim().isEmpty()) {
            if (title.contains(albumName.toLowerCase()) || description.contains(albumName.toLowerCase())) {
                score += 5;
            }
        }
        
        // Prefer official channels or music-related channels
        if (channelTitle.contains("official") || channelTitle.contains("music") || 
            channelTitle.contains("records") || channelTitle.contains("vevo")) {
            score += 3;
        }
        
        // Check for music-related keywords in title (but penalize music videos and lyric videos)
        if (title.contains("official audio") || title.contains("audio only") || 
            title.contains("full song") || title.contains("studio version")) {
            score += 5;
        }
        
        // HEAVILY penalize music videos and lyric videos - we want audio only
        if (title.contains("music video") || title.contains("official video") || 
            title.contains("official music video") || title.contains("lyric video") ||
            title.contains("lyrics video") || title.contains("with lyrics") ||
            title.contains("visualizer") || title.contains("video clip") ||
            title.contains("mv") || title.toLowerCase().matches(".*\\blyrics?\\b.*")) {
            score -= 50; // Heavy penalty to essentially exclude these
        }
        
        // Additional penalties for visual content indicators
        if (title.contains("4k") || title.contains("hd") || title.contains("1080p") ||
            title.contains("720p") || title.contains("video") && !title.contains("audio")) {
            score -= 10;
        }
        
        // Bonus for audio-only indicators
        if (title.contains("audio") && !title.contains("video")) {
            score += 8;
        }
        
        // Penalize covers, remixes, live versions, and clean versions (unless specifically searched for)
        if (title.contains("cover") || title.contains("remix") || title.contains("live")) {
            score -= 2;
        }
        
        // Penalize clean/censored versions
        if (title.contains("clean") || title.contains("censored") || title.contains("radio edit") ||
            title.contains("clean version") || title.contains("radio version") || 
            title.contains("clean edit") || title.contains("radio safe") ||
            title.contains("family friendly") || title.contains("edited")) {
            score -= 8;
        }
        
        // Bonus for explicit versions (original uncensored content)
        if (title.contains("explicit") || title.contains("uncensored") || 
            title.contains("original version") || title.contains("album version")) {
            score += 3;
        }
        
        // Penalize multiple artists when only one is specified
        String lowerTitle = title.toLowerCase();
        String lowerChannelTitle = channelTitle.toLowerCase();
        String lowerArtistName = artistName.toLowerCase();
        
        // Count indicators of multiple artists
        int multiArtistIndicators = 0;
        
        // Check for featuring indicators
        if (lowerTitle.contains("feat.") || lowerTitle.contains("featuring") || 
            lowerTitle.contains("ft.") || lowerTitle.contains("with") ||
            lowerTitle.contains("vs.") || lowerTitle.contains("versus") ||
            lowerTitle.contains("vs ") || lowerTitle.contains(" x ") ||
            lowerTitle.contains("&") || lowerTitle.contains(" and ")) {
            multiArtistIndicators++;
        }
        
        // Check for multiple artist separators
        if (lowerTitle.contains(",") && !lowerTitle.contains("vol.") && !lowerTitle.contains("pt.")) {
            multiArtistIndicators++;
        }
        
        // Check if channel title contains multiple artists
        if (lowerChannelTitle.contains(",") || lowerChannelTitle.contains("&") || 
            lowerChannelTitle.contains(" and ") || lowerChannelTitle.contains(" x ")) {
            multiArtistIndicators++;
        }
        
        // Check if title contains artist names other than the specified one
        // (This is more complex and may need refinement based on common patterns)
        String[] commonCollabWords = {"feat", "featuring", "ft", "with", "vs", "versus", "and", "x"};
        for (String collabWord : commonCollabWords) {
            if (lowerTitle.contains(collabWord) && !lowerArtistName.contains(collabWord)) {
                // Extract text after collaboration indicator
                String afterCollab = lowerTitle.substring(lowerTitle.indexOf(collabWord) + collabWord.length());
                // If there's substantial text after the collaboration word, it's likely another artist
                if (afterCollab.trim().length() > 2 && !afterCollab.trim().startsWith("(") && 
                    !afterCollab.trim().startsWith("[")) {
                    multiArtistIndicators++;
                    break;
                }
            }
        }
        
        // Apply penalties based on multiple artist indicators
        if (multiArtistIndicators >= 3) {
            score -= 12; // Heavy penalty for clearly multiple artists
        } else if (multiArtistIndicators >= 2) {
            score -= 8;  // Moderate penalty for likely multiple artists
        } else if (multiArtistIndicators >= 1) {
            score -= 4;  // Light penalty for possible multiple artists
        }
        
        // Bonus for exact artist match without collaborations
        if (lowerTitle.contains(lowerArtistName) && multiArtistIndicators == 0) {
            score += 2;
        }
        
        // Additional penalties for non-audio content
        if (description.contains("music video") || description.contains("official video") ||
            description.contains("lyric video") || description.contains("lyrics") ||
            description.contains("directed by") || description.contains("cinematography")) {
            score -= 25;
        }
        
        // Penalize clean versions in description
        if (description.contains("clean") || description.contains("censored") ||
            description.contains("radio edit") || description.contains("family friendly") ||
            description.contains("edited for content") || description.contains("safe for radio")) {
            score -= 5;
        }
        
        // Bonus for audio-focused descriptions
        if (description.contains("audio only") || description.contains("official audio") ||
            description.contains("studio recording") || description.contains("full song")) {
            score += 5;
        }
        
        // Bonus for explicit/original content in description
        if (description.contains("explicit") || description.contains("uncensored") ||
            description.contains("original recording") || description.contains("album version")) {
            score += 2;
        }
        
        // Additional penalties for multiple artists in description
        String lowerDescription = description.toLowerCase();
        if (lowerDescription.contains("featuring") || lowerDescription.contains("collaboration") ||
            lowerDescription.contains("feat.") || lowerDescription.contains("ft.") ||
            lowerDescription.contains("guest artist") || lowerDescription.contains("duet")) {
            score -= 3;
        }
        
        // Filter and score based on video length
        if (result.durationMillis > 0 && lengthMillis > 0) {
            long lengthDiffMillis = Math.abs(result.durationMillis - lengthMillis);
            double lengthDiffSeconds = lengthDiffMillis / 1000.0;
            
            // Perfect match (within 5 seconds)
            if (lengthDiffSeconds <= 5) {
                score += 15;
            }
            // Very close match (within 15 seconds)
            else if (lengthDiffSeconds <= 15) {
                score += 10;
            }
            // Close match (within 30 seconds)
            else if (lengthDiffSeconds <= 30) {
                score += 5;
            }
            // Moderate match (within 60 seconds)
            else if (lengthDiffSeconds <= 60) {
                score += 2;
            }
            // Poor match (more than 2 minutes difference)
            else if (lengthDiffSeconds > 120) {
                score -= 5;
            }
            // Very poor match (more than 5 minutes difference)
            else if (lengthDiffSeconds > 300) {
                score -= 10;
            }
        }
        
        return score;
    }
    
    /**
     * Fetches video details including duration for a list of video IDs.
     */
    private void fetchVideoDetails(List<SearchResult> results) throws IOException, InterruptedException {
        if (results.isEmpty()) return;
        
        // Build comma-separated list of video IDs
        StringBuilder videoIds = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            if (i > 0) videoIds.append(",");
            videoIds.append(results.get(i).videoId);
        }
        
        try {
            String url = String.format("https://www.googleapis.com/youtube/v3/videos?part=contentDetails&id=%s&key=%s",
                    videoIds.toString(), apiKey);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                System.err.println("Failed to fetch video details: " + response.statusCode());
                return;
            }
            
            parseVideoDetails(response.body(), results);
            
        } catch (URISyntaxException e) {
            throw new IOException("Invalid URL syntax", e);
        }
    }
    
    /**
     * Parses video details and updates SearchResult objects with duration.
     */
    private void parseVideoDetails(String jsonResponse, List<SearchResult> results) {
        JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
        JsonArray items = root.getAsJsonArray("items");
        
        if (items != null) {
            for (JsonElement element : items) {
                JsonObject item = element.getAsJsonObject();
                String videoId = item.get("id").getAsString();
                JsonObject contentDetails = item.getAsJsonObject("contentDetails");
                
                if (contentDetails != null) {
                    String duration = contentDetails.get("duration").getAsString();
                    long durationMillis = parseDuration(duration);
                    
                    // Find the corresponding SearchResult and update it
                    for (SearchResult result : results) {
                        if (result.videoId.equals(videoId)) {
                            result.durationMillis = durationMillis;
                            break;
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Parses ISO 8601 duration string (e.g., "PT4M13S") to milliseconds.
     */
    private long parseDuration(String duration) {
        if (duration == null || !duration.startsWith("PT")) {
            return 0;
        }
        
        long totalMillis = 0;
        duration = duration.substring(2); // Remove "PT"
        
        // Parse hours
        int hIndex = duration.indexOf('H');
        if (hIndex != -1) {
            String hours = duration.substring(0, hIndex);
            totalMillis += Long.parseLong(hours) * 3600000;
            duration = duration.substring(hIndex + 1);
        }
        
        // Parse minutes
        int mIndex = duration.indexOf('M');
        if (mIndex != -1) {
            String minutes = duration.substring(0, mIndex);
            totalMillis += Long.parseLong(minutes) * 60000;
            duration = duration.substring(mIndex + 1);
        }
        
        // Parse seconds
        int sIndex = duration.indexOf('S');
        if (sIndex != -1) {
            String seconds = duration.substring(0, sIndex);
            totalMillis += Long.parseLong(seconds) * 1000;
        }
        
        return totalMillis;
    }

    /**
     * Inner class to represent a search result.
     */
    private static class SearchResult {
        final String videoId;
        final String title;
        final String channelTitle;
        final String description;
        long durationMillis = 0; // Duration in milliseconds, 0 if unknown
        
        SearchResult(String videoId, String title, String channelTitle, String description) {
            this.videoId = videoId;
            this.title = title;
            this.channelTitle = channelTitle;
            this.description = description;
        }
    }
}