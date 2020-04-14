package pt.sayf.depot.server;

import pt.sayf.depot.server.exceptions.*;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import pt.sayf.depot.grpc.Depot;
import pt.sayf.depot.grpc.DepotServiceGrpc;
import pt.sayf.depot.grpc.Depot.ctrlPingReply.Builder;

public class DepotServiceImpl extends DepotServiceGrpc.DepotServiceImplBase {

	private DepotBase depot;

	DepotServiceImpl(int replica) {
		depot = new DepotBase(replica);
	}

	@Override
	public void join(Depot.joinRequest request, StreamObserver<Depot.joinReply> responseObserver) {
		String name = request.getName();
		float lon = request.getLong();
		float lat = request.getLat();
		Depot.joinReply reply;
		boolean status = true;
		try {
			depot.addSentry(name, lat, lon);

		} catch (SentryNameException se) {
			System.out.println("Failed to add Sentry.");
			status = false;
		}
		System.out.println("New Sentry Joined.\n");
		reply = Depot.joinReply.newBuilder().setStatus(status).setReplicaNr(depot.getReplica())
				.addAllTimestamp(depot.getGlobalTimestampAsCollection()).build();
		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}

	@Override
	public void report(Depot.reportRequest request, StreamObserver<Depot.reportReply> responseObserver) {
		String sentryName = request.getSentry();
		ArrayList<String> macs = new ArrayList<String>();
		Depot.reportReply reply;
		boolean status = true;

		int i = request.getObservationsCount();
		while (i-- > 0) { // Loads macs from proto request
			macs.add(request.getObservations(i));
		}
		
		int[] timestamp = new int[depot.MAXSIZE];
		for(i = 0; i < depot.MAXSIZE; i++) {
			timestamp[i] = request.getTimestamp(i);
		}

		try {
			depot.addObservationList(sentryName, macs, timestamp);
			
		} catch (Exception e) { // Can be either wrong sentry name or invalid MAC address
			System.out.println("Failed to add one or more observations");
			status = false;
		}

		reply = Depot.reportReply.newBuilder().setStatus(status).addAllTimestamp(depot.getGlobalTimestampAsCollection()).build();
		responseObserver.onNext(reply);

		responseObserver.onCompleted();
	}

	@Override 
	public void search(Depot.searchRequest request, StreamObserver<Depot.searchReply> responseObserver) {
		List<Observation> tempObservations = depot.sortObservationList(depot.getObservations());
		List<Observation> resultObservations = new ArrayList<Observation>();
		ArrayList<Depot.observ> observations = new ArrayList<Depot.observ>(); // by time
		String mac = request.getMac();
		int maxResults = request.getMaxResults();
		Depot.searchReply reply;
		boolean sendData = true;

		for (Observation o : tempObservations) {
			if (o.getMacAddress().equals(mac)) {
				resultObservations.add(o);
			}
		}

		for (int i = 0; i < depot.getMAXSIZE(); i++) { // check if client's timestamp is older than replica's
			if(request.getTimestamp(i) > depot.getGlobalTimestamp()[i]){
				sendData = false;
			}
		}

		if (sendData){
			observations = processObservations(resultObservations, maxResults);
		}

		if (observations.size() > 0) {
			Depot.searchReply.Builder builder = Depot.searchReply.newBuilder();
			builder.addAllObservations(observations);
			for (int i = 0; i < depot.getMAXSIZE(); i++) {
				builder.addTimestamp(depot.getGlobalTimestamp()[i]);
			}

			reply = builder.build();
		}
		else
			reply = Depot.searchReply.newBuilder().setError("No observations found.").build();

		responseObserver.onNext(reply);
		responseObserver.onCompleted();

	}

