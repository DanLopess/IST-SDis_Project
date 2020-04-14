package pt.sayf.depot.server;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Observation class for easier manipulation of observations
 * 
 * @author DanLopess - Daniel Lopes
 * @version 1.0.0
 *
 */

public class Observation {
    // attributes
    private String macAddress;
    private Date timeDate;
    private String sentry;
    private float lat;
    private float lon;

    // Constructor

    /**
     * Observation constructor
     * 
     * @param mac        - observed mac address
     * @param timedate   of depot registration
     * @param sentryName of the sentry that registered the observation
     * @param lon        - location longitude
     * @param lat        - location latitude
     */

    protected Observation(String mac, Date timedate, String sentryName, float latitude, float longitude) {
        macAddress = mac;
        timeDate = timedate;
        sentry = sentryName;
        lat = latitude;
        lon = longitude;
    }

    // Getters

    /**
     * Getter for the mac Address
     * 
     * @return string correspondent to the macAddress of this observation
     */
    protected String getMacAddress() {
        return macAddress;
    }

    /**
     * Getter for the time date
     * 
     * @return timeDate
     */

    protected Date getTimeDate() {
        return timeDate;
    }

    /*
     * Getter for the time date in the project related format
     * 
     * @return - string timedate
     */
    protected String getTimeDateToString() {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat formatter2 = new SimpleDateFormat("HH:mm:ss");
        String date = formatter.format(timeDate);
        String time = formatter2.format(timeDate);
        return date + 'T' + time;
    }

    /**
     * Getter for the sentry name
     * 
     * @return sentry name
     */

    protected String getSentryName() {
        return sentry;
    }

    /*
     * Getter for the latitude
     * 
     * @return latitude
     */
    protected float getLat() {
        return lat;
    }

    /**
     * Getter for the longitude
     * 
     * @return longitude
     */
    protected float getLon() {
        return lon;
    }

    /**
     * Getter for the float array that contains the coordenates
     * 
     * @return float[] that has lat and lon
     */
    protected float[] getCoords() {
        float[] coords = { lat, lon };
        return coords;
    }

    /**
     * Pretty print function for converting observation to a string
     * 
     * @return string
     */
    protected String prettyPrint() {
        return macAddress + "," + this.getTimeDateToString() + "," + sentry + "," + Float.toString(lat) + ","
                + Float.toString(lon);
    }
    // No Setters are needed
}