package hara.truffle;

import hara.kernel.Conn;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

/** RESP server and session broker for the Truffle runtime. */
public final class HaraServer implements AutoCloseable {
  public static final String DEFAULT_HOST = "127.0.0.1";
  public static final int DEFAULT_PORT = 1311;

  private static final List<String> COMMANDS =
      List.of(
          "HELLO",
          "AUTH",
          "PING",
          "ECHO",
          "COMMANDS",
          "INFO",
          "STATUS",
          "SESSION",
          "EVAL",
          "LOAD",
          "DOC",
          "COMPLETE",
          "INTERRUPT",
          "QUIT");

  private final String host;
  private final int requestedPort;
  private final boolean logRequests;
  private final boolean allowNetwork;
  private final Map<String, KernelSession> sessions = new ConcurrentHashMap<>();
  private final ExecutorService clients = Executors.newVirtualThreadPerTaskExecutor();
  private final AtomicBoolean running = new AtomicBoolean();
  private ServerSocket socket;
  private Thread acceptor;

  public HaraServer() {
    this(DEFAULT_HOST, DEFAULT_PORT, false, false);
  }

  public HaraServer(String host, int port, boolean logRequests) {
    this(host, port, logRequests, false);
  }

  public HaraServer(String host, int port, boolean logRequests, boolean allowNetwork) {
    this.host = host;
    this.requestedPort = port;
    this.logRequests = logRequests;
    this.allowNetwork = allowNetwork;
  }

  public synchronized HaraServer start() throws IOException {
    if (running.get()) return this;
    socket = new ServerSocket();
    socket.bind(new InetSocketAddress(host, requestedPort));
    sessions.put("ROOT", new KernelSession("ROOT", allowNetwork));
    running.set(true);
    acceptor = new Thread(this::acceptLoop, "hara-resp-acceptor");
    acceptor.setDaemon(false);
    acceptor.start();
    return this;
  }

  public boolean isRunning() {
    return running.get();
  }

  public int port() {
    ServerSocket current = socket;
    return current == null ? requestedPort : current.getLocalPort();
  }

  public Set<String> sessionNames() {
    return Collections.unmodifiableSet(sessions.keySet());
  }

  public synchronized void stop() {
    if (!running.getAndSet(false)) return;
    try {
      if (socket != null) socket.close();
    } catch (IOException ignored) {
    }
    clients.shutdownNow();
    for (KernelSession session : sessions.values()) session.close();
    sessions.clear();
    if (acceptor != null) acceptor.interrupt();
  }

  @Override
  public void close() {
    stop();
  }

  private void acceptLoop() {
    while (running.get()) {
      try {
        Socket client = socket.accept();
        clients.submit(() -> handle(client));
      } catch (IOException error) {
        if (running.get()) error.printStackTrace();
      }
    }
  }

  private void handle(Socket client) {
    String connection = Integer.toHexString(System.identityHashCode(client));
    String attached = "ROOT";
    boolean negotiated = false;
    try (Socket ignored = client;
        Conn conn = new Conn(client)) {
      while (!client.isClosed()) {
        Object raw = conn.read();
        if (raw == null) return;
        List<String> request = strings(raw);
        if (request.isEmpty()) {
          conn.write(new RuntimeException("EMPTY_COMMAND"));
          continue;
        }
        if (logRequests) System.err.println("REQUEST " + connection + " " + request);
        String command = request.get(0).toUpperCase(java.util.Locale.ROOT);
        try {
          if ("HELLO".equals(command)) {
            negotiated = true;
            conn.write(hello(connection));
          } else if ("PING".equals(command)) {
            conn.writeString("PONG");
          } else if ("ECHO".equals(command)) {
            conn.write(request.subList(1, request.size()));
          } else if ("COMMANDS".equals(command) || "HELP".equals(command)) {
            conn.write(COMMANDS);
          } else if ("INFO".equals(command) || "STATUS".equals(command)) {
            conn.write(info(connection, attached));
          } else if ("SESSION".equals(command)) {
            attached = sessionCommand(conn, request, attached, negotiated);
          } else if ("EVAL".equals(command) || "LOAD".equals(command)) {
            attached = evaluate(conn, request, attached, negotiated, command);
          } else if ("DOC".equals(command)) {
            evaluateDoc(conn, request, attached, negotiated);
          } else if ("COMPLETE".equals(command)) {
            evaluateComplete(conn, request, attached, negotiated);
          } else if ("QUIT".equals(command)) {
            conn.writeString("BYE");
            return;
          } else if ("INTERRUPT".equals(command)) {
            conn.write(new RuntimeException("INTERRUPT_UNSUPPORTED"));
          } else {
            conn.write(new RuntimeException("UNKNOWN_COMMAND " + command));
          }
        } catch (Throwable error) {
          writeError(conn, request, negotiated, error);
        }
      }
    } catch (IOException ignored) {
    }
  }

