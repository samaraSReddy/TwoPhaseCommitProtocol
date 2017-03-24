import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;


/** 
 * This class ParticipantHandler is implementation of  ParticipantFileStore.Iface
 *  process the requests of write and read from coordinator 
 *  Takes decision and sends vote to coordinator 
 *  persists the files in permanent file system
 *  uses sql-lite to log the transactions.
 * 
 */

public class ParticipantHandler extends InterfaceConstants implements ParticipantFileStore.Iface {

	public static String replicaName;
	public static int replicaPort;
	public static String coordinatorHostname;
	public static int coordinatorPort;
	public static Map<String, String> finalFileStore;
	private static int testCase = 0;

	public ParticipantHandler(String rPort, String rName, String hostName, String portNum) {
		ParticipantHandler.replicaName = rName;
		ParticipantHandler.coordinatorHostname = hostName;
		ParticipantHandler.coordinatorPort = Integer.parseInt(portNum);
		ParticipantHandler.replicaPort = Integer.parseInt(rPort);
		finalFileStore = new ConcurrentHashMap<String, String>();
		createTable(rName);
		checkIncompleteTransactions();
	}

	/**
	 * This method  createTable is invoked for every start of participant.
	 * creates logging table for the participant if not present already. 
	 * 
	 * @param dbName
	 */

	public void createTable(String dbName) {
		Connection connection1 = null;
		Statement statement1 = null;
		try {
			Class.forName("org.sqlite.JDBC");
			connection1 = DriverManager.getConnection("jdbc:sqlite:" + dbName + ".db");
			statement1 = connection1.createStatement();
			String sqlQuery1 = "CREATE TABLE IF NOT EXISTS " + dbName + "Log" + " ( TID INT PRIMARY KEY     NOT NULL,"
					+ " FILENAME           varchar(255)    NOT NULL, " + " CONTENT            Text     NOT NULL, "
					+ " STATUS        varchar(255) )";
			statement1.executeUpdate(sqlQuery1);
			statement1.close();
			connection1.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
			// System.exit(0);
		}
	}

	/** This method inserts  the request into log table 
	 *  
	 * @param rFile
	 * @param tMessage
	 */

	public static void createLog(RFile rFile, TransactionMessage tMessage) {

		int TID = (int) tMessage.transactionid;
		String FILENAME = rFile.filename;
		String STATUS = tMessage.votestatus;
		String CONTENT = rFile.content;
		Connection createConnection = null;
		Statement createStmt = null;
		try {
			Class.forName("org.sqlite.JDBC");
			createConnection = DriverManager.getConnection("jdbc:sqlite:" + replicaName + ".db");
			createStmt = createConnection.createStatement();

			String sqlInsert = "INSERT INTO " + replicaName + "Log (TID,FILENAME,CONTENT,STATUS) " + "VALUES (" + TID
					+ ",'" + FILENAME + "','" + CONTENT + "','" + STATUS + "');";
			createStmt.executeUpdate(sqlInsert);

			createStmt.close();
			createConnection.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}

	}

	/**upDateLog method 
	 * updates the log for transaction based on the latest status 
	 * @param transactionmessage
	 */
	public static void upDateLog(TransactionMessage transactionmessage) {

		int TID = (int) transactionmessage.transactionid;
		String currentStatus = transactionmessage.votestatus;
		Connection updateConnection = null;
		Statement updateStatement = null;
		try {
			Class.forName("org.sqlite.JDBC");
			updateConnection = DriverManager.getConnection("jdbc:sqlite:" + replicaName + ".db");

			updateStatement = updateConnection.createStatement();
			String sql = "UPDATE " + replicaName + "Log set STATUS = '" + currentStatus + "' where TID=" + TID + ";";
			updateStatement.executeUpdate(sql);

			updateStatement.close();
			updateConnection.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}

	}

