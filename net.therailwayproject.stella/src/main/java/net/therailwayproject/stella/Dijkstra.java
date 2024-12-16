package net.therailwayproject.stella;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public class Dijkstra {
	
	private SpeedCalculator sp;
	
	public Dijkstra(SpeedCalculator sp) {
		this.sp = sp;
	}
	
	public List<Integer> findPath(RailwayTrack start, RailwayTrack goal) {
	    Map<RailwayTrack, RailwayTrack> cameFrom = new HashMap<>();
	    Map<RailwayTrack, Double> gScore = new HashMap<>();
	    Set<RailwayTrack> closedSet = new HashSet<>();
	    
	    PriorityQueue<RailwayTrack> openSet = new PriorityQueue<>(Comparator.comparingDouble(gScore::get));

	    gScore.put(start, 0.0);
	    openSet.offer(start);

	    while (!openSet.isEmpty()) {
	        RailwayTrack currentTrack = openSet.poll();
	        sp.searchedTracks.add(currentTrack);
	        
	        if (currentTrack.equals(goal)) {
	            return reconstructPath(cameFrom, currentTrack);
	        }

	        closedSet.add(currentTrack);

	        for (Integer neighborId : currentTrack.getConnections()) {
	            RailwayTrack neighbor = sp.getTrackById(neighborId, false);
	            if (closedSet.contains(neighbor)) {
	                continue;
	            }

	            double tentativeGScore = gScore.get(currentTrack) + currentTrack.getWeight();
	            if (tentativeGScore < gScore.getOrDefault(neighbor, Double.POSITIVE_INFINITY)) {
	                cameFrom.put(neighbor, currentTrack);
	                gScore.put(neighbor, tentativeGScore);

	                if (!openSet.contains(neighbor)) {
	                    openSet.offer(neighbor);
	                }
	            }
	        }
	    }

	    return null;
	}

	private List<Integer> reconstructPath(Map<RailwayTrack, RailwayTrack> cameFrom, RailwayTrack currentTrack) {
        List<Integer> path = new ArrayList<>();
        while (cameFrom.containsKey(currentTrack)) {
            path.add(0, currentTrack.getId());
            currentTrack = cameFrom.get(currentTrack);
        }
        path.add(0, currentTrack.getId());
        return path;
    }
}