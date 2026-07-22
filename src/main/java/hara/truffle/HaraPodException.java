package hara.truffle;

/** Runtime failure at the pod boundary. */
public final class HaraPodException extends RuntimeException {
  public HaraPodException(String message) {
    super(message);
  }

  public HaraPodException(String message, Throwable cause) {
    super(message, cause);
  }
}
