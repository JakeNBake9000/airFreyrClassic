package com.mycompany.airfreyr;
//exiftool "-CoverArt<=coverart.jpg" -album="Short n' Sweet (Deluxe)" -title="Busy Woman" -genre="Pop" -track=10 -total=20 busy.m4a

import java.io.*;

public class mp3Tagger {
    public void writeTrack(String artist, String trackName, String album, String coverart, int track, String filepath) throws IOException, InterruptedException {
        Process pbx = Runtime.getRuntime().exec(new String[]{"bash", "-c", "sacad \"" + "\" \"" + album + "\" 640 " + "\"" + coverart + "\""});
                    
            //System.out.println("sacad \"" + "\" \"" + album + "\" 640 " + "\"" + coverart + "\"");
                    
            try {
                BufferedWriter loggerx = new BufferedWriter(new FileWriter("sacad_log.txt"));
                //System.out.println("sacad \"" + track.get("artistName").getAsString() + "\" \"" + album.get("collectionName").getAsString() + "\" 640 AlbumArt_" + revisedAlbum + ".jpeg");
                BufferedReader readerx = new BufferedReader(new InputStreamReader(pbx.getInputStream()));
                String line;
                while ((line = readerx.readLine()) != null) {
                    //System.out.println(line);
                    loggerx.write(line);
                }
                
                int exitCode = pbx.waitFor();
                        
                System.out.println("        🖼️  Got new album artwork for " + album);
                
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            
            } catch (IOException e) {
                System.err.println("        ❌ SACAD failed!: " + e.getMessage());
            }
        
        Process pb = Runtime.getRuntime().exec(new String[]{"bash", "-c", "exiftool -overwrite_original \"-CoverArt<=" + coverart + "\" -album=\"" + album + "\" -title=\"" + trackName + "\" -artist=\"" + artist + "\" -track=" + track + " " + filepath});
        //System.out.println("exiftool \"-CoverArt<=" + coverart + "\" -album=\"" + album + "\" -title=\"" + trackName + "\" -artist=\"" + artist + "\" -track=" + track + " \"" + filepath);
        
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(pb.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                //System.out.println(line);
                BufferedWriter logger = new BufferedWriter(new FileWriter("exiftool.txt"));
                logger.write(line);
            }
        } catch (IOException e) {
        
        }
    }
}
