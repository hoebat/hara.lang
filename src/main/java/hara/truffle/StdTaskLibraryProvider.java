package hara.truffle;

/** Optional Java implementation of std.task and its process/bulk namespaces. */
public final class StdTaskLibraryProvider implements HaraLibraryProvider {
  @Override
  public String namespace() {
    return "std.task";
  }

  @Override
  public int order() {
    return 20;
  }

  @Override
  public void install(HaraContext context) {
    context.installTaskLibrary();
  }
}
