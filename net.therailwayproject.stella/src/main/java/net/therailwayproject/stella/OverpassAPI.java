package net.therailwayproject.stella;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;

public class OverpassAPI {

	public OverpassAPI() {
		
	}
	
	public String getDataAndWrite(String query, String fileName, boolean trimNodeTags) {
	    @SuppressWarnings("deprecation")
		String encodedQuery = URLEncoder.encode(query);
	    String apiUrl = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;

	    try {
	        URL url = new URL(apiUrl);

	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("GET");

	        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	        	     BufferedWriter out = Files.newBufferedWriter(Paths.get("res/" + fileName + ".osm"))) {

	        	    String line;
	        	    while ((line = in.readLine()) != null) {
	        	        if (!trimNodeTags || !line.trim().startsWith("<node")) {
	        	            out.write(line);
	        	            out.newLine();
	        	        }
	        	    }
	        	    return "File written successfully.";
	        	}
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return null;
	}
}
