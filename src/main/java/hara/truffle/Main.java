package hara.truffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import hara.kernel.ReplConfig;
import hara.kernel.base.Parser;
import hara.lang.base.G;
import hara.lang.data.Keyword;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Iterator;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.History;
import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.AttributedString;
import org.jline.widget.AutosuggestionWidgets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;
import hara.kernel.Conn;

public final class Main {
  private Main() {}

  public static void main(String[] args) {
    int status = run(args, System.out, System.err);
    if (status != 0) {
      System.exit(status);
    }
  }

  static int run(String[] args, PrintStream output, PrintStream error) {
    return run(args, System.in, output, error);
  }

  static int run(String[] args, InputStream input, PrintStream output, PrintStream error) {
    Capabilities capabilities;
    try {
      capabilities = parseCapabilities(args);
    } catch (IllegalArgumentException exception) {
      error.println(exception.getMessage());
      return 2;
    }
    args = capabilities.arguments;
    if (args.length == 0) {
      return runRepl(
          input,
          output,
          error,
          input == System.in && System.console() != null,
          capabilities,
          !capabilities.offline);
    }

    if ("help".equals(args[0]) || "--help".equals(args[0])) {
      printUsage(output);
      return 0;
    }

    if ("repl".equals(args[0])) {
      return runRepl(
          input,
          output,
          error,
          input == System.in && System.console() != null,
          capabilities,
          !capabilities.offline);
    }

    if ("standalone".equalsIgnoreCase(args[0])) {
      return runRepl(
          input,
          output,
          error,
          input == System.in && System.console() != null,
          capabilities,
          false);
    }

    if ("server".equalsIgnoreCase(args[0]) || "headless".equalsIgnoreCase(args[0])) {
      return runServer(output, error, capabilities);
    }
    if ("remote".equalsIgnoreCase(args[0])) {
      if (args.length < 2) {
        error.println("remote requires HOST:PORT");
        return 2;
      }
      return runRemote(args[1], input, output, error);
    }
    if ("conformance".equals(args[0])) {
      return runConformance(output, error);
    }

    try {
      String source;
      switch (args[0]) {
        case "eval":
          if (args.length < 2) {
            error.println("eval requires a Hara expression");
            return 2;
          }
          source = args[1];
          break;
        case "run":
          if (args.length < 2) {
            error.println("run requires a file path");
            return 2;
          }
          source = Files.readString(Path.of(args[1]), StandardCharsets.UTF_8);
          break;
        case "stdin":
          source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
          break;
        default:
          error.println("Unknown command: " + args[0]);
          printUsage(error);
          return 2;
      }

      try (Context context = context(capabilities)) {
        context.eval(HaraLanguage.ID, "(load-resource \"hara/l0-core.hal\")");
        Value result = context.eval(HaraLanguage.ID, source);
        output.println(display(result));
      }
      return 0;
    } catch (PolyglotException exception) {
      error.println(exception.getMessage());
      return 1;
    } catch (IOException exception) {
      error.println(exception.getMessage());
      return 1;
    }
  }

