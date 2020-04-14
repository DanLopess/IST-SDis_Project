package pt.sayf.sentry.server;

import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;

import java.util.Collection;
import java.util.Random;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import pt.sayf.sentry.grpc.Sentry;
import pt.sayf.sentry.grpc.Sentry.ctrlPingResponse.Builder;
import pt.sayf.sentry.grpc.sentryServiceGrpc;

import pt.sayf.depot.grpc.Depot;
import pt.sayf.depot.grpc.DepotServiceGrpc;
import pt.sayf.sentry.server.exceptions.DepotException;

public class SentryImpl extends sentryServiceGrpc.sentryServiceImplBase {

	private SentryBase sentry;
	private ZKNaming zkNaming;
	private String depotURI;
	private String depotPath;
	private String rootPath;

	/**
	 * SentryImpl Constructor
	 * 
	 * @param depotPath
	 * @param sentryName
	 * @param sentryLat
	 * @param sentryLong
	 * @param zkname
	 * @throws ZKNamingException
	 * @throws DepotException
	 */
	SentryImpl(String rootPath, String sentryName, float sentryLat, float sentryLong, ZKNaming zkname)
			throws ZKNamingException, DepotException {
		sentry = new SentryBase(sentryName, sentryLat, sentryLong);
		zkNaming = zkname;
		this.rootPath = rootPath;

		searchDepot();
		joinDepot();

	}

	@Override
	public void ctrlPing(Sentry.ctrlPingRequest request, StreamObserver<Sentry.ctrlPingResponse> responseObserver) {
		Builder builder = Sentry.ctrlPingResponse.newBuilder();
		builder.setName(sentry.getName());
		builder.setLat(sentry.getLatitude());
		builder.setLon(sentry.getLongitude());
		builder.addAllMac(sentry.getMacList());

		responseObserver.onNext(builder.build());
		responseObserver.onCompleted();
	}

	@Override
	public void ctrlClear(Sentry.ctrlClearRequest request, StreamObserver<Sentry.ctrlClearResponse> responseObserver) {
		sentry = new SentryBase(sentry.getName(), sentry.getLatitude(), sentry.getLongitude());
		final ManagedChannel channel = ManagedChannelBuilder.forTarget(depotURI).usePlaintext().build();

		DepotServiceGrpc.DepotServiceBlockingStub stub = DepotServiceGrpc.newBlockingStub(channel);
		Depot.ctrlClearRequest depotRequest = Depot.ctrlClearRequest.newBuilder().build();
		Depot.ctrlClearReply depotReply = stub.ctrlClear(depotRequest); // also clears depot, for testing purposes
		channel.shutdownNow();

		try {
			joinDepot();
		} catch (Exception e) {
			System.out.println("Failed to Join Depot once cleared.");
		}

		responseObserver.onNext(Sentry.ctrlClearResponse.newBuilder().setStatus(depotReply.getStatus()).build());
		responseObserver.onCompleted();
	}

	@Override
	public void ctrlInit(Sentry.ctrlInitRequest request, StreamObserver<Sentry.ctrlInitResponse> responseObserver) {
		sentry = new SentryBase();

		sentry.setName(request.getName());
		sentry.setLatitude(request.getLat());
		sentry.setLongitude(request.getLon());

		int i = request.getMacsCount();
		while (i-- > 0) {
			sentry.addMac(request.getMacs(i));
		}

		responseObserver.onNext(Sentry.ctrlInitResponse.newBuilder().build());
		responseObserver.onCompleted();
	}

	@Override
	public void feed(Sentry.feedRequest request, StreamObserver<Sentry.feedResponse> responseObserver) {
		int i = request.getMacsCount();
		boolean result = i == 0 ? false : true;

		while (i-- > 0) {
			result = result && sentry.addMac(request.getMacs(i));
		}

		responseObserver.onNext(Sentry.feedResponse.newBuilder().setStatus(result).build());
		responseObserver.onCompleted();
	}

	@Override
	public void push(Sentry.pushRequest request, StreamObserver<Sentry.pushResponse> responseObserver) {
		while(true) {
			try {
				ManagedChannel channel = ManagedChannelBuilder.forTarget(depotURI).usePlaintext().build();

				DepotServiceGrpc.DepotServiceBlockingStub stub = DepotServiceGrpc.newBlockingStub(channel);

				Depot.reportRequest.Builder depotRequest = Depot.reportRequest.newBuilder().addAllObservations(sentry.getMacList())
					.setSentry(sentry.getName());
				int[] timestamp = sentry.getTimestamp();
		
				for(int i = 0; i < sentry.MAXSIZE; i++) {
					depotRequest.addTimestamp(timestamp[i]);
				}

				Depot.reportReply depotReply = stub.report(depotRequest.build());

				if (depotReply.getStatus()) {
					sentry.clearMacs();
					for(int j = 0; j < sentry.MAXSIZE; j++) {
						timestamp[j] = depotReply.getTimestamp(j);
					}
					sentry.setTimestamp(timestamp);
				}

				Sentry.pushResponse response = Sentry.pushResponse.newBuilder().setStatus(depotReply.getStatus()).build();
				responseObserver.onNext(response);
				break;
			} catch (StatusRuntimeException e) {
				System.err.printf("Lost connection to depot: %s\nRetrying...\n", e.getMessage());
				try{
					searchDepot();
					joinDepot();
				} catch (DepotException e1) {
					System.err.printf("Error wwhen attempting reconnection: %s: %s\nTerminating...", e1.getPath(), e1.getMessage());
					System.exit(-1);
				} catch (ZKNamingException e2) {
					System.err.printf("ZKNaming error: %s\n", e2.getMessage());
				}
				
			}
		}
		responseObserver.onCompleted();
		

	}

	/**
	 * join depot specified in the constructor
	 * 
	 * @throws ZKNamingException
	 * @throws DepotException
	 */
	private void joinDepot() throws ZKNamingException, DepotException {

		final ManagedChannel channel = ManagedChannelBuilder.forTarget(depotURI).usePlaintext().build();

		DepotServiceGrpc.DepotServiceBlockingStub stub = DepotServiceGrpc.newBlockingStub(channel);

		Depot.joinRequest request = Depot.joinRequest.newBuilder().setName(sentry.getName())
				.setLat(sentry.getLatitude()).setLong(sentry.getLongitude()).build();

		Depot.joinReply reply = stub.join(request);

		channel.shutdownNow();

		if (reply.getStatus() == false) {
			throw new DepotException(depotPath, reply.getError());
		} else {
			System.out.printf("Joined Depot: %s\n", depotPath);
		}
	}
	
	/**
	 * Search for a depot on zookeeper
	 * @throws ZKNamingException
	 * @throws DepotException
	 */
	private void searchDepot() throws ZKNamingException, DepotException {
		Collection<ZKRecord> records = zkNaming.listRecords(rootPath);
		Random rand = new Random();
		if (records.size() > 0)
			depotPath = ((ZKRecord) records.toArray()[rand.nextInt(records.size())]).getPath();
		else {
			throw new DepotException(rootPath, "No Depot Available");
		}
		ZKRecord record = zkNaming.lookup(depotPath);
		depotURI = record.getURI();
	}

}