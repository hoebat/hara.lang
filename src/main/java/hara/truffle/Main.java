package hara.truffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;

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
    if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
      printUsage(output);
      return 0;
    }

    if ("repl".equals(args[0])) {
      return runRepl(input, output, error, input == System.in && System.console() != null);
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

      try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
        Value result = context.eval(HaraLanguage.ID, source);
        output.println(result.isNull() ? "nil" : result.toString());
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

  private static int runRepl(
      InputStream input, PrintStream output, PrintStream error, boolean interactive) {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      StringBuilder source = new StringBuilder();
      String line;
      boolean continuation = false;
      while (true) {
        if (interactive) {
          output.print(continuation ? "..> " : "hara> ");
          output.flush();
        }
        line = reader.readLine();
        if (line == null) {
          if (source.length() != 0) {
            error.println("Incomplete source");
            return 1;
          }
          return 0;
        }
        if (source.length() == 0 && ":quit".equals(line.trim())) {
          return 0;
        }
        if (source.length() == 0 && ":help".equals(line.trim())) {
          output.println(":quit          exit the REPL");
          output.println(":help          show this help");
          continue;
        }

        source.append(line).append('\n');
        continuation = !isComplete(source);
        if (continuation) {
          continue;
        }

        try {
          Value result = context.eval(HaraLanguage.ID, source.toString());
          output.println(result.isNull() ? "nil" : result.toString());
        } catch (PolyglotException exception) {
          error.println(exception.getMessage());
        }
        source.setLength(0);
        continuation = false;
      }
    } catch (IOException exception) {
      error.println(exception.getMessage());
      return 1;
    }
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
    output.println("hara-truffle eval <expression>");
    output.println("hara-truffle run <file>");
    output.println("hara-truffle stdin");
    output.println("hara-truffle repl");
    output.println("hara-truffle help");
  }
}
