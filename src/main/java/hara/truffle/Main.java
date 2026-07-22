package hara.truffle;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import hara.kernel.base.Parser;
import hara.lang.base.G;
import hara.lang.data.Keyword;
import hara.lang.data.types.ILinearType;
import hara.lang.data.types.IMapType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
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
          try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
            if (setup != null) context.eval(HaraLanguage.ID, setup);
            Value actual = context.eval(HaraLanguage.ID, form);
            if (expectedError != null) {
              throw new IllegalStateException(id + " expected an error");
            }
            assertConformanceValue(id, actual, expected.lookup(Keyword.create("value")));
          } catch (PolyglotException guestError) {
            if (expectedError == null) throw guestError;
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
      InputStream input, PrintStream output, PrintStream error, boolean interactive) {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      StringBuilder source = new StringBuilder();
      List<String> history = new ArrayList<>();
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
          output.println(":history       show evaluated forms");
          continue;
        }
        if (source.length() == 0 && ":history".equals(line.trim())) {
          for (int i = 0; i < history.size(); i++) {
            output.println((i + 1) + ": " + history.get(i));
          }
          continue;
        }

        source.append(line).append('\n');
        continuation = !isComplete(source);
        if (continuation) {
          continue;
        }

        history.add(source.toString().stripTrailing());
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
    output.println("hara-truffle conformance");
    output.println("hara-truffle help");
  }
}
