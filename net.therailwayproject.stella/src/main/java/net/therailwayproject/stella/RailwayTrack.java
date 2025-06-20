package net.therailwayproject.stella;

import java.util.ArrayList;
import java.util.List;

public class RailwayTrack {

	List<Long> nodes;
	List<Integer> connections;
	int id;
	int speed;
	double length;
	double weight;
	List<Integer> railwayIds;
	
	public RailwayTrack(List<Integer> railwayIds) {
		this.railwayIds = railwayIds;
		nodes = new ArrayList<Long>();
		connections = new ArrayList<Integer>();
	}
	
	public void addNode(Long node) {
		nodes.add(node);
	}
	
	public void addNodes(ArrayList<Long> nodeList) {
		nodes.addAll(nodeList);
	}
	
	public void addConnection(int id) {
		connections.add(id);
	}
	
	public List<Long> getNodes() {
		return nodes;
	}
	
	public List<Integer> getConnections() {
		return connections;
	}

	public int getId() {
		return id;
	}
	
	public void setId(int id) {
		this.id = id;
	}
	
	public List<Integer> getRailwayIds() {
		return railwayIds;
	}
	
	public void setRailwayId(List<Integer> railwayIds) {
		this.railwayIds = railwayIds;
	}

	public int getSpeed() {
		return speed;
	}
	
	public void setSpeed(int speed) {
		this.speed = speed;
	}

	public double getLength() {
		return length;
	}

	public void setLength(double length) {
		this.length = length;
	}
	
	public double getWeight() {
		return weight;
	}
	
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	@Override
	public String toString() {
		return railwayIds + ", " + id + ", " + speed + ", " + length + ", " + weight + ", " + connections + ", " + nodes;
	}
}