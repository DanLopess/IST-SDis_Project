package pt.sayf.sentry.server;

import java.util.ArrayList;

/**
 * Sentry base class with method implementation
 * 
 * @author dsm43 - Duarte Matias
 * @version 1.0.0
 *
 */

public class SentryBase {

	private ArrayList<String> macList;
	private String name;
	private float latitude;
	private float longitude;
	private int[] timestamp;
	public final int MAXSIZE = 10;

	// Constructor
	/**
	 * Empty Sentry constructor, receives no arguments and starts the sentry empty
	 */
	protected SentryBase() {
		macList = new ArrayList<String>();
		name = "";
		latitude = 0;
		longitude = 0;
		timestamp = new int[MAXSIZE];		
	}

	/**
	 * Standard Sentry constructor
	 * 
	 * @param n   sentry name
	 * @param lat sentry latitude
	 * @param lon sentry longitude
	 */
	protected SentryBase(String n, float lat, float lon) {
		macList = new ArrayList<String>();
		name = n;
		latitude = lat;
		longitude = lon;
		timestamp = new int[MAXSIZE];	
	}

	// Getters

	/**
	 * @return Sentry name
	 */
	protected String getName() {
		return name;
	}

	/**
	 * @return sentry latitude
	 */
	protected float getLatitude() {
		return latitude;
	}

	/**
	 * @return sentry longitude
	 */
	protected float getLongitude() {
		return longitude;
	}

	/**
	 * @return Current MAC address list
	 */
	protected ArrayList<String> getMacList() {
		return macList;
	}

	// Setters

	/**
	 * @param n new name
	 */
	protected synchronized void setName(String n) {
		name = n;
	}

	/**
	 * @param lnew latitude
	 */
	protected synchronized void setLatitude(float l) {
		latitude = l;
	}

	/**
	 * @param l new longitude
	 */
	protected synchronized void setLongitude(float l) {
		longitude = l;
	}

	// List modifications

	/**
	 * @param mac new MAC address
	 * @return 
	 */
	protected synchronized boolean addMac(String mac) {
		return macList.add(mac);
	}

	/**
	 * Empty MAC address list
	 */
	protected synchronized void clearMacs() {
		macList = new ArrayList<String>();

	}
	
	/**
	 * Update Timestamp List
	 * @param ts new timestamp
	 */
	protected synchronized void setTimestamp(int[] ts) {
		for(int i = 0; i < MAXSIZE; i++) {
			if(timestamp[i] < ts[i]) {
				timestamp[i] = ts[i];
			}
		}
	}
	
	/**
	 * @return timestamp list
	 */
	protected synchronized int[] getTimestamp() {
		return timestamp;
	}
	
}
