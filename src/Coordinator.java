import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransportException;

public class Coordinator {
	public static CoordinatorHandler handler;
	public static CoordinatorFileStore.Processor  processor;	
	public static int port;
	public static List<ParticipantReplica> participantList= new ArrayList<ParticipantReplica>();

	public static void main(String[] args) {

		if(args!=null &&  args.length>0 && args.length==2){	
			try {
				BufferedReader reader = null;
				String line=null;	
				reader = new BufferedReader(new FileReader(args[1]));
				line= reader.readLine();
				while(line!=null ){
					String participantName = null;
					String participantIP = null;
					Integer port = null;
					Pattern pattern = Pattern.compile("(\\s+)");
					String[] participantParamsString = pattern.split(line);			
					if(participantParamsString!=null && participantParamsString.length==3){ 

						participantName=participantParamsString[0];
						participantIP= participantParamsString[1];
						port=Integer.parseInt(participantParamsString[2]);
						ParticipantReplica participant= new ParticipantReplica();
						participant.setReplicaIp(participantIP);
						participant.setReplicaname(participantName);	
						participant.setReplicaPort(port);
						participantList.add(participant);
					}	
					line=reader.readLine();
				}
				System.out.println("Checking recoverable Transactions");
				handler = new CoordinatorHandler(participantList);
				processor = new CoordinatorFileStore.Processor(handler);
				port = Integer.valueOf(args[0]);
			
				simple(processor,port);
				performCoordinatorRecovery();


			}  catch (Exception e) {
			System.out.println(e.getMessage());
			System.out.println("The Coordinator is Down,forcing it to restart");
			}		
		}

	}

	public static void simple(CoordinatorFileStore.Processor processor, Integer port) throws TTransportException {
		
			TServerTransport serverTransport = new TServerSocket(port);
			TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));
			System.out.println("Starting the Coordinator server...");
			server.serve();
	
	}

	public static void performCoordinatorRecovery(){


	}

}
