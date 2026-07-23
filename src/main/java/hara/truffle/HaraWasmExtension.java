package hara.truffle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.ByteSequence;

/** Generic, import-free core-WASM extension instance. */
final class HaraWasmExtension implements AutoCloseable {
  private final HaraExtensionManifest manifest;
  private final Context context;
  private final Map<String, Value> exports;

  HaraWasmExtension(HaraExtensionPackage extensionPackage) {
    manifest = extensionPackage.manifest();
    if (!"wasm".equals(manifest.provider())) {
      throw new HaraException(
          "extension/provider-unsupported: "
              + manifest.provider()
              + " for "
              + manifest.namespace());
    }
    if (!"core-v1".equals(manifest.abi())) {
      throw new HaraException(
          "extension/abi-unsupported: " + manifest.abi() + " for " + manifest.namespace());
    }
    if (!manifest.capabilities().isEmpty()) {
      throw new HaraException(
          "extension/capability-denied: "
              + manifest.capabilities()
              + " for "
              + manifest.namespace());
    }

    Context opened = null;
    try {
      byte[] bytes = extensionPackage.moduleBytes();
      Source source =
          Source.newBuilder(
                  "wasm",
                  ByteSequence.create(bytes),
                  manifest.namespace() + "/" + manifest.module())
              .build();
      opened = Context.newBuilder("wasm").allowAllAccess(false).build();
      Value module = opened.eval(source);
      Value instance = module.canInstantiate() ? module.newInstance() : module;
      Value members = instance.hasMember("exports") ? instance.getMember("exports") : instance;
      LinkedHashMap<String, Value> declared = new LinkedHashMap<>();
      for (String name : manifest.exports().keySet()) {
        Value function = members.getMember(name);
        if (function == null || !function.canExecute()) {
          throw new HaraException(
              "extension/malformed: module " + manifest.module() + " has no export " + name);
        }
        declared.put(name, function);
      }
      context = opened;
      exports = Map.copyOf(declared);
    } catch (HaraException error) {
      if (opened != null) opened.close(true);
      throw error;
    } catch (Exception error) {
      if (opened != null) opened.close(true);
      throw new HaraException(
          "extension/module-invalid: " + manifest.namespace() + " (" + error.getMessage() + ")");
    }
  }

  synchronized Object invoke(String name, Object[] values) {
    HaraExtensionManifest.Export spec = manifest.exports().get(name);
    if (spec == null) throw new HaraException("extension/export-missing: " + name);
    if (values.length != spec.arguments().size()) {
      throw new HaraException(
          manifest.namespace() + "/" + name + " expects " + spec.arguments().size() + " arguments");
    }
    Object[] arguments = new Object[values.length];
    for (int i = 0; i < values.length; i++) {
      arguments[i] = argument(spec.arguments().get(i), values[i], name);
    }
    try {
      return result(spec.returns(), exports.get(name).execute(arguments), name);
    } catch (HaraException error) {
      throw error;
    } catch (Exception error) {
      throw new HaraException(
          "extension/invoke-failed: "
              + manifest.namespace()
              + "/"
              + name
              + " ("
              + error.getMessage()
              + ")");
    }
  }

  private Object argument(String type, Object value, String export) {
    Object input = HaraBox.unwrap(value);
    if ("boolean".equals(type)) {
      if (!(input instanceof Boolean)) throw typeError(export, type);
      return (Boolean) input ? 1 : 0;
    }
    if (!(input instanceof Number)) throw typeError(export, type);
    Number number = (Number) input;
    if ("i32".equals(type)) return number.intValue();
    if ("i64".equals(type)) return number.longValue();
    if ("f32".equals(type)) return number.floatValue();
    if ("f64".equals(type)) return number.doubleValue();
    throw new HaraException("extension/abi-type-unsupported: " + type);
  }

  private Object result(String type, Value value, String export) {
    if ("void".equals(type)) return HaraNull.SINGLETON;
    if ("boolean".equals(type)) return value.asInt() != 0;
    if ("i32".equals(type)) return (long) value.asInt();
    if ("i64".equals(type)) return value.asLong();
    if ("f32".equals(type)) return (double) value.asFloat();
    if ("f64".equals(type)) return value.asDouble();
    throw new HaraException(
        "extension/abi-type-unsupported: " + manifest.namespace() + "/" + export + " -> " + type);
  }

  private HaraException typeError(String export, String expected) {
    return new HaraException(
        "extension/type-error: " + manifest.namespace() + "/" + export + " expects " + expected);
  }

  @Override
  public void close() {
    context.close(true);
  }
}
