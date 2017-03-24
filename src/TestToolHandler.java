import java.io.IOException;

/*Class that automatically starts the coordinator 
 * and replicas using the bash scripts 
*/
import org.apache.thrift.TException;

public class TestToolHandler implements TestTool.Iface {

	@Override
	public void start(EventMessage eMessage) throws TException {

		if (eMessage.getProcessname().equals("COORDINATOR")) {

			ProcessBuilder pb = new ProcessBuilder("./coordinator.sh", Integer.toString(eMessage.coordinatorport),
					eMessage.argument1);
			// inherit IO
			pb.inheritIO();
			Process process;
			try {
				process = pb.start();
				process.waitFor();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		} else if (eMessage.getProcessname().equals("PARTICIPANT")) {
			ProcessBuilder pb = new ProcessBuilder("./replica.sh", Integer.toString(eMessage.processport),
					eMessage.replicaname, eMessage.coordinatorip, Integer.toString(eMessage.coordinatorport));
			// inherit IO
			pb.inheritIO();
			Process process;
			try {
				process = pb.start();
				process.waitFor();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		} else {
			ProcessBuilder pb = new ProcessBuilder("./client.sh", eMessage.coordinatorip,
					Integer.toString(eMessage.coordinatorport), eMessage.getOperation(), eMessage.argument1);
			// inherit IO
			pb.inheritIO();
			Process process;
			try {
				process = pb.start();
				process.waitFor();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
	}

}
