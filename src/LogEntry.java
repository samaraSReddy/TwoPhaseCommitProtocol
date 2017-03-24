
public class LogEntry {
	
	TransactionMessage transactionMessage;
	RFile rFile;
	public TransactionMessage getTransactionMessage() {
		return transactionMessage;
	}
	public void setTransactionMessage(TransactionMessage transactionMessage) {
		this.transactionMessage = transactionMessage;
	}
	public RFile getrFile() {
		return rFile;
	}
	public void setrFile(RFile rFile) {
		this.rFile = rFile;
	}

}
