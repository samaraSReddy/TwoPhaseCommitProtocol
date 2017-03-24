import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.thrift.TBase;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TJSONProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

public class Client {

	public static void main(String [] args){
		try {
			TTransport transport;
			transport = new TSocket(args[0], Integer.valueOf(args[1]));
			transport.open();
			TProtocol protocol = new  TBinaryProtocol(transport);
			CoordinatorFileStore.Client coordinator = new CoordinatorFileStore.Client(protocol);
			perform(coordinator, args);
			transport.close();
		} 
		catch (SystemException x) {
			printStreamtoConsole(x);
		} catch (Exception x) {
			System.out.println("CLIENT: Operation: " + args[2] +" uncertain, due to Coordinator Failure");
		} 
	}

	private static void perform(CoordinatorFileStore.Client client, String[] args)  throws TException 
	{
		try{
			
			
			if(args!=null){	
				Operation operaionType= Operation.fromString(args[2]);
				switch(operaionType) {		
				case READ: 
					String fileName =args[3];
					RFile fileData=client.readFile(fileName);
					printStreamtoConsole(fileData);
					System.out.println();
					return;

				case WRITE:
					String fileName1 =args[3];
					RFile rfile= assembleFileDetails(fileName1);
					StatusReport report= client.writeFile(rfile); 
					printStreamtoConsole(report);
					System.out.println();
					return ;

				default:
					return;
				}  
			}	 
		}catch (SystemException e) {
			throw new SystemException().setMessage(e.toString());
		}
		catch(IOException e){
			throw new SystemException().setMessage(e.toString());
		}
	}

	
	private static RFile assembleFileDetails(String filePath) throws IOException, SystemException{
		RFile rfile= new RFile();
		Path path = Paths.get(filePath);
		String content=readFile(filePath,StandardCharsets.UTF_8);
		rfile.setFilename(path.getFileName().toString());
		rfile.setContent(content);
		rfile.setContentIsSet(true);
		rfile.setContent(content);
		return rfile;
	}
	
	
	
	public static String readFile(String path, Charset encoding) 
			throws IOException, SystemException 
			{
				File file = new File(path);
				String content=null;
				byte[] encoded=null;
				if(file.exists()){
					encoded= Files.readAllBytes(Paths.get(path));
					content=new String(encoded, encoding);
				}else{
					throw new SystemException().setMessage("The input file "+path+" doesnt exist in the location" );
				}
				return content;
			}
	
	private static void printStreamtoConsole(TBase obj) {
		try {
			BufferedOutputStream   bufferedOut = new BufferedOutputStream(new PrintStream(System.out));
			TProtocol jsonproto = new TJSONProtocol(new TIOStreamTransport(bufferedOut));
			obj.write(jsonproto);
			bufferedOut.flush();
			System.out.print("\n");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
}
