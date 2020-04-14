package pt.sayf.depot.server;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Collections;
import java.util.Comparator;

import pt.sayf.depot.server.exceptions.*;

/**
 * Sentry base class with method implementation
 * 
 * @author DanLopess - Daniel Lopes
 * @version 1.0.0
 *
 */

public class DepotBase {

    private Map<String, float[]> sentries; // sentries --> name, (lat, long)

    private List<Observation> globalObservations; // observations --> (MAC , Time , Sentry , Location)

    private Map<Integer, List<Observation>> localLog; // contains: version id --> list of observations (added on a given
    // update)
    private Map<Integer, List<int[]>> pendingUpdates; // localVersion -> (ReplicaNr , ReplicaVersion)

    final int MAXSIZE = 10;

    private int[] globalTimestamp;
    private int[] localTimestamp;

    private int replica;

    private int globalVersion = 1;
    private int logVersion = 1;

    // Constructor
    /**
     * Empty Sentry constructor, receives no arguments and starts the depot empty
     */
    protected DepotBase(int replicaNumber) {
        sentries = new HashMap<String, float[]>();

        globalObservations = Collections.synchronizedList(new ArrayList<Observation>());
        localLog = new HashMap<Integer, List<Observation>>();
        pendingUpdates = new TreeMap<Integer, List<int[]>>();

        globalTimestamp = new int[MAXSIZE];
        localTimestamp = new int[MAXSIZE];

        replica = replicaNumber;

        localTimestamp[replica - 1] = logVersion;
        globalTimestamp[replica - 1] = globalVersion;

    }

    /**
     * Method for restarting server from zero
     */
    protected void clearServer() {
        sentries = new HashMap<String, float[]>();

        globalObservations = Collections.synchronizedList(new ArrayList<Observation>());
        localLog = new HashMap<Integer, List<Observation>>();
        pendingUpdates = new HashMap<Integer, List<int[]>>();

        globalTimestamp = new int[MAXSIZE];
        localTimestamp = new int[MAXSIZE];

        globalVersion = 1;
        logVersion = 1;

        localTimestamp[replica - 1] = logVersion;
        globalTimestamp[replica - 1] = globalVersion;
    }

    // Getters

    /**
     * @param sentryName receives a sentry name
     * @return Sentry name's correspondent float[] location i.e. "lat, long"
     */
    protected float[] getSentryLoc(String sentryName) {
        float[] coords;

        synchronized (sentries) {
            coords = sentries.get(sentryName);
        }
        return coords;
    }

    /**
     * @param sentryName receives a sentry name
     * @return Sentry name's correspondent location i.e. "lat, long"
     */
    protected String getSentryLocToString(String sentryName) {
        float[] coords;
        synchronized (sentries) {
            coords = sentries.get(sentryName);
        }
        return Float.toString(coords[0]) + "," + Float.toString(coords[1]);
    }

    /**
     * @return Sentry Names list
     */
    protected ArrayList<String> getSentryList() {
        ArrayList<String> sentryNames = new ArrayList<String>();

        synchronized (sentries) {
            for (String name : sentries.keySet()) {
                String str = name + ',' + getSentryLocToString(name);
                sentryNames.add(str);
            }
        }

        return sentryNames;
    }

    /**
     * @param lastbit - boolean that tells the function whether the fragmac is in
     *                the beggining or end
     * @param fragMac
     * @return MAC address
     */
    protected List<Observation> getObservationByMac(String fragMac, boolean lastBits) {
        List<Observation> resultObservations = new ArrayList<Observation>();

        synchronized (globalObservations) {
            if (lastBits) {
                for (int i = 0; i < globalObservations.size(); i++) {
                    if (globalObservations.get(i).getMacAddress().substring(17 - fragMac.length(), 17)
                            .equals(fragMac)) {
                        resultObservations.add(globalObservations.get(i));
                    }
                }
            } else {
                for (int i = 0; i < globalObservations.size(); i++) {
                    if (globalObservations.get(i).getMacAddress().substring(0, fragMac.length()).equals(fragMac)) {
                        resultObservations.add(globalObservations.get(i));
                    }
                }
            }
        }
        return resultObservations;
    }

