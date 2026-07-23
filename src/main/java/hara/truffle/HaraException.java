package hara.truffle;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;

public final class HaraException extends AbstractTruffleException {
  private final Node haraLocation;

  public HaraException(String message) {
    super(message);
    this.haraLocation = null;
  }

  public HaraException(String message, Node location) {
    super(message, location);
    this.haraLocation = location;
  }

  public Node haraLocation() {
    return haraLocation;
  }

  /** Enables diagnostic Hara frames without changing normal exception messages. */
  public static boolean tracingEnabled() {
    return Boolean.getBoolean("hara.stacktrace");
  }

  public static HaraException withFrame(Throwable error, Node location, String frame) {
    String message =
        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    String marker = "\n[hara stack]\n";
    if (message.contains(marker)) {
      message = message.replace(marker, marker + "  at " + frame + "\n");
    } else {
      message += marker + "  at " + frame;
    }
    return new HaraException(message, location);
  }
}
