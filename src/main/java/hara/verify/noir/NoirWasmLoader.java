package hara.verify.noir;

import java.util.ServiceLoader;
import java.util.concurrent.CompletableFuture;

/** Host capability for compiling Noir through a WASM compiler implementation. */
public interface NoirWasmLoader {
  String id();

  boolean available();

  CompletableFuture<NoirArtifact> compile(NoirProgram program);

  static NoirWasmLoader discover() {
    for (NoirWasmLoader loader : ServiceLoader.load(NoirWasmLoader.class)) {
      if (loader.available()) return loader;
    }
    return Unavailable.INSTANCE;
  }

  final class Unavailable implements NoirWasmLoader {
    private static final Unavailable INSTANCE = new Unavailable();

    private Unavailable() {}

    @Override
    public String id() {
      return "unavailable";
    }

    @Override
    public boolean available() {
      return false;
    }

    @Override
    public CompletableFuture<NoirArtifact> compile(NoirProgram program) {
      CompletableFuture<NoirArtifact> result = new CompletableFuture<>();
      result.completeExceptionally(
          new IllegalStateException(
              "noir/compile is unavailable: install a capability-scoped NoirWasmLoader"));
      return result;
    }
  }
}