    /**
     * Method for sorting a given observation list
     */
    @SuppressWarnings("unchecked")
	protected List<Observation> sortObservationList(List<Observation> observations) {
        List<Observation> sortedObservations = observations;
		// Sort by Time (older to most recent)
		Collections.sort(sortedObservations, new Comparator() {
			@Override
			public int compare(Object observOne, Object observTwo) {
				return ((Observation) observOne).getTimeDateToString().compareTo(((Observation) observTwo).getTimeDateToString());
			}
		});
		Collections.reverse(sortedObservations); // most recent to older
		// Sort by MAC address
		Collections.sort(sortedObservations, new Comparator() {
			@Override
			public int compare(Object observOne, Object observTwo) {
				return ((Observation) observOne).getMacAddress().compareTo(((Observation) observTwo).getMacAddress());
			}
        });
        return sortedObservations;
    }

    /**
     * @return Sentries map
     */
    protected Map<String, float[]> getSentries() {
        return sentries;
    }

    /**
     * @return MAXSIZE
     */
    protected int getMAXSIZE() {
        return MAXSIZE;
    }

    /**
     * @return Current observations list e.g. MAC,Sentry,Location,Time
     */
    protected ArrayList<String> getObservationsToString() {
        ArrayList<String> observ = new ArrayList<String>();

        synchronized (globalObservations) {
            for (Observation temp : globalObservations) {
                observ.add(temp.prettyPrint());
            }
        }

        return observ;
    }

    /**
     * @return observations list
     */
    protected List<Observation> getObservations() {
        return globalObservations;
    }

    /**
     * @return replica instance number
     */
    protected int getReplica() {
        return replica;
    }

    /**
     * @return local timestamp vector
     */
    protected int[] getLocalTimestamp() {
        return localTimestamp;
    }

    /**
     * @return global timestamp vector
     */
    protected int[] getGlobalTimestamp() {
        return globalTimestamp;
    }

    protected Collection<Integer> getGlobalTimestampAsCollection() {
        Collection<Integer> res = new ArrayList<Integer>();
        for (int i = 0; i < MAXSIZE; i++) {
            res.add(globalTimestamp[i]);
        }
        return res;
    }

    /**
     * @return depot log version number
     */
    protected int getLogVersion() {
        return logVersion;
    }

    /**
     * @return depot global version number
     */
    protected int getGlobalVersion() {
        return globalVersion;
    }

    /**
     * @param ver log version
     * @return List<Observation> with local updates
     */
    protected List<Observation> getObsFromLogVersion(int ver) {
        return localLog.get(ver);
    }

    /**
     * Validation method for mac addresses
     * 
     * @param MAC
     * @return boolean
     */
    protected boolean isMAC(String MAC) {
        Pattern pattern = Pattern
                .compile("^(?=(:|.*:$|.{17}$))(?=.{3,17}$)(?!.{4}$):?[0-9a-fA-F]{2}(:[0-9a-fA-F]{2})*:?$");
        Matcher matcher = pattern.matcher(MAC);
        return matcher.matches();
    }

    // Setters or Modifiers

    /**
     * Function for incrementing the local version number
     */
    protected void increaseLocalVersion() {
        logVersion++;
        localTimestamp[replica - 1] = logVersion;
    }

    /**
     * Function for incrementing the global version number
     */
    protected void increaseGlobalVersion() {
        globalVersion++;
        globalTimestamp[replica - 1] = globalVersion;
    }

    /**
     * @param sentryName new sentry's name
     * @param lat        sentry's latitude in a float variable
     * @param lon        sentry's longitude in a float variable
     */
    protected void addSentry(String sentryName, float lat, float lon) throws SentryNameException {
        synchronized (sentries) {
            float[] sentryCoords = sentries.get(sentryName);
            float[] coords = { lat, lon };

            if (sentryCoords == null) {
                sentries.put(sentryName, coords);
            } else {
                if (sentryCoords != coords) {
                    throw new SentryNameException("Sentry already exists.");
                }
            }
        }
    }

