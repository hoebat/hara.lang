package hara.truffle;

import hara.pod.v1.Handle;

/** Opaque value owned by a pod process or service. */
public final class HaraPodHandle {
  private final Handle handle;

  public HaraPodHandle(Handle handle) {
    this.handle = handle;
  }

  public Handle handle() {
    return handle;
  }

  @Override
  public String toString() {
    return "#<pod-handle " + handle.getOwner() + ":" + handle.getId() + ">";
  }
}
