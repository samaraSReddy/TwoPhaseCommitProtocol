import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
/**
 * 
 * This class is implementation of CoordinatorFileStore.Iface
 *  
 * Process 1. write 2. read requests from client 
 * 
 *
 */
public class CoordinatorHandler extends InterfaceConstants implements CoordinatorFileStore.Iface {

	public static List<ParticipantReplica> participantList = new ArrayList<ParticipantReplica>();
	public static Integer uniqueTID = 0;
	public static Map<Integer, ConcurrentHashMap<ParticipantReplica, Boolean>> transactionVotemap = new ConcurrentHashMap<Integer, ConcurrentHashMap<ParticipantReplica, Boolean>>();
	public static Map<Integer, Integer> voteCountMap = new ConcurrentHashMap<Integer, Integer>();
	public static Map<Integer, Integer> acknowledgeCountMap = new ConcurrentHashMap<Integer, Integer>();
	public static volatile int testCase = 0;
	
	
	public CoordinatorHandler(List<ParticipantReplica> participantList) {
		CoordinatorHandler.participantList = participantList;
		createLogTableIfNotExists();
		performCoordinatorRecovery();

	}
/**   
 * This is the listener method for write request.
 * 1. Generates new transaction ID. 
 * 2. It sends the write file to all the participants.    
 * 3. Asks the participants if they are ready to process write request and collects votes. 
 * 4. If all the participants voted yes, records the decision as global commit sends commit request to all the participants. 
 * 5. If at-least one participant voted no, records the decision as global abort sends abort request to all the participants.
 
 *  
 */
	@Override
	public StatusReport writeFile(RFile rFile) throws SystemException, TException {
		TransactionMessage transactionmessage = assembleTransactionMessage(WRITE_OPERATION);
		StatusReport report = new StatusReport();
		writeLog(transactionmessage, rFile, WRITE_SENT);
		for (ParticipantReplica replica : participantList) {
			transactionmessage.setVotestatus(WRITE_SENT);
			ParticipantFileStore.Client client = null;
			try {
				TTransport transport;
				transport = new TSocket(replica.getReplicaIp(), replica.getReplicaPort());
				transport.open();
				TProtocol protocol = new TBinaryProtocol(transport);
				client = new ParticipantFileStore.Client(protocol);
				client.writeFile(rFile, transactionmessage);
				transport.close();
			} catch (SystemException x) {
				throw new SystemException().setMessage(x.getMessage());
			}

		}
		try {
			Thread.sleep(1000);
			if(testCase == 3){
				System.exit(0);
			}
		} catch (InterruptedException e) {
			throw new SystemException().setMessage(e.getMessage());
		}
         
		
		transactionmessage.setVotestatus(VOTE_REQUESTED);
		upDateLog(transactionmessage, VOTE_REQUESTED);
		System.out.println("COORDINATOR:  STARTED VOTING PHASE FOR WRITE REQUEST ON FILE: "+rFile.filename );
		for (ParticipantReplica replica : participantList) {

			transactionmessage.setVotestatus(VOTE_REQUESTED);
			ParticipantFileStore.Client client = fetchParticipant(replica);
			try {
				client.canCommit(transactionmessage);
		
			} catch (TException e) {
				throw new SystemException().setMessage(e.getMessage());
			}
		}
		try {
			Thread.sleep(2000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		try {
			int one = voteCountMap.get((int) transactionmessage.transactionid);
			if (one != participantList.size()) {
				System.out.println("COORDINATOR: ALL THE PARTICIPANTS COULD NOT VOTE WITHIN TIME OUT, ABORTING WRITE REQUEST ON FILE : "+rFile.filename );
				for (ParticipantReplica replica : participantList) {
					transactionmessage.setVotestatus(GLOBAL_ABORT);
					ParticipantFileStore.Client client = fetchParticipant(replica);
					client.doAbort(transactionmessage);

				}
				upDateLog(transactionmessage, GLOBAL_ABORT);
			}

			else if (checkVotesRecieved(transactionmessage)) {
				//System.out.println("Inside COMMIT Block for id: " + transactionmessage);
				System.out.println("COORDINATOR: ALL THE PARTICIPANTS ARE READY FOR WRITE OPERATION ON FILE : "+rFile.filename );
				upDateLog(transactionmessage, LOCAL_GLOBAL_COMMIT);
				System.out.println("COORDINATOR: LOGGED DECISION AS COMMIT");
				if(testCase == 41){
					System.exit(0);
				}
				for (ParticipantReplica replica : participantList) {
					transactionmessage.setVotestatus(GLOBAL_COMMIT);
					ParticipantFileStore.Client client1 = fetchParticipant(replica);
					client1.doCommit(transactionmessage);
				}
				System.out.println("COORDINATOR: SENT DECISION AS COMMIT TO ALL PARTICIPANTS");
				upDateLog(transactionmessage, GLOBAL_COMMIT);
				report.setStatus(Status.SUCCESSFUL);
				// System.exit(0);
			} else {
				System.out.println("COORDINATOR: ALL THE PARTICIPANTS ARE NOT READY FOR WRITE REQUEST, ABORTING THE WRITE REQUEST ON  : "+rFile.filename );
				upDateLog(transactionmessage, LOCAL_GLOBAL_ABORT);
				System.out.println("COORDINATOR: LOGGED DECISION AS ABORT");
				if(testCase == 41){
					System.exit(0);
				}
				for (ParticipantReplica replica : participantList) {
					transactionmessage.setVotestatus(GLOBAL_ABORT);

					ParticipantFileStore.Client client = fetchParticipant(replica);
					client.doAbort(transactionmessage);
				}
				upDateLog(transactionmessage, GLOBAL_ABORT);
				System.out.println("COORDINATOR: SENT DECISION AS ABORT TO ALL PARTICIPANTS");
				throw new SystemException()
						.setMessage("CONCURRENT WRITE OPERATION ENCOUNTERED ON FILE " + rFile.filename);
			}
		} catch (Exception e) {
			throw new SystemException().setMessage(e.getMessage());
		}
		return report;
	}
/**
 * This method checks if all the participants voted yes for the write request.
 * 
 * @param transactionMessage
 * @return
 */
	private boolean checkVotesRecieved(TransactionMessage transactionMessage) {
		boolean readyToCommit = true;
		if (transactionMessage != null) {
			// transactionMessage.getTransactionid();
			ConcurrentHashMap<ParticipantReplica, Boolean> voteMap = transactionVotemap
					.get((int) transactionMessage.getTransactionid());
			for (ParticipantReplica replica : participantList) {
				if (!voteMap.get(replica)) {
					return false;
				}
			}
		}
		return readyToCommit;
	}
/**
 * This is the listener method for read request,
 * Every time read request is received from client a random participant is chosen 
 *  and request is redirected to the participant.   
 */
	@Override
	public RFile readFile(String filename) throws SystemException, TException {

		RFile getRFile = null;

		Random randomGenerator = new Random();
		int getParticipantIndex = randomGenerator.nextInt(participantList.size());
		ParticipantFileStore.Client client = null;
		try {
			TTransport transport;
			transport = new TSocket(participantList.get(getParticipantIndex).getReplicaIp(),
					participantList.get(getParticipantIndex).getReplicaPort());
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			System.out.println("COORDINATOR: REDIRECTING THE READ REQUEST ON FILE "+filename + " TO "+participantList.get(getParticipantIndex).getReplicaname());
			client = new ParticipantFileStore.Client(protocol);
			getRFile = client.readFile(filename);
			transport.close();
		} catch (Exception x) {
			SystemException se = new SystemException();
			se.setMessage(x.getMessage());
			throw se;
		}

		return getRFile;
	}

/** assembleTransactionMessage method  
 * 1.Generates the Transaction message based on the operation.
 * 2.Initializes voteCountMap for every transaction Id. 
 * 3.Initializes transactionVotemap for every transaction Id  
 * @param operation
 * @return
 */
	private TransactionMessage assembleTransactionMessage(String operation) {
		ConcurrentHashMap<ParticipantReplica, Boolean> voteMap = new ConcurrentHashMap<ParticipantReplica, Boolean>();
		for (ParticipantReplica replica : participantList) {
			voteMap.put(replica, Boolean.FALSE);
		}

		TransactionMessage transactionMessage = new TransactionMessage();
		Integer transactionId = generateNextTransactionId();
		transactionMessage.setTransactionid(transactionId);
		voteCountMap.put(transactionId, 0);
		acknowledgeCountMap.put(transactionId, 0);
		transactionVotemap.put(transactionId, voteMap);
		transactionMessage.setOperation(operation);
		return transactionMessage;
	}

	/**This method generates unique transactionId.
 * @return
 */
	private synchronized Integer generateNextTransactionId() {
		uniqueTID = uniqueTID + 1;
		return uniqueTID;
	}

	/**
	 * This method fetchParticipant gets the client for each replica 
 *      
 * @param participantReplica
 * @return
 */
	public ParticipantFileStore.Client fetchParticipant(ParticipantReplica participantReplica) {
		ParticipantFileStore.Client client = null;
		try {
			TTransport transport;
			transport = new TSocket(participantReplica.getReplicaIp(), participantReplica.getReplicaPort());
			transport.open();
			TProtocol protocol = new TBinaryProtocol(transport);
			client = new ParticipantFileStore.Client(protocol);
		} catch (Exception x) {
			//System.out.println(x);
		}
		return client;
	}
/**
 * 
 * @param message
 * @param rFile
 * @param status
 */
	public void writeLog(TransactionMessage message, RFile rFile, String status) {
		int TRANSACTION_ID = (int) message.transactionid;
		String FILE_NAME = rFile.filename;
		String OPERATION = message.operation;
		String TRANSACTION_STATUS = status;
		Connection con = null;
		Statement statement = null;
		try {
			Class.forName("org.sqlite.JDBC");
			con = DriverManager.getConnection("jdbc:sqlite:coordinator.db");
			statement = con.createStatement();

			String sqlInsert = "INSERT INTO COORDINATOR_LOG (TRANSACTION_ID,FILE_NAME,OPERATION,TRANSACTION_STATUS) "
					+ "VALUES (" + TRANSACTION_ID + ",'" + FILE_NAME + "','" + OPERATION + "','" + TRANSACTION_STATUS
					+ "');";
			statement.executeUpdate(sqlInsert);

			statement.close();
			con.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
		}
	}
	
/**
 * 
 * @param TransactionMessage
 * @param status
 */
	public void upDateLog(TransactionMessage message, String status) {
		String TRANSACTION_STATUS = status;
		Connection connection = null;
		Statement praparedStatement = null;
		int TRANSACTION_ID = (int) message.getTransactionid();
		try {
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:coordinator.db");

			praparedStatement = connection.createStatement();
			String sql = "UPDATE COORDINATOR_LOG set TRANSACTION_STATUS = '" + TRANSACTION_STATUS
					+ "' where TRANSACTION_ID=" + TRANSACTION_ID + ";";
			praparedStatement.executeUpdate(sql);

			praparedStatement.close();
			connection.close();
		} catch (Exception error) {
			error.printStackTrace();
			System.err.println(error.getClass().getName() + ": " + error.getMessage());
		}
	}

	/**
	* 
 */
	@Override
	public void voteStatus(TransactionMessage transactionmessage, ParticipantReplica participantReplica)
			throws SystemException, TException {
		if (transactionmessage != null && participantReplica != null) {
			ConcurrentHashMap<ParticipantReplica, Boolean> votesMap = transactionVotemap
					.get((int) transactionmessage.transactionid);
			if (transactionmessage.getVotestatus().equals(VOTED_YES)) {
				votesMap.put(participantReplica, Boolean.TRUE);
				Integer count = voteCountMap.get((int) transactionmessage.transactionid);
				voteCountMap.put((int) transactionmessage.transactionid, count + 1);
				if(testCase == 42 && count ==1){
					System.exit(0);
				}

			} else {
				//System.out.println("Recieved VOTE != Yes : " + transactionmessage.getVotestatus());
				votesMap.put(participantReplica, Boolean.FALSE);
				Integer count = voteCountMap.get((int) transactionmessage.transactionid);
				voteCountMap.put((int) transactionmessage.transactionid, count + 1);
			}
		}
	}

	/** All the transaction logs are maintained in a 
 * 
 */
	public static void createLogTableIfNotExists() {
		Connection connection = null;
		Statement preparedStatement = null;
		try {
			Class.forName("org.sqlite.JDBC");
			connection = DriverManager.getConnection("jdbc:sqlite:coordinator.db");
			preparedStatement = connection.createStatement();
			String sqlCreate = "CREATE TABLE if not exists COORDINATOR_LOG "
					+ "(TRANSACTION_ID INT PRIMARY KEY  NOT NULL," + " FILE_NAME  TEXT  NOT NULL, "
					+ " OPERATION	TEXT  NOT NULL, " + " TRANSACTION_STATUS  TEXT, " + " CONTENT  TEXT)";
			preparedStatement.executeUpdate(sqlCreate);
			preparedStatement.close();
			connection.close();
		} catch (Exception e) {
			e.printStackTrace();
			System.err.println(e.getClass().getName() + ": " + e.getMessage());
			System.exit(0);
		}
	}
/**
 *  This method  sends the decision on the transaction ID passed.
 *  Participants utilize this method to know the coordinator decision during their recovery and act accordingly. 
 */
	@Override
	public TransactionMessage getDecision(TransactionMessage transactionMessage) throws SystemException, TException {
		String currentStatus = "";
		TransactionMessage retrieveStatusMessage = transactionMessage;
		Connection getConnection = null;
		Statement getStatement = null;
		try {

			Class.forName("org.sqlite.JDBC");
			getConnection = DriverManager.getConnection("jdbc:sqlite:coordinator.db");

			getStatement = getConnection.createStatement();
			ResultSet resultString = getStatement
					.executeQuery("SELECT TRANSACTION_STATUS FROM COORDINATOR_LOG WHERE TRANSACTION_ID ="
							+ transactionMessage.transactionid + ";");
			currentStatus = resultString.getString("TRANSACTION_STATUS");
			retrieveStatusMessage.setVotestatus(currentStatus);
			resultString.close();
			getStatement.close();
			getConnection.close();
		} catch (Exception error) {
			error.printStackTrace();
			System.err.println(error.getClass().getName() + ": " + error.getMessage());
		}
		return retrieveStatusMessage;
	}

	/**
	 * Performs recovery of pending transactions after coordinator is up.
	 */
	public void performCoordinatorRecovery() {
		uniqueTID = setTransactionID();
		checkIncompleteTransactions();
	}
	
/** 
 *   Incomplete transactions i.e TRANSACTION_STATUS IN('WRITE_SENT', 'VOTE_REQUESTED','LOCAL_GLOBAL_COMMIT','LOCAL_GLOBAL_ABORT')
 *   are recovered by coordinator and resolved based on the status.
 *   IF status is WRITE_SENT --> coordinator is dead before voting hence gets decision from all the participants.  
 * 	 IF status is VOTE_REQUESTED --> coordinator is dead after voting started hence gets decision from all the participants again acts accordingly.
 * 	 IF status is LOCAL_GLOBAL_COMMIT--> coordinator is dead after taking decision hence sends the decision to all the participants during recovery. 	
 */
	public void checkIncompleteTransactions() {

		List<LogEntry> resultSet = new ArrayList<LogEntry>();
		Connection con = null;
		Statement statement = null;
		try {

			Class.forName("org.sqlite.JDBC");
			con = DriverManager.getConnection("jdbc:sqlite:coordinator.db");

			statement = con.createStatement();
			ResultSet rs = statement.executeQuery(
					"SELECT * FROM COORDINATOR_LOG WHERE TRANSACTION_STATUS IN('WRITE_SENT', 'VOTE_REQUESTED','LOCAL_GLOBAL_COMMIT','LOCAL_GLOBAL_ABORT');");
			while (rs.next()) {
				TransactionMessage tMsg = new TransactionMessage();
				tMsg.setTransactionid(rs.getInt("TRANSACTION_ID"));
				tMsg.setVotestatus(rs.getString("TRANSACTION_STATUS"));

				RFile rFile = new RFile();
				rFile.setFilename(rs.getString("FILE_NAME"));
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
				System.out.println("COORDINATOR: RECOVERY MODE, RESOLVING INCOMPLETE TRANSACTIONS");
			}
			
			//System.out.println("Size of resulSet of Recovery " + resultSet.size());
			for (LogEntry lEntry : resultSet) {
				//System.out.println(lEntry.getTransactionMessage().getVotestatus());
				if (lEntry.getTransactionMessage().getVotestatus().equals(WRITE_SENT)) {
					System.out.println("COORDINATOR: LAST DECISION ON FILE \""+ lEntry.rFile.filename+ "\" WITH TRANSACTION ID "+ lEntry.transactionMessage.transactionid+ " :  COORDINATIOR FAILED BEFORE VOTING STARTED"  );
					for (int i = 0; i < participantList.size(); i++) {

						TTransport transport;
						transport = new TSocket(participantList.get(i).getReplicaIp(),
								participantList.get(i).replicaPort);
						transport.open();
						TProtocol protocol = new TBinaryProtocol(transport);
						ParticipantFileStore.Client client = new ParticipantFileStore.Client(protocol);
						TransactionMessage statusMessage = client.getDecision(lEntry.getTransactionMessage());
						if (statusMessage.getVotestatus().equals(GLOBAL_ABORT)) {
							System.out.println("COORDINATOR: COLLECTED  DECISIONS FROM  ALL THE PARTICIPANTS LOGGING THE FINAL STATUS AS GLOBAL_ABORT"  );
							upDateLog(statusMessage, GLOBAL_ABORT);
							break;
						}
					}

				} else if (lEntry.getTransactionMessage().getVotestatus().equals(VOTE_REQUESTED)) {
					System.out.println("COORDINATOR: LAST DECISION ON FILE \""+ lEntry.rFile.filename+ "\" WITH TRANSACTION ID "+ lEntry.transactionMessage.transactionid+ ": FAILED AFTER VOTING STARTED AND BEFORE TAKING DECISION."  );
					List<ParticipantReplica> replicas_Voted_Yes = new ArrayList<ParticipantReplica>();
					int countVotes = 0;
					for (int i = 0; i < participantList.size(); i++) {

						TTransport transport;
						transport = new TSocket(participantList.get(i).getReplicaIp(),
								participantList.get(i).replicaPort);
						transport.open();
						TProtocol protocol = new TBinaryProtocol(transport);
						ParticipantFileStore.Client client = new ParticipantFileStore.Client(protocol);
						TransactionMessage statusMessage = client.getDecision(lEntry.getTransactionMessage());
						if (statusMessage.getVotestatus().equals(GLOBAL_ABORT)) {
							System.out.println("COORDINATOR: COLLECTED  DECISIONS FROM  ALL THE PARTICIPANTS LOGGING THE FINAL STATUS AS GLOBAL_ABORT"  );
							upDateLog(statusMessage, GLOBAL_ABORT);
						} else if (statusMessage.getVotestatus().equals(VOTED_YES)) {
							countVotes++;
							replicas_Voted_Yes.add(participantList.get(i));
						}
					}
					if (countVotes == participantList.size()) {

						//System.out.println("Sent Vote_Commit");
						for (ParticipantReplica replica : participantList) {
							TransactionMessage tm = new TransactionMessage();
							tm.setTransactionid(lEntry.getTransactionMessage().getTransactionid());
							tm.setOperation(lEntry.getTransactionMessage().getOperation());
							tm.setVotestatus(GLOBAL_COMMIT);
							//System.out.println("Sent Vote_Commit" + replica.toString());
							ParticipantFileStore.Client client1 = fetchParticipant(replica);
							client1.doCommit(tm);
						}
						upDateLog(lEntry.getTransactionMessage(), GLOBAL_COMMIT);
					}else {
						System.out.println("COORDINATOR: SENDING GLOBAL_ABORT TO ALL PARTICIPANTS THAT VOTED_YES");
						for (ParticipantReplica replica : replicas_Voted_Yes) {
							TransactionMessage tm = new TransactionMessage();
							tm.setTransactionid(lEntry.getTransactionMessage().getTransactionid());
							tm.setOperation(lEntry.getTransactionMessage().getOperation());
							tm.setVotestatus(GLOBAL_ABORT);
							//System.out.println("Sent Vote_Commit" + replica.toString());
							ParticipantFileStore.Client client1 = fetchParticipant(replica);
							client1.doAbort(tm);
						}
					}

				} else if (lEntry.getTransactionMessage().getVotestatus().equals(LOCAL_GLOBAL_COMMIT)) {
					System.out.println("COORDINATOR: LAST DECISION ON FILE "+ lEntry.rFile.filename+ " WITH TRANSACTION ID "+ lEntry.transactionMessage.transactionid+ ": FAILED AFTER TAKING COMMIT DECISION."  );
					TransactionMessage transactionmessage = new TransactionMessage();
					transactionmessage.setTransactionid(lEntry.getTransactionMessage().transactionid);
					transactionmessage.setVotestatus(GLOBAL_COMMIT);
					System.out.println("COORDINATOR: SENDING GLOBAL_COMMIT TO ALL PARTICIPANTS");
					for (ParticipantReplica replica : participantList) {

						ParticipantFileStore.Client client = fetchParticipant(replica);
						client.doCommit(transactionmessage);

					}
					upDateLog(transactionmessage, GLOBAL_COMMIT);
				} else if (lEntry.getTransactionMessage().getVotestatus().equals(LOCAL_GLOBAL_ABORT)) {
					System.out.println("COORDINATOR: LAST DECISION ON FILE "+ lEntry.rFile.filename+ " : FAILED AFTER TAKING ABORT DECISION."  );
					TransactionMessage transactionmessage = new TransactionMessage();
					transactionmessage.setTransactionid(lEntry.getTransactionMessage().transactionid);
					transactionmessage.setVotestatus(GLOBAL_ABORT);
					System.out.println("COORDINATOR: SENDING GLOBAL_ABORT TO ALL PARTICIPANTS");
					for (ParticipantReplica replica : participantList) {

						ParticipantFileStore.Client client = fetchParticipant(replica);
						client.doAbort(transactionmessage);

					}
					System.out.println("COORDINATOR: SENT THE ABORT DECISION TO ALL PARTICIPANTS."  );
					upDateLog(transactionmessage, GLOBAL_ABORT);
				}

			}
			if(!resultSet.isEmpty() && resultSet.size()>0){
				System.out.println("COORDINATOR: SUCCESFULLY RESOLVED ALL INCOMPLETE TRANSACTIONS."  );
			}

		} catch (Exception error) {
			error.printStackTrace();
			System.err.println(error.getClass().getName() + ": " + error.getMessage());
		}
	}

	/** 
	 * haveCommitted: listens to the acknowledgement of the participant committed the write request. 
 * 
 */
	@Override
	public void haveCommitted(TransactionMessage transactionmessage, ParticipantReplica participantReplica)
			throws TException {
		if (transactionmessage != null && participantReplica != null) {
			//System.out.println("Got Ack from ");
			if (transactionmessage.getVotestatus().equals(GLOBAL_COMMIT)) {
				//System.out.println("Got Ack from " + participantReplica.replicaPort);
				Integer count = acknowledgeCountMap.get((int) transactionmessage.transactionid);
				acknowledgeCountMap.put((int) transactionmessage.transactionid, count + 1);
			}
		}

		Integer count = acknowledgeCountMap.get((int) transactionmessage.transactionid);
		if (count == participantList.size()) {
			upDateLog(transactionmessage, GLOBAL_COMMIT);
		}

	}
/**
 *  If Coordinator is dead during processing requests.
 *  The latest transaction id should be set again from the log.
 *  setTransactionID() method gets last transaction id and sets in scope of CoordinatorHandler
 * 
 * @return
 */
	
	public Integer setTransactionID() {

		Integer currentID = 0;
		Connection getConnection = null;
		Statement getStatement = null;
		try {
			Class.forName("org.sqlite.JDBC");
			getConnection = DriverManager.getConnection("jdbc:sqlite:coordinator.db");

			getStatement = getConnection.createStatement();
			ResultSet resultSet = getStatement.executeQuery(
					"SELECT TRANSACTION_ID FROM COORDINATOR_LOG WHERE TRANSACTION_ID =(SELECT MAX(TRANSACTION_ID) FROM COORDINATOR_LOG);");
			if (resultSet.next()) {
				currentID = Integer.parseInt(resultSet.getString("TRANSACTION_ID"));
			}
			resultSet.close();
			getStatement.close();
			getConnection.close();
		} catch (Exception error) {
			error.printStackTrace();
			System.err.println(error.getClass().getName() + ": " + error.getMessage());
		}
		return currentID;
	}
@Override
public void initializeTestCase(int testcase) throws TException {
	this.testCase=testcase;
	
	
}
}