    /**
     * @param sentryName string of the correspondent sentry
     * @param macs       arraylist that contains the macs observed by the sentry
     */
    protected void addObservationList(String sentryName, ArrayList<String> macs, int[] opTimestamp)
            throws SentryNameException, MacException {
        float[] coords;
        boolean macFail = false;

        List<Observation> tmp = new ArrayList<Observation>();

        synchronized (sentries) { // lock
            coords = sentries.get(sentryName);
        }
        if (coords == null) {
            throw new SentryNameException("Sentry Name is not found");
        } else {
            for (String tempMAC : macs) {
                if (this.isMAC(tempMAC)) {
                    Date date = new Date(); // this object contains the current date value
                    tmp.add(new Observation(tempMAC, date, sentryName, coords[0], coords[1]));
                } else {
                    macFail = true;
                }
            }
        }
        if (macFail)
            throw new MacException();

        synchronized (this) {
            increaseLocalVersion();
            addLog(logVersion, tmp);

            if (!addToPending(opTimestamp)) { // If update is not dependant, add to globalObservations
                globalObservations.addAll(tmp);
                increaseGlobalVersion();
            }
        }

    }

    /**
     * @param sentryName new sentry's name
     * @param lat        sentry's latitude in a float variable
     * @param lon        sentry's longitude in a float variable
     */
    protected void addObservation(String mac, Date timedate, String sentry, float lat, float lon) {
        synchronized (globalObservations) {
            globalObservations.add(new Observation(mac, timedate, sentry, lat, lon));
        }
    }

    /**
     * adds observation to the local log
     * 
     * @param ver update version
     * @param obs List<Observation>
     */
    protected void addLog(int ver, List<Observation> obs) {
        synchronized (localLog) {
            localLog.put(ver, obs);
        }
    }

    /**
     * Function for adding a list of dependencies to a given log version
     * 
     * @param timestamp
     * @return true if was able to add to pending
     */
    protected boolean addToPending(int[] timestamp) {
        int[] dependency;
        List<int[]> dependencies = new ArrayList<int[]>();

        for (int i = 0; i < MAXSIZE; i++) {
            if (timestamp[i] > globalTimestamp[i]) {
                dependency = new int[2];
                dependency[0] = i + 1;
                dependency[1] = timestamp[i];

                dependencies.add(dependency);
            }
        }

        if (dependencies.size() > 0) {
            pendingUpdates.put(logVersion, dependencies);
            return true;
        }
        return false;
    }

    /**
     * Function use to convert a given time string into a Date object
     * 
     * @param timedate
     * @return Date Object
     * @throws ParseException
     */
    protected Date parseTimedate(String timedate) throws ParseException {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        java.util.Date timedateFormat = formatter.parse(timedate.replace('T', ' '));
        return timedateFormat;
    }

    /**
     * 
     * @param pos replica number
     * @param val replica version
     * @return true if val < timestamp[pos - 1]
     */
    protected boolean compareGlobalTimestamp(int pos, int val) {
        if (globalTimestamp[pos - 1] >= val) {
            return true;
        }
        return false;
    }

    /**
     * Add Pending Updates from log to global observations list
     */
    protected void addFromPending() {
        Iterator<Entry<Integer, List<int[]>>> iter = pendingUpdates.entrySet().iterator();

        // Iterate pending updates
        while (iter.hasNext()) {
            Entry<Integer, List<int[]>> tmp = iter.next();

            Iterator<int[]> iter2 = tmp.getValue().iterator();

            while (iter2.hasNext()) {
                int[] tmp2 = iter2.next();

                // For each update pending, check how many versions it's dependant on
                // If the version on globalObservations is already superior

                if (compareGlobalTimestamp(tmp2[1], tmp2[0])) {

                    // Remove the dependency from the dependencies list
                    pendingUpdates.get(tmp.getKey()).remove(tmp2);

                    // If list is empty
                    if (pendingUpdates.get(tmp.getKey()).size() == 0) {
                        // Push the update
                        globalObservations.addAll(localLog.get(tmp.getKey()));
                        // Remove from pending
                        pendingUpdates.remove(tmp.getKey());
                    }
                }
            }
        }
    }

    /**
     * Updates global timestamp variable with new version
     * 
     * @param replicaNr
     * @param newVal
     */
    protected void updateGlobalTimestamp(int replicaNr, int newVal) {
        globalTimestamp[replicaNr - 1] = newVal;
    }

    /**
     * Adds an observation list from gossip, updates the global timestamp and checks
     * if the new added version fixed a dependency on the pending variable
     * 
     * @param obs
     * @param timestamp
     * @param replicaNr
     */
    protected void addObservationsFromGossip(List<Observation> obs, int[] timestamp, int replicaNr) {
        globalObservations.addAll(obs);
        updateGlobalTimestamp(replicaNr, timestamp[replicaNr - 1]);
        addFromPending();
    }

}
