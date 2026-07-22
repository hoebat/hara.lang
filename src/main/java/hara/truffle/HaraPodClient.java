package hara.truffle;

import hara.pod.v1.Manifest;

/** Transport-neutral client used by Truffle-facing pod values. */
public interface HaraPodClient extends AutoCloseable {
  Manifest manifest();

  Object call(String function, Object[] arguments);

  default void release(Object handle) {
    // Stateless pods may not need to release values.
  }

  @Override
  default void close() {}
}
