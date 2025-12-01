package hara.kernel;

import hara.kernel.base.Builtin;
import hara.kernel.base.RT;
import hara.kernel.base.Parser;
import hara.kernel.base.Reader;
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
  static final String[] JAVA_LANG_CLASSES = {
    "Boolean",
    "Byte",
    "Character",
    "Class",
    "ClassLoader",
    "ClassValue",
    "Compiler",
    "Double",
    "Enum",
    "Float",
    "InheritableThreadLocal",
    "Integer",
    "Long",
    "Math",
    "Number",
    "Object",
    "Package",
    "Process",
    "ProcessBuilder",
    "Runtime",
    "RuntimePermission",
    "SecurityManager",
    "Short",
    "StackTraceElement",
    "StrictMath",
    "String",
    "StringBuffer",
    "StringBuilder",
    "System",
    "Thread",
    "ThreadGroup",
    "ThreadLocal",
    "Throwable",
    "Void",
    "ArithmeticException",
    "ArrayIndexOutOfBoundsException",
    "ArrayStoreException",
    "ClassCastException",
    "ClassNotFoundException",
    "CloneNotSupportedException",
    "EnumConstantNotPresentException",
    "Exception",
    "IllegalAccessException",
    "IllegalArgumentException",
    "IllegalMonitorStateException",
    "IllegalStateException",
    "IllegalThreadStateException",
    "IndexOutOfBoundsException",
    "InstantiationException",
    "InterruptedException",
    "NegativeArraySizeException",
    "NoSuchFieldException",
    "NoSuchMethodException",
    "NullPointerException",
    "NumberFormatException",
    "RuntimeException",
    "SecurityException",
    "StringIndexOutOfBoundsException",
    "TypeNotPresentException",
    "UnsupportedOperationException",
    "AbstractMethodError",
    "AssertionError",
    "ClassCircularityError",
    "ClassFormatError",
    "Error",
    "ExceptionInInitializerError",
    "IllegalAccessError",
    "IncompatibleClassChangeError",
    "InstantiationError",
    "InternalError",
    "LinkageError",
    "NoClassDefFoundError",
    "NoSuchFieldError",
    "NoSuchMethodError",
    "OutOfMemoryError",
    "StackOverflowError",
    "ThreadDeath",
    "UnknownError",
    "UnsatisfiedLinkError",
    "UnsupportedClassVersionError",
    "VerifyError",
    "VirtualMachineError"
  };

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

    Completer completer =
        new Completer() {
          @Override
          public void complete(LineReader reader, ParsedLine line, List<Candidate> candidates) {
            String word = line.word();
            var it = rt.getEnv().keys();
            while (it.hasNext()) {
              Object key = it.next();
              String name = (key instanceof IDisplay) ? ((IDisplay) key).display() : key.toString();

              if (name.startsWith(word)) {
                candidates.add(new Candidate(name));
              }
            }

            for (String name : JAVA_LANG_CLASSES) {
              if (name.startsWith(word)) {
                candidates.add(new Candidate(name));
              }
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
        // Consume rest of the line/buffer to reset state if needed?
        // With Reader, it might be tricky to "flush".
        // But resetPrompt() will set the prompt back to "> ".
        // We might want to ensure we start fresh.
        // But since we are stream based, maybe we don't strictly need to flush unless the reader is
        // in a bad state.
      }
    }
  }
}
