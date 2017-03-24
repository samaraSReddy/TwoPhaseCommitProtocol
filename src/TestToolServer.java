import java.io.BufferedOutputStream;

import org.apache.thrift.TBase;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;

public class TestToolServer {


	public static TestToolHandler testToolHandler;

	public static TestTool.Processor testToolProcessor;

	public static int port;
	
	/*
	 * "main" method that starts TestTool Server.
	 * */

	public static void main(String[] args) {
		try {
			
			testToolHandler = new TestToolHandler();
			testToolProcessor = new TestTool.Processor(testToolHandler);
			
			port = Integer.valueOf(args[0]);
			
			simple(testToolProcessor);

/*			Runnable runnableSimple = new Runnable() {
				public void run() {
					simple(testToolProcessor);
				}
			};

			new Thread(runnableSimple).start();*/

		} catch (Exception x) {
			write(new SystemException().setMessage(x.toString()));
		}

	}
	
	
    /*
     * Method that fires up the server and 
     * gets things going
     * */
	public static void simple(TestTool.Processor processor) {
		try {
			TServerTransport serverTransport = new TServerSocket(port);
			TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

			System.out.println("Starting the TestTool server...");
			server.serve();
		} catch (Exception e) {
			write(new SystemException().setMessage(e.toString()));
		}
	}
	
	/*
	 * */

	public static void write(TBase t) {
		try {
			BufferedOutputStream bufferedOutStreamServer = new BufferedOutputStream(System.out, 2048);
			TJSONProtocol jsonProtocolServer = new TJSONProtocol(new TIOStreamTransport(bufferedOutStreamServer));
			t.write(jsonProtocolServer);

			bufferedOutStreamServer.flush();
			System.out.println("\n");
		} catch (Exception e) {
			write(new SystemException().setMessage(e.toString()));
		}
	}
}
