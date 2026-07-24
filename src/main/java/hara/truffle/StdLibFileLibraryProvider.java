package hara.truffle;

/** Lazy Java implementation of {@code std.lib.file}. */
public final class StdLibFileLibraryProvider implements HaraLibraryProvider {
  @Override
  public String namespace() { return "std.lib.file"; }

  @Override
  public int order() { return 20; }

  @Override
  public void install(HaraContext context) { context.installFileLibrary(); }
}
