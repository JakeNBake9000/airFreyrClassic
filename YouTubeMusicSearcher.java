/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.mycompany.airfreyr;

import com.mycompany.airfreyr.YouTubeMusicLinkFetcher;
import java.io.*;

public class YouTubeMusicSearcher {
    private static final String YOUTUBE_API_KEY = "API_KEY";
    private static final String SPOTIFY_CLIENT_SECRET = "CLIENT_SECRET";
    private static final String SPOTIFY_API_KEY = "API_KE"; // Replace with your actual API key
    private static YouTubeMusicLinkFetcher linkFetcher = new YouTubeMusicLinkFetcher(YOUTUBE_API_KEY);
    
    public static String searchTrack(String artist, String track, String album, String artpath, int trackInt, int duration) throws IOException {
        String alphaTrack = track.replaceAll("[^a-zA-Z0-9 ]", "");
        String alphaAlbum = album.replaceAll("[^a-zA-Z0-9 ]", "");
        String alphaArtist = artist.replaceAll("[^a-zA-Z0-9 ]", "");
        
        // Convert duration from seconds to milliseconds for the API
        long durationMillis = duration;
        
        String query = null;
        try {
            // Use the new YouTubeMusicLinkFetcher class
            query = linkFetcher.getYouTubeMusicLink(track, artist, album, durationMillis);
            
            if (query == null) {
                System.err.println("❌ No YouTube Music link found for: " + artist + " - " + track);
                return null;
            }
            
        } catch (IOException | InterruptedException e) {
            System.err.println("❌ Error fetching YouTube Music link: " + e.getMessage());
            return null;
        }
        
        System.out.println("        🕵️  Searching YouTube Music for sources...");
        
        String trackPath = "~/AirFreyr/Library/" + alphaArtist + "/" + alphaAlbum + "/" + alphaTrack + ".m4a\" ";
        String args = "-f m4a --no-playlist" + " -o \"" + trackPath + " -6 \"" + query + "\" ";
        String enumeratedPath = "~/AirFreyr/Library/" + alphaArtist.replace(" ", "\\ ") + "/" + alphaAlbum.replace(" ", "\\ ") + "/" + alphaTrack.replace(" ", "\\ ") + ".m4a";
        
        String revisedQuery = "\"" + query + "\"";
        
        Process pb = Runtime.getRuntime().exec(new String[]{"bash", "-c", "yt-dlp " + args});
        
        //System.out.println("        yt-dlp" + " " + args);
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(pb.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(pb.getErrorStream()));
            
            // Create logger outside the loop and ensure it's closed properly
            BufferedWriter logger = new BufferedWriter(new FileWriter("yt_music_log.txt", true));
            
            try {
                // Read stdout
                String line;
                while ((line = reader.readLine()) != null) {
                    //System.out.println(line);
                    logger.write(line);
                    logger.newLine();
                }
                
                // Read stderr
                while ((line = errorReader.readLine()) != null) {
                    System.err.println(line);
                    logger.write("ERROR: " + line);
                    logger.newLine();
                }
                
                // Wait for process to complete
                int exitCode = pb.waitFor();
                if (exitCode != 0) {
                    System.err.println("❌ yt-dlp failed with exit code: " + exitCode);
                    return null;
                }
                
            } finally {
                logger.close();
                reader.close();
                errorReader.close();
            }
            
            // Tag the downloaded file
            
            mp3Tagger tag = new mp3Tagger();
            tag.writeTrack(artist, track, album, artpath, trackInt, enumeratedPath);
            
            System.out.println("        🪗 Successfully downloaded " + track);
            
        } catch (IOException e) {
            System.err.println("        ❌ yt-dlp failed: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            System.err.println("        ❌ yt-dlp was interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
            return null;
        }
        
        return query;
    }
    
    /**
     * Alternative method that allows passing a custom API key
     */
    public static String searchTrackWithApiKey(String artist, String track, String album, String artpath, 
                                             int trackInt, int duration, String apiKey) throws IOException {
        YouTubeMusicLinkFetcher customFetcher = new YouTubeMusicLinkFetcher(apiKey);
        
        String alphaTrack = track.replaceAll("[^a-zA-Z0-9 ]", "");
        String alphaAlbum = album.replaceAll("[^a-zA-Z0-9 ]", "");
        String alphaArtist = artist.replaceAll("[^a-zA-Z0-9 ]", "");
        
        long durationMillis = duration * 1000L;
        
        String query = null;
        try {
            query = customFetcher.getYouTubeMusicLink(track, artist, album, durationMillis);
            
            if (query == null) {
                System.err.println("❌ No YouTube Music link found for: " + artist + " - " + track);
                return null;
            }
            
        } catch (IOException | InterruptedException e) {
            System.err.println("❌ Error fetching YouTube Music link: " + e.getMessage());
            return null;
        }
        
        // Rest of the method remains the same as searchTrack...
        // (You can extract the common download logic into a separate method if needed)
        
        return query;
    }
    
    /**
     * Set a new API key for the default fetcher
     */
    public static void setApiKey(String apiKey) {
        linkFetcher = new YouTubeMusicLinkFetcher(apiKey);
    }
}