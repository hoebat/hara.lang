package hara.kernel;

import hara.kernel.base.*;
import hara.kernel.builtin.BuiltinStruct;
import hara.kernel.base.completion.ClasspathScanner;
import hara.kernel.base.completion.PackageTree;
import hara.lang.base.Ex;
import hara.lang.base.G;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
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
            .variable(
                LineReader.HISTORY_FILE,
                Paths.get(System.getProperty("user.home"), ".hara_history"))
            .option(LineReader.Option.HISTORY_INCREMENTAL, true)
            .build();

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
        if (t instanceof Ex.Runtime) {
          System.out.println("Error: " + t.getMessage());
        } else {
          t.printStackTrace();
        }
      }
    }
  }
}
