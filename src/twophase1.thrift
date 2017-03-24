exception SystemException {
  1: optional string message
}

enum Status {
  FAILED = 0;
  SUCCESSFUL = 1;
}

struct StatusReport {
  1: required Status status;
}

struct RFile {
  1: optional string filename;
  2: optional string content;
}

service CoordinatorFileStore {
      
  StatusReport writeFile(1: RFile rFile)
    throws (1: SystemException systemException),
  
  RFile readFile(1: string filename)
    throws (1: SystemException systemException),
}


struct TransactionMessage {
1: optional string operation;
2: required i64 transactionid;
3: optional string votestatus;
}


service ParticipantFileStore {
      
  TransactionMessage writeFile(1: RFile rFile, 2: TransactionMessage transactionmessage )
    throws (1: SystemException systemException),
  
  TransactionMessage doCommit(1: TransactionMessage transactionmessage )
    throws (1: SystemException systemException),
  
  RFile readFile(1: string filename)
  throws (1: SystemException systemException),
 
 }