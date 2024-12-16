package net.therailwayproject.stella;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.jdesktop.swingx.mapviewer.GeoPosition;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
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
	private AtomicInteger index = new AtomicInteger(0);
	public Map<Long, WayNode> wayNodesMap;
	public double progress;
	public String progressMsg = "";
	public List<GeoPosition> routeCords;
	public List<RailwayTrack> searchedTracks; //List of tracks that the A* algorithm has just searched
	private Set<Long> junctionNodes;
	private Map<Integer, RailwayTrack> trackIdMap = new ConcurrentHashMap<>();
	private Map<Integer, RailwayTrack> railwayIdMap = new ConcurrentHashMap<>();

	public static SpeedCalculator INSTANCE() {
		return sp;
	}

	public SpeedCalculator() {
		sp = this;
		tracks = new ArrayList<RailwayTrack>();
		stations = new ArrayList<Station>();
		routeCords = new ArrayList<GeoPosition>();
		searchedTracks = new ArrayList<RailwayTrack>();
		wayNodesMap = new HashMap<>();
		trackIdMap = new ConcurrentHashMap<>();
		railwayIdMap = new ConcurrentHashMap<>();
		op = new OverpassAPI();
		if (!new File("res/trackData.bin").exists() || !new File("res/stationData.bin").exists() || !new File("res/nodeData.bin").exists()) {
			geometryFactory = new GeometryFactory();
			String coordinates = "(52.00405169419172, 4.21514369248833,52.48906017795534, 7.4453551270490035)";
			progressMsg = "Downloading data";
			downloadData(coordinates, false);
		} else {
			loadingFromFile = true;
			progressMsg = "Loading data";
			loadTrackData();
			loadNodeData();
			loadStationData();
		}
		doneLoading = true;
	}

	public void downloadData(String area, boolean isCountry) {
		progress = 0;
		progressMsg = "Downloading data";
		tracks = new ArrayList<RailwayTrack>();
		stations = new ArrayList<Station>();
		if (isCountry) {
			op.getDataAndWrite("[timeout:400];\r\n"
					+ "area[\"name:en\"=\"" + area + "\"]->.boundaryarea;\r\n"
					+ "way[\"railway\"=\"rail\"](area.boundaryarea);\r\n"
					+ "out meta geom;\r\n"
					+ ">;\r\n"
					+ "out skel qt;", "requestedTracks", true);
			op.getDataAndWrite("[timeout:400];\r\n"
					+ "area[\"name:en\"=\"" + area + "\"]->.boundaryarea;\r\n"
					+ "node[\"railway\"=\"station\"](area.boundaryarea);\r\n" + "out meta geom;\r\n" + ">;\r\n"
					+ "out skel qt;", "requestedStations", false);
		} else {
			op.getDataAndWrite(
					"[timeout:400];\r\n"
					+ "way[\"railway\"=\"rail\"]" + area + ";\r\n"
					+ "out meta geom;\r\n" +
					">;\r\n" +
					"out skel qt;",
					"requestedTracks", true);
			op.getDataAndWrite(
					"[timeout:400];\r\n"
					+ "(node[\"railway\"=\"station\"]" + area + ";);\r\n"
					+ "out meta geom;\r\n" + ">;\r\n"
					+ "out skel qt;",
					"requestedStations", false);
		}
		loadDataFrom("res/requestedTracks.osm", "res/requestedStations.osm");
		doneLoading = true;
	}

	public void loadDataFrom(String locationTracks, String locationStations) {
		long a = System.currentTimeMillis();
		progressMsg = "Loading railway tracks";
		loadRailwayTracks(locationTracks);
		progress = 0.125;
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
		loadStationTracks();
		progress = 0.75;
		progressMsg = "Writing track data";
		writeTrackData();
		progress = 0.875;
		progressMsg = "Writing station data";
		writeStationData();
		writeNodeData();
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

				
				List<Integer> railwayIds = new ArrayList<>();
                railwayIds.add(Integer.parseInt(railwayId));

                RailwayTrack rt = new RailwayTrack(railwayIds);
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
	    int numberOfThreads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, tracks.size()));
	    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

	    Map<Long, List<RailwayTrack>> nodeToTracksMap = new HashMap<>();
	    for (RailwayTrack track : tracks) {
	        long firstNode = track.getNodes().get(0);
	        long lastNode = track.getNodes().get(track.getNodes().size() - 1);

	        nodeToTracksMap.computeIfAbsent(firstNode, k -> new ArrayList<>()).add(track);
	        nodeToTracksMap.computeIfAbsent(lastNode, k -> new ArrayList<>()).add(track);
	    }

	    List<Future<?>> futures = new ArrayList<>();

	    for (Map.Entry<Long, List<RailwayTrack>> entry : nodeToTracksMap.entrySet()) {
	        List<RailwayTrack> tracksForNode = entry.getValue();

	        futures.add(executorService.submit(() -> {
	            for (int i = 0; i < tracksForNode.size(); i++) {
	                for (int j = i + 1; j < tracksForNode.size(); j++) {
	                    RailwayTrack track1 = tracksForNode.get(i);
	                    RailwayTrack track2 = tracksForNode.get(j);

	                    synchronized (track1) {
	                        if (!track1.getConnections().contains(track2.getId())) {
	                            track1.addConnection(track2.getId());
	                        }
	                    }
	                    synchronized (track2) {
	                        if (!track2.getConnections().contains(track1.getId())) {
	                            track2.addConnection(track1.getId());
	                        }
	                    }
	                }
	            }
	        }));
	    }

	    for (Future<?> future : futures) {
	        try {
	            future.get();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    executorService.shutdown();

	    tracks = tracks.stream()
	            .filter(rt -> !rt.getConnections().isEmpty())
	            .collect(Collectors.toList());

	    trackIdMap.clear();
	    railwayIdMap.clear();

	    for (RailwayTrack track : tracks) {
	        trackIdMap.put(track.getId(), track);
	        for (Integer railwayId : track.getRailwayIds()) {
	            railwayIdMap.put(railwayId, track);
	        }
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

	public void loadStationTracks() {
	    int numberOfThreads = Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, stations.size()));
	    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);

	    List<Future<?>> futures = new ArrayList<>();

	    Map<Long, WayNode> trackCenterCoordinates = new HashMap<>();
	    for (RailwayTrack track : tracks) {
	        long centerNodeId = track.getNodes().get(track.getNodes().size() / 2);
	        WayNode centerNode = longToWayNode(centerNodeId);
	        trackCenterCoordinates.put((long) track.getId(), centerNode);
	    }

	    List<Station> retainedStations = new ArrayList<>();
	    
	    int chunkSize = Math.max(1, stations.size() / numberOfThreads);
	    for (int t = 0; t < numberOfThreads; t++) {
	        final int start = t * chunkSize;
	        final int end = (t == numberOfThreads - 1) ? stations.size() : (start + chunkSize);

	        futures.add(executorService.submit(() -> {
	            List<Station> retainedStationsChunk = new ArrayList<>();

	            for (int i = start; i < end; i++) {
	                Station station = stations.get(i);
	                boolean stationHasTracks = false;
	                double minLatitudeDiff = Double.MAX_VALUE;
	                double minLongitudeDiff = Double.MAX_VALUE;
	                RailwayTrack closestTrack = null;

	                for (RailwayTrack track : tracks) {
	                    WayNode wn = trackCenterCoordinates.get((long) track.getId());

	                    double latitudeDiff = Math.abs(station.getLat() - wn.getLatitude());
	                    double longitudeDiff = Math.abs(station.getLon() - wn.getLongitude());

	                    if (latitudeDiff < minLatitudeDiff && longitudeDiff < minLongitudeDiff) {
	                        minLatitudeDiff = latitudeDiff;
	                        minLongitudeDiff = longitudeDiff;
	                        closestTrack = track;
	                    }
	                }

	                if (closestTrack != null) {
	                    station.addTrack(closestTrack.getId());
	                    stationHasTracks = true;
	                }

	                if (stationHasTracks) {
	                    retainedStationsChunk.add(station);
	                }
	            }

	            synchronized (retainedStations) {
	                retainedStations.addAll(retainedStationsChunk);
	            }
	        }));
	    }

	    for (Future<?> future : futures) {
	        try {
	            future.get();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    executorService.shutdown();

	    stations.clear();
	    stations.addAll(retainedStations);
	}
	
	public void writeTrackData() {
		try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("res/trackData.bin")))) {
	        dos.writeInt(tracks.size());

	        for (RailwayTrack rt : tracks) {
	            dos.writeInt(rt.getId());
	            dos.writeInt(rt.getSpeed());
	            dos.writeDouble(rt.getLength());
	            dos.writeDouble(rt.getWeight());

	            List<Integer> railwayIds = rt.getRailwayIds();
	            dos.writeInt(railwayIds.size());
	            for (int railwayId : railwayIds) {
	                dos.writeInt(railwayId);
	            }

	            List<Integer> connections = rt.getConnections();
	            dos.writeInt(connections.size());
	            for (int connection : connections) {
	                dos.writeInt(connection);
	            }

	            List<Long> nodes = rt.getNodes();
	            dos.writeInt(nodes.size());
	            for (long node : nodes) {
	                dos.writeLong(node);
	            }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void writeStationData() {
		try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("res/stationData.bin")))) {
	        dos.writeInt(stations.size());

	        for (Station station : stations) {
	        	dos.writeUTF(station.getName());

	            dos.writeDouble(station.getLat());
	            dos.writeDouble(station.getLon());

	            List<Integer> tracks = station.getTracks();
	            dos.writeInt(tracks.size());
	            for (int trackId : tracks) {
	                dos.writeInt(trackId);
	            }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void writeNodeData() {
		try (DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("res/nodeData.bin")))) {
	        dos.writeInt(wayNodesMap.size());

	        for (Map.Entry<Long, WayNode> entry : wayNodesMap.entrySet()) {
	            dos.writeLong(entry.getKey());

	            WayNode node = entry.getValue();
	            dos.writeLong(node.getNodeId());
	            dos.writeDouble(node.getLatitude());
	            dos.writeDouble(node.getLongitude());
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void loadTrackData() {
	    int c = 0;
	    double range = 1.0 / 3.0;
	    double offset = 0.0;

	    try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream("res/trackData.bin")))) {
	        int trackCount = dis.readInt();

	        for (int i = 0; i < trackCount; i++) {
	            c++;
	            progress = (c / (double) trackCount) * range + offset;
	            progressMsg = "Loading track: " + c;

	            int id = dis.readInt();
	            int speed = dis.readInt();
	            double length = dis.readDouble();
	            double weight = dis.readDouble();

	            int railwayIdCount = dis.readInt();
	            List<Integer> railwayIds = new ArrayList<>();
	            for (int j = 0; j < railwayIdCount; j++) {
	                railwayIds.add(dis.readInt());
	            }

	            int connectionCount = dis.readInt();
	            List<Integer> connections = new ArrayList<>();
	            for (int j = 0; j < connectionCount; j++) {
	                connections.add(dis.readInt());
	            }

	            int nodeCount = dis.readInt();
	            List<Long> nodes = new ArrayList<>();
	            for (int j = 0; j < nodeCount; j++) {
	                nodes.add(dis.readLong());
	            }

	            RailwayTrack track = new RailwayTrack(railwayIds);
	            track.setId(id);
	            track.setSpeed(speed);
	            track.setLength(length);
	            track.setWeight(weight);
	            for (int connection : connections) {
	                track.addConnection(connection);
	            }
	            for (long node : nodes) {
	                track.addNode(node);
	            }

	            tracks.add(track);

	            trackIdMap.put(id, track);
	            for (int railwayId : railwayIds) {
	                railwayIdMap.put(railwayId, track);
	            }
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void loadStationData() {
		int c = 0;
		double range = 1.0 / 3.0;
	    double offset = 2.0 / 3.0;
	    
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream("res/stationData.bin")))) {
	        int stationCount = dis.readInt();

	        for (int i = 0; i < stationCount; i++) {
	        	c++;
	        	progress = (c / (double) stationCount) * range + offset;
	            String name = dis.readUTF();
	            progressMsg = "Loading station: " + name;
	            
	            double lat = dis.readDouble();
	            double lon = dis.readDouble();

	            int trackCount = dis.readInt();
	            List<Integer> stationTracks = new ArrayList<>();
	            for (int j = 0; j < trackCount; j++) {
	                stationTracks.add(dis.readInt());
	            }

	            Station station = new Station(name, lat, lon);
	            for (int trackId : stationTracks) {
	                station.addTrack(trackId);
	            }

	            stations.add(station);
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void loadNodeData() {
		int c = 0;
		double range = 1.0 / 3.0;
	    double offset = 1.0 / 3.0;
	    
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream("res/nodeData.bin")))) {
	        int size = dis.readInt();

	        for (int i = 0; i < size; i++) {
	        	c++;
	        	progress = (c / (double) size) * range + offset;
	            long key = dis.readLong();
	            long nodeId = dis.readLong();
	            progressMsg = "Loading node: " + nodeId;
	            double latitude = dis.readDouble();
	            double longitude = dis.readDouble();

	            wayNodesMap.put(key, new WayNode(nodeId, latitude, longitude));
	        }
	    } catch (IOException e) {
	        e.printStackTrace();
	    }
	}

	public void outputToMap(RailwayTrack start, RailwayTrack end, List<RailwayTrack> stops) {
        routeCords.clear();
        Dijkstra d = new Dijkstra(this);
        List<Integer> fullPath = new ArrayList<>();
        double totalLength = 0.0;
        double totalTime = 0;
        WayNode connection = null;

        if (stops != null) {
            fullPath.addAll(d.findPath(start, stops.get(0)));

            for (int i = 0; i < stops.size() - 1; i++) {
                fullPath.addAll(d.findPath(stops.get(i), stops.get(i + 1)));
            }

            fullPath.addAll(d.findPath(stops.get(stops.size() - 1), end));
        } else {
            fullPath.addAll(d.findPath(start, end));
        }

        StringBuilder contentBuilder = new StringBuilder();

        for (int i = 0; i < fullPath.size() - 1; i++) {
            RailwayTrack rt = getTrackById(fullPath.get(i), false);

            if (i == 0) {
                connection = findConnectingNode(rt, getTrackById(fullPath.get(1), false));
                if (connection.getNodeId() == rt.getNodes().get(0)) {
                    Collections.reverse(rt.getNodes());
                }
            } else {
                if (connection.getNodeId() != rt.getNodes().get(0)) {
                    Collections.reverse(rt.getNodes());
                }
                if (rt.getId() != getTrackById(fullPath.get(fullPath.size() - 1), false).getId())
                    connection = findConnectingNode(rt, getTrackById(fullPath.get(i + 1), false));
            }

            totalLength += rt.getLength();
            totalTime += rt.getWeight();

            WayNode lastNode = null;
            for (Long l : rt.getNodes()) {
                WayNode wn = longToWayNode(l);
                if (wn != lastNode) {
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
        System.out.println("Realistic time: " + (totalTime * 1.535) + " minutes");
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
		if (maxSpeed.contains("mph")) {
			for (char c : maxSpeed.toCharArray()) {
				if (Character.isDigit(c)) {
					result.append(c);
				} else {
					break;
				}
			}
			if (result.toString() == "")
				return 60;
			if(result.toString() == "0")
				return 60;
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
			if(result.toString() == "0")
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

	public RailwayTrack getTrackById(int wayId, boolean isRailwayId) {
	    if (isRailwayId) {
	        return railwayIdMap.get(wayId);
	    } else {
	        return trackIdMap.get(wayId);
	    }
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
	    junctionNodes = createJunctionNodes();
	    ConcurrentLinkedQueue<RailwayTrack> segmentedTracks = new ConcurrentLinkedQueue<>();

	    int numberOfThreads = Runtime.getRuntime().availableProcessors();

	    int chunkSize = Math.max(1, tracks.size() / numberOfThreads);
	    ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreads);
	    List<Future<?>> futures = new ArrayList<>();

	    for (int i = 0; i < tracks.size(); i += chunkSize) {
	        final int start = i;
	        final int end = Math.min(i + chunkSize, tracks.size());

	        futures.add(executorService.submit(() -> {
	            List<RailwayTrack> localSegmentedTracks = new ArrayList<>();
	            for (int j = start; j < end; j++) {
	                RailwayTrack track = tracks.get(j);
	                List<Long> segmentNodes = new ArrayList<>();

	                for (Long l : track.getNodes()) {
	                    segmentNodes.add(l);

	                    if (shouldTrackBeSplit(l, track)) {
	                        RailwayTrack segment = createSegmentTrack(segmentNodes, track);
	                        localSegmentedTracks.add(segment);

	                        segmentNodes = new ArrayList<>();
	                        segmentNodes.add(l);
	                    }
	                }

	                if (!segmentNodes.isEmpty()) {
	                    localSegmentedTracks.add(createSegmentTrack(segmentNodes, track));
	                }
	            }
	            segmentedTracks.addAll(localSegmentedTracks);
	        }));
	    }

	    for (Future<?> future : futures) {
	        try {
	            future.get();
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }

	    executorService.shutdown();

	    tracks = new ArrayList<>(segmentedTracks);
	}

	public Set<Long> createJunctionNodes() {
	    Map<Long, Integer> nodeOccurrences = new HashMap<>();

	    for (RailwayTrack track : tracks) {
	        for (Long l : track.getNodes()) {
	            nodeOccurrences.put(l, nodeOccurrences.getOrDefault(l, 0) + 1);
	        }
	    }

	    Set<Long> junctionNodes = new HashSet<>();
	    for (Map.Entry<Long, Integer> entry : nodeOccurrences.entrySet()) {
	        if (entry.getValue() > 1) {
	            junctionNodes.add(entry.getKey());
	        }
	    }
	    return junctionNodes;
	}

	public boolean shouldTrackBeSplit(Long nodeId, RailwayTrack parentTrack) {
	    if (nodeId.equals(parentTrack.getNodes().get(0))
	            || nodeId.equals(parentTrack.getNodes().get(parentTrack.getNodes().size() - 1))) {
	        return false;
	    }

	    return junctionNodes.contains(nodeId);
	}

	public RailwayTrack createSegmentTrack(List<Long> segmentNodes, RailwayTrack parentRailway) {
		RailwayTrack segment = new RailwayTrack(parentRailway.getRailwayIds());
		segment.setId(index.getAndIncrement());
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
		return node1.getNodeId() == node2.getNodeId();
	}

	public List<Station> getStationByName(String name) {
	    List<Station> matchingStations = new ArrayList<>();
	    int minDistance = Integer.MAX_VALUE;

	    for (Station s : stations) {
	        int distance = levenshteinDistance(name.toLowerCase(), s.getName().toLowerCase());

	        if (distance < minDistance) {
	            minDistance = distance;
	            matchingStations.clear();
	            matchingStations.add(s);
	        } else if (distance == minDistance) {
	            matchingStations.add(s);
	        }
	    }
	    if(matchingStations.isEmpty() || minDistance != 0) {
	    	return null;
	    } else {
	    	return matchingStations;
	    }
	}
	
	public int levenshteinDistance(String s1, String s2) {
	    s1 = removeDiacritics(s1);
	    s2 = removeDiacritics(s2);

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
	    if (inputWord.length() > 2) {
	        inputWord = removeDiacritics(inputWord);
	        for (String word : wordList) {
	            String normalizedWord = removeDiacritics(word);
	            double similarity = similarityPercentage(inputWord,
	                    normalizedWord.substring(0, normalizedWord.indexOf(" ") != -1 ? normalizedWord.indexOf(" ") : normalizedWord.length()));
	            if (inputWord.length() <= normalizedWord.length()) {
	                if (similarity >= 70 || (normalizedWord.toLowerCase().contains(inputWord.toLowerCase()) && similarity >= 5)) {
	                    suggestedWords.add(word);
	                }
	            }
	        }
	        Collections.sort(suggestedWords);
	    }
	    return suggestedWords;
	}

	public double similarityPercentage(String s1, String s2) {
	    s1 = removeDiacritics(s1);
	    s2 = removeDiacritics(s2);

	    int maxLength = Math.max(s1.length(), s2.length());
	    int distance = levenshteinDistance(s1, s2);
	    return (1 - (double) distance / maxLength) * 100;
	}

	
	public String removeDiacritics(String input) {
	    if (input == null) return null;
	    return Normalizer.normalize(input, Normalizer.Form.NFD)
	            .replaceAll("\\p{M}", "");
	}

	public WayNode longToWayNode(long id) {
		return wayNodesMap.get(id);
	}

	public long getCommonNode(List<Long> l1, List<Long> l2) {
		for (long wn : l1) {
			if (l2.contains(wn)) {
				return wn;
			}
		}
		return 0;
	}

	public List<RailwayTrack> tracksAtNode(Long wn) {
		List<RailwayTrack> tracksAtNode = new ArrayList<>();
		for (RailwayTrack rt : tracks) {
			for (long wnid : rt.getNodes()) {
				if (wnid == wn) {
					tracksAtNode.add(rt);
				}
			}
		}
		return tracksAtNode;
	}
}