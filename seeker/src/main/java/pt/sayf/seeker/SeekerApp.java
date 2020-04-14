package pt.sayf.seeker;

import pt.ulisboa.tecnico.sdis.zk.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import pt.sayf.depot.grpc.Depot;
import pt.sayf.depot.grpc.DepotServiceGrpc;
import pt.sayf.depot.grpc.Depot.observ;

public class SeekerApp {

	private static final int MAXSIZE = 10;
	private static int[] prevTS = new int[MAXSIZE];
	private static Map<String, List<observ>> cache = new HashMap<String, List<observ>>(); 
	// request message --> list of observations
	private static Map<String, int[]> cacheContent = new HashMap<String, int[]>(); 
	// completes cache map with request message and its timestamp

	/**
	 * Method for processing a reply and building the output for printing
	 * 
	 * @param resp
	 * @param isSearchMatch
	 * @param max
	 * @param mac
	 * @return ArrayList<String> containing the reply lines
	 */
	private static ArrayList<String> processReply(Object resp, boolean isSearchMatch, int max, String mac) {
		ArrayList<String> output = new ArrayList<String>();
		String requestMessage = (max == -1 ? "trace" : "track") + ':' + mac;
		int[] timestamp = new int[MAXSIZE];
		ArrayList<observ> observations = new ArrayList<observ>();
		ArrayList<observ> sortedObservations = new ArrayList<observ>();
		String out;

		if (isSearchMatch) {
			Depot.searchMatchReply reply = (Depot.searchMatchReply) resp;
			for (int i = 0; i < MAXSIZE; i++) {
				timestamp[i] = reply.getTimestamp(i);
			}
		} else {
			Depot.searchReply reply = (Depot.searchReply) resp;
			for (int i = 0; i < MAXSIZE; i++) {
				timestamp[i] = reply.getTimestamp(i); 
			}
		}

		if (inCache(requestMessage, timestamp)) { // checks if its in cache and has a >= timestamp
			observations = getFromCache(requestMessage); // get observations from cache
		} else { // print the reply and add to cache
			observations = getObservations(resp, isSearchMatch, timestamp);
			pushToCache(requestMessage, observations, timestamp); // pushes new request and its observations to cache
		}

		// sorts the observations
		sortedObservations = sortObservations(observations);

		// prints the observations
		for (observ iter : sortedObservations) {
			out = new String();

			out = out.concat(iter.getMac() + ",");
			out = out.concat(iter.getTimedate() + ",");
			out = out.concat(iter.getSentry() + ",");
			out = out.concat(Float.toString(iter.getLat()) + ",");
			out = out.concat(Float.toString(iter.getLon()) + "\n");

			output.add(out);
		}
		return output;
	}

