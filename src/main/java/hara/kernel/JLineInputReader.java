package hara.kernel;

import org.jline.reader.LineReader;

import java.io.IOException;
import java.io.Reader;

public class JLineInputReader extends Reader {
  private final LineReader lineReader;
  private String buffer = "";
  private int pos = 0;
  private boolean firstLine = true;

  public JLineInputReader(LineReader lineReader) {
    this.lineReader = lineReader;
  }

  @Override
  public int read(char[] cbuf, int off, int len) throws IOException {
    if (len == 0) return 0;

    ensureBuffer();

    if (buffer == null) return -1; // EOF

    int count = 0;
    while (count < len) {
      if (pos >= buffer.length()) {
        ensureBuffer();
        if (buffer == null) {
          return (count == 0) ? -1 : count;
        }
      }

      int toCopy = Math.min(len - count, buffer.length() - pos);
      buffer.getChars(pos, pos + toCopy, cbuf, off + count);
      pos += toCopy;
      count += toCopy;
    }
    return count;
  }

  private void ensureBuffer() {
    if (buffer != null && pos >= buffer.length()) {
      try {
        String prompt = firstLine ? "> " : "... ";
        String line = lineReader.readLine(prompt);
        if (line == null) {
          buffer = null;
        } else {
          buffer = line + "\n";
          pos = 0;
          firstLine = false;
        }
      } catch (org.jline.reader.UserInterruptException e) {
        // On Ctrl-C, we can either throw or clear buffer.
        // Best to clear and reset to prompt.
        buffer = "\n";
        pos = 0;
        firstLine = true;
      } catch (org.jline.reader.EndOfFileException e) {
        buffer = null;
      }
    }
  }

  public void resetPrompt() {
    firstLine = true;
  }

  @Override
  public void close() throws IOException {
    // No-op
  }
}
