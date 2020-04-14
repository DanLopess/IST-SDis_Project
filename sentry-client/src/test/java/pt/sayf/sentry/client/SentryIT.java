package pt.sayf.sentry.client;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import pt.sayf.sentry.grpc.Sentry;
import pt.sayf.sentry.grpc.sentryServiceGrpc;

public class SentryIT {
	// static members
	final static String SentryURI = "localhost:8091";
	static sentryServiceGrpc.sentryServiceBlockingStub stub;
	static ManagedChannel channel;

	// one-time initialization and clean-up
	@BeforeClass
	public static void oneTimeSetUp() {
		channel = ManagedChannelBuilder.forTarget(SentryURI).usePlaintext().build();
		stub = sentryServiceGrpc.newBlockingStub(channel);
		stub.ctrlClear(Sentry.ctrlClearRequest.newBuilder().build());
	}

	@AfterClass
	public static void oneTimeTearDown() {
		stub.ctrlClear(Sentry.ctrlClearRequest.newBuilder().build());
		channel.shutdownNow();
	}

	// initialization and clean-up for each test

	@Before
	public void setUp() {
		stub.ctrlClear(Sentry.ctrlClearRequest.newBuilder().build());
	}

	@After
	public void tearDown() {
		stub.ctrlClear(Sentry.ctrlClearRequest.newBuilder().build());
	}

	// tests

	@Test
	public void testPing() {
		stub.ctrlInit(Sentry.ctrlInitRequest.newBuilder().setLat(38.737613F).setLon(-9.303164F).setName("ISTTaguspark")
				.addMacs("AA:AA:AA:AA:AA:AA").addMacs("BB:BB:BB:BB:BB:BB").build());

		Sentry.ctrlPingResponse reply = stub.ctrlPing(Sentry.ctrlPingRequest.newBuilder().build());

		assert (reply.getMacCount() == 2);
		assert (reply.getLat() == 38.737613F);
		assert (reply.getLon() == -9.303164F);
		assert (reply.getName().compareTo("ISTTaguspark") == 0);
	}

	@Test
	public void feedOneMac() {
		Sentry.feedResponse reply = stub.feed(Sentry.feedRequest.newBuilder().addMacs("AA:AA:AA:AA:AA:AA").build());
		assert (reply.getStatus() == true);
	}

	@Test
	public void feedThreeMacs() {
		Sentry.feedResponse reply = stub.feed(Sentry.feedRequest.newBuilder().addMacs("AA:AA:AA:AA:AA:AA")
				.addMacs("BB:BB:BB:BB:BB:9A").addMacs("CC:CC:CC:CC:CC:CC").build());
		assert (reply.getStatus() == true);
	}

	@Test
	public void feedNineMacs() {
		Sentry.feedResponse reply = stub.feed(Sentry.feedRequest.newBuilder().addMacs("AA:AA:AA:AA:AA:AA")
				.addMacs("BB:BB:BB:BB:BB:9A").addMacs("CC:CC:CC:CC:CC:CC").addMacs("DD:DD:7F:DD:DD:DD")
				.addMacs("EE:EE:EE:EE:EE:EE").addMacs("FF:FF:FF:FF:FF:FF").addMacs("44:44:44:44:44:44")
				.addMacs("66:66:66:66:66:66").addMacs("88:88:8A:8A:8A:8A").build());
		assert (reply.getStatus() == true);
	}

	@Test
	public void pushOneMac() {
		Sentry.feedResponse replyOne = stub.feed(Sentry.feedRequest.newBuilder().addMacs("1A:66:EA:66:18:66").build());
		Sentry.pushResponse replyTwo = stub.push(Sentry.pushRequest.newBuilder().build());
		assert (replyOne.getStatus() == true);
		assert (replyTwo.getStatus() == true);
	}

	@Test
	public void pushTreeMacs() {
		Sentry.feedResponse replyOne = stub.feed(Sentry.feedRequest.newBuilder().addMacs("1A:44:EA:44:18:44")
				.addMacs("66:66:66:66:14:66").addMacs("88:88:8A:8A:8A:8A").build());
		Sentry.pushResponse replyTwo = stub.push(Sentry.pushRequest.newBuilder().build());
		assert (replyOne.getStatus() == true);
		assert (replyTwo.getStatus() == true);
	}

	@Test
	public void feedFiveMacsPingPushPing() {
		stub.ctrlInit(Sentry.ctrlInitRequest.newBuilder().setLat(38.737613F).setLon(-9.303164F).setName("ISTTaguspark")
				.build());

		// ping response after feed and push of five macs
		Sentry.feedResponse replyOne = stub.feed(Sentry.feedRequest.newBuilder().addMacs("1A:FF:EA:FF:18:FF")
				.addMacs("00:00:00:00:14:00").addMacs("88:88:8A:8A:8A:8A").addMacs("00:00:00:00:00:00")
				.addMacs("FF:FF:8A:8A:8A:8A").build());
		Sentry.ctrlPingResponse replyTwo = stub.ctrlPing(Sentry.ctrlPingRequest.newBuilder().build());
		Sentry.pushResponse replyThree = stub.push(Sentry.pushRequest.newBuilder().build());
		Sentry.ctrlPingResponse replyFour = stub.ctrlPing(Sentry.ctrlPingRequest.newBuilder().build());

		assert (replyOne.getStatus() == true);
		assert (replyThree.getStatus() == true);

		assert (replyTwo.getName().compareTo("ISTTaguspark") == 0);
		assert (replyTwo.getLat() == 38.737613F);
		assert (replyTwo.getLon() == -9.303164F);
		assert (replyTwo.getMacCount() == 5);

		assert (replyFour.getName().compareTo("ISTTaguspark") == 0);
		assert (replyFour.getLat() == 38.737613F);
		assert (replyFour.getLon() == -9.303164F);
		assert (replyFour.getMacCount() == 0);
	}

}