	/**
	 * This method helps to know the current status on the file based on transactionId.
	 * 
	 * @param transactionmessage
	 * @return
	 */
	public String getStatus(TransactionMessage transactionmessage) {

		String currentStatus = "";
		Connection getConnection = null;
		Statement getStatement = null;
		try {

			Class.forName("org.sqlite.JDBC");
			getConnection = DriverManager.getConnection("jdbc:sqlite:" + replicaName + ".db");

			getStatement = getConnection.createStatement();
			ResultSet resultString = getStatement.executeQuery(
					"SELECT STATUS FROM " + replicaName + "Log WHERE TID =" + transactionmessage.transactionid + ";");
			if(resultString.next()){
				currentStatus = resultString.getString("STATUS");
			}
			resultString.close();
			getStatement.close();
			getConnection.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
		return currentStatus;
	}

	/** This method helps to know the current status on the file based on filename. 
	 * 
	 * @param fileName
	 * @return
	 */
	public String getWriteStatus(String fileName) {

		String currentStatus = "NO_STATUS";
		Connection getConnection = null;
		Statement getStatement = null;
		try {

			Class.forName("org.sqlite.JDBC");
			getConnection = DriverManager.getConnection("jdbc:sqlite:" + replicaName + ".db");

			getStatement = getConnection.createStatement();
			ResultSet resultString = getStatement.executeQuery(
					"SELECT STATUS FROM " + replicaName + "Log WHERE FILENAME = '" + fileName + "' ORDER BY TID DESC;");
			if(resultString.next()){
				currentStatus = resultString.getString("STATUS");
			}
			resultString.close();
			getStatement.close();
			getConnection.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
		return currentStatus;
	}
	/** writeFile : This method is invoked from coordinator to process write request.
	 * 1.Logs the file received in temporary storage 
	 * 2. If there is concurrent write requests aborts the transaction by voting no to the coordinator
	 * 3. If there are no concurrent requests votes yes to the coordinator.
	 * 4. waits for the coordinator decision to  commit or abort the transaction.
	 * 5. If decision is commit stores the file in permanent file storage
	 * 6. If decision is abort marks the decision as  abort.
	 */

	@Override
	public void writeFile(RFile rFile, TransactionMessage transactionmessage) throws SystemException, TException {

		System.out.println("PARTICIPANT "+replicaName + ": RECIEVED WRITE_OPERATION" + " ON FILE : " + rFile.filename);
		ParticipantReplica pReplica = new ParticipantReplica();
		pReplica.setReplicaname(replicaName);
		try {
			pReplica.setReplicaIp(Inet4Address.getLocalHost().getHostAddress());
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
		String currentStatus1 = getWriteStatus(rFile.filename);
		if(currentStatus1.equals(VOTE_REQUESTED) || currentStatus1.equals(WRITE_SENT)){
			System.out.println("PARTICIPANT "+replicaName + ": ABORTING THE CURRENT WRITE REQUEST & VOTING NO DUE TO CONCURRENT OPERATION ON FILE : " + rFile.filename);
			transactionmessage.setVotestatus(GLOBAL_ABORT);
			createLog(rFile, transactionmessage);
			transactionmessage.setVotestatus(VOTED_NO);
			TTransport transport1;
			transport1 = new TSocket(coordinatorHostname, Integer.valueOf(coordinatorPort));
			transport1.open();
			TProtocol protocol1 = new TBinaryProtocol(transport1);
			CoordinatorFileStore.Client coordClient = new CoordinatorFileStore.Client(protocol1);
			coordClient.voteStatus(transactionmessage, pReplica);
			transport1.close();

		} else {
			createLog(rFile, transactionmessage);
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			String currentStatus2 = getStatus(transactionmessage);
			if (currentStatus2.equals(VOTE_REQUESTED)) {
				transactionmessage.setVotestatus(VOTED_YES);
				upDateLog(transactionmessage);
				System.out.println("PARTICIPANT "+replicaName + ": VOTED_YES FOR WRITE_OPERATION" + " ON FILE : " + rFile.filename);
				TTransport transport1;
				transport1 = new TSocket(coordinatorHostname, Integer.valueOf(coordinatorPort));
				transport1.open();
				TProtocol protocol1 = new TBinaryProtocol(transport1);
				CoordinatorFileStore.Client client1 = new CoordinatorFileStore.Client(protocol1);
				pReplica.setReplicaPort(replicaPort);
				client1.voteStatus(transactionmessage, pReplica);
				transport1.close();
		
			}else {
				transactionmessage.setVotestatus(GLOBAL_ABORT);
				upDateLog(transactionmessage);

			}
		}
	}
	/** doCommit:
	 * 1.This is the listener method for commit request from coordinator.
	 * 2.Logs the decision as commit and stores the file in permanently.
	 * 
	 */
	@Override
	public void doCommit(TransactionMessage transactionmessage) throws SystemException, TException {
		if(testCase == 5){
			System.exit(0);
		}
		RFile rFile = new RFile();
		ParticipantReplica pReplica = new ParticipantReplica();
		pReplica.setReplicaname(replicaName);
		pReplica.setReplicaPort(replicaPort);

		Connection con = null;
		Statement statement = null;
		try {

			Class.forName("org.sqlite.JDBC");
			con = DriverManager.getConnection("jdbc:sqlite:" + replicaName + ".db");
			statement = con.createStatement();
			ResultSet rs = statement.executeQuery(
					"SELECT * FROM " + replicaName + "Log WHERE TID =" + transactionmessage.transactionid + ";");
			rFile.setFilename(rs.getString("FILENAME"));
			rFile.setContent(rs.getString("CONTENT"));
			pReplica.setReplicaIp(Inet4Address.getLocalHost().getHostAddress());
			con.close();
			transactionmessage.setVotestatus(GLOBAL_COMMIT);
			upDateLog(transactionmessage);
			BufferedWriter bw = new BufferedWriter(new FileWriter(replicaName + "_FileStore/" + rFile.getFilename()));
			bw.write(rFile.getContent());
			bw.flush();
			bw.close();
			System.out.println("PARTICIPANT "+replicaName + ": COMMITED THE FILE : " + rFile.filename+" SUCCESFULLY");
		} catch (IOException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		} catch (SQLException e) {
			e.printStackTrace();
			System.err.println(e.getMessage());
		}
		finalFileStore.put(rFile.filename, rFile.getContent());
		TTransport transport2;
		transport2 = new TSocket(coordinatorHostname, Integer.valueOf(coordinatorPort));
		transport2.open();
		TProtocol protocol2 = new TBinaryProtocol(transport2);
		CoordinatorFileStore.Client client2 = new CoordinatorFileStore.Client(protocol2);
		client2.haveCommitted(transactionmessage, pReplica);
		transport2.close();
		if(testCase == 1){
			System.exit(0);
		}

	}

	/**
	 *  Listener method for abort request from the coordinator 
	 * 1. Updates the log to abort for the given transaction
	 */
	@Override
	public void doAbort(TransactionMessage transactionmessage) throws SystemException, TException {
		upDateLog(transactionmessage);
	}
	
	
	/** readFile: reads the request from coordinator
	 * checks the file is present in the file system and returns back with the content.
	 * If not present raises error message  "File " + filename + " DOES NOT EXIST"
	 *
	 */
	@Override
	public RFile readFile(String filename) throws SystemException, TException {
		RFile recordFile = new RFile();
		System.out.println("PARTICIPANT "+replicaName + ": RECIEVED READ_OPERATION" + " ON FILE  \"" + filename +"\".");
		if(finalFileStore.containsKey(filename)){
			recordFile.setFilename(filename);
			recordFile.setContent(finalFileStore.get(filename));
		}else {
			SystemException se = new SystemException();
			se.setMessage("File " + filename + " DOES NOT EXIST");
			throw se;
		}
		return recordFile;
	}
/**
 * getDecision :
 * 1. gives the decision logged by the participant for the transaction Id 
 * 
 */
	@Override
	public TransactionMessage getDecision(TransactionMessage transactionMessage) throws SystemException, TException {
		String currentStatus = getStatus(transactionMessage);
		return transactionMessage.setVotestatus(currentStatus);
	}
/**
 * checkIncompleteTransactions : 
 * 1.Gets the incomplete transactions which are in the state of 'VOTE_REQUESTED'
 * 2. Calls the coordinator to know the decision on the transaction 
 * 3. Knows the decision(commit/abort) from coordinator and process accordingly.
 * 
 * 
 */
	public static void checkIncompleteTransactions() {
		
		
		Connection con = null;
		Statement statement = null;
		List<LogEntry> resultSet = new ArrayList<LogEntry>();
		try {

			Class.forName("org.sqlite.JDBC");
			con = DriverManager.getConnection("jdbc:sqlite:" + replicaName + ".db");

			statement = con.createStatement();
			ResultSet rs = statement
					.executeQuery("SELECT * FROM " + replicaName + "Log WHERE STATUS IN( 'VOTE_REQUESTED','VOTED_YES');");


			while (rs.next()) {
				TransactionMessage tMsg = new TransactionMessage();
				tMsg.setTransactionid(rs.getInt("TID"));
				tMsg.setVotestatus(rs.getString("STATUS"));

				RFile rFile = new RFile();
				rFile.setFilename(rs.getString("FILENAME"));
				rFile.setContent(rs.getString("CONTENT"));

				LogEntry logEntry = new LogEntry();
				logEntry.setrFile(rFile);
				logEntry.setTransactionMessage(tMsg);
				resultSet.add(logEntry);

			}

			rs.close();
			statement.close();
			con.close();
			if(resultSet!=null && resultSet.size()>0){
				System.out.println("PARTICIPANT "+replicaName + ": RESOLVING INCOMPLETE TRANSACTIONS ");
			}
			for(LogEntry lEntry : resultSet) {
				System.out.println("PARTICIPANT "+replicaName + ": FAILED AFTER VOTING FOR WRITE REQUEST ON FILE \""+lEntry.rFile.filename + "\" WITH TRANASACTION ID "+lEntry.transactionMessage.transactionid);
				try {
					TTransport transport;

					transport = new TSocket(coordinatorHostname, coordinatorPort);
					transport.open();

					TProtocol protocol = new TBinaryProtocol(transport);
					CoordinatorFileStore.Client cClient = new CoordinatorFileStore.Client(protocol);
					System.out.println("PARTICIPANT "+replicaName + ": SENDING REQUEST TO COORDINATOR FOR DECISION ON FILE "+lEntry.getrFile().getFilename());
					TransactionMessage tm = cClient.getDecision(lEntry.getTransactionMessage());
					if (tm.getVotestatus().equals(GLOBAL_COMMIT)) {
						System.out.println("PARTICIPANT "+replicaName + ": ");
						upDateLog(tm);
						try {
							BufferedWriter bw = new BufferedWriter(
									new FileWriter(replicaName + "_FileStore/" + lEntry.getrFile().getFilename()));
							bw.write(lEntry.getrFile().getContent());
							bw.flush();
							bw.close();
							System.out.println("PARTICIPANT "+replicaName + ": UPDATING THE DECISION TO GLOBAL COMMIT AND PERSISTING THE FILE "+lEntry.getrFile().getFilename());
						} catch (IOException e) {
							System.err.println(e.getMessage());
						}
						finalFileStore.put(lEntry.getrFile().getFilename(), lEntry.getrFile().content);

					} else if (tm.getVotestatus().equals(GLOBAL_ABORT)) {
						upDateLog(tm);
						System.out.println("PARTICIPANT "+replicaName + ": UPDATING THE DECISION TO GLOBAL ABORT ON LAST WRITE REQUEST FOR FILE "+lEntry.getrFile().getFilename());
					}
					transport.close();
					if(resultSet!=null && resultSet.size()>0){
						System.out.println("PARTICIPANT "+replicaName + ": RESOLVED ALL INCOMPLETE TRANSACTIONS ");
					}
				} catch (TException x) {
					x.printStackTrace();
					System.err.println(x.getMessage());
				} catch (Exception e) {
					e.printStackTrace();
					System.err.println(e.getMessage());
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
	}
/** 
 * canCommit :
 * is used to know the readiness of participant to process the write request.
 * 
 */
	@Override
	public void canCommit(TransactionMessage transactionmessage) throws TException {

		String currentStatus = getStatus(transactionmessage);
		if(!currentStatus.equals(GLOBAL_ABORT)){
			upDateLog(transactionmessage);	
		}
	}

@Override
public void initializeTestCase(int testcase) throws TException {
	this.testCase=testcase;
	
}
}
