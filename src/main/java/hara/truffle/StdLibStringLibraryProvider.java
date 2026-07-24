package hara.truffle;

/** Lazy Java implementation of {@code std.lib.string}. */
public final class StdLibStringLibraryProvider implements HaraLibraryProvider {
  @Override
  public String namespace() { return "std.lib.string"; }

  @Override
  public int order() { return 20; }

  @Override
  public void install(HaraContext context) { context.installStringLibrary(); }
}