	/**
	 * Method for verifying the response timestamp, compare it and evaluate whether
	 * to print from the cached response already stored, or print the new one and
	 * save it also to the cache
	 * 
	 * @param requestMessage
	 * @param timestamp
	 * @return true if the most response is on the cache or false otherwise
	 */
	private static Boolean inCache(String requestMessage, int[] timestamp) {
		int[] prevTimestamp = cacheContent.get(requestMessage);

		if (prevTimestamp == null) { // this request has not been made
			return false;
		} else {
			for (int i = 0; i < MAXSIZE; i++) {
				if (prevTimestamp[i] > timestamp[i]) { // if higher version, then should not get from cache
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * Method for pushing new reply messages to cache. If request has been made,
	 * replaces reply message. If request has not been made, adds the reply message.
	 * 
	 * @param requestMessage - trace:mac or track:mac
	 * @param observations   - list of observations
	 * @param timestamp      - vector of replicas' versions
	 */
	private static void pushToCache(String requestMessage, ArrayList<observ> observations, int[] timestamp) {
		if (cacheContent.get(requestMessage) != null) { // if request has been made, replaces
			cacheContent.replace(requestMessage, timestamp);
			cache.replace(requestMessage, observations);
		} else { // if it has not, adds it to cache
			cacheContent.put(requestMessage, timestamp);
			cache.put(requestMessage, observations);
		}
	}

	/**
	 * Method for returning list of observations from cache
	 * 
	 * @param requestMessage
	 * @return ArrayList of observations
	 */
	private static ArrayList<observ> getFromCache(String requestMessage) {
		ArrayList<observ> observations = new ArrayList<observ>();
		int obsCount = cache.get(requestMessage).size();

		while (obsCount-- > 0) {
			observations.add((cache.get(requestMessage)).get(obsCount));
		}
		return observations;
	}

	/**
	 * Method for getting observations either from a searchmatch reply or a
	 * searchreply having in mind the timestamp validation
	 * 
	 * @param resp
	 * @param isSearchMatch
	 * @param timestamp
	 * @return ArrayList containing the observations
	 */
	private static ArrayList<observ> getObservations(Object resp, boolean isSearchMatch, int[] timestamp) {
		ArrayList<observ> observations = new ArrayList<observ>();

		if (isSearchMatch) {
			Depot.searchMatchReply reply = (Depot.searchMatchReply) resp;
			int obsCount = reply.getObservationsCount();
			while (obsCount-- > 0) {
				observations.add(reply.getObservations(obsCount));
			}
		} else {
			Depot.searchReply reply = (Depot.searchReply) resp;
			int obsCount = reply.getObservationsCount();
			while (obsCount-- > 0) {
				observations.add(reply.getObservations(obsCount));
			}
		}

		if (verifyTimestamp(timestamp))
			prevTS = timestamp;

		return observations;
	}

	/**
	 * compare received timestamp with the previous one
	 * 
	 * @param ts received timestamp
	 * @return true if timestamp is equal or higher
	 */
	private static boolean verifyTimestamp(int[] ts) {
		for (int i = 0; i < MAXSIZE; i++) {
			if (ts[i] < prevTS[i]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * After receiving a list of observations, returns a sorted listed by time and
	 * mac address
	 * 
	 * @param observations - list of received observations
	 * @return sortedObservations - list of sorted observations
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static ArrayList<observ> sortObservations(ArrayList<observ> observations) {
		ArrayList<observ> sortedObservations = observations;

		// Sort by Time (older to most recent)
		Collections.sort(sortedObservations, new Comparator() {
			@Override
			public int compare(Object observOne, Object observTwo) {
				return ((observ) observOne).getTimedate().compareTo(((observ) observTwo).getTimedate());
			}
		});
		Collections.reverse(sortedObservations); // most recent to older
		// Sort by MAC address
		Collections.sort(sortedObservations, new Comparator() {
			@Override
			public int compare(Object observOne, Object observTwo) {
				return ((observ) observOne).getMac().compareTo(((observ) observTwo).getMac());
			}
		});
		return sortedObservations;
	}

	/**
	 * Validates if a given string is a valid mac address
	 * 
	 * @param mac
	 * @return boolean
	 */
	private static boolean validateMac(String mac) {
		Pattern pattern = Pattern
				.compile("^(?=(:|.*:$|.{17}$))(?=.{3,17}$)(?!.{4}$):?[0-9a-fA-F]{2}(:[0-9a-fA-F]{2})*:?$");
		Matcher matcher = pattern.matcher(mac);
		return matcher.matches();
	}

	/**
	 * Method for performing the search function
	 * 
	 * @param mac     - mac address
	 * @param max     - max results
	 * @param channel
	 */
	private static void search(String mac, int max, ManagedChannel channel) {
		if (!validateMac(mac)) {
			System.out.println("Invalid mac address");
			return;
		} else {
			if (mac.length() == 17) {
				DepotServiceGrpc.DepotServiceBlockingStub stub = DepotServiceGrpc.newBlockingStub(channel);
				Depot.searchRequest.Builder builder = Depot.searchRequest.newBuilder();
				builder.setMac(mac);
				builder.setMaxResults(max);
				for (int i = 0; i < MAXSIZE; i++) {
					builder.addTimestamp(prevTS[i]);
				}

				Depot.searchReply resp = stub.search(builder.build());

				if(!resp.getError().equals("No observations found.")) { // if no error --> has data
					for (String string : processReply(resp, false, max, mac)) {
						System.out.println(string);
					}
				}

				ArrayList<observ> observations = new ArrayList<observ>();

			} else {
				DepotServiceGrpc.DepotServiceBlockingStub stub = DepotServiceGrpc.newBlockingStub(channel);
				Depot.searchMatchRequest.Builder builder = Depot.searchMatchRequest.newBuilder();
				builder.setFragMac(mac);
				builder.setLastBits(mac.startsWith(":") ? true : false);
				builder.setMaxResults(max);
				for (int i = 0; i < MAXSIZE; i++) {
					builder.addTimestamp(prevTS[i]);
				}
				Depot.searchMatchReply resp = stub.searchMatch(builder.build());
				if(!resp.getError().equals("No observations found.")) { // if no error --> has data
					for (String string : processReply(resp, true, max, mac)) {
						System.out.println(string);
					}
				}

			}

		}
	}

	/**
	 * Returns a zkrecord related to a depot server
	 * 
	 * @param rootPath - parent path for depot servers
	 * @param zk
	 * @param recovery - used to tell if it's the first time asking or an error has
	 *                 occured
	 * @param args
	 * @return ZKRecord
	 */
	private static ZKRecord getRecord(String rootPath, ZKNaming zk, Boolean recovery, String[] args) {
		ZKRecord record = null;
		try {
			if (!recovery && args.length == 1) {
				record = zk.lookup(rootPath + "/" + args[0]); // Receives instance tag
			} else {
				Collection<ZKRecord> records = zk.listRecords(rootPath);
				Random rand = new Random();
				if (records.size() > 0)
					record = (ZKRecord) records.toArray()[rand.nextInt(records.size())];
				else {
					System.out.println("Couldn't find any available servers, terminating...");
					System.exit(-1);
				}
			}
		} catch (ZKNamingException ex) {
			System.out.println("Couldn't find any available servers, terminating...");
			System.exit(-1);
		}
		return record;
	}

	public static void main(String[] args) {
		ZKRecord record;
		String rootPath = "/grpc/sayf/T01_Depot";

		if (args.length != 1 && args.length != 0) {
			System.err.println("Invalid arguments. Leaving...");
			System.exit(-1);
		}

		System.out.println(SeekerApp.class.getSimpleName());
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		ZKNaming zk = new ZKNaming("localhost", "2181");
		ManagedChannel channel;

		// Server connection
		System.out.println("Connecting to Server...");

		record = getRecord(rootPath, zk, false, args);
		String uri = record.getURI();
		channel = ManagedChannelBuilder.forTarget(uri).usePlaintext().build();

		System.out.println("Connected to: " + uri);

		// Client Functionality
		while (true) {
			System.out.println("Waiting for commands:\n");

			try {
				BufferedReader br = new BufferedReader(new InputStreamReader(System.in));

				System.out.println("Enter your command:");
				String command = br.readLine();

				if (command.startsWith("track")) {
					search(command.replace("track ", ""), 1, channel);
				} else if (command.startsWith("trace")) {
					search(command.replace("trace ", ""), -1, channel);
				} else if (command.startsWith("quit")) {
					channel.shutdownNow();
					System.exit(0);
					return;
				}

			} catch (IOException | StatusRuntimeException e) {
				System.out.println("Lost server connection, reconnecting...");
				record = getRecord(rootPath, zk, true, args);
				uri = record.getURI();
				channel = ManagedChannelBuilder.forTarget(uri).usePlaintext().build();
				System.out.println("Connected.");
			} catch (Exception ex) {
				channel.shutdownNow();
				ex.printStackTrace();
				System.out.println("Something very bad went wrong. Terminating...");
				System.exit(-1);
			}
		}
	}
}
