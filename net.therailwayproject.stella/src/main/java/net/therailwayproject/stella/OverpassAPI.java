package net.therailwayproject.stella;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OverpassAPI {
	
	private long lastPrintedKB = -1;

	public OverpassAPI() {
		
	}
	
	public String getDataAndWrite(String query, String fileName) {
	    String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
	    String apiUrl = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;

	    try {
	        URL url = new URL(apiUrl);
	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("GET");

	        Path filePath = Paths.get("res", fileName + ".osm");
	        Files.createDirectories(filePath.getParent());

	        System.out.println("Downloading data and saving to: " + filePath.toAbsolutePath());

	        try (InputStream in = new BufferedInputStream(connection.getInputStream());
	             OutputStream out = Files.newOutputStream(filePath)) {
	            byte[] buffer = new byte[8192];
	            int bytesRead;
	            long totalBytesRead = 0;

	            while ((bytesRead = in.read(buffer)) != -1) {
	                out.write(buffer, 0, bytesRead);
	                totalBytesRead += bytesRead;

                    if (totalBytesRead / 1024 != lastPrintedKB) {
                        lastPrintedKB = totalBytesRead / 1024;
                        SpeedCalculator.INSTANCE().progressMsg = "Downloaded: " + lastPrintedKB + " KB";
                    }
	            }
	        }

	        System.out.println("File written successfully.");
	        SpeedCalculator.INSTANCE().progressMsg = "Saved data to: " + filePath.getFileName();
	        return "File written successfully to: " + filePath.toAbsolutePath();
	    } catch (IOException e) {
	        System.err.println("Error occurred during data download or file writing.");
	        e.printStackTrace();
	    }
	    return null;
	}

}
