package hara.kernel.flavor;

import java.util.Set;

/** Runtime-owned authority and services exposed to a native flavor provider. */
public interface NativeFlavorAccess {
  ClassLoader classLoader();

  boolean allows(NativeCapability capability);

  static NativeFlavorAccess of(ClassLoader classLoader, Set<NativeCapability> capabilities) {
    Set<NativeCapability> grants = Set.copyOf(capabilities);
    return new NativeFlavorAccess() {
      @Override
      public ClassLoader classLoader() {
        return classLoader;
      }

      @Override
      public boolean allows(NativeCapability capability) {
        return grants.contains(capability);
      }
    };
  }
}
