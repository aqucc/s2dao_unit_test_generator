package javax.sql;
import java.sql.SQLException;
public interface XAConnection extends PooledConnection {
  javax.transaction.xa.XAResource getXAResource() throws SQLException;
}