  private List<Object> hello(String connection) {
    return Arrays.asList(
        "SERVER",
        "HARA",
        "VERSION",
        "0.1.0",
        "PROTO",
        3L,
        "RUNTIME",
        "TRUFFLE",
        "CONNECTION",
        connection,
        "CAPABILITIES",
        COMMANDS);
  }

  private List<Object> info(String connection, String attached) {
    return Arrays.asList(
        "SERVER",
        "HARA",
        "HOST",
        host,
        "PORT",
        (long) port(),
        "CONNECTION",
        connection,
        "SESSION",
        attached,
        "SESSIONS",
        (long) sessions.size());
  }

  private String sessionCommand(
      Conn conn, List<String> request, String attached, boolean negotiated) throws IOException {
    require(request, 2);
    String sub = request.get(1).toUpperCase(java.util.Locale.ROOT);
    switch (sub) {
      case "NEW":
        require(request, 3);
        String created = normalizeName(request.get(2));
        if (sessions.putIfAbsent(created, new KernelSession(created, allowNetwork)) != null)
          throw new IllegalArgumentException("SESSION_EXISTS " + created);
        respond(conn, negotiated, request, created);
        return attached;
      case "LIST":
        List<String> names = new ArrayList<>(sessions.keySet());
        Collections.sort(names);
        respond(conn, negotiated, request, names);
        return attached;
      case "ATTACH":
        require(request, 3);
        String target = normalizeName(request.get(2));
        requireSession(target);
        respond(conn, negotiated, request, target);
        return target;
      case "DETACH":
        respond(conn, negotiated, request, "DETACHED");
        return null;
      case "INFO":
        String targetInfo = request.size() > 2 ? normalizeName(request.get(2)) : attached;
        KernelSession sessionInfo = requireSession(targetInfo);
        respond(conn, negotiated, request, sessionInfo.info());
        return attached;
      case "CLOSE":
      case "KILL":
        require(request, 3);
        String targetClose = normalizeName(request.get(2));
        if ("ROOT".equals(targetClose)) throw new IllegalArgumentException("ROOT_CANNOT_CLOSE");
        KernelSession removed = sessions.remove(targetClose);
        if (removed == null) throw new IllegalArgumentException("NO_SESSION " + targetClose);
        removed.close();
        respond(conn, negotiated, request, true);
        return targetClose.equals(attached) ? null : attached;
      default:
        throw new IllegalArgumentException("UNKNOWN_SESSION_COMMAND " + sub);
    }
  }

  private String evaluate(
      Conn conn, List<String> request, String attached, boolean negotiated, String command)
      throws IOException {
    String requestId = negotiated ? requireValue(request, 1) : "";
    String sessionName;
    String source;
    if (negotiated) {
      sessionName = requireSessionName(attached);
      source = requireValue(request, 2);
    } else {
      sessionName = normalizeName(requireValue(request, 1));
      source = requireValue(request, 2);
    }
    KernelSession session = requireSession(sessionName);
    Object result = session.eval(source);
    if (negotiated) {
      conn.write(Arrays.asList("RESULT", requestId, display(result)));
      conn.write(Arrays.asList("DONE", requestId, "OK"));
    } else {
      conn.writeString(display(result));
    }
    return attached;
  }

  private void evaluateDoc(Conn conn, List<String> request, String attached, boolean negotiated)
      throws IOException {
    String requestId = negotiated ? requireValue(request, 1) : "";
    String symbol = negotiated ? requireValue(request, 2) : requireValue(request, 1);
    validateSymbol(symbol);
    Object result =
        requireSession(requireSessionName(attached))
            .eval(
                "(object :symbol \""
                    + symbol
                    + "\" :doc (get (meta #'"
                    + symbol
                    + ") :doc) :arglists (get (meta #'"
                    + symbol
                    + ") :arglists))");
    respond(conn, negotiated, request, result, requestId);
  }

  private void evaluateComplete(
      Conn conn, List<String> request, String attached, boolean negotiated) throws IOException {
    String requestId = negotiated ? requireValue(request, 1) : "";
    String prefix = negotiated ? requireValue(request, 2) : requireValue(request, 1);
    Object result = requireSession(requireSessionName(attached)).eval("(current-symbols)");
    List<String> matches = new ArrayList<>();
    if (result instanceof Value) {
      Value values = (Value) result;
      for (long i = 0; i < values.getArraySize(); i++) {
        String name = values.getArrayElement(i).asString();
        if (name.toLowerCase(java.util.Locale.ROOT)
            .contains(prefix.toLowerCase(java.util.Locale.ROOT))) matches.add(name);
      }
    }
    Collections.sort(matches);
    respond(conn, negotiated, request, matches, requestId);
  }

