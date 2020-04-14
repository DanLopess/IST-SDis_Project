package pt.sayf.feeder;

import pt.sayf.sentry.grpc.sentryServiceGrpc;
import pt.sayf.sentry.grpc.sentryServiceGrpc.sentryServiceBlockingStub;
import pt.sayf.sentry.grpc.Sentry;
import pt.ulisboa.tecnico.sdis.zk.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class FeederApp {

	public static boolean validateMac(String mac) {
		Pattern pattern = Pattern
				.compile("^(?=(:|.*:$|.{17}$))(?=.{3,17}$)(?!.{4}$):?[0-9a-fA-F]{2}(:[0-9a-fA-F]{2})*:?$");
		Matcher matcher = pattern.matcher(mac);
		return matcher.matches();
	}

	// Creates message to be sent to seeker

	// read and send the mac adresses to seeker
	static void feedSentry(sentryServiceBlockingStub stub, List<String> macList) {
		String mac;

		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			System.out.println("Enter mac addresses: (finish with enter)");
			while (true) {
				mac = br.readLine();
				if (mac.compareTo("") == 0)
					break;
				if (validateMac(mac)) {
					macList.add(mac);
				} else {
					System.err.println("Not a mac adress!");
				}
			}
		} catch (IOError e) {
			System.err.printf("Caught Exception : %s\n", e);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			System.out.println("Sending macs to sentry");
			Sentry.feedResponse resp = stub.feed(Sentry.feedRequest.newBuilder().addAllMacs(macList).build());
			System.out.printf("Feed Status: %s\n", resp.getStatus());

			Sentry.pushResponse resp_p = stub.push(Sentry.pushRequest.newBuilder().build());
			System.out.printf("Push Status: %s\n", resp_p.getStatus());

		}
	}

	/**
	 * Returns a zkrecord related to a sentry server
	 * 
	 * @param rootPath - parent path for sentry servers
	 * @param zk
	 * @param args
	 * @return ZKRecord
	 */
	private static ZKRecord getRecord(String rootPath, ZKNaming zk, String[] args) {
		ZKRecord record = null;
		try {
			if (args.length == 1) {
				record = zk.lookup(rootPath + "/" + args[0]); // Receives instance tag
			} else {
				Collection<ZKRecord> records = (List<ZKRecord>) zk.listRecords(rootPath);
				Random rand = new Random();
				if (records.size() > 0)
					record = (ZKRecord) records.toArray()[rand.nextInt(records.size())];
				else {
					System.out.println("Can't find any online servers, terminating...");
					System.exit(-1);
				}
			}
		} catch (ZKNamingException ex) {
			System.out.println("Can't find any online servers, terminating...");
			System.exit(-1);
		}
		return record;
	}

	public static void main(String[] args) {
		List<String> macList = new ArrayList<String>();
		ZKRecord record;
		String rootPath = "/grpc/sayf/T01_Sentry";

		if (args.length != 1 && args.length != 0) {
			System.err.println("Invalid arguments. Leaving...");
			System.exit(-1);
		}

		System.out.println(FeederApp.class.getSimpleName());
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		ZKNaming zk = new ZKNaming("localhost", "2181");
		ManagedChannel channel;

		// Server Connection
		System.out.println("Connecting to Server...");

		record = getRecord(rootPath, zk, args);
		String uri = record.getURI();
		channel = ManagedChannelBuilder.forTarget(uri).usePlaintext().build();

		System.out.println("Connected to: " + uri);

		// Client Functionality
		try {
			sentryServiceGrpc.sentryServiceBlockingStub stub = sentryServiceGrpc.newBlockingStub(channel);
			feedSentry(stub, macList);
			channel.shutdownNow();
			System.exit(0);
		} catch (StatusRuntimeException e) {
			System.out.println("Lost server connection, Terminating...");
			channel.shutdownNow();
			System.exit(-1);
		} catch (Exception ex) {
			System.out.println("Something very bad went wrong. Terminating...");
			channel.shutdownNow();
			System.exit(-1);
		}

	}
}
