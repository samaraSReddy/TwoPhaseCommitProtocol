import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

/*Client class that implements test cases*/

public class TestClient {

	public static TestToolHandler handler;
	public static List<TestParticipant> testParticipants = new ArrayList<TestParticipant>();
	public static String coordIp;
	public static int coordPort;
	public static int localPort;
	public static String fileName;

	public static void main(String[] args) {
		BufferedReader reader = null;

		if (args.length == 5) {
			try {
				localPort = Integer.parseInt(args[0]);
				coordIp = args[1];
				coordPort = Integer.parseInt(args[2]);
				fileName = args[3];
				String line = null;
				reader = new BufferedReader(new FileReader(args[4]));
				line = reader.readLine();
				while (line != null) {
					String participantName = null;
					String participantIP = null;
					Integer participantPort = null;
					Integer lPort = null;
					Pattern pattern = Pattern.compile("(\\s+)");
					String[] participantParamsString = pattern.split(line);
					if (participantParamsString != null && participantParamsString.length == 4) {

						participantName = participantParamsString[0];
						participantIP = participantParamsString[1];
						participantPort = Integer.parseInt(participantParamsString[2]);
						lPort = Integer.parseInt(participantParamsString[3]);
						TestParticipant testParticipant = new TestParticipant();
						testParticipant.setReplicaIp(participantIP);
						testParticipant.setReplicaname(participantName);
						testParticipant.setReplicaPort(participantPort);
						testParticipant.setLocalPort(lPort);
						testParticipants.add(testParticipant);
					}
					line = reader.readLine();
				}
				StartParticipants(coordIp, coordPort);
				System.out.println("TEST:Starting Participants");
				Thread.sleep(10000);
				System.out.println("TEST:Starting Coordinator");
				StartCoordinator(coordIp, localPort, coordPort, fileName);
				Thread.sleep(10000);
				TestCase1();
				TestCase2();
				TestCase3();
				TestCase4();
				TestCase5();

			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else {
			System.out.println("Invalid number of Arguments expected 5");
		}

	}

	public static void StartCoordinator(String coordIP, int localPort, int coordPort, String inputFilename) {
		TTransport transport;
		transport = new TSocket(coordIP, localPort);
		try {
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			TestTool.Client coordinator = new TestTool.Client(protocol);
			EventMessage em = new EventMessage();
			em.setProcessname("COORDINATOR");
			em.setCoordinatorip(coordIP);
			em.setCoordinatorport(coordPort);
			em.setArgument1(inputFilename);
			coordinator.start(em);
			transport.close();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public static void StartParticipants(String coordIP, int coordPort) {

		for (TestParticipant testParticipants : testParticipants) {
			EventMessage em = new EventMessage();
			em.setProcessname("PARTICIPANT");
			em.setProcessport(testParticipants.getReplicaPort());
			em.setReplicaname(testParticipants.getReplicaname());
			em.setCoordinatorip(coordIP);
			em.setCoordinatorport(coordPort);
			try {
				TTransport transport;
				transport = new TSocket(testParticipants.getReplicaIp(), testParticipants.getLocalPort());
				transport.open();
				TProtocol protocol = new TBinaryProtocol(transport);
				TestTool.Client coordinator = new TestTool.Client(protocol);
				coordinator.start(em);
				transport.close();
			} catch (Exception x) {
				x.printStackTrace();
			}
		}
	}

	public static void writeRequest(String fileName) {
		try {
			EventMessage em = new EventMessage();
			em.setProcessname("WRITE");
			em.setCoordinatorip(coordIp);
			em.setCoordinatorport(coordPort);
			em.setOperation("write");
			em.setArgument1(fileName);

			TTransport transport;
			transport = new TSocket(coordIp, localPort);
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			TestTool.Client coordinator = new TestTool.Client(protocol);
			coordinator.start(em);
			transport.close();
		} catch (Exception x) {
			x.printStackTrace();
		}

	}

	public static void readRequest(String fileName) {
		try {
			EventMessage em = new EventMessage();
			em.setProcessname("READ");
			em.setCoordinatorip(coordIp);
			em.setCoordinatorport(coordPort);
			em.setOperation("read");
			em.setArgument1(fileName);

			TTransport transport;
			transport = new TSocket(coordIp, localPort);
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			TestTool.Client coordinator = new TestTool.Client(protocol);
			coordinator.start(em);
			transport.close();
		} catch (Exception x) {
			x.printStackTrace();
		}

	}

	public static void TestCase1() {

		System.out.println("TEST CASE-1 START...");

		try {

			Thread.sleep(20000);
			for (TestParticipant tp : testParticipants) {
				TTransport transport;
				transport = new TSocket(tp.getReplicaIp(), tp.getReplicaPort());
				transport.open();
				TProtocol protocol = new TBinaryProtocol(transport);
				ParticipantFileStore.Client cClient = new ParticipantFileStore.Client(protocol);
				cClient.initializeTestCase(1);
				transport.close();
			}
			// Thread.sleep(10000);
			writeRequest("sample1");
			Thread.sleep(30000);
			StartParticipants(coordIp, coordPort);
			Thread.sleep(20000);
			readRequest("sample1");
			System.out.println("TEST CASE-1 END...");

		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public static void TestCase2() {

		System.out.println("TEST CASE-2 START...");

		try {

			Thread.sleep(5000);
			writeRequest("sample2");
			Thread.sleep(1000);
			writeRequest("sample2");
			Thread.sleep(5000);
			readRequest("sample2");
            
			System.out.println("TEST CASE-2 END...");

		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public static void TestCase3() {

		System.out.println("TEST CASE-3 START...");

		try {

			Thread.sleep(20000);
			TTransport transport;
			transport = new TSocket(coordIp, coordPort);
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			CoordinatorFileStore.Client cClient3 = new CoordinatorFileStore.Client(protocol);
			cClient3.initializeTestCase(3);
			Thread.sleep(10000);
			writeRequest("sample3");
			Thread.sleep(60000);
			StartCoordinator(coordIp, localPort, coordPort, fileName);
			Thread.sleep(30000);
			readRequest("sample3");
			System.out.println("TEST CASE-3 END...");
			transport.close();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public static void TestCase4() {

		System.out.println("TEST CASE-4 START...");

		try {

			Thread.sleep(5000);
			TTransport transport;
			transport = new TSocket(coordIp, coordPort);
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			CoordinatorFileStore.Client cClient = new CoordinatorFileStore.Client(protocol);
			System.out.println("TEST CASE-4 SUB CASE-1 START...");
			cClient.initializeTestCase(41);
			Thread.sleep(3000);
			writeRequest("sample4");
			Thread.sleep(5000);
			StartCoordinator(coordIp, localPort, coordPort, fileName);
			Thread.sleep(5000);
			readRequest("sample4");
			System.out.println("TEST CASE-4 SUB CASE-1 END..." + "\n");

			Thread.sleep(60000);

			TTransport transport2;
			transport2 = new TSocket(coordIp, coordPort);
			transport2.open();
			TProtocol protocol2 = new TBinaryProtocol(transport2);
			CoordinatorFileStore.Client cClient2 = new CoordinatorFileStore.Client(protocol2);
			System.out.println("TEST CASE-4 SUB CASE-2 START...");
			cClient2.initializeTestCase(42);
			Thread.sleep(3000);
			writeRequest("sample4");
			Thread.sleep(5000);
			StartCoordinator(coordIp, localPort, coordPort, fileName);
			System.out.println("TEST CASE-4 SUB CASE-2 END..." + "\n");
			Thread.sleep(60000);
			readRequest("sample4");
			System.out.println("TEST CASE-4 END...");

		} catch (Exception x) {
			x.printStackTrace();
		}
	}

	public static void TestCase5() {

		System.out.println("TEST CASE-5 START...");

		try {

			Thread.sleep(20000);
			TTransport transport;
			transport = new TSocket(testParticipants.get(0).getReplicaIp(), testParticipants.get(0).getReplicaPort());
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			ParticipantFileStore.Client cClient = new ParticipantFileStore.Client(protocol);
			cClient.initializeTestCase(5);
			transport.close();
			writeRequest("sample5");
			Thread.sleep(60000);
			EventMessage em = new EventMessage();
			em.setProcessname("PARTICIPANT");
			em.setProcessport(testParticipants.get(0).getReplicaPort());
			em.setReplicaname(testParticipants.get(0).getReplicaname());
			em.setCoordinatorip(coordIp);
			em.setCoordinatorport(coordPort);

			TTransport transport1;
			transport1 = new TSocket(testParticipants.get(0).getReplicaIp(), testParticipants.get(0).getLocalPort());
			transport1.open();
			TProtocol protocol1 = new TBinaryProtocol(transport1);
			TestTool.Client coordinator1 = new TestTool.Client(protocol1);
			coordinator1.start(em);
			transport1.close();

			Thread.sleep(60000);
			readRequest("sample5");
			System.out.println("TEST CASE-5 END...");
			transport.close();
		} catch (Exception x) {
			x.printStackTrace();
		}
	}
}
