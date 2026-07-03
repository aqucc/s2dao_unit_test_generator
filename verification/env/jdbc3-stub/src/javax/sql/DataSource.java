package javax.sql;
import java.sql.Connection;
import java.sql.SQLException;
import java.io.PrintWriter;
public interface DataSource extends java.sql.Wrapper {
  Connection getConnection() throws SQLException;
  Connection getConnection(String username, String password) throws SQLException;
  PrintWriter getLogWriter() throws SQLException;
  void setLogWriter(PrintWriter out) throws SQLException;
  void setLoginTimeout(int seconds) throws SQLException;
  int getLoginTimeout() throws SQLException;
}
