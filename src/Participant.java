import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

public class Participant {

	public static ParticipantHandler replicaHandler;

	public static String replicaName ;
	public static ParticipantFileStore.Processor processor;

	public static int port;

	public static void main(String[] args) {

		if (args.length != 4) {
			System.err.println(" Expected Arguments are: [Replica Port] [ReplicaName] [Coordinator HostName], [Coordinator Port]");
			System.exit(0);
		}
		try {
			replicaName = args[1];
			System.out.println("Checking Recoverable Transactions");
			replicaHandler = new ParticipantHandler(args[0],args[1],args[2],args[3]);
			processor = new ParticipantFileStore.Processor(replicaHandler);
			replicaHandler.checkIncompleteTransactions();
			port = Integer.valueOf(args[0]);

			Runnable simple = new Runnable() {
				@Override
				public void run() {
					simple(processor);
				}
			};

			new Thread(simple).start();

		} catch (NumberFormatException x) {
			System.err.println(x.getMessage());
			System.exit(0);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			System.exit(0);
		}

	}

	public static void simple(ParticipantFileStore.Processor processor) {
		try {
			
			TServerTransport serverTransport = new TServerSocket(port);
			TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));
			checkAndLoadFiles();
			System.out.println("Starting Participant Server " + replicaName + "...");
			server.serve();
		} catch (Exception e) {
			System.out.println("Port conflict due to abrupt shutdown, trying to restart");
		}
	}

	public static void checkAndLoadFiles() {

		File permamnentFileStore = new File(replicaName + "_FileStore/");
		String textContent = "";
		Scanner scanner = null;
		if (!permamnentFileStore.exists()) {
			permamnentFileStore.mkdir();
		} else {
			File[] listOfFiles = permamnentFileStore.listFiles();
			for (int i = 0; i < listOfFiles.length; i++) {
		//		File inputFile = new File(listOfFiles[i].getName());
				try {
					scanner = new Scanner(listOfFiles[i]);
					textContent = scanner.useDelimiter("\\A").next();

				} catch (FileNotFoundException e) {
					System.out.println(e.getMessage());
				} finally {
					if(scanner != null)
					scanner.close();
				}
				replicaHandler.finalFileStore.put(listOfFiles[i].getName(), textContent);
			}
		}

	}



}
