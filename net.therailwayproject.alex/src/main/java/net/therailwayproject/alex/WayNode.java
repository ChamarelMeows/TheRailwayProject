package net.therailwayproject.alex;

import java.io.Serializable;

public class WayNode implements Serializable{

	private static final long serialVersionUID = 1L;
	private long id;
	private double lat;
	private double lon;
	
	public WayNode(long id) {
		this.id = id;
	}
	
	public WayNode(long id, double lat, double lon) {
		this.id = id;
		this.lat = lat;
		this.lon = lon;
	}

	public long getNodeId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public double getLatitude() {
		return lat;
	}

	public void setLat(double lat) {
		this.lat = lat;
	}

	public double getLongitude() {
		return lon;
	}

	public void setLon(double lon) {
		this.lon = lon;
	}
}
