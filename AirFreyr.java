/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.airfreyr;

import java.io.*;
import java.net.*;
import java.util.regex.*;
import com.google.gson.*;

//import net.jthink.jaudiotagger.*;

public class AirFreyr {

    public static void main(String[] args) throws Exception {
        
        System.out.println("\u001B[31m         _      ______\u001B[0m");                              
        System.out.println("\u001B[38;5;208m  ____ _(_)____/ ____/_______  __  _______\u001B[0m");          
        System.out.println("\u001B[33m / __ `/ / ___/ /_  / ___/ _ \\/ / / / ___/\u001B[0m");          
        System.out.println("\u001B[32m/ /_/ / / /  / __/ / /  /  __/ /_/ / /\u001B[0m");              
        System.out.println("\u001B[36m\\__,_/_/_/  /_/   /_/   \\___/\\__, / /___ _    _____\u001B[0m"); 
        System.out.println("\u001B[34m                            /____// /   | |  / /   |\u001B[0m");
        System.out.println("\u001B[38;5;54m                             __  / / /| | | / / /| |\u001B[0m");
        System.out.println("\u001B[35m                            / /_/ / ___ | |/ / ___ |\u001B[0m");
        System.out.println("\u001B[95m                            \\____/_/  |_|___/_/  |_| [no-ai]\u001B[0m" + "\n");
        System.out.println("(c) Jake Prescott (JakeNBake9000) - airFreyr Classic\n");
        
        String ytdlpPath = "/usr/local/bin/yt-dlp";
        File ytdlp = new File(ytdlpPath);
        
        String sacadPath = "/usr/local/bin/sacad";
        File sacad = new File(sacadPath);
        
        String passkeyPath = "/Library/Application Support/AirFreyr/allgood.airfryer";
        File runkey = new File(passkeyPath);
        
        System.out.println("🛎️  Checking dependencies + preferences");
        /*
        if (!runkey.exists()) {
            System.out.println("    🛟 Could not verify dependency integrity. Attempting to detect + install dependencies");
            System.out.println("        [Not implemented]");
            return;
        }
        */
        if (args.length == 0) {
            System.out.println("Usage: java AirFreyr <Apple Music Album URL>");
            return;
        }
        /*
        if (!ytdlp.exists() || !sacad.exists()) {
            System.out.println("❌ Could not contact a required dependency. Please reinstall");
            return;
        }
        */
        System.out.println("🛎️  Contacting Apple Music...");

        String appleUrl = args[0];
        URI uri = URI.create(appleUrl);
        String path = uri.getPath();

        Pattern pattern = Pattern.compile("^/(\\w{2})/album/([^/]+)/(\\d+)");
        Matcher matcher = pattern.matcher(path);

        if (!matcher.find()) {
            System.out.println("    ❌ Could not parse Apple Music album URL.");
            return;
        }
        
        System.out.println("    ‼️ Connected to Apple Music");

        String storefront = matcher.group(1);
        String rawName = matcher.group(2);
        String searchTerm = rawName.replace("-", "+");

        // Step 1: Search iTunes for album
        System.out.println("🛎️ Starting album tasks");
        
        String searchUrl = String.format(
            "https://itunes.apple.com/search?term=%s&entity=album&country=%s",
            URLEncoder.encode(searchTerm, "UTF-8"), storefront
        );

        String searchResponse = fetchJson(searchUrl);
        JsonArray searchResults = JsonParser.parseString(searchResponse)
                .getAsJsonObject().getAsJsonArray("results");

        if (searchResults.size() == 0) {
            System.out.println("    ❌ Album not found in iTunes.");
            return;
        }

        JsonObject albumObj = searchResults.get(0).getAsJsonObject();
        int collectionId = albumObj.get("collectionId").getAsInt();

        // Step 2: Lookup album tracks
        String lookupUrl = "https://itunes.apple.com/lookup?id=" + collectionId + "&entity=song";
        System.out.println(lookupUrl);
        String lookupResponse = fetchJson(lookupUrl);

        JsonArray allResults = JsonParser.parseString(lookupResponse)
                .getAsJsonObject().getAsJsonArray("results");

        try (
            BufferedWriter metaWriter = new BufferedWriter(new FileWriter("itunes_album_metadata.txt"));
            BufferedWriter ytWriter = new BufferedWriter(new FileWriter("ytmusic_links.txt"))
        ) {
            JsonObject album = allResults.get(0).getAsJsonObject();
            
            System.out.println("    🚨 Resolving album resorces for " + album.get("collectionName").getAsString());

            metaWriter.write("=== Album ===\n");
            writeField(metaWriter, "Album Name", album.get("collectionName"));
            writeField(metaWriter, "Artist", album.get("artistName"));
            writeField(metaWriter, "Genre", album.get("primaryGenreName"));
            writeField(metaWriter, "Release Date", album.get("releaseDate"));
            writeField(metaWriter, "Track Count", album.get("trackCount"));
            writeField(metaWriter, "iTunes URL", album.get("collectionViewUrl"));
            metaWriter.write("\n");

            ytWriter.write("Track Links for Album: " + album.get("collectionName").getAsString() + "\n\n");
            metaWriter.write("=== Tracks ===\n");
            
            System.out.println("🛎️  Starting track specific tasks");

            for (int i = 1; i < allResults.size(); i++) {
                JsonObject track = allResults.get(i).getAsJsonObject();
                if (!"track".equals(track.get("wrapperType").getAsString())) continue;

                String trackTitle = track.get("trackName").getAsString();
                String artistName = track.get("artistName").getAsString();
                String albumName = album.get("collectionName").getAsString();
                
                String alphaAlbum = album.get("collectionName").getAsString().replaceAll("[^a-zA-Z0-9 ]", "");
                String revisedAlbum = alphaAlbum.replace(" ", "");
                
                String artPath = System.getProperty("user.home") + "/AirFreyr/Library/" + track.get("artistName").getAsString().replaceAll("[^a-zA-Z0-9 ]", "").replace(" ", "\\ ") + "/" + album.get("collectionName").getAsString().replaceAll("[^a-zA-Z0-9 ]", "").replace(" ", "\\ ") + "/AlbumArt.jpeg";
                String enumPath = System.getProperty("user.home") + "/AirFreyr/Library/" + track.get("artistName").getAsString().replaceAll("[^a-zA-Z0-9 ]", "") + "/" + album.get("collectionName").getAsString().replaceAll("[^a-zA-Z0-9 ]", "") + "/AlbumArt.jpeg";
                File art = new File(artPath);
                
                System.out.println("    🚨 Resolving resources for " + track.get("trackName").getAsString() + " by " + track.get("artistName").getAsString());
                
                
                
                metaWriter.write("Track " + i + ":\n");
                writeField(metaWriter, "  Name", track.get("trackName"));
                writeField(metaWriter, "  Duration (ms)", track.get("trackTimeMillis"));
                writeField(metaWriter, "  Track Number", track.get("trackNumber"));
                writeField(metaWriter, "  Preview URL", track.get("previewUrl"));
                metaWriter.write("\n");

                // Use yt-dlp to find and download audio file from YouTube Music
                int duration = track.get("trackTimeMillis").getAsInt();
                String link = YouTubeMusicSearcher.searchTrack(artistName, trackTitle, albumName, enumPath, track.get("trackNumber").getAsInt(), duration);
                
                ytWriter.write(String.format("Track: %s\nArtist: %s\nYouTube Music: %s\n\n",
                    trackTitle, artistName, (link != null ? link : "❌ Not Found")));
            
            
            
             }

            System.out.println("✅ Metadata written to itunes_album_metadata.txt");
            System.out.println("✅ SACAD Art Scraper logs written to sacad_log.txt");
            System.out.println("✅ YouTube Music links written to ytmusic_links.txt");
            System.out.println("✅ yt-dlp log written to yt_music_log.txt");
        }
    }

    private static String fetchJson(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String input;
        while ((input = in.readLine()) != null) response.append(input);
        in.close();
        return response.toString();
    }

    private static void writeField(BufferedWriter writer, String label, JsonElement element) throws IOException {
        if (element != null && !element.isJsonNull()) {
            writer.write(label + ": " + element.getAsString() + "\n");
        }
    }
}