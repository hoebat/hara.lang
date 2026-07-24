package hara.truffle;

/** Eager optimized implementation of the canonical Hara core namespace. */
public final class StdLibFoundationLibraryProvider implements HaraLibraryProvider {
  @Override
  public String namespace() {
    return "std.lib.foundation";
  }

  @Override
  public int order() {
    return 5;
  }

  @Override
  public String fallbackResource() {
    return "std/lib/foundation.hal";
  }

  @Override
  public boolean eager() {
    return true;
  }

  @Override
  public void install(HaraContext context) {
    HaraStaticLibrary.install(context, namespace(), StdLibFoundationSequence.class);
    HaraStaticLibrary.install(context, namespace(), StdLibFoundationCollection.class);
  }
}
