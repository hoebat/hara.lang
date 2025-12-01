package hara.kernel;

import hara.kernel.base.RT;
import java.io.IOException;
import java.util.List;
import org.jline.reader.Candidate;
import org.jline.reader.Completer;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.ParsedLine;
import org.jline.terminal.TerminalBuilder;

public class Terminal {
  private final org.jline.terminal.Terminal terminal;
  private final LineReader lineReader;
  private final JLineInputReader inputReader;

  public Terminal(RT.Instance rt) throws IOException {
    this.terminal = TerminalBuilder.builder().system(true).build();

    Completer completer =
        new Completer() {
          @Override
          public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            var globals = rt.getEnv().getMap();
            var it = globals.keys();
            while (it.hasNext()) {
              String name = it.next().toString();
              if (name.startsWith(word)) {
                candidates.add(new Candidate(name));
              }
            }
          }
        };

    this.lineReader = LineReaderBuilder.builder().terminal(terminal).completer(completer).build();

    this.inputReader = new JLineInputReader(lineReader);
  }

  public JLineInputReader getInputReader() {
    return inputReader;
  }
}
