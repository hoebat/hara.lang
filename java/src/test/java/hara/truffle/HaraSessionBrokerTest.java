package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hara.kernel.Conn;
import java.net.Socket;
import org.junit.Test;

public class HaraSessionBrokerTest {
  @Test
  public void localAndRespClientsShareRootAcrossListenerRestarts() throws Exception {
    try (HaraSessionBroker broker = new HaraSessionBroker(false, false)) {
      broker.root().eval("(def answer 41)");

      try (HaraServer first = new HaraServer(broker, "127.0.0.1", 0, false)) {
        first.start();
        assertEquals("42", legacyEval(first.port(), "(+ answer 1)"));
      }

      assertEquals("user", broker.root().currentNamespace());
      broker.root().eval("(def answer 42)");

      try (HaraServer second = new HaraServer(broker, "127.0.0.1", 0, false)) {
        second.start();
        assertEquals("42", legacyEval(second.port(), "answer"));
      }
    }
  }

  @Test
  public void respControllerCanStartStopAndRestartWithoutClosingRoot() {
    try (HaraSessionBroker broker = new HaraSessionBroker(false, false);
        Main.RespController resp = new Main.RespController(broker, "127.0.0.1", 0, false)) {
      assertEquals("RESP ○ offline", resp.command("/resp"));
      assertTrue(resp.command("/resp start").startsWith("RESP ● 127.0.0.1:"));
      broker.root().eval("(def retained 42)");
      assertEquals("RESP ○ offline", resp.command("/resp stop"));
      assertTrue(resp.command("/resp restart 0").startsWith("RESP ● 127.0.0.1:"));
      assertEquals("42", broker.root().eval("retained").toString());
    }
  }

  private static String legacyEval(int port, String source) throws Exception {
    try (Socket socket = new Socket("127.0.0.1", port)) {
      Conn conn = new Conn(socket);
      conn.write("EVAL", "ROOT", source);
      return text(conn.read());
    }
  }

  private static String text(Object value) {
    if (value instanceof byte[])
      return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
    return String.valueOf(value);
  }
}
