package net.therailwayproject.stella;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.jdesktop.swingx.mapviewer.GeoPosition;

public class Exporter {

	public void exportAsGPX(List<GeoPosition> cords, String fileName) {
	    if (cords == null || cords.isEmpty()) {
	        System.out.println("No coordinates to export.");
	        return;
	    }

	    try (BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
	        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
	        writer.write("<gpx version=\"1.1\" creator=\"" + System.getProperty("user.name") + "\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n");

	        writer.write("  <trk>\n");
	        writer.write("    <name>My Path</name>\n");
	        writer.write("    <trkseg>\n");
	        
	        for (GeoPosition coordinate : cords) {
	            if (coordinate == null ||
	                coordinate.getLatitude() < -90 || coordinate.getLatitude() > 90 ||
	                coordinate.getLongitude() < -180 || coordinate.getLongitude() > 180) {
	                System.out.println("Skipping invalid coordinate: " + coordinate);
	                continue;
	            }

	            writer.write(String.format(Locale.US,
	                "      <trkpt lat=\"%.7f\" lon=\"%.7f\"></trkpt>\n",
	                coordinate.getLatitude(), coordinate.getLongitude()
	            ));
	        }

	        writer.write("    </trkseg>\n");
	        writer.write("  </trk>\n");
	        
	        writer.write("</gpx>\n");
	    } catch (IOException e) {
	        System.err.println("Error writing GPX file: " + e.getMessage());
	        e.printStackTrace();
	    }
	}
}
