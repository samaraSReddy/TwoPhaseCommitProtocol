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


struct TransactionMessage {
1: optional string operation;
2: required i64 transactionid;
3: optional string votestatus;
}

struct ParticipantReplica {
  1:string replicaname;
  2:string replicaIp;
  3:i32 replicaPort;
}


service CoordinatorFileStore {
      
StatusReport writeFile(1: RFile rFile)
    throws (1: SystemException systemException),
  
 RFile readFile(1: string filename)
    throws (1: SystemException systemException),
	
void voteStatus(1: TransactionMessage transactionMessage, 2:ParticipantReplica participantReplica)
    throws (1: SystemException systemException),

oneway  void haveCommitted(1: TransactionMessage transactionmessage , 2:ParticipantReplica participantReplica),
  
		
  TransactionMessage getDecision(1: TransactionMessage transactionMessage)
    throws (1: SystemException systemException),
	
}


service ParticipantFileStore {
      
 oneway void  writeFile(1: RFile rFile, 2: TransactionMessage transactionmessage ),
  
  oneway  void doCommit(1: TransactionMessage transactionmessage ),
  
 oneway  void doAbort(1: TransactionMessage transactionmessage ),

oneway  void canCommit(1: TransactionMessage transactionmessage ),


  RFile readFile(1: string filename)
  throws (1: SystemException systemException),
  
   TransactionMessage getDecision(1: TransactionMessage transactionMessage)
    throws (1: SystemException systemException),

 
 }
 
 
 
 
