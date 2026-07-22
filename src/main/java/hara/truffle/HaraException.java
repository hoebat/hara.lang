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
}
