package hara.truffle;

/** Lazy Java implementation of {@code std.lib.bytes}. */
public final class StdLibBytesLibraryProvider implements HaraLibraryProvider {
  @Override
  public String namespace() { return "std.lib.bytes"; }

  @Override
  public int order() { return 20; }

  @Override
  public void install(HaraContext context) { context.installBytesLibrary(); }
}
