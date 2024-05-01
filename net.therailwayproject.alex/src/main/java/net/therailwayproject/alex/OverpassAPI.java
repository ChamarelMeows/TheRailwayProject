package net.therailwayproject.alex;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

public class OverpassAPI {

	public OverpassAPI() {
		
	}
	
	public String getDataAndWrite(String query, String fileName, boolean trimNodeTags) {
	    String encodedQuery = URLEncoder.encode(query);
	    String apiUrl = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;

	    try {
	        URL url = new URL(apiUrl);

	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("GET");

	        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
	            String response = in.lines().collect(Collectors.joining("\n"));
	            if (trimNodeTags) {
	                response = response.lines()
	                        .filter(line -> !line.trim().startsWith("<node"))
	                        .collect(Collectors.joining("\n"));
	            }

	            Path filePath = Paths.get("res/" + fileName + ".osm");
	            Files.write(filePath, response.getBytes());

	            return response;
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return null;
	}
}
