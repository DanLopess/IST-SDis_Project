package pt.sayf.depot.client;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import pt.sayf.depot.grpc.DepotServiceGrpc;
import pt.sayf.depot.grpc.Depot;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

public class DepotIT {

	// static members

	final static String DepotURI = "localhost:8081";
	static DepotServiceGrpc.DepotServiceBlockingStub stub;
	static ManagedChannel channel;

	// one-time initialization and clean-up
	@BeforeClass
	public static void oneTimeSetUp() {
		channel = ManagedChannelBuilder.forTarget(DepotURI).usePlaintext().build();
		stub = DepotServiceGrpc.newBlockingStub(channel);
	}

	@AfterClass
	public static void oneTimeTearDown() {
		stub.ctrlClear(Depot.ctrlClearRequest.newBuilder().build());
		channel.shutdownNow();
	}

	// initialization and clean-up for each test

	@Before
	public void setUp() {
		stub.ctrlClear(Depot.ctrlClearRequest.newBuilder().build());
	}

	@After
	public void tearDown() {
		stub.ctrlClear(Depot.ctrlClearRequest.newBuilder().build());
	}

	// tests

	@Test
	public void testPing() {
		Depot.ctrlPingReply reply = stub.ctrlPing(Depot.ctrlPingRequest.newBuilder().build());
		assert (reply.getSentriesCount() == 0);

	}

	@Test
	public void registerSentry() {
		stub.join(Depot.joinRequest.newBuilder().setName("TP").setLat(9.01F).setLong(-31.87F).build());

		Depot.ctrlPingReply reply = stub.ctrlPing(Depot.ctrlPingRequest.newBuilder().build());

		assert (reply.getSentriesCount() == 1);
		assert (reply.getSentries(0).compareTo("TP,9.01,-31.87") == 0);

	}

	@Test
	public void registerTwoSentries() {
		char name = 65;
		float lat = 1.03F;
		float lon = 20.095F;
		int i = 2;

		while (i-- > 0) {
			stub.join(Depot.joinRequest.newBuilder().setName(Character.toString(name + i)).setLat(lat + (float) i)
					.setLong(lon + (float) i).build());
		}

		Depot.ctrlPingReply reply = stub.ctrlPing(Depot.ctrlPingRequest.newBuilder().build());
		assert (reply.getSentriesCount() == 2);

		assert (reply.getSentries(0).compareTo("A,1.03,20.095") == 0);
		assert (reply.getSentries(1).compareTo("B,2.03,21.095") == 0);

	}

