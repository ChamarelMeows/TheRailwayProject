package net.therailwayproject.alex;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class OverpassAPI {

	public OverpassAPI() {
		
	}
	
	public String getDataAndWrite(String query, String fileName, boolean trimNodeTags) {
	    String encodedQuery = URLEncoder.encode(query);

	    try {
	        String apiUrl = "https://overpass-api.de/api/interpreter?data=" + encodedQuery;
	        URL url = new URL(apiUrl);

	        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	        connection.setRequestMethod("GET");

	        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
	        String inputLine;
	        StringBuilder response = new StringBuilder();
	        if (trimNodeTags) {
	            while ((inputLine = in.readLine()) != null) {
	                if (!inputLine.startsWith("  <node"))
	                    response.append(inputLine).append("\n");
	            }
	        } else {
	            while ((inputLine = in.readLine()) != null) {
	                response.append(inputLine).append("\n");
	            }
	        }

	        in.close();

	        try (BufferedWriter writer = new BufferedWriter(new FileWriter("res/" + fileName + ".osm"))) {
	            writer.write(response.toString());
	        } catch (IOException e) {
	            e.printStackTrace();
	        }

	        connection.disconnect();

	        return response.toString();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	    return null;
	}
}
