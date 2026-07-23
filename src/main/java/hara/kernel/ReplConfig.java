package hara.kernel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/** Visual and persistence settings shared by Hara's interactive REPLs. */
public final class ReplConfig {
  public static final String DEFAULT_SPLASH =
      """
                                      .-==========-.
                                _.-'       __       '-._
                           _.-'         .-'  '-.        '-._
                       _.-'____________/  ◉  ◉  \\____________'-._
                      /________________\\   /\\   /________________\\
                      '-----------------\\  --  /-----------------'
                                         '----'
                                           ||
                                        \\  ||  /
                                         \\ || /
                                      .   \\||/   .
                                  .       /||\\       .
                              .          / || \\          .
                          .             /  ||  \\             .
                      .                / ░░░░░░ \\                .
                  .                   /░░▒▒▒▒▒▒░░\\                   .
              .______________________/▒▒▓▓▓▓▓▓▓▓▒▒\\______________________.

                 ██╗  ██╗ █████╗ ██████╗  █████╗
                 ██║  ██║██╔══██╗██╔══██╗██╔══██╗
                 ███████║███████║██████╔╝███████║
                 ██╔══██║██╔══██║██╔══██╗██╔══██║
                 ██║  ██║██║  ██║██║  ██║██║  ██║
                 ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝▓
                    ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓
                       ░░░░░░░░░░░░░░░░░░░░░░░░░
      """;


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

  public String renderedSplash() {
    String value = splash.stripTrailing();
    if (!color || value.isBlank()) return value;
    String[] lines = value.split("\\R", -1);
    StringBuilder rendered = new StringBuilder();
    int gradientStart = Math.min(6, lines.length - 1);
    int gradientLength = Math.max(1, lines.length - gradientStart - 1);
    for (int index = 0; index < lines.length; index++) {
      if (index > 0) rendered.append('\n');
      if (index < gradientStart) {
        rendered.append(lines[index]);
        continue;
      }
      double position = (index - gradientStart) / (double) gradientLength;
      int red;
      int green;
      int blue;
      if (position < 0.4) {
        double phase = position / 0.4;
        red = blend(190, 20, phase);
        green = blend(235, 105, phase);
        blue = 255;
      } else {
        double phase = (position - 0.4) / 0.6;
        red = blend(20, 0, phase);
        green = blend(105, 0, phase);
        blue = blend(255, 0, phase);
      }
      rendered
          .append("\u001b[38;2;")
          .append(red)
          .append(';')
          .append(green)
          .append(';')
          .append(blue)
          .append('m')
          .append(lines[index])
          .append("\u001b[0m");
    }
    return rendered.toString();
  }

  private static int blend(int from, int to, double position) {
    return (int) Math.round(from + (to - from) * position);
  }

  public boolean color() {
    return color;
  }

  public ReplConfig withHistoryFile(Path value) {
    return new ReplConfig(value, splash, color);
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
      out.append(renderedSplash()).append('\n');
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
    return paint("36;1", "hara") + paint("2", "[" + namespace + "] ");
  }

  public String sessionPrompt(String namespace) {
    return paint("2", "[") + paint("36;1", namespace) + paint("2", "] ");
  }

  public String continuationPrompt() {
    return paint("2", "       … ");
  }

  private String paint(String code, String text) {
    return color ? "\u001b[" + code + "m" + text + "\u001b[0m" : text;
  }
}
