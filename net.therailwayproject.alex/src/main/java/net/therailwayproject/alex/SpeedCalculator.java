package net.therailwayproject.alex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class SpeedCalculator {

	public List<RailwayTrack> tracks;
	public List<Station> stations;
	private GeometryFactory geometryFactory;
	private OverpassAPI op;
	private static SpeedCalculator sp;
	public boolean doneLoading = false, loadingFromFile = false;
	private int index = 0;
	public Map<Long, WayNode> wayNodesMap;
	public double progress;
	public String progressMsg = "";
	public List<GeoPosition> routeCords;
	
	public static SpeedCalculator INSTANCE() {
		return sp;
	}

	public SpeedCalculator() {
		sp = this;
		tracks = new ArrayList<RailwayTrack>();
		stations = new ArrayList<Station>();
		routeCords = new ArrayList<GeoPosition>();
		wayNodesMap = new HashMap<>();
		op = new OverpassAPI();
		if (!new File("res/trackData.ser").exists() || !new File("res/stationData.ser").exists()) {
			geometryFactory = new GeometryFactory();
			String coordinates = "(52.00405169419172, 4.21514369248833,52.48906017795534, 7.4453551270490035)";
			progressMsg = "Downloading data";
			downloadData(coordinates);
		} else {
			loadingFromFile = true;
			progressMsg = "Loading data";
			loadTrackData();
			progress = 0.5;
			loadStationData();
			progress = 1;
		}
		doneLoading = true;
	}
	
	public void downloadData(String coordinates) {
		progress = 0;
		progressMsg = "Downloading data";
		tracks = new ArrayList<RailwayTrack>();
		stations = new ArrayList<Station>();
		op.getDataAndWrite("way[\"railway\"=\"rail\"]" + coordinates + ";\r\n" + "out meta geom;\r\n" + ">;\r\n"
				+ "out skel qt;", "requestedTracks", true);
		op.getDataAndWrite("(node[\"railway\"=\"station\"]" + coordinates + ";);\r\n" + "out meta geom;\r\n"
				+ ">;\r\n" + "out skel qt;", "requestedStations", false);
		loadDataFrom("res/requestedTracks.osm", "res/requestedStations.osm");
		doneLoading = true;
	}

	public void loadDataFrom(String locationTracks, String locationStations) {
		long a = System.currentTimeMillis();
		progressMsg = "Loading railway tracks";
		loadRailwayTracks(locationTracks);
		progress = 0.125;
		System.out.println(progress);
		progressMsg = "Segmenting railway tracks";
		segmentTracks();
		progress = 0.25;
		progressMsg = "Making connections";
		makeConnections();
		progress = 0.375;
		progressMsg = "Calculating track lengths";
		calculateLengths();
		progress = 0.5;
		progressMsg = "Loading stations";
		loadStations(locationStations);
		progress = 0.625;
		progressMsg = "Finding station tracks";
		loadStationTracks(400);
		progress = 0.75;
		progressMsg = "Writing track data";
		writeTrackData();
		progress = 0.875;
		progressMsg = "Writing station data";
		writeStationData();
		progress = 1;
		System.out.println("Total computing time: " + (System.currentTimeMillis() - a) + "ms");
		progressMsg = "";
		progress = 0;
	}

	public void loadRailwayTracks(String location) {
		try {
			FileInputStream fileInputStream = new FileInputStream(location);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			Document doc = dBuilder.parse(fileInputStream);
			doc.getDocumentElement().normalize();

			NodeList wayList = doc.getElementsByTagName("way");

			for (int i = 0; i < wayList.getLength(); i++) {
				Element wayElement = (Element) wayList.item(i);

				int maxSpeed = getMaxSpeed(wayElement);
				String railwayId = wayElement.getAttribute("id");

				RailwayTrack rt = new RailwayTrack(Integer.parseInt(railwayId));
				rt.setSpeed(maxSpeed);

				NodeList nodeList = wayElement.getElementsByTagName("nd");

				for (int j = 0; j < nodeList.getLength(); j++) {
					Element nodeElement = (Element) nodeList.item(j);
					String nodeId = nodeElement.getAttribute("ref");
					String latitude = nodeElement.getAttribute("lat");
					String longitude = nodeElement.getAttribute("lon");
					if (nodeId != null && latitude != null && longitude != null) {
						WayNode wn = new WayNode(Long.parseLong(nodeId), Double.parseDouble(latitude),
								Double.parseDouble(longitude));
						if (!wayNodesMap.containsKey(wn.getNodeId())) {
							wayNodesMap.put(wn.getNodeId(), wn);
						}
						rt.addNode((wn.getNodeId()));
					}
				}
				if (rt != null) {
					tracks.add(rt);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void makeConnections() {
		Set<RailwayTrack> checkedSet = new HashSet<>(); 
		for (int i = 0; i < tracks.size(); i++) {
			RailwayTrack track1 = tracks.get(i);
			WayNode firstNode1 = longToWayNode(track1.getNodes().get(0));
			WayNode lastNode1 = longToWayNode(track1.getNodes().get(track1.getNodes().size() - 1));

			for (int j = i + 1; j < tracks.size(); j++) {
				RailwayTrack track2 = tracks.get(j);
				if(checkedSet.contains(track2))
					continue;
				WayNode node = longToWayNode(track2.getNodes().get(0));
				if(Math.max(firstNode1.getLatitude(), node.getLatitude()) - Math.min(firstNode1.getLatitude(), node.getLatitude()) > 0.5 || Math.max(firstNode1.getLongitude(), node.getLongitude()) - Math.min(firstNode1.getLongitude(), node.getLongitude()) > 0.5)
					continue;
				if (track1 != track2) {
					WayNode lastNode2 = longToWayNode(track2.getNodes().get(track2.getNodes().size() - 1));

					if (areNodesConnected(firstNode1, node) || areNodesConnected(firstNode1, lastNode2)
							|| areNodesConnected(lastNode1, node) || areNodesConnected(lastNode1, lastNode2)) {
						if (!track1.getConnections().contains(track2.getId()))
							track1.addConnection(track2.getId());
						if (!track2.getConnections().contains(track1.getId()))
							track2.addConnection(track1.getId());
					}
				}
			}
			checkedSet.add(track1);
		}
	}

	public void calculateLengths() {
		DecimalFormat df = new DecimalFormat("0.000");

		for (RailwayTrack track : tracks) {
			double totalDistance = 0.0;

			for (int i = 0; i < track.getNodes().size() - 1; i++) {
				WayNode currentNode = longToWayNode(track.getNodes().get(i));
				WayNode nextNode = longToWayNode(track.getNodes().get(i + 1));
				double distance = calculateDistance(currentNode.getLatitude(), currentNode.getLongitude(),
						nextNode.getLatitude(), nextNode.getLongitude());
				totalDistance += distance;
			}

			double length = Double.valueOf(df.format(totalDistance).replace(",", "."));
			double weight = Double.valueOf(df.format(length / track.getSpeed()).replace(",", "."));

			track.setLength(length);
			track.setWeight(weight);
		}
	}

	public void loadStations(String location) {
		try {
			FileInputStream fileInputStream = new FileInputStream(location);

			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

			Document doc = dBuilder.parse(fileInputStream);
			doc.getDocumentElement().normalize();

			NodeList nodeList = doc.getElementsByTagName("node");

			for (int i = 0; i < nodeList.getLength(); i++) {
				Element nodeElement = (Element) nodeList.item(i);

				String name = "";
				double lat = Double.parseDouble(nodeElement.getAttribute("lat"));
				double lon = Double.parseDouble(nodeElement.getAttribute("lon"));

				NodeList tagList = nodeElement.getElementsByTagName("tag");
				for (int j = 0; j < tagList.getLength(); j++) {
					Element tagElement = (Element) tagList.item(j);
					if (tagElement.getAttribute("k").equals("name")) {
						name = tagElement.getAttribute("v");
						break;
					}
				}
				if (name != "") {
					stations.add(new Station(name, lat, lon));
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void loadStationTracks(double distanceThreshold) {
		Iterator<Station> iterator = stations.iterator();
		while (iterator.hasNext()) {
			Station station = iterator.next();
			boolean stationHasTracks = false;

			for (RailwayTrack track : tracks) {
				for (Long l : track.getNodes()) {
					WayNode wn = longToWayNode(l);
					double distance = calculateDistance(station.getLat(), station.getLon(), wn.getLatitude(),
							wn.getLongitude());
					if (distance <= distanceThreshold) {
						station.addTrack(track.getId());
						stationHasTracks = true;
						break;
					}
				}
			}

			if (!stationHasTracks) {
				iterator.remove();
			}
		}
	}

	public void writeTrackData() {
	    try {
	        String path = "res/trackData.ser";
	        FileOutputStream fos = new FileOutputStream(path);
	        ObjectOutputStream oos = new ObjectOutputStream(fos);
	        
	        oos.writeObject(tracks);
	        
	        oos.close();
	        fos.close();
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void writeStationData() {
		try {
			String path = "res/stationData.ser";
			FileOutputStream fos = new FileOutputStream(path);
	        ObjectOutputStream oos = new ObjectOutputStream(fos);
	        
	        oos.writeObject(stations);
	        
	        oos.close();
	        fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@SuppressWarnings("unchecked")
	public void loadTrackData() {
	    try {
	        String path = "res/trackData.ser";
	        FileInputStream fis = new FileInputStream(path);
	        ObjectInputStream ois = new ObjectInputStream(fis);

	        tracks = (List<RailwayTrack>) ois.readObject();

	        ois.close();
	        fis.close();
	    } catch (IOException | ClassNotFoundException e) {
	        e.printStackTrace();
	    }
	}

	@SuppressWarnings("unchecked")
	public void loadStationData() {
	    try {
	        String path = "res/stationData.ser";
	        FileInputStream fis = new FileInputStream(path);
	        ObjectInputStream ois = new ObjectInputStream(fis);
	        
	        stations = (ArrayList<Station>) ois.readObject();
	        
	        ois.close();
	        fis.close();
	    } catch (IOException | ClassNotFoundException e) {
	        e.printStackTrace();
	    }
	}

	public void outputToMap(RailwayTrack start, RailwayTrack end, List<RailwayTrack> stops) {
	    routeCords.clear();
	    Astar a = new Astar(this);
	    List<Integer> fullPath = new ArrayList<>();
	    double totalLength = 0.0;
	    double totalTime = 0;
	    WayNode connection = null;

	    if(stops != null) {
	    	fullPath.addAll(a.findPath(start, stops.get(0)));

		    for (int i = 0; i < stops.size() - 1; i++) {
		        fullPath.addAll(a.findPath(stops.get(i), stops.get(i + 1)));
		    }

		    fullPath.addAll(a.findPath(stops.get(stops.size() - 1), end));
	    } else {
	    	fullPath.addAll(a.findPath(start, end));
	    }
	    
	    StringBuilder contentBuilder = new StringBuilder();

	    for (int i = 0; i < fullPath.size()-1; i++) {
	        RailwayTrack rt = getTrackById(fullPath.get(i));

	        if (i == 0) {
	            connection = findConnectingNode(rt, getTrackById(fullPath.get(1)));
	            if (connection.getNodeId() == rt.getNodes().get(0)) {
	                Collections.reverse(rt.getNodes());
	            }
	        } else {
	            if (connection.getNodeId() != rt.getNodes().get(0)) {
	                Collections.reverse(rt.getNodes());
	            }
	            if (rt.getId() != getTrackById(fullPath.get(fullPath.size() - 1)).getId())
	                connection = findConnectingNode(rt, getTrackById(fullPath.get(i + 1)));
	        }

	        totalLength += rt.getLength();
	        totalTime += rt.getWeight();

	        WayNode lastNode = null;
	        for (Long l : rt.getNodes()) {
	        	WayNode wn = longToWayNode(l);
	        	if(wn != lastNode) {
	        		routeCords.add(new GeoPosition(wn.getLatitude(), wn.getLongitude()));
		            String coordinate = "[" + wn.getLatitude() + ", " + wn.getLongitude() + "]";
		            contentBuilder.append(coordinate).append(",\n");
	        	}
	            lastNode = wn;
	        }
	    }

	    double averageSpeed = Math.round((totalLength / totalTime));
	    long roundedTotalLength = Math.round(totalLength / 1000);
	    totalTime = Math.round((roundedTotalLength / averageSpeed) * 60);

	    String content = contentBuilder.toString();
	    writeToFile("res/index.html", content);

	    System.out.println("Total length of the path: " + roundedTotalLength + " km");
	    System.out.println("Average Speed: " + averageSpeed + " km/h");
	    System.out.println("Total time: " + totalTime + " minutes");
	}

	public void writeToFile(String filePath, String content) {
		try {
			FileWriter writer = new FileWriter(filePath);
			writer.write("<!DOCTYPE html>\n");
			writer.write("<html>\n");
			writer.write("<head>\n");
			writer.write("    <title>The Railway Project</title>\n");
			writer.write("    <link rel=\"stylesheet\" href=\"https://unpkg.com/leaflet/dist/leaflet.css\" />\n");
			writer.write("    <script src=\"https://unpkg.com/leaflet/dist/leaflet.js\"></script>\n");
			writer.write("    <style>\n");
			writer.write("        #map {\n");
			writer.write("            height: 1297px;\n");
			writer.write("        }\n");
			writer.write("    </style>\n");
			writer.write("</head>\n");
			writer.write("<body>\n");
			writer.write("    <div id=\"map\"></div>\n");
			writer.write("    <script>\n");
			writer.write("        var map = L.map('map').setView([51.505, -0.09], 13);\n");
			writer.write("        map.setMaxZoom(20);");
			writer.write("        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {\n");
			writer.write("            attribution: '© OpenStreetMap contributors'\n");
			writer.write("        }).addTo(map);\n");
			writer.write("        var railwayTrackCoordinates = [\n");
			writer.write("			" + content);
			writer.write("		 ];\n");
			writer.write(
					"   	 var polyline = L.polyline(railwayTrackCoordinates, { color: 'red', weight: 5 }).addTo(map);\n");
			writer.write("   	 map.fitBounds(polyline.getBounds());\n");
			writer.write("    </script>\n");
			writer.write("</body>\n");
			writer.write("</html>");
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public int getMaxSpeed(Element wayElement) {
		String maxSpeed = "";
		NodeList tagList = wayElement.getElementsByTagName("tag");
		for (int i = 0; i < tagList.getLength(); i++) {
			Element tagElement = (Element) tagList.item(i);
			String key = tagElement.getAttribute("k");
			if ("maxspeed".equals(key)) {
				maxSpeed = tagElement.getAttribute("v");
			}
		}
		if (maxSpeed == "")
			return 100;

		StringBuilder result = new StringBuilder();
		if(maxSpeed.contains("mph")) {
			for (char c : maxSpeed.toCharArray()) {
				if (Character.isDigit(c)) {
					result.append(c);
				} else {
					break;
				}
			}
			if (result.toString() == "")
				return 100;
			return (int) (Integer.parseInt(result.toString()) * 1.60934);
		} else {
			for (char c : maxSpeed.toCharArray()) {
				if (Character.isDigit(c)) {
					result.append(c);
				} else {
					break;
				}
			}
			if (result.toString() == "")
				return 100;
			return Integer.parseInt(result.toString());
		}
	}

	public LineString createLineString(List<WayNode> nodes) {
		Coordinate[] coordinates = new Coordinate[nodes.size()];
		for (int i = 0; i < nodes.size(); i++) {
			WayNode node = nodes.get(i);
			coordinates[i] = new Coordinate(node.getLongitude(), node.getLatitude());
		}
		return geometryFactory.createLineString(coordinates);
	}

	public RailwayTrack getTrackById(int wayId) {
		for (RailwayTrack track : tracks) {
			if (track.getId() == wayId) {
				return track;
			}
		}
		return null;
	}

	public double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLon = Math.toRadians(lon2 - lon1);

		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return 6371000 * c;
	}

	public List<Double> avgLatLon(List<Double> lat, List<Double> lon) {
		double averageLat = lat.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double averageLon = lon.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		return Arrays.asList(averageLat, averageLon);
	}

	public void segmentTracks() {
		List<Long> junctionNodes = createJunctionNodes();
		List<RailwayTrack> segmentedTracks = new ArrayList<>();

	    for (RailwayTrack track : tracks) {
	        List<Long> segmentNodes = new ArrayList<>();
	        for (Long l : track.getNodes()) {
	            segmentNodes.add(l);

	            if (shouldTrackBeSplit(l, track, junctionNodes)) {
	                RailwayTrack segment = createSegmentTrack(segmentNodes, track);
	                segmentedTracks.add(segment);

	                segmentNodes = new ArrayList<>();
	                segmentNodes.add(l);
	            }
	        }
	        if (!segmentNodes.isEmpty()) {
	            segmentedTracks.add(createSegmentTrack(segmentNodes, track));
	        }
	    }
	    tracks = segmentedTracks;
	}
	
	public List<Long> createJunctionNodes() {
		Map<Long, Integer> nodeOccurrences = new HashMap<>();

        for (RailwayTrack track : tracks) {
            for (Long l : track.getNodes()) {
                nodeOccurrences.put(l, nodeOccurrences.getOrDefault(l, 0) + 1);
            }
        }

        List<Long> junctionNodes = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : nodeOccurrences.entrySet()) {
            if (entry.getValue() > 1) {
                junctionNodes.add(entry.getKey());
            }
        }
	    return new ArrayList<>(junctionNodes);
	}

	public boolean shouldTrackBeSplit(Long nodeId, RailwayTrack parentTrack, List<Long> junctionNodes) {
	    if (nodeId.equals(parentTrack.getNodes().get(0)) || nodeId.equals(parentTrack.getNodes().get(parentTrack.getNodes().size() - 1))) {
	        return false;
	    }
	    
	    if(junctionNodes.contains(nodeId)) {
	    	return true;
	    }
	    
	    return false;
	}

	public RailwayTrack createSegmentTrack(List<Long> segmentNodes, RailwayTrack parentRailway) {
	    RailwayTrack segment = new RailwayTrack(parentRailway.getRailwayId());
	    segment.setId(index++);
	    segment.setSpeed(parentRailway.getSpeed());
	    segment.getNodes().addAll(segmentNodes);
	    return segment;
	}

	public WayNode findConnectingNode(RailwayTrack rt1, RailwayTrack rt2) {
		for (Long l1 : rt1.getNodes()) {
			WayNode wn1 = longToWayNode(l1);
			for (Long l2 : rt2.getNodes()) {
				WayNode wn2 = longToWayNode(l2);
				if (wn1.getNodeId() == wn2.getNodeId()) {
					return wn1;
				}
			}
		}
		return null;
	}

	public boolean areNodesConnected(WayNode node1, WayNode node2) {
		return node1.getNodeId() == node2.getNodeId() && node1.getLatitude() == node2.getLatitude()
				&& node1.getLongitude() == node2.getLongitude();
	}
	
	public Station getStationByName(String name) {
		Station bestMatch = null;
		int minDistance = Integer.MAX_VALUE;

		for (Station s : stations) {
			int distance = levenshteinDistance(name.toLowerCase(), s.getName().toLowerCase());

			if (distance < minDistance) {
				minDistance = distance;
				bestMatch = s;
			}
		}

		if (bestMatch != null && minDistance < 3) {
			return bestMatch;
		}

		return null;
	}
	
	public List<Station> getClosestStations(String name) {
        List<Station> closestStations = new ArrayList<>();
        List<Station> copy = stations;
        copy.sort(Comparator.comparingInt(s -> levenshteinDistance(name.toLowerCase(), s.getName().toLowerCase())));

        int count = 0;
        for (Station s : copy) {
            closestStations.add(s);
            count++;
            if (count >= 6) {
                break;
            }
        }

        return closestStations;
    }
	
	public int levenshteinDistance(String s1, String s2) {
		Normalizer.normalize(s1, Normalizer.Form.NFKC);
		Normalizer.normalize(s2, Normalizer.Form.NFKC);
        int m = s1.length();
        int n = s2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i;
        }
        
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j;
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
                }
            }
        }
        
        return dp[m][n];
    }
	
	public List<String> suggestSimilarWords(String inputWord, List<String> wordList) {
        List<String> suggestedWords = new ArrayList<>();
        
        if(inputWord.length()>2) {
        	Normalizer.normalize(inputWord, Normalizer.Form.NFKC);
            for (String word : wordList) {
            	Normalizer.normalize(word, Normalizer.Form.NFKC);
                double similarity = similarityPercentage(inputWord, word.substring(0, word.indexOf(" ") != -1 ? word.indexOf(" ") : word.length()));
                if(inputWord.length() <= word.length()) {
                	if(similarity >= 70 || (word.toLowerCase().contains(inputWord.toLowerCase()) && similarity >= 5)) {
                		suggestedWords.add(word);
                	}
                }
            }
            Collections.sort(suggestedWords);
        }
        return suggestedWords;
    }
	
	public double similarityPercentage(String s1, String s2) {
        int maxLength = Math.max(s1.length(), s2.length());
        int distance = levenshteinDistance(s1, s2);
        return (1 - (double) distance / maxLength) * 100;
    }
	
	public WayNode longToWayNode(long id) {
		return wayNodesMap.get(id);
	}
}