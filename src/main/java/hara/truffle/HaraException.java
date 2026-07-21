package hara.truffle;

import com.oracle.truffle.api.exception.AbstractTruffleException;
import com.oracle.truffle.api.nodes.Node;

public final class HaraException extends AbstractTruffleException {
  public HaraException(String message) {
    super(message);
  }

  public HaraException(String message, Node location) {
    super(message, location);
  }
}
