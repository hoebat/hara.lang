package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import hara.kernel.Conn;
import java.net.Socket;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class HaraServerTest {
  private HaraServer server;

  @Before
  public void setUp() throws Exception {
    server = new HaraServer("127.0.0.1", 0, false);
    server.start();
  }

  @After
  public void tearDown() {
    if (server != null) server.stop();
  }

  @Test
  public void createsAndAttachesIsolatedSessions() throws Exception {
    try (Socket socket = new Socket("127.0.0.1", server.port())) {
      Conn conn = new Conn(socket);

      conn.write("HELLO", "3");
      List<?> hello = (List<?>) conn.read();
      assertEquals("SERVER", text(hello.get(0)));
      assertEquals("HARA", text(hello.get(1)));

      conn.write("SESSION", "NEW", "APP");
      assertEquals("APP", text(((List<?>) conn.read()).get(2)));
      conn.write("SESSION", "ATTACH", "APP");
      assertEquals("APP", text(((List<?>) conn.read()).get(2)));

      conn.write("EVAL", "REQ-1", "(def answer 41)");
      conn.read();
      conn.read();
      conn.write("EVAL", "REQ-2", "(+ answer 1)");
      List<?> result = (List<?>) conn.read();
      assertEquals("42", text(result.get(2)));
      conn.read();
    }

    assertTrue(server.sessionNames().contains("ROOT"));
    assertTrue(server.sessionNames().contains("APP"));
  }

  @Test
  public void legacyClientsCanEvaluateBySessionWithoutHello() throws Exception {
    try (Socket socket = new Socket("127.0.0.1", server.port())) {
      Conn conn = new Conn(socket);
      conn.write("EVAL", "ROOT", "(+ 19 23)");
      assertEquals("42", text(conn.read()));
    }
  }

  @Test
  public void sessionsAreIsolated() throws Exception {
    try (Socket socket = new Socket("127.0.0.1", server.port())) {
      Conn conn = new Conn(socket);
      conn.write("SESSION", "NEW", "OTHER");
      conn.read();
      conn.write("SESSION", "ATTACH", "OTHER");
      conn.read();
      conn.write("EVAL", "OTHER", "answer");
      Object response = conn.read();
      assertTrue(text(response).contains("ERROR") || text(response).contains("Unbound"));
    }
  }

  @Test
  public void twoTruffleServersExposeIndependentRespEndpoints() throws Exception {
    try (HaraServer peer = new HaraServer("127.0.0.1", 0, false, true);
        HaraServer client = new HaraServer("127.0.0.1", 0, false, true)) {
      peer.start();
      client.start();
      try (Socket peerSocket = new Socket("127.0.0.1", peer.port());
          Socket clientSocket = new Socket("127.0.0.1", client.port())) {
        Conn peerConn = new Conn(peerSocket);
        Conn clientConn = new Conn(clientSocket);
        peerConn.write("HELLO", "3", "CLIENT", "PEER");
        peerConn.read();
        clientConn.write("HELLO", "3", "CLIENT", "CLIENT");
        clientConn.read();
        peerConn.write("SESSION", "NEW", "PEER-SESSION");
        peerConn.read();
        clientConn.write("EVAL", "REQ-CLIENT", "(+ 19 23)");
        clientConn.read();
        clientConn.read();
      }
      assertTrue(peer.sessionNames().contains("PEER-SESSION"));
      assertEquals(java.util.Set.of("ROOT"), client.sessionNames());
    }
  }

  private static String text(Object value) {
    if (value instanceof byte[])
      return new String((byte[]) value, java.nio.charset.StandardCharsets.UTF_8);
    return String.valueOf(value);
  }
}
