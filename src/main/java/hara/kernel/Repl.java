package hara.kernel;

import hara.kernel.base.*;
import hara.kernel.builtin.BuiltinStruct;
import hara.kernel.base.completion.ClasspathScanner;
import hara.kernel.base.completion.PackageTree;
import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.data.Keyword;
import hara.lang.data.Symbol;
import hara.lang.protocol.IMetadata;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Reference;
import org.jline.reader.Widget;
import org.jline.reader.impl.DefaultParser;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.nio.file.Paths;

/**
 * Interactive Read-Eval-Print Loop (REPL) for the Hara runtime.
 *
 * <p>Features: - JLine-based line editing with history - Autocomplete for symbols and Java classes
 * - Persistent command history
 */
@SuppressWarnings("rawtypes")
public class Repl {
  private final RT.Instance rt;
  private final Terminal terminal;
  private final LineReader lineReader;
  private final JLineInputReader inputReader;
  private final Reader reader;

  public Repl(RT.Instance rt) throws IOException {
    this.rt = rt;

    // Initialize Terminal
    this.terminal = TerminalBuilder.builder().system(true).build();

    // Initialize Completion System
    PackageTree packageTree = new PackageTree();
    ClasspathScanner scanner = new ClasspathScanner(packageTree);
    scanner.scanBackground();

    ReplCompleter completer = new ReplCompleter(rt, scanner, packageTree);

    // Configure LineReader with history and completion
    this.lineReader =
        LineReaderBuilder.builder()
            .terminal(terminal)
            .completer(completer)
            .parser(new LispParser())
            .variable(
                LineReader.HISTORY_FILE,
                Paths.get(System.getProperty("user.home"), ".hara_history"))
            .option(LineReader.Option.HISTORY_INCREMENTAL, true)
            .build();

    // Register "Show Doc" widget
    this.lineReader.getWidgets().put("show-doc", new DocWidget());
    // Bind to Shift+Enter (commonly \033[13;2u in xterm/vscode, or \033OM)
    this.lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("show-doc"), "\033[13;2u");
    this.lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("show-doc"), "\033OM");
    // Bind to Alt+q (Meta-q)
    this.lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("show-doc"), "\033q");
    // Bind to F1
    this.lineReader.getKeyMaps().get(LineReader.MAIN).bind(new Reference("show-doc"), "\033OP");

    this.inputReader = new JLineInputReader(lineReader);
    this.reader = new Reader(inputReader);
  }

  /** Print the REPL banner with session information. */
  public void printBanner() {
    System.out.println("Hara Runtime Environment (HRE)");
    System.out.println("Session: " + rt._key);
  }

  /**
   * Start the REPL loop. Reads forms, evaluates them, and prints results until EOF is encountered.
   */
  public void run() {
    printBanner();

    // Use hara.lang.data.Map from BuiltinStruct.hashMap
    hara.lang.data.Map opts = BuiltinStruct.hashMap(new Object[] {});
    Object EOF_SENTINEL = new Object();

    while (true) {
      try {
        inputReader.resetPrompt();
        Object form = Parser.LispReader.read(reader, false, EOF_SENTINEL, false, opts);

        if (form == EOF_SENTINEL) break;

        Object res = rt.eval(form);
        System.out.println(G.display(res));
      } catch (Throwable t) {
        Throwable cause = t;
        while (cause instanceof java.lang.reflect.InvocationTargetException
            || cause instanceof java.util.concurrent.ExecutionException
            || (cause instanceof RuntimeException
                && cause.getCause() != null
                && cause.getMessage() == null)) {
          if (cause.getCause() != null) {
            cause = cause.getCause();
          } else {
            break;
          }
        }

        if (cause instanceof Ex.Runtime
            || cause instanceof Ex.Unsupported
            || cause instanceof Ex.Arity) {
          System.out.println("Error: " + cause.getMessage());
        } else {
          t.printStackTrace();
        }
      }
    }
  }

  public static class LispParser extends DefaultParser {
    @Override
    public boolean isDelimiterChar(CharSequence buffer, int pos) {
      char c = buffer.charAt(pos);
      return c == '('
          || c == ')'
          || c == '['
          || c == ']'
          || c == '{'
          || c == '}'
          || Character.isWhitespace(c);
    }
  }

  /** Widget to display documentation and arguments for the current symbol. */
  public class DocWidget implements Widget {
    @Override
    public boolean apply() {
      var buffer = lineReader.getBuffer().toString();
      var cursor = lineReader.getBuffer().cursor();
      var word = ReplCompleter.extractWord(buffer, cursor);

      if (word == null || word.isEmpty()) return true;

      try {
        Symbol sym = Symbol.create(word);
        // Look up Var in the environment
        Var v = rt.getObj(sym);
        if (v != null) {
          IMetadata meta = v.meta();
          if (meta != null) {
            Object doc = Keyword.create("doc").invoke(meta);
            Object arglists = Keyword.create("arglists").invoke(meta);

            if (doc != null || arglists != null) {
              // Print info below the prompt
              lineReader.callWidget(LineReader.CLEAR);

              // We use the terminal writer directly to print above the prompt
              var writer = lineReader.getTerminal().writer();
              writer.println();

              if (arglists != null) {
                writer.println("Arglists: " + G.display(arglists));
              }
              if (doc != null) {
                writer.println("Doc: " + doc);
              }
              // Force redraw to restore prompt and buffer
              lineReader.callWidget(LineReader.REDRAW_LINE);
              lineReader.callWidget(LineReader.REDISPLAY);
            }
          }
        }
      } catch (Throwable t) {
        // ignore
      }
      return true;
    }
  }
}
