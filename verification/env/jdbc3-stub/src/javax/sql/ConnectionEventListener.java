package javax.sql;
import java.util.EventListener;
public interface ConnectionEventListener extends EventListener {
  void connectionClosed(ConnectionEvent event);
  void connectionErrorOccurred(ConnectionEvent event);
}
