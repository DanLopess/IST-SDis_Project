package pt.sayf.sentry.server;

import java.util.Collection;
import java.util.regex.Pattern;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import io.grpc.Server;
import pt.sayf.sentry.server.exceptions.DepotException;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKRecord;

public class SentryServerApp {

	public static void main(String[] args) throws Exception {

		System.out.println(SentryServerApp.class.getSimpleName());
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// check arguments
		if (args.length < 7) {
			System.err.printf("Missing arguments.\nProgram will now exit.\nPress any key to continue...");
			System.in.read();
			System.exit(1);
		}

		String zooPath = args[0];
		String host = args[1];
		String port = args[2];
		String path = args[3];
		String sentryName = args[4];
		String xCoord = args[5];
		String yCoord = args[6];

		if (!Pattern.matches("[a-zA-Z0-9]{3,12}", sentryName)) {
			System.err.printf("Invalid Sentry Name: %s\n", sentryName);
			System.exit(-1);
		}

		String rootPath = "/grpc/sayf/T01_Depot"; // depot's root path
		ZKNaming zkNaming = null;
		Server server = null;

		try {
			zkNaming = new ZKNaming(zooPath);
			zkNaming.rebind(path, host, port);

			// Search for a depot server...
			Collection<ZKRecord> records = zkNaming.listRecords(rootPath);
			if (records.size() == 0) {
				
				System.out.println("Couldn't find any available servers, terminating...");
				System.exit(-1);
			}

			final BindableService impl = new SentryImpl(rootPath, sentryName, Float.parseFloat(yCoord),
					Float.parseFloat(xCoord), zkNaming);

			server = ServerBuilder.forPort(Integer.parseInt(port)).addService(impl).build();

			server.start();

			System.out.println("Server started\n");
			System.out.println("Press enter to shutdown");
			System.in.read();

		} catch (DepotException e) {
			System.err.printf("Can't join Depot %s : %s\n", e.getPath() , e.getMessage());

		} catch (Exception e) {
			System.err.printf("Caught Exception: %s\n", e);

		} finally {
			try {
				if (zkNaming != null) {
					// remove
					zkNaming.unbind(path, host, port);
				}
			} catch (Exception e) {
				System.out.printf("Caught exception when deleting: %s%n", e);
			}

			try {
				if (server != null) {
					server.shutdown();
					System.exit(0);
				}
			} catch (Exception e) {
				System.out.printf("Caught exception when shuting down: %s%n", e);
			}
		}
	}

}
