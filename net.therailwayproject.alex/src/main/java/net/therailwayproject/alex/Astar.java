package net.therailwayproject.alex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.stream.Collectors;

public class Astar {
	
	private SpeedCalculator sp;
	
	public Astar(SpeedCalculator sp) {
		this.sp = sp;
	}
	
    public List<Integer> findPath(RailwayTrack start, RailwayTrack goal) {
        PriorityQueue<Node> openSet = new PriorityQueue<>();
        Set<RailwayTrack> closedSet = new HashSet<>();
        Map<RailwayTrack, RailwayTrack> cameFrom = new HashMap<>();
        Map<RailwayTrack, Double> gScore = new HashMap<>();
        Map<RailwayTrack, Double> fScore = new HashMap<>();

        gScore.put(start, 0.0);
        fScore.put(start, heuristic(start, goal));
        openSet.offer(new Node(start, fScore.get(start)));

        while (!openSet.isEmpty()) {
            Node current = openSet.poll();
            RailwayTrack currentTrack = current.getTrack();
            if(!sp.searchedTracks.contains(currentTrack)) {
            	sp.searchedTracks.add(currentTrack);
            }

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
                    fScore.put(neighbor, tentativeGScore + heuristic(neighbor, goal));
                    openSet.offer(new Node(neighbor, fScore.get(neighbor)));
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

    private double heuristic(RailwayTrack current, RailwayTrack goal) {
    	List<Double> latCurrent = current.getNodes().stream().map(id -> sp.longToWayNode(id)).map(WayNode::getLatitude).collect(Collectors.toList());
    	List<Double> lonCurrent = current.getNodes().stream().map(id -> sp.longToWayNode(id)).map(WayNode::getLongitude).collect(Collectors.toList());
    	List<Double> latGoal = goal.getNodes().stream().map(id -> sp.longToWayNode(id)).map(WayNode::getLatitude).collect(Collectors.toList());
    	List<Double> lonGoal = goal.getNodes().stream().map(id -> sp.longToWayNode(id)).map(WayNode::getLongitude).collect(Collectors.toList());
    	List<Double> avgCurrent = sp.avgLatLon(latCurrent, lonCurrent);
    	List<Double> avgGoal = sp.avgLatLon(latGoal, lonGoal);
        return Math.sqrt(Math.pow(avgCurrent.get(0) - avgGoal.get(0), 2) + Math.pow(avgCurrent.get(1) - avgGoal.get(1), 2));
    }

    private static class Node implements Comparable<Node> {
        private final RailwayTrack track;
        private final double fScore;

        public Node(RailwayTrack track, double fScore) {
            this.track = track;
            this.fScore = fScore;
        }

        public RailwayTrack getTrack() {
            return track;
        }

        @Override
        public int compareTo(Node other) {
            return Double.compare(this.fScore, other.fScore);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Node node = (Node) obj;
            return Double.compare(node.fScore, fScore) == 0 && track.equals(node.track);
        }

        @Override
        public int hashCode() {
            return Objects.hash(track, fScore);
        }
    }
}