	@Override 
	public void searchMatch(Depot.searchMatchRequest request, StreamObserver<Depot.searchMatchReply> responseObserver) {
		String fragMac = request.getFragMac();
		int maxResults = request.getMaxResults();
		boolean lastBits = request.getLastBits();
		Depot.searchMatchReply reply;
		ArrayList<Depot.observ> observations = new ArrayList<Depot.observ>();
		List<Observation> resultObservations = depot.sortObservationList(depot.getObservationByMac(fragMac, lastBits));
		boolean sendData = true;

		for (int i = 0; i < depot.getMAXSIZE(); i++) { // check if client's timestamp is older than replica's
			if(request.getTimestamp(i) > depot.getGlobalTimestamp()[i]){
				sendData = false;
			}
		}

		if (sendData){
			observations = processObservations(resultObservations, maxResults);
		}

		if (observations.size() > 0) {
			Depot.searchMatchReply.Builder builder = Depot.searchMatchReply.newBuilder();
			builder.addAllObservations(observations);
			for (int i = 0; i < depot.getMAXSIZE(); i++) {
				builder.addTimestamp(depot.getGlobalTimestamp()[i]);
			}

			reply = builder.build();
		}
		else
			reply = Depot.searchMatchReply.newBuilder().setError("No observations found.").build();

		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}

	/**
	 * Auxiliary method for processing observations to grpc types
	 */
	protected ArrayList<Depot.observ> processObservations(List<Observation> observs, int maxResults) {
		List<Observation> resultObservations = observs;
		ArrayList<Depot.observ> observations = new ArrayList<Depot.observ>();


		List<String> seenMacs = new ArrayList<String>();

		for (int i = 0; i < resultObservations.size(); i++) {
			Observation obv = resultObservations.get(i);
			// the most recent per mac - it is already sorted
			if (maxResults == 1 && seenMacs.contains(resultObservations.get(i).getMacAddress()) == false) {
				seenMacs.add(obv.getMacAddress());
				Depot.observ newObservation = Depot.observ.newBuilder().setMac(obv.getMacAddress())
						.setTimedate(obv.getTimeDateToString()).setSentry(obv.getSentryName()).setLat(obv.getLat())
						.setLon(obv.getLon()).build();
				observations.add(newObservation);

			} else if (maxResults == -1) {
				Depot.observ newObservation = Depot.observ.newBuilder().setMac(obv.getMacAddress())
						.setTimedate(obv.getTimeDateToString()).setSentry(obv.getSentryName()).setLat(obv.getLat())
						.setLon(obv.getLon()).build();
				observations.add(newObservation);
			}
		}
		return observations;
	}

	@Override 
	public void ctrlPing(Depot.ctrlPingRequest request, StreamObserver<Depot.ctrlPingReply> responseObserver) {
		Builder builder = Depot.ctrlPingReply.newBuilder();
		builder.setStatus(true);
		builder.addAllSentries(depot.getSentryList());
		builder.addAllObservations(depot.getObservationsToString());

		responseObserver.onNext(builder.build());
		responseObserver.onCompleted();
	}

	@Override
	public void ctrlClear(Depot.ctrlClearRequest request, StreamObserver<Depot.ctrlClearReply> responseObserver) {
		Depot.ctrlClearReply reply;

		try {
			depot.clearServer();
			reply = Depot.ctrlClearReply.newBuilder().setStatus(true).build();
		} catch (Exception e) {
			System.out.println("Failed to clear server.");
			reply = Depot.ctrlClearReply.newBuilder().setStatus(false).build();
		}

		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}

	@Override
	public void ctrlInit(Depot.ctrlInitRequest request, StreamObserver<Depot.ctrlInitReply> responseObserver) {
		ArrayList<Depot.sentry> sentries = new ArrayList<Depot.sentry>();
		ArrayList<Depot.observ> observs = new ArrayList<Depot.observ>();
		int sentriesCount = request.getSentriesCount();
		int observsCount = request.getObservationsCount();
		boolean status = true;

		depot.clearServer(); // Initially clears server

		// Loops for getting initial sentries and observations
		for (int i = 0; i < sentriesCount; i++) {
			sentries.add(request.getSentries(i));
		}
		for (int i = 0; i < observsCount; i++) {
			observs.add(request.getObservations(i));
		}

		for (Depot.sentry sentry : sentries) {
			try {
				depot.addSentry(sentry.getName(), sentry.getLat(), sentry.getLon());

			} catch (Exception e) { // Can receive sentryNameException
				System.out.println(e.getMessage());
				status = false;
			}
		}
		for (Depot.observ observation : observs) {
			try {
				java.util.Date timedate = depot.parseTimedate(observation.getTimedate());
				depot.addObservation(observation.getMac(), timedate, observation.getSentry(), observation.getLat(),
						observation.getLon());

			} catch (Exception e) { // Can receive observation exception or parse exception
				System.out.println(e.getMessage());
				status = false;
			}
		}

		Depot.ctrlInitReply reply = Depot.ctrlInitReply.newBuilder().setStatus(status).build();
		responseObserver.onNext(reply);
		responseObserver.onCompleted();
	}

