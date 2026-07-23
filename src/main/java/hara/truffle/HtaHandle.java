package hara.truffle;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opaque value owned by one HTA extension context. */
public final class HtaHandle implements AutoCloseable {
  private final String owner;
  private final String type;
  private final long id;
  private final AtomicBoolean released = new AtomicBoolean();
  private volatile HaraWasmExtension extension;

  HtaHandle(String owner, String type, long id) {
    this.owner = Objects.requireNonNull(owner);
    this.type = Objects.requireNonNull(type);
    this.id = id;
  }

  String owner() {
    return owner;
  }

  String type() {
    return type;
  }

  long id() {
    return id;
  }

  boolean released() {
    return released.get();
  }

  HtaHandle bind(HaraWasmExtension ownerExtension) {
    if (extension != null && extension != ownerExtension) {
      throw new HaraException("hta/handle-owner-mismatch: " + owner + ":" + id);
    }
    extension = ownerExtension;
    return this;
  }

  void requireUsable(HaraWasmExtension expected) {
    if (released()) throw new HaraException("hta/handle-released: " + owner + ":" + id);
    requireOwner(expected);
  }

  void requireOwner(HaraWasmExtension expected) {
    if (extension != expected)
      throw new HaraException("hta/handle-owner-mismatch: " + owner + ":" + id);
  }

  @Override
  public void close() {
    if (!released.compareAndSet(false, true)) return;
    HaraWasmExtension owner = extension;
    if (owner != null) owner.release(this);
  }

  @Override
  public String toString() {
    return "#<hta-handle " + owner + "/" + type + "#" + Long.toUnsignedString(id) + ">";
  }
}