  private void respond(Conn conn, boolean negotiated, List<String> request, Object value)
      throws IOException {
    respond(conn, negotiated, request, value, negotiated ? requireValue(request, 1) : "");
  }

  private void respond(Conn conn, boolean negotiated, List<String> request, Object value, String id)
      throws IOException {
    if (negotiated) conn.write(Arrays.asList("RESULT", id, valueAsWire(value)));
    else conn.write(valueAsWire(value));
  }

  private void writeError(Conn conn, List<String> request, boolean negotiated, Throwable error)
      throws IOException {
    String message = error.getMessage() == null ? error.toString() : error.getMessage();
    if (negotiated) {
      String id = request.size() > 1 ? request.get(1) : "";
      conn.write(Arrays.asList("ERROR", id, "REQUEST_ERROR", message));
      if (!id.isEmpty()) conn.write(Arrays.asList("DONE", id, "ERROR"));
    } else {
      conn.write(new RuntimeException("ERR " + message));
    }
  }

  private static Object valueAsWire(Object value) {
    if (value == null) return null;
    if (value instanceof Value) return display(value);
    if (value instanceof Map<?, ?>) {
      List<Object> result = new ArrayList<>();
      for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet())
        result.add(Arrays.asList(String.valueOf(entry.getKey()), valueAsWire(entry.getValue())));
      return result;
    }
    if (value instanceof Iterable<?>) {
      List<Object> result = new ArrayList<>();
      for (Object item : (Iterable<?>) value) result.add(valueAsWire(item));
      return result;
    }
    return value instanceof Number || value instanceof Boolean ? value.toString() : value;
  }

  private static String display(Object value) {
    if (value == null) return "nil";
    if (value instanceof Value) {
      Value v = (Value) value;
      if (v.isNull()) return "nil";
      return v.toString();
    }
    return String.valueOf(value);
  }

  private KernelSession requireSession(String name) {
    KernelSession session = sessions.get(name);
    if (session == null) throw new IllegalArgumentException("NO_SESSION " + name);
    return session;
  }

  private static String requireSessionName(String name) {
    if (name == null) throw new IllegalArgumentException("NO_SESSION_ATTACHED");
    return name;
  }

  private static String normalizeName(String value) {
    if (value == null || value.isEmpty() || !value.matches("[A-Za-z0-9_.-]+"))
      throw new IllegalArgumentException("INVALID_SESSION_NAME");
    return value;
  }

  private static void validateSymbol(String symbol) {
    if (!symbol.matches("[A-Za-z0-9_.*?!+/<>=:-]+"))
      throw new IllegalArgumentException("INVALID_SYMBOL");
  }

  private static void require(List<String> request, int count) {
    if (request.size() < count) throw new IllegalArgumentException("WRONG_NUMBER_OF_ARGUMENTS");
  }

  private static String requireValue(List<String> request, int index) {
    require(request, index + 1);
    return request.get(index);
  }

  private static List<String> strings(Object value) {
    if (!(value instanceof List<?>)) return List.of(String.valueOf(value));
    List<String> result = new ArrayList<>();
    for (Object item : (List<?>) value) {
      if (item instanceof byte[]) result.add(new String((byte[]) item, StandardCharsets.UTF_8));
      else result.add(String.valueOf(item));
    }
    return result;
  }

  private static final class KernelSession implements AutoCloseable {
    private final String name;
    private final Context context;

    private KernelSession(String name, boolean allowNetwork) {
      this.name = name;
      this.context =
          Context.newBuilder(HaraLanguage.ID)
              .allowIO(
                  org.graalvm.polyglot.io.IOAccess.newBuilder()
                      .allowHostSocketAccess(allowNetwork)
                      .build())
              .build();
      context.eval(HaraLanguage.ID, "(load-resource \"hara/l0-core.hal\")");
    }

    private synchronized Object eval(String source) {
      try {
        return context.eval(HaraLanguage.ID, source);
      } catch (PolyglotException error) {
        throw new IllegalArgumentException(error.getMessage(), error);
      }
    }

    private List<Object> info() {
      return Arrays.asList("NAME", name, "STATE", "RUNNING");
    }

    @Override
    public void close() {
      context.close(true);
    }
  }
}
