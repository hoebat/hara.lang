package hara.kernel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Visual and persistence settings shared by Hara's interactive REPLs. */
public final class ReplConfig {
  public static final String DEFAULT_SPLASH =
      "    __  __\n"
          + "   / / / /___ __________ _\n"
          + "  / /_/ / __ `/ ___/ __ `/\n"
          + " / __  / /_/ / /  / /_/ /\n"
          + "/_/ /_/\\__,_/_/   \\__,_/\n";

  private final Path historyFile;
  private final String splash;
  private final boolean color;

  public ReplConfig(Path historyFile, String splash, boolean color) {
    this.historyFile = Objects.requireNonNull(historyFile, "historyFile");
    this.splash = Objects.requireNonNull(splash, "splash");
    this.color = color;
  }

  public static ReplConfig defaults(String historyName) {
    String configuredSplash = System.getProperty("hara.repl.splash");
    String splash =
        configuredSplash == null ? DEFAULT_SPLASH : configuredSplash.replace("\\n", "\n");
    boolean color = !Boolean.getBoolean("hara.repl.no-color") && System.getenv("NO_COLOR") == null;
    return new ReplConfig(Paths.get(System.getProperty("user.home"), historyName), splash, color);
  }

  public Path historyFile() {
    return historyFile;
  }

  public String splash() {
    return splash;
  }

  public boolean color() {
    return color;
  }

  public ReplConfig withSplash(String value) {
    return new ReplConfig(historyFile, value, color);
  }

  public ReplConfig withColor(boolean value) {
    return new ReplConfig(historyFile, splash, value);
  }

  public String banner(String runtime, String session) {
    StringBuilder out = new StringBuilder();
    if (!splash.isBlank()) {
      out.append(paint("36;1", splash.stripTrailing())).append('\n');
    }
    out.append(paint("35;1", "Hara"))
        .append(" · ")
        .append(runtime)
        .append(" · session ")
        .append(session)
        .append('\n')
        .append(paint("2", "Type /help for commands."));
    return out.toString();
  }

  public String prompt(String namespace) {
    return paint("36;1", "hara") + paint("2", "[" + namespace + "]") + paint("35;1", " › ");
  }

  public String continuationPrompt() {
    return paint("2", "       … ");
  }

  private String paint(String code, String text) {
    return color ? "\u001b[" + code + "m" + text + "\u001b[0m" : text;
  }
}