	@Override
	public void gossip(Depot.gossipRequest request, StreamObserver<Depot.gossipReply> responseObserver) {
		System.out.println("Received Gossip Request");
		int diff = request.getTimestamp(depot.getReplica() - 1) - depot.getGlobalTimestamp()[depot.getReplica() - 1];
		
		if (diff >= 0 ) { 
			responseObserver.onNext(Depot.gossipReply.newBuilder().setHasdata(false).build());
			responseObserver.onCompleted();
		
		} else {
			
			if(depot.getGlobalTimestamp()[depot.getReplica() - 1] == 1) { //This replica has no updates for a starting replica
				responseObserver.onNext(Depot.gossipReply.newBuilder().setHasdata(true)
						.setVersion(depot.getGlobalVersion())
						.setReplica(depot.getReplica())
						.addAllObs(new ArrayList<Depot.observ>())
						.build());
				responseObserver.onCompleted();
			} else {
				
				boolean initialSync = false;
				if(request.getTimestamp(depot.getReplica() - 1) == 0) {
					initialSync = true;
				}
				
				diff = Math.abs(diff);

				ArrayList<Depot.observ> obsList = new ArrayList<Depot.observ>();

				while (diff > (initialSync == false ? 0 : 1)) {
					
					Iterator<Observation> tmp = depot.getObsFromLogVersion(request.getTimestamp(depot.getReplica() - 1) + diff).iterator();
					System.out.println("Sending these observations (version" + depot.getGlobalVersion() + "):"); //debug
				
					while (tmp.hasNext()) {
						Observation tmpObs = tmp.next();
						System.out.println(tmpObs.prettyPrint()); //debug
						obsList.add(Depot.observ.newBuilder().setMac(tmpObs.getMacAddress()).setLat(tmpObs.getLat())
							.setLon(tmpObs.getLon()).setSentry(tmpObs.getSentryName())
							.setTimedate(tmpObs.getTimeDateToString()).build());
					}	
					diff--;
					
				}
				responseObserver.onNext(Depot.gossipReply.newBuilder().setHasdata(true).addAllObs(obsList)
					.setReplica(depot.getReplica()).setVersion(depot.getGlobalVersion()).build());
				responseObserver.onCompleted();
			}
		}

	}
	
	
	
	/**
	 * Method for sending gossip updates to other replicas
	 * 
	 * @param zk in order to access other replicas' paths
	 * @throws ZKNamingException
	 * @throws ParseException
	 */
	protected void sendGossip(ZKNaming zk, String path) throws ZKNamingException, ParseException {
		System.out.println("Doing Gossip...\n");

		Collection<ZKRecord> depotLst = zk.listRecords(path);

		depotLst.remove(zk.lookup(path + "/" + depot.getReplica())); 

		Iterator<ZKRecord> iter = depotLst.iterator();

		while (iter.hasNext()) {
			ZKRecord record = iter.next();

			ManagedChannel channel = ManagedChannelBuilder.forTarget(record.getURI()).usePlaintext().build();

			DepotServiceGrpc.DepotServiceBlockingStub stub = DepotServiceGrpc.newBlockingStub(channel);

			Depot.gossipRequest request = Depot.gossipRequest.newBuilder()
					.addAllTimestamp(depot.getGlobalTimestampAsCollection())
						.build();

			Depot.gossipReply reply = stub.gossip(request);
			
			channel.shutdownNow();

			if (reply.getHasdata()) {
				
				if(reply.getObsCount() == 0) { //Initial case, where vector needs to be put to 1
					depot.updateGlobalTimestamp(reply.getReplica(), reply.getVersion());
				}
				
				else {
					for (int j = 0; j < reply.getObsCount(); j++) {
				
						Depot.observ tmp = reply.getObs(j);
						depot.addObservation(tmp.getMac(), depot.parseTimedate(tmp.getTimedate()), tmp.getSentry(),
								tmp.getLat(), tmp.getLon());
						
						
					}
					depot.updateGlobalTimestamp(reply.getReplica(), reply.getVersion());
				}
			}
		}
	}

}