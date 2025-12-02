package hara.kernel;

import hara.kernel.base.RT;
import hara.kernel.base.completion.ClasspathScanner;
import hara.kernel.base.completion.PackageTree;
import hara.lang.protocol.IDisplay;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.util.List;

/**
 * JLine completer for the Hara REPL that provides: 1. Symbol completion from the runtime
 * environment 2. Java package and class completion
 *
 * <p>Handles Lisp-style syntax including completion inside parentheses.
 */
public class ReplCompleter implements Completer {
  private final RT.Instance<?> rt;
  private final ClasspathScanner scanner;
  private final PackageTree packageTree;

  public ReplCompleter(RT.Instance<?> rt, ClasspathScanner scanner, PackageTree packageTree) {
    this.rt = rt;
    this.scanner = scanner;
    this.packageTree = packageTree;
  }

  @Override
  public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
    // Update classpath from RT before completion
    scanner.scan(rt.classLoader().getURLs());

    // Extract word using Lisp-aware logic
    String word = extractWord(line.line(), line.cursor());

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

  /**
   * Extract word for Lisp-style completion (handles parens, brackets, etc.) Scans backwards from
   * cursor to find the start of the current word.
   */
  String extractWord(String buffer, int cursor) {
    if (buffer == null || cursor <= 0) return "";

    // Find the start of the current word by looking backwards
    int start = cursor - 1;
    while (start >= 0) {
      char c = buffer.charAt(start);
      // Break on Lisp delimiters
      if (c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}' || c == ' '
          || c == '\t' || c == '\n' || c == '\r' || c == '"' || c == '\'') {
        start++;
        break;
      }
      start--;
    }
    if (start < 0) start = 0;

    return buffer.substring(start, cursor);
  }
}
