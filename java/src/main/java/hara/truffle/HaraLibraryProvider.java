package hara.truffle;

/** A runtime library that can be installed independently of the core language context. */
public interface HaraLibraryProvider {
  String namespace();

  default int order() { return 0; }

  /** Optional classpath HAL resource used for symbols not supplied by this provider. */
  default String fallbackResource() { return null; }

  /** Eager providers are installed while the language context is initialized. */
  default boolean eager() { return false; }

  void install(HaraContext context);
}
