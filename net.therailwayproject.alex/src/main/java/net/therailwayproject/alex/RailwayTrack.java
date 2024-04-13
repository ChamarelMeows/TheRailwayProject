package net.therailwayproject.alex;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;

public class RailwayTrack {

	List<WayNode> nodes;
	List<Integer> connections;
	int id;
	int speed;
	double length;
	double weight;
	int railwayId;
	
	public RailwayTrack(int railwayId) {
		this.railwayId = railwayId;
		nodes = new ArrayList<WayNode>();
		connections = new ArrayList<Integer>();
	}
	
	public void addNode(WayNode node) {
		nodes.add(node);
	}
	
	public void addConnection(int id) {
		connections.add(id);
	}

	public List<WayNode> getNodes() {
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
	
	public int getRailwayId() {
		return railwayId;
	}
	
	public void setRailwayId(int railwayId) {
		this.railwayId = railwayId;
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
}