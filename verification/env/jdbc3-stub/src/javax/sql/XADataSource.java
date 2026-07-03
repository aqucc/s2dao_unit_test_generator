package javax.sql;
import java.sql.SQLException;
import java.io.PrintWriter;
public interface XADataSource {
  XAConnection getXAConnection() throws SQLException;
  XAConnection getXAConnection(String user, String password) throws SQLException;
  PrintWriter getLogWriter() throws SQLException;
  void setLogWriter(PrintWriter out) throws SQLException;
  void setLoginTimeout(int seconds) throws SQLException;
  int getLoginTimeout() throws SQLException;
}
