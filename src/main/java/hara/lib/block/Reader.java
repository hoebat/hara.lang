package hara.lib.block;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.function.Predicate;

public class Reader {

    private final PushbackReader reader;
    private int lineNumber = 1;
    private int columnNumber = 1;

    public Reader(String s) {
        this.reader = new PushbackReader(new StringReader(s), 2);
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    public Character peekChar() {
        try {
            int c = reader.read();
            if (c == -1) {
                return null;
            }
            reader.unread(c);
            return (char) c;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Character readChar() {
        try {
            int c = reader.read();
            if (c == -1) {
                return null;
            }
            if (c == '\n') {
                lineNumber++;
                columnNumber = 1;
            } else {
                columnNumber++;
            }
            return (char) c;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void unreadChar(Character c) {
        try {
            if (c == '\n') {
                lineNumber--;
            } else {
                columnNumber--;
            }
            reader.unread(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String readWhile(Predicate<Character> pred) {
        StringBuilder sb = new StringBuilder();
        while (true) {
            Character c = readChar();
            if (c == null || !pred.test(c)) {
                if (c != null) {
                    unreadChar(c);
                }
                break;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public String readUntil(Predicate<Character> pred) {
        return readWhile(pred.negate());
    }

    public static class ReaderException extends RuntimeException {
        public ReaderException(String message, int line, int column) {
            super(message + " [line " + line + ", column " + column + "]");
        }
    }
}
