package hara.truffle;

/** A runtime library that can be installed independently of the core language context. */
public interface HaraLibraryProvider {
  String namespace();

  default int order() { return 0; }

  void install(HaraContext context);
}
