package hara.truffle;

import java.io.IOException;
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
    if (args.length == 0 || "help".equals(args[0]) || "--help".equals(args[0])) {
      printUsage(output);
      return 0;
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
          source = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
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

  private static void printUsage(PrintStream output) {
    output.println("hara-truffle eval <expression>");
    output.println("hara-truffle run <file>");
    output.println("hara-truffle stdin");
    output.println("hara-truffle help");
  }
}
