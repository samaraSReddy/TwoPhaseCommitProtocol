import java.sql.*;

public class SQLiteJDBC
{
  public static void main( String args[] )


  {
	  Connection updateConnection = null;
		Statement updateStatement = null;
    Connection c = null;
    Statement stmt = null;
    int TID = 4;
    String currentStatus = "VOTE_REQUESTED";
    try {
    	Class.forName("org.sqlite.JDBC");
		updateConnection = DriverManager.getConnection("jdbc:sqlite:" + "p1" + ".db");

		updateStatement = updateConnection.createStatement();
		String sql = "UPDATE " + "p1" + "Log set STATUS = '" + currentStatus + "' where TID=" + TID + ";";
		updateStatement.executeUpdate(sql);

		updateStatement.close();
		updateConnection.close();
     /* Class.forName("org.sqlite.JDBC");
      c = DriverManager.getConnection("jdbc:sqlite:test.db");
      c.setAutoCommit(false);
      System.out.println("Opened database successfully");

      stmt = c.createStatement();
      String sql = "INSERT INTO COMPANY (ID,NAME,AGE,ADDRESS,SALARY) " +
                   "VALUES (1, 'Paul', 32, 'California', 20000.00 );"; 
      stmt.executeUpdate(sql);

      sql = "INSERT INTO COMPANY (ID,NAME,AGE,ADDRESS,SALARY) " +
            "VALUES (2, 'Allen', 25, 'Texas', 15000.00 );"; 
      stmt.executeUpdate(sql);

      sql = "INSERT INTO COMPANY (ID,NAME,AGE,ADDRESS,SALARY) " +
            "VALUES (3, 'Teddy', 23, 'Norway', 20000.00 );"; 
      stmt.executeUpdate(sql);

      sql = "INSERT INTO COMPANY (ID,NAME,AGE,ADDRESS,SALARY) " +
            "VALUES (4, 'Mark', 25, 'Rich-Mond ', 65000.00 );"; 
      stmt.executeUpdate(sql);

      stmt.close();
      c.commit();
      c.close();*/
    } catch ( Exception e ) {
      System.err.println( e.getClass().getName() + ": " + e.getMessage() );
      System.exit(0);
    }
    System.out.println("Records created successfully");
  }
}