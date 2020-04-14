package pt.sayf.depot.server;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import pt.ulisboa.tecnico.sdis.zk.ZKNaming;
import pt.ulisboa.tecnico.sdis.zk.ZKNamingException;

import java.text.ParseException;

public class DepotServerApp {

	static volatile ZKNaming zkNaming = null; // Variable needs to be defined here for timing thread to work
												// Volatile so thread will look at updated value when it tries to access
												// it

	public static void main(String[] args) throws ZKNamingException {
		System.out.println(DepotServerApp.class.getSimpleName());

		// receive and print arguments
		System.out.printf("Received %d arguments%n", args.length);
		for (int i = 0; i < args.length; i++) {
			System.out.printf("arg[%d] = %s%n", i, args[i]);
		}

		// check arguments
		if (args.length != 5) {
			System.out.println("Usage: zk://ADDR:PORT host port path replica");
			return;
		}

		String zkAddr = args[0];
		String host = args[1];
		String port = args[2];
		String path = args[3];
		String replica = args[4];

		Server server = null;

		Thread t = null;

		try {

			zkNaming = new ZKNaming(zkAddr);

			// publish
			zkNaming.rebind((path + "/" + replica), host, port);

			final DepotServiceImpl impl = new DepotServiceImpl(Integer.parseInt(replica));

			// Create a new server to listen on port
			server = ServerBuilder.forPort(Integer.parseInt(port)).addService(impl).build();

			server.start();
			// Server threads are running in the background.
			System.out.println("Server started");

			// Starts timer thread
			t = new Thread(new Runnable() {
				public void run() {
					try {
						while(true) {
							
							impl.sendGossip(zkNaming, path);
							Thread.sleep(30000);
						}
					} catch (ZKNamingException | ParseException e) {
						System.err.printf("Caught exception while in gossip: %s\n", e);
					} catch (InterruptedException e) {
						Thread.yield();
					}
				}
			});

			t.start();

			// wait
			System.out.println("Awaiting connections");
			System.out.println("Press enter to shutdown");
			System.in.read();
			
			

		} catch (Exception e) {
			System.out.printf("Caught exception: %s : %s\n", e.getMessage(), e.getCause());

		} finally {
			try {
				if (zkNaming != null) {
					// remove
					zkNaming.unbind(path + "/" + replica, host, port);
				}
			} catch (Exception e) {
				System.out.printf("Caught exception when deleting: %s%n", e);
			}

			try {
				if (server != null) {
					server.shutdown();
					if (t != null) {
						t.interrupt(); // Interrupts the thread which causes InterruptedException
										// and forces the thread to end
					}
					System.exit(0);
				}

			} catch (Exception e) {
				System.out.printf("Caught exception when shuting down: %s%n", e);
			}
		}
	}
}