	@Test
	public void reportOneMac() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(-31.2F).build()).build());

		stub.report(Depot.reportRequest.newBuilder().setSentry("A").addObservations("11:11:11:11:11:11").build());

		Depot.ctrlPingReply reply = stub.ctrlPing(Depot.ctrlPingRequest.newBuilder().build());

		assert (reply.getObservationsCount() == 1);

		String args[] = reply.getObservations(0).split(",");

		assert (args.length == 5);
		assert (args[0].compareTo("11:11:11:11:11:11") == 0);
		assert (args[2].compareTo("A") == 0);
		assert (args[3].compareTo("1.1") == 0);
		assert (args[4].compareTo("-31.2") == 0);
	}

	@Test
	public void reportFiveMacsSameSentry() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(-31.2F).build()).build());

		List<String> macs = new ArrayList<String>();
		for (int i = 0; i < 5; i++) {
			macs.add("FF:FF:FF:FF:FF:0" + Integer.toString(i));
		}

		stub.report(Depot.reportRequest.newBuilder().setSentry("A").addAllObservations(macs).build());

		Depot.ctrlPingReply reply = stub.ctrlPing(Depot.ctrlPingRequest.newBuilder().build());

		assert (reply.getObservationsCount() == 5);
		for (int j = 4, i = 0; j >= 0 && i < 5; j--, i++) {
			String args[] = reply.getObservations(j).split(",");
			assert (args[0].compareTo(("FF:FF:FF:FF:FF:0" + Integer.toString(i))) == 0);
			assert (args[2].compareTo("A") == 0);
			assert (args[3].compareTo("1.1") == 0);
			assert (args[4].compareTo("-31.2") == 0);

		}

	}

	@Test
	public void reportTwoMacsSeparateSentry() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(-31.2F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(-32.2F).build()).build());

		stub.report(Depot.reportRequest.newBuilder().setSentry("A").addObservations("AA:AA:AA:AA:AA:AA").build());
		stub.report(Depot.reportRequest.newBuilder().setSentry("B").addObservations("BB:BB:BB:BB:BB:BB").build());

		Depot.ctrlPingReply reply = stub.ctrlPing(Depot.ctrlPingRequest.newBuilder().build());

		assert (reply.getObservationsCount() == 2);
		String args[] = reply.getObservations(0).split(",");

		assert (args[0].compareTo("AA:AA:AA:AA:AA:AA") == 0);
		assert (args[2].compareTo("A") == 0);

		args = reply.getObservations(1).split(",");

		assert (args[0].compareTo("BB:BB:BB:BB:BB:BB") == 0);
		assert (args[2].compareTo("B") == 0);

	}

	@Test
	public void searchSingleMac() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(32.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:EE:EE:EE:EE").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.build());

		Depot.searchReply reply = stub
				.search(Depot.searchRequest.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setMaxResults(1).build());

		assert (reply.getObservationsCount() == 1);
		assert (reply.getObservations(0).getMac().compareTo("FF:FF:FF:FF:FF:FF") == 0);

	}

	@Test
	public void searchNonExistantMac() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(32.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:EE:EE:EE:EE").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.build());

		Depot.searchReply reply = stub.search(Depot.searchRequest.newBuilder().setMac("FF:FF:FF:FF:FF:FF").build());

		assert (reply.getObservationsCount() == 0);
	}

	@Test
	public void searchTwoMacs() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:01:00").build())
				.build());

		Depot.searchReply reply = stub
				.search(Depot.searchRequest.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setMaxResults(2).build());

		assert (reply.getObservationsCount() == 2);
		assert (reply.getObservations(1).getMac().compareTo("FF:FF:FF:FF:FF:FF") == 0);
		assert (reply.getObservations(1).getTimedate().compareTo("2019-11-06T20:00:00") == 0);

		assert (reply.getObservations(0).getMac().compareTo("FF:FF:FF:FF:FF:FF") == 0);
		assert (reply.getObservations(0).getTimedate().compareTo("2019-11-06T20:01:00") == 0);

	}

	@Test
	public void searchTwoMacsDiffSentry() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(32.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.build());

		Depot.searchReply reply = stub
				.search(Depot.searchRequest.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setMaxResults(2).build());

		assert (reply.getObservationsCount() == 2);
		assert (reply.getObservations(1).getMac().compareTo("FF:FF:FF:FF:FF:FF") == 0);
		assert (reply.getObservations(1).getTimedate().compareTo("2019-11-06T20:00:00") == 0);

		assert (reply.getObservations(0).getMac().compareTo("FF:FF:FF:FF:FF:FF") == 0);
		assert (reply.getObservations(0).getTimedate().compareTo("2019-11-06T20:01:00") == 0);
	}

	@Test
	public void matchMacWhole() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(32.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FE:EE:EE:EE").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:EE:EE:EE:EE").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.build());

		Depot.searchMatchReply reply = stub.searchMatch(
				Depot.searchMatchRequest.newBuilder().setFragMac("FF:FF:FF:FF:FF:FF").setMaxResults(2).build());

		assert (reply.getObservationsCount() == 2);
		assert (reply.getObservations(0).getMac().compareTo("FF:FF:FF:FF:FF:FF") == 0);

	}

	@Test
	public void matchMacStart() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(32.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FE:EE:EE:EE").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:EE:EE:EE:EE").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:AA:AA:AA:AA").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.build());

		Depot.searchMatchReply reply = stub.searchMatch(
				Depot.searchMatchRequest.newBuilder().setFragMac("EE:EE:").setLastBits(false).setMaxResults(2).build());

		assert (reply.getObservationsCount() == 2);
		assert (reply.getObservations(0).getMac().compareTo("EE:EE:AA:AA:AA:AA") == 0);
		assert (reply.getObservations(1).getMac().compareTo("EE:EE:EE:EE:EE:EE") == 0);
	}

	@Test
	public void matchMacEnd() {
		stub.ctrlInit(Depot.ctrlInitRequest.newBuilder()
				.addSentries(Depot.sentry.newBuilder().setName("A").setLat(1.1F).setLon(31.9F).build())
				.addSentries(Depot.sentry.newBuilder().setName("B").setLat(2.1F).setLon(32.9F).build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("A").setLat(1.1F)
						.setLon(31.9F).setTimedate("2019-11-06T20:00:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FF:FF:FF:FF").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("FF:FF:FE:EE:EE:EE").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:EE:EE:EE:EE").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.addObservations(Depot.observ.newBuilder().setMac("EE:EE:AA:AA:AA:AA").setSentry("B").setLat(2.1F)
						.setLon(32.9F).setTimedate("2019-11-06T20:01:00").build())
				.build());

		Depot.searchMatchReply reply = stub.searchMatch(
				Depot.searchMatchRequest.newBuilder().setFragMac(":EE:EE").setLastBits(true).setMaxResults(2).build());

		assert (reply.getObservationsCount() == 2);
		assert (reply.getObservations(0).getMac().compareTo("EE:EE:EE:EE:EE:EE") == 0);
		assert (reply.getObservations(1).getMac().compareTo("FF:FF:FE:EE:EE:EE") == 0);
	}
}