  private static int runServer(PrintStream output, PrintStream error, Capabilities capabilities) {
    if (capabilities.offline) {
      error.println("--offline cannot be used with headless");
      return 2;
    }
    try (HaraSessionBroker broker =
            new HaraSessionBroker(capabilities.file, capabilities.network);
        HaraServer server =
            new HaraServer(
                broker, capabilities.host, capabilities.port, capabilities.logRequests)) {
      server.start();
      output.println("HARA RESP " + capabilities.host + ":" + server.port() + " · session ROOT");
      output.flush();
      while (server.isRunning()) {
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          return 0;
        }
      }
      return 0;
    } catch (IOException exception) {
      error.println(exception.getMessage());
      return 1;
    }
  }

  private static int runRemote(
      String endpoint, InputStream input, PrintStream output, PrintStream error) {
    int separator = endpoint.lastIndexOf(':');
    if (separator <= 0 || separator == endpoint.length() - 1) {
      error.println("remote expects HOST:PORT");
      return 2;
    }
    String host = endpoint.substring(0, separator);
    try (Socket socket = new Socket(host, Integer.parseInt(endpoint.substring(separator + 1)))) {
      Conn conn = new Conn(socket);
      conn.write("HELLO", "3", "CLIENT", "HARA-REMOTE");
      output.println(remoteText(conn.read()));
      java.io.BufferedReader reader =
          new java.io.BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
      String line;
      long request = 0;
      while ((line = reader.readLine()) != null) {
        String command = line.strip();
        if (command.isEmpty()) continue;
        if ("/quit".equals(command) || ":quit".equals(command)) {
          conn.write("QUIT");
          output.println(remoteText(conn.read()));
          return 0;
        }
        if (command.startsWith("/session use ")) {
          conn.write("SESSION", "ATTACH", command.substring(13).strip());
          output.println(remoteText(conn.read()));
          continue;
        }
        if (command.startsWith("/session ")) {
          String[] parts = command.substring(9).strip().split("\\s+");
          List<Object> requestArgs = new ArrayList<>();
          requestArgs.add("SESSION");
          for (String part : parts) requestArgs.add(part);
          conn.write(requestArgs);
          output.println(remoteText(conn.read()));
          continue;
        }
        String id = "REMOTE-" + (++request);
        conn.write("EVAL", id, command);
        output.println(remoteText(conn.read()));
        output.println(remoteText(conn.read()));
      }
      return 0;
    } catch (Exception exception) {
      error.println(exception.getMessage());
      return 1;
    }
  }

  private static String remoteText(Object value) {
    if (value instanceof byte[]) return new String((byte[]) value, StandardCharsets.UTF_8);
    if (value instanceof List<?>) {
      StringBuilder result = new StringBuilder("[");
      boolean first = true;
      for (Object item : (List<?>) value) {
        if (!first) result.append(' ');
        first = false;
        result.append(remoteText(item));
      }
      return result.append(']').toString();
    }
    return String.valueOf(value);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static int runConformance(PrintStream output, PrintStream error) {
    try (InputStream resource =
        Main.class.getClassLoader().getResourceAsStream("spec/hara/l0-conformance.edn")) {
      if (resource == null) {
        error.println("Missing packaged L0 conformance manifest");
        return 1;
      }
      String source = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
      IMapType manifest = (IMapType) Parser.LispReader.readString(source, null);
      ILinearType<?> cases = (ILinearType<?>) manifest.lookup(Keyword.create("cases"));
      int completed = 0;
      for (Object item : cases) {
        IMapType testCase = (IMapType) item;
        String id = ((Keyword) testCase.lookup(Keyword.create("id"))).getName();
        String form = (String) testCase.lookup(Keyword.create("source"));
        String className = ((Keyword) testCase.lookup(Keyword.create("class"))).getName();
        IMapType expected = (IMapType) testCase.lookup(Keyword.create("expect"));
        if ("reader".equals(className)) {
          Object actual = Parser.LispReader.readString(form, null);
          Object expectedForm = expected.lookup(Keyword.create("form"));
          if (expectedForm != null && !expectedForm.toString().equals(G.display(actual))) {
            throw new IllegalStateException(
                id + " expected " + expectedForm + ", got " + G.display(actual));
          }
        } else {
          String setup = (String) testCase.lookup(Keyword.create("setup"));
          Object expectedError = expected.lookup(Keyword.create("error"));
          Object expectedMessage = expected.lookup(Keyword.create("message"));
          try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
            if (setup != null) context.eval(HaraLanguage.ID, setup);
            Value actual = context.eval(HaraLanguage.ID, form);
            if (expectedError != null) {
              throw new IllegalStateException(id + " expected an error");
            }
            assertConformanceValue(id, actual, expected.lookup(Keyword.create("value")));
          } catch (PolyglotException guestError) {
            if (expectedError == null) throw guestError;
            if (expectedMessage != null
                && !guestError.getMessage().contains(expectedMessage.toString())) {
              throw new IllegalStateException(
                  id + " error mismatch: expected message containing " + expectedMessage);
            }
          }
        }
        completed++;
      }
      output.println("L0 conformance passed: " + completed + " cases");
      return 0;
    } catch (Exception failure) {
      error.println("L0 conformance failed: " + failure.getMessage());
      return 1;
    }
  }

  private static void assertConformanceValue(String id, Value actual, Object expected) {
    if (expected == null) {
      if (!actual.isNull()) throw new IllegalStateException(id + " expected nil");
    } else if (expected instanceof Boolean) {
      if (actual.asBoolean() != (Boolean) expected)
        throw new IllegalStateException(id + " boolean mismatch");
    } else if (expected instanceof String) {
      if (!actual.asString().equals(expected))
        throw new IllegalStateException(id + " string mismatch");
    } else if (expected instanceof BigInteger) {
      if (!actual.as(BigInteger.class).equals(expected))
        throw new IllegalStateException(id + " bigint mismatch");
    } else if (expected instanceof BigDecimal) {
      if (!actual.as(BigDecimal.class).equals(expected))
        throw new IllegalStateException(id + " bigdec mismatch");
    } else if (expected instanceof Number) {
      if (actual.asLong() != ((Number) expected).longValue())
        throw new IllegalStateException(id + " number mismatch");
    } else {
      throw new IllegalStateException(id + " has unsupported expected value " + expected);
    }
  }

  private static int runRepl(
      InputStream input,
      PrintStream output,
      PrintStream error,
      boolean interactive,
      Capabilities capabilities,
      boolean enableResp) {
    try (HaraSessionBroker broker =
        new HaraSessionBroker(capabilities.file, capabilities.network)) {
      RespController resp =
          new RespController(
              broker, capabilities.host, capabilities.port, capabilities.logRequests);
      if (enableResp) {
        try {
          resp.start();
        } catch (IOException exception) {
          error.println("Unable to start RESP listener: " + exception.getMessage());
          return 1;
        }
      }
      try {
        if (interactive)
          return runJLineRepl(broker.root(), resp, output, error, capabilities);
        try (BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
          StringBuilder source = new StringBuilder();
          List<String> history = new ArrayList<>();
          String line;
          while ((line = reader.readLine()) != null) {
            if (source.length() == 0 && ":quit".equals(line.trim())) return 0;
            if (source.length() == 0 && ":help".equals(line.trim())) {
              output.println(":quit          exit the REPL");
              output.println(":help          show this help");
              output.println(":history       show evaluated forms");
              continue;
            }
            if (source.length() == 0 && ":history".equals(line.trim())) {
              for (int i = 0; i < history.size(); i++)
                output.println((i + 1) + ": " + history.get(i));
              continue;
            }
            source.append(line).append('\n');
            if (!isComplete(source)) continue;
            history.add(source.toString().stripTrailing());
            try {
              output.println(display(broker.root().eval(source.toString())));
            } catch (RuntimeException exception) {
              error.println(exception.getMessage());
            }
            source.setLength(0);
          }
          if (source.length() != 0) {
            error.println("Incomplete source");
            return 1;
          }
          return 0;
        }
      } finally {
        resp.close();
      }
    } catch (IOException exception) {
      error.println(exception.getMessage());
      return 1;
    }
  }

  private static Context context(Capabilities capabilities) {
    IOAccess access =
        IOAccess.newBuilder()
            .allowHostFileAccess(capabilities.file)
            .allowHostSocketAccess(capabilities.network)
            .build();
    return Context.newBuilder(HaraLanguage.ID).allowIO(access).build();
  }

  private static Capabilities parseCapabilities(String[] arguments) {
    ArrayList<String> positional = new ArrayList<>();
    boolean file = false;
    boolean network = false;
    boolean offline = false;
    boolean logRequests = false;
    boolean noHistory = false;
    boolean noSplash = false;
    boolean noColor = false;
    Path historyFile = null;
    String host = HaraServer.DEFAULT_HOST;
    int port = HaraServer.DEFAULT_PORT;
    boolean options = true;
    for (int index = 0; index < arguments.length; index++) {
      String argument = arguments[index];
      if (options && "--".equals(argument)) {
        options = false;
      } else if (options && "--allow-file".equals(argument)) file = true;
      else if (options && "--allow-net".equals(argument)) network = true;
      else if (options && "--offline".equals(argument)) offline = true;
      else if (options && "--log-requests".equals(argument)) logRequests = true;
      else if (options && "--no-history".equals(argument)) noHistory = true;
      else if (options && "--no-splash".equals(argument)) noSplash = true;
      else if (options && "--no-color".equals(argument)) noColor = true;
      else if (options && argument.startsWith("--host="))
        host = requiredOption("--host", argument.substring("--host=".length()));
      else if (options && "--host".equals(argument))
        host = requiredOption("--host", nextOption(arguments, ++index, "--host"));
      else if (options && argument.startsWith("--port="))
        port = parsePort(argument.substring("--port=".length()));
      else if (options && "--port".equals(argument))
        port = parsePort(nextOption(arguments, ++index, "--port"));
      else if (options && argument.startsWith("--history="))
        historyFile = Path.of(requiredOption("--history", argument.substring("--history=".length())));
      else if (options && "--history".equals(argument))
        historyFile = Path.of(nextOption(arguments, ++index, "--history"));
      else if (options && argument.startsWith("--"))
        throw new IllegalArgumentException("Unknown option: " + argument);
      else positional.add(argument);
    }
    return new Capabilities(
        file,
        network,
        offline,
        host,
        port,
        logRequests,
        noHistory,
        noSplash,
        noColor,
        historyFile,
        positional.toArray(new String[0]));
  }

  private static String nextOption(String[] arguments, int index, String option) {
    if (index >= arguments.length) throw new IllegalArgumentException(option + " requires a value");
    return requiredOption(option, arguments[index]);
  }

  private static String requiredOption(String option, String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException(option + " requires a value");
    return value;
  }

  private static int parsePort(String value) {
    try {
      int port = Integer.parseInt(requiredOption("--port", value));
      if (port < 0 || port > 65535) throw new NumberFormatException();
      return port;
    } catch (NumberFormatException exception) {
      throw new IllegalArgumentException("--port must be between 0 and 65535");
    }
  }

  private static final class Capabilities {
    private final boolean file;
    private final boolean network;
    private final boolean offline;
    private final String host;
    private final int port;
    private final boolean logRequests;
    private final boolean noHistory;
    private final boolean noSplash;
    private final boolean noColor;
    private final Path historyFile;
    private final String[] arguments;

    private Capabilities(
        boolean file,
        boolean network,
        boolean offline,
        String host,
        int port,
        boolean logRequests,
        boolean noHistory,
        boolean noSplash,
        boolean noColor,
        Path historyFile,
        String[] arguments) {
      this.file = file;
      this.network = network;
      this.offline = offline;
      this.host = host;
      this.port = port;
      this.logRequests = logRequests;
      this.noHistory = noHistory;
      this.noSplash = noSplash;
      this.noColor = noColor;
      this.historyFile = historyFile;
      this.arguments = arguments;
    }
  }

  private static int runJLineRepl(
      HaraSessionBroker.HaraSession session,
      RespController resp,
      PrintStream output,
      PrintStream error,
      Capabilities capabilities)
      throws IOException {
    try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
      ReplConfig baseConfig = ReplConfig.defaults(".hara_history");
      if (capabilities.historyFile != null)
        baseConfig = baseConfig.withHistoryFile(capabilities.historyFile);
      if (capabilities.noSplash) baseConfig = baseConfig.withSplash("");
      ReplConfig config =
          baseConfig.withColor(
              baseConfig.color()
                  && !capabilities.noColor
                  && !"dumb".equalsIgnoreCase(terminal.getType()));
      LineReaderBuilder builder =
          LineReaderBuilder.builder()
              .terminal(terminal)
              .parser(new LispLineParser())
              .highlighter(new SlashCommandHighlighter())
              .completer(new HaraCompleter(session));
      if (!capabilities.noHistory) {
        builder
            .variable(LineReader.HISTORY_FILE, config.historyFile())
            .option(LineReader.Option.HISTORY_INCREMENTAL, true);
      }
      LineReader reader = builder.build();
      AutosuggestionWidgets autosuggestions = new AutosuggestionWidgets(reader);
      autosuggestions.enable();
      reader.setAutosuggestion(LineReader.SuggestionType.TAIL_TIP);
      clearTerminal(terminal);
      printHeader(terminal, config, resp, true);
      reader
          .getWidgets()
          .put(
              "show-doc",
              () -> {
                showDocumentation(session, reader, terminal);
                return true;
              });
      reader
          .getKeyMaps()
          .get(LineReader.MAIN)
          .bind(new org.jline.reader.Reference("show-doc"), "\033OP");
      reader
          .getKeyMaps()
          .get(LineReader.MAIN)
          .bind(new org.jline.reader.Reference("show-doc"), "\033q");
      StringBuilder source = new StringBuilder();
      long lastElapsedNanos = -1L;
      String namespace = session.currentNamespace();
      while (true) {
        try {
          String prompt =
              source.length() == 0
                  ? config.sessionPrompt(namespace)
                  : config.continuationPrompt();
          String line = reader.readLine(prompt, resp.rightPrompt(), (Character) null, null);
          if (source.length() == 0) {
            String command = line.strip();
            if ("/quit".equals(command) || "/exit".equals(command) || ":quit".equals(command))
              return 0;
            if ("/help".equals(command) || ":help".equals(command)) {
              printInteractiveHelp(terminal);
              continue;
            }
            if (command.startsWith("/history") || command.startsWith(":history")) {
              String query = command.replaceFirst("^[/:]history\\s*", "").strip();
              for (History.Entry entry : reader.getHistory()) {
                if (query.isEmpty() || fuzzyScore(query, entry.line()) < Integer.MAX_VALUE)
                  terminal.writer().println((entry.index() + 1) + ": " + entry.line());
              }
              terminal.writer().println();
              terminal.writer().flush();
              continue;
            }
            if (command.startsWith("/doc ")) {
              showDocumentation(session, command.substring(5).strip(), terminal);
              continue;
            }
            if (command.startsWith("/apropos ")) {
              printApropos(session, command.substring(9).strip(), terminal);
              continue;
            }
            if ("/time".equals(command)) {
              terminal
                  .writer()
                  .println(
                      lastElapsedNanos < 0
                          ? "No evaluation yet."
                          : formatElapsed(lastElapsedNanos));
              terminal.writer().println();
              terminal.writer().flush();
              continue;
            }
            if ("/status".equals(command)) {
              printHeader(terminal, config, resp, false);
              continue;
            }
            if ("/clear".equals(command)) {
              clearTerminal(terminal);
              printHeader(terminal, config, resp, true);
              continue;
            }
            if ("/splash".equals(command)) {
              printHeader(terminal, config, resp, true);
              continue;
            }
            if ("/ns".equals(command)) {
              terminal.writer().println(namespace);
              terminal.writer().println();
              terminal.writer().flush();
              continue;
            }
            if (command.equals("/resp") || command.startsWith("/resp ")) {
              terminal.writer().println(resp.command(command));
              terminal.writer().println();
              terminal.writer().flush();
              continue;
            }
            if (line.startsWith("/")) {
              terminal.writer().println("Unknown command: " + command + ". Try /help.");
              terminal.writer().println();
              terminal.writer().flush();
              continue;
            }
          }
          source.append(line).append('\n');
          if (!isComplete(source)) continue;
          long started = System.nanoTime();
          try {
            output.println(display(session.eval(source.toString())));
            output.println();
            namespace = session.currentNamespace();
          } catch (RuntimeException exception) {
            error.println(exception.getMessage());
            error.println();
          } finally {
            lastElapsedNanos = System.nanoTime() - started;
          }
          source.setLength(0);
        } catch (UserInterruptException exception) {
          source.setLength(0);
          output.println("^C");
          output.println();
        } catch (EndOfFileException exception) {
          return 0;
        }
      }
    }
  }

  private static void printHeader(
      Terminal terminal, ReplConfig config, RespController resp, boolean includeSplash) {
    if (includeSplash && !config.splash().isBlank()) {
      terminal.writer().println(config.renderedSplash());
      terminal.writer().println();
    }
    terminal.writer().println("HARA · TRUFFLE                                      SESSION ROOT");
    terminal.writer().println("Journey Within");
    terminal.writer().println("────────────────────────────────────────────────────────────────");
    terminal.writer().println();
    terminal
        .writer()
        .println("  /help  Help       /history  History      /status  Status");
    terminal
        .writer()
        .println("  /resp  Listener   /clear    Clear        /quit    Exit");
    terminal.writer().println();
    terminal.writer().println("RESP  " + resp.status());
    terminal.writer().println();
    terminal.writer().flush();
  }

  private static final String[][] REPL_COMMANDS = {
    {"/help", "show REPL commands"},
    {"/history", "show persistent input history"},
    {"/clear", "clear the terminal and redraw the header"},
    {"/splash", "show the Hara splash and header"},
    {"/status", "show ROOT and RESP status"},
    {"/resp", "show or control the RESP listener"},
    {"/ns", "show the current namespace"},
    {"/doc", "show documentation for a symbol"},
    {"/apropos", "search documented symbols"},
    {"/time", "show the last evaluation time"},
    {"/quit", "exit the REPL"},
    {"/exit", "exit the REPL"}
  };


  private static int fuzzyScore(String query, String value) {
    if (query == null || query.isEmpty()) return 0;
    if (value.equals(query)) return 0;
    String q = query.toLowerCase(java.util.Locale.ROOT);
    String v = value.toLowerCase(java.util.Locale.ROOT);
    if (v.startsWith(q)) return 10 + value.length() - query.length();
    if (v.contains(q)) return 100 + v.indexOf(q);
    int index = 0;
    int gaps = 0;
    for (int i = 0; i < v.length() && index < q.length(); i++) {
      if (v.charAt(i) == q.charAt(index)) index++;
      else if (index > 0) gaps++;
    }
    return index == q.length() ? 200 + gaps : Integer.MAX_VALUE;
  }

  private static String formatElapsed(long nanos) {
    if (nanos < 1_000_000L) return nanos + " ns";
    if (nanos < 1_000_000_000L)
      return String.format(java.util.Locale.ROOT, "%.2f ms", nanos / 1_000_000.0);
    return String.format(java.util.Locale.ROOT, "%.2f s", nanos / 1_000_000_000.0);
  }

  private static void clearTerminal(Terminal terminal) {
    terminal.puts(InfoCmp.Capability.clear_screen);
    terminal.flush();
  }

  private static void showDocumentation(
      HaraSessionBroker.HaraSession session, LineReader reader, Terminal terminal) {
    String symbol =
        HaraCompleter.extractWord(reader.getBuffer().toString(), reader.getBuffer().cursor());
    showDocumentation(session, symbol, terminal);
  }

  private static void showDocumentation(
      HaraSessionBroker.HaraSession session, String symbol, Terminal terminal) {
    if (symbol == null || symbol.isEmpty() || symbol.startsWith("/")) return;
    try {
      Value doc = session.eval("(get (meta #'" + symbol + ") :doc)");
      Value arglists = session.eval("(get (meta #'" + symbol + ") :arglists)");
      terminal.writer().println();
      terminal.writer().println("Documentation: " + symbol);
      if (arglists != null && !arglists.isNull())
        terminal.writer().println("  Arglists: " + arglists);
      if (doc != null && !doc.isNull()) terminal.writer().println("  " + doc.asString());
      terminal.writer().println();
      terminal.writer().flush();
    } catch (RuntimeException ignored) {
      terminal.writer().println("No documentation for " + symbol);
      terminal.writer().println();
      terminal.writer().flush();
    }
  }

  private static void printApropos(
      HaraSessionBroker.HaraSession session, String query, Terminal terminal) {
    try {
      for (String name : session.currentSymbols()) {
        if (fuzzyScore(query, name) == Integer.MAX_VALUE) continue;
        Value doc = session.eval("(get (meta #'" + name + ") :doc)");
        if (doc != null && !doc.isNull()) terminal.writer().println(name + " — " + doc.asString());
      }
      terminal.writer().println();
      terminal.writer().flush();
    } catch (RuntimeException ignored) {
      terminal.writer().println("Unable to search documentation.");
      terminal.writer().println();
      terminal.writer().flush();
    }
  }

  private static void printInteractiveHelp(Terminal terminal) {
    terminal.writer().println();
    terminal.writer().println("REPL");
    printHelpEntry(terminal, "/help", "show this command guide");
    printHelpEntry(terminal, "/history [QUERY]", "search persistent input history");
    printHelpEntry(terminal, "/clear", "clear the terminal and redraw the menu");
    printHelpEntry(terminal, "/splash", "redraw the splash and menu");
    printHelpEntry(terminal, "/time", "show the last evaluation time");
    terminal.writer().println();
    terminal.writer().println("SESSION · ROOT");
    printHelpEntry(terminal, "/status", "show session and listener status");
    printHelpEntry(terminal, "/ns", "show the current namespace");
    printHelpEntry(terminal, "/doc SYMBOL", "show symbol documentation");
    printHelpEntry(terminal, "/apropos QUERY", "search documented symbols");
    terminal.writer().println();
    terminal.writer().println("RESP LISTENER");
    printHelpEntry(terminal, "/resp", "show listener status");
    printHelpEntry(terminal, "/resp start [PORT|HOST:PORT]", "start the listener");
    printHelpEntry(terminal, "/resp stop", "stop the listener; keep ROOT");
    printHelpEntry(terminal, "/resp restart [PORT|HOST:PORT]", "restart the listener; keep ROOT");
    terminal.writer().println();
    terminal.writer().println("EXIT");
    printHelpEntry(terminal, "/quit", "leave Hara");
    printHelpEntry(terminal, "/exit", "leave Hara");
    terminal.writer().println();
    terminal.writer().println("Tab completes commands and visible Hara symbols.");
    terminal.writer().println();
    terminal.writer().flush();
  }

  private static void printHelpEntry(Terminal terminal, String command, String description) {
    terminal.writer().printf("  %-36s %s%n", command, description);
  }

  private static String display(Value result) {
    if (result.isNull()) return "nil";
    if (result.isHostObject() && result.asHostObject() instanceof Iterator<?>) {
      return "#<lazy-iterator>";
    }
    if (result.hasIterator() && !result.hasArrayElements()) return "#<lazy-iterator>";
    return result.toString();
  }

  private static boolean isComplete(CharSequence source) {
    int depth = 0;
    boolean string = false;
    boolean escape = false;
    boolean comment = false;
    for (int i = 0; i < source.length(); i++) {
      char ch = source.charAt(i);
      if (comment) {
        if (ch == '\n' || ch == '\r') {
          comment = false;
        }
        continue;
      }
      if (string) {
        if (escape) {
          escape = false;
        } else if (ch == '\\') {
          escape = true;
        } else if (ch == '"') {
          string = false;
        }
        continue;
      }
      if (ch == ';') {
        comment = true;
      } else if (ch == '"') {
        string = true;
      } else if (ch == '(' || ch == '[' || ch == '{') {
        depth++;
      } else if (ch == ')' || ch == ']' || ch == '}') {
        depth--;
      }
    }
    return depth == 0 && !string && !escape;
  }

  private static void printUsage(PrintStream output) {
    output.println("hara [OPTIONS]                         JLine REPL + ROOT RESP listener");
    output.println("hara --offline                        JLine REPL without a listener");
    output.println("hara headless [OPTIONS]               ROOT RESP listener only");
    output.println("hara server [OPTIONS]                 compatibility alias for headless");
    output.println("hara standalone [OPTIONS]             compatibility alias for --offline");
    output.println("hara remote HOST:PORT");
    output.println("hara [--allow-file] [--allow-net] eval <expression>");
    output.println("hara [--allow-file] [--allow-net] run <file>");
    output.println("hara [--allow-file] [--allow-net] stdin");
    output.println("hara conformance");
    output.println();
    output.println("Options:");
    output.println("  --host HOST, --host=HOST");
    output.println("  --port PORT, --port=PORT");
    output.println("  --offline  --log-requests  --allow-file  --allow-net");
    output.println("  --history PATH  --no-history  --no-splash  --no-color");
  }


  static final class RespController implements AutoCloseable {
    private final HaraSessionBroker broker;
    private final boolean logRequests;
    private String host;
    private int port;
    private HaraServer server;

    RespController(HaraSessionBroker broker, String host, int port, boolean logRequests) {
      this.broker = broker;
      this.host = host;
      this.port = port;
      this.logRequests = logRequests;
    }

    synchronized void start() throws IOException {
      if (isRunning()) return;
      HaraServer candidate = new HaraServer(broker, host, port, logRequests);
      try {
        candidate.start();
        server = candidate;
        port = candidate.port();
      } catch (IOException exception) {
        candidate.close();
        throw exception;
      }
    }

    synchronized void stop() {
      if (server != null) server.close();
      server = null;
    }

    synchronized boolean isRunning() {
      return server != null && server.isRunning();
    }

    synchronized String endpoint() {
      return host + ":" + (isRunning() ? server.port() : port);
    }

    synchronized String status() {
      return isRunning() ? "● " + endpoint() : "○ offline";
    }

    synchronized String rightPrompt() {
      return "RESP " + status();
    }

    synchronized String command(String line) {
      String[] parts = line.strip().split("\\s+");
      if (parts.length == 1) return "RESP " + status();
      String action = parts[1].toLowerCase(java.util.Locale.ROOT);
      try {
        if ("stop".equals(action)) {
          stop();
          return "RESP ○ offline";
        }
        if ("start".equals(action) || "restart".equals(action)) {
          if (parts.length > 3)
            return "Usage: /resp " + action + " [PORT|HOST:PORT]";
          String nextHost = host;
          int nextPort = port;
          if (parts.length == 3) {
            Endpoint endpoint = Endpoint.parse(parts[2], host);
            nextHost = endpoint.host;
            nextPort = endpoint.port;
          }
          if ("start".equals(action) && isRunning())
            return "RESP already running at " + endpoint();
          stop();
          host = nextHost;
          port = nextPort;
          start();
          return "RESP ● " + endpoint();
        }
        return "Usage: /resp [start [PORT|HOST:PORT]|stop|restart [PORT|HOST:PORT]]";
      } catch (IOException | IllegalArgumentException exception) {
        return "RESP error: " + exception.getMessage();
      }
    }

    @Override
    public synchronized void close() {
      stop();
    }
  }

  static final class Endpoint {
    private final String host;
    private final int port;

    private Endpoint(String host, int port) {
      this.host = host;
      this.port = port;
    }

    static Endpoint parse(String value, String defaultHost) {
      int separator = value.lastIndexOf(':');
      if (separator < 0) return new Endpoint(defaultHost, parsePort(value));
      String host = requiredOption("host", value.substring(0, separator));
      return new Endpoint(host, parsePort(value.substring(separator + 1)));
    }
  }

  private static final class SlashCommandHighlighter implements Highlighter {
    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
      String suggestion = "";
      if (reader.getBuffer().cursor() == buffer.length()
          && buffer.startsWith("/")
          && buffer.indexOf(' ') < 0) {
        for (String[] command : REPL_COMMANDS) {
          if (command[0].startsWith(buffer) && command[0].length() > buffer.length()) {
            suggestion = command[0].substring(buffer.length());
            break;
          }
        }
      }
      reader.setTailTip(suggestion);
      return new AttributedString(buffer);
    }

    @Override
    public void setErrorPattern(java.util.regex.Pattern pattern) {}

    @Override
    public void setErrorIndex(int index) {}
  }

  private static final class LispLineParser extends DefaultParser {
    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      char c = buffer.charAt(pos);
      return c == '('
          || c == ')'
          || c == '['
          || c == ']'
          || c == '{'
          || c == '}'
          || Character.isWhitespace(c)
          || c == '"'
          || c == '\'';
    }
  }

  private static final class HaraCompleter implements Completer {
    private final HaraSessionBroker.HaraSession session;

    private HaraCompleter(HaraSessionBroker.HaraSession session) {
      this.session = session;
    }

    @Override
    public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
      String buffer = line.line();
      int cursor = Math.min(line.cursor(), buffer.length());
      int start = wordStart(buffer, cursor);
      String prefix = buffer.substring(start, cursor);
      if (prefix.startsWith("/")) {
        addCommandCandidates(prefix, candidates);
        return;
      }
      try {
        List<ScoredCandidate> matches = new ArrayList<>();
        for (String name : session.currentSymbols()) {
          int score = fuzzyScore(prefix, name);
          if (score < Integer.MAX_VALUE)
            matches.add(new ScoredCandidate(name, score, describe(name)));
        }
        matches.sort(
            (left, right) -> {
              int result = Integer.compare(left.score, right.score);
              return result == 0 ? left.name.compareTo(right.name) : result;
            });
        for (ScoredCandidate match : matches)
          candidates.add(
              new Candidate(
                  match.name, match.name, "Hara symbols", match.description, null, null, true));
      } catch (RuntimeException ignored) {
        // Completion must never disrupt the REPL when the session is unavailable.
      }
    }

    private void addCommandCandidates(String prefix, List<Candidate> candidates) {
      for (String[] command : REPL_COMMANDS) {
        if (fuzzyScore(prefix, command[0]) != Integer.MAX_VALUE)
          candidates.add(
              new Candidate(command[0], command[0], "REPL commands", command[1], null, null, true));
      }
    }

    private String describe(String name) {
      try {
        String escaped = name.replace("\\", "\\\\").replace("\"", "\\\"");
        Value doc = session.eval("(get (meta #'" + escaped + ") :doc)");
        Value arglists = session.eval("(get (meta #'" + escaped + ") :arglists)");
        StringBuilder result = new StringBuilder();
        if (arglists != null && !arglists.isNull()) result.append(arglists);
        if (doc != null && !doc.isNull()) {
          if (result.length() > 0) result.append(" — ");
          result.append(doc.asString().replace('\n', ' '));
        }
        return result.toString();
      } catch (RuntimeException ignored) {
        return "";
      }
    }

    public static String extractWord(String buffer, int cursor) {
      if (buffer == null || buffer.isEmpty() || cursor <= 0) return "";
      int start = wordStart(buffer, Math.min(cursor, buffer.length()));
      return buffer.substring(start, Math.min(cursor, buffer.length()));
    }

    private static int wordStart(String buffer, int cursor) {
      if (buffer == null || buffer.isEmpty() || cursor <= 0) return 0;
      cursor = Math.min(cursor, buffer.length());
      int start = Math.max(0, cursor - 1);
      while (start >= 0) {
        char c = buffer.charAt(start);
        if (c == '('
            || c == ')'
            || c == '['
            || c == ']'
            || c == '{'
            || c == '}'
            || Character.isWhitespace(c)
            || c == '"'
            || c == '\'')
          return start + 1;
        start--;
      }
      return 0;
    }

    private static final class ScoredCandidate {
      private final String name;
      private final int score;
      private final String description;

      private ScoredCandidate(String name, int score, String description) {
        this.name = name;
        this.score = score;
        this.description = description;
      }
    }
  }

}
