import java.sql.*;

public class TestDB
{
  public static void main( String args[] )
  {
    Connection c1 = null;
    Connection c2 = null;
    Connection c3 = null;
    Statement stmt1 = null;Statement stmt2 = null; Statement stmt3 = null;
    try {
      Class.forName("org.sqlite.JDBC");
      c1 = DriverManager.getConnection("jdbc:sqlite:coordinator.db");
      c2 = DriverManager.getConnection("jdbc:sqlite:p1.db");
      c3 = DriverManager.getConnection("jdbc:sqlite:p2.db");
      c1.setAutoCommit(false);
      c2.setAutoCommit(false);
      c3.setAutoCommit(false);
      System.out.println("Opened database successfully");

      stmt1 = c1.createStatement();
      System.out.println("Coordinator TABLE");
      ResultSet rs1 = stmt1.executeQuery( "SELECT * FROM COORDINATOR_LOG;" );
      stmt2 = c2.createStatement();
      stmt3 = c3.createStatement();
      
      ResultSet rs2 = stmt2.executeQuery( "SELECT * FROM p1LOG;" );
      ResultSet rs3 = stmt3.executeQuery( "SELECT * FROM p2LOG;" );
  //    System.out.println(rs.toString());
      while ( rs1.next() ) {
    	  for (int i = 1; i <5; i++) {
              if (i > 1) System.out.print(",  ");
              String columnValue = rs1.getString(i);
              System.out.print(columnValue );
                        }
    	  System.out.println("\n");
      }
      System.out.println("P1 TABLE");
      while ( rs2.next() ) {
    	  for (int i = 1; i <5; i++) {
              if (i > 1) System.out.print(",  ");
              String columnValue = rs2.getString(i);
              System.out.print(columnValue );
                        }
    	  System.out.println("\n");
      }
      System.out.println("P2 TABLE");
      while ( rs3.next() ) {
    	  for (int i = 1; i <5; i++) {
              if (i > 1) System.out.print(",  ");
              String columnValue = rs3.getString(i);
              System.out.print(columnValue );
                        }
    	  System.out.println("\n");
      }
      rs1.close();
      stmt1.close();
      c1.close();
      rs2.close();
      stmt2.close();
      c2.close();
      rs3.close();
      stmt3.close();
      c3.close();
    } catch ( Exception e ) {
      System.err.println( e.getClass().getName() + ": " + e.getMessage() );
      System.exit(0);
    }
    System.out.println("\n"+ "Operation done successfully");
  }
}