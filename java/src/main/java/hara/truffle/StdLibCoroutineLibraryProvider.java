package hara.truffle;

/** Optional Java implementation of std.lib.coroutine. */
public final class StdLibCoroutineLibraryProvider implements HaraLibraryProvider {
  @Override
  public String namespace() {
    return "std.lib.coroutine";
  }

  @Override
  public int order() {
    return 30;
  }

  @Override
  public void install(HaraContext context) {
    HaraStaticLibrary.install(context, namespace(), StdLibCoroutine.class);
  }
}
