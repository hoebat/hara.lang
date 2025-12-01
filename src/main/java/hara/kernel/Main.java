package hara.kernel;

import hara.kernel.base.Builtin;
import hara.kernel.base.RT;
import hara.kernel.base.Parser;
import hara.kernel.base.Reader;
import hara.kernel.base.completion.ClasspathScanner;
import hara.kernel.base.completion.PackageTree;
import hara.kernel.redirect.FileRedirect;
import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.protocol.IDisplay;

import java.io.FileNotFoundException;
import java.util.List;

import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Candidate;
import org.jline.reader.ParsedLine;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

@SuppressWarnings("rawtypes")
public class Main {

  public static void main(String[] args) throws FileNotFoundException, java.io.IOException {
    var F = new Foundation();
    var redirect = new FileRedirect("log/in.txt", "log/out.txt");
    var server = new Server(F, "PRIMARY", Foundation.DEFAULT_PORT, redirect);
    var rt = new RT.Instance(F, "global");

    F.SERVERS.put(server._key, server);
    F.RTS.put(rt._key, rt);

    server.start();

    // REPL Loop
    System.out.println("Hara Runtime Environment (HRE)");
    System.out.println("Session: " + rt._key);

    Terminal terminal = TerminalBuilder.builder().system(true).build();

    // Initialize Completion System
    PackageTree packageTree = new PackageTree();
    ClasspathScanner scanner = new ClasspathScanner(packageTree);
    scanner.scanBackground();

    Completer completer =
        new Completer() {
          @Override
          public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            // Update classpath from RT before completion
            scanner.scan(rt.classLoader().getURLs());

            String word = line.word();

            // 1. Symbol Completion
            var it = rt.getEnv().keys();
            while (it.hasNext()) {
              Object key = it.next();
              String name = (key instanceof IDisplay) ? ((IDisplay) key).display() : key.toString();

              if (name.startsWith(word)) {
                candidates.add(new Candidate(name));
              }
            }

            // 2. Package/Class Completion
            List<String> suggestions = packageTree.suggest(word);
            for (String s : suggestions) {
              candidates.add(new Candidate(s));
            }
          }
        };

    LineReader lineReader =
        LineReaderBuilder.builder().terminal(terminal).completer(completer).build();

    JLineInputReader inputReader = new JLineInputReader(lineReader);
    Reader r = new Reader(inputReader);

    // Use hara.lang.data.Map from Builtin.Struct.hashMap
    hara.lang.data.Map opts = Builtin.Struct.hashMap(new Object[] {});
    Object EOF_SENTINEL = new Object();

    while (true) {
      try {
        inputReader.resetPrompt();
        Object form = Parser.LispReader.read(r, false, EOF_SENTINEL, false, opts);

        if (form == EOF_SENTINEL) break;

        Object res = rt.eval(form);
        G.prn(res);
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
