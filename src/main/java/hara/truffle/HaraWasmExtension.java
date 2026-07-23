package hara.truffle;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
  private final Value memory;
  private final Value allocator;

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
      Value memoryValue = members.hasMember("memory") ? members.getMember("memory") : null;
      Value allocatorValue = members.hasMember("alloc") ? members.getMember("alloc") : null;
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
      memory = memoryValue;
      allocator = allocatorValue;
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
    ArrayList<Object> arguments = new ArrayList<>();
    for (int i = 0; i < values.length; i++) {
      appendArgument(arguments, spec.arguments().get(i), values[i], name);
    }
    try {
      return result(spec.returns(), exports.get(name).execute(arguments.toArray()), name);
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

  private void appendArgument(
      ArrayList<Object> arguments, String type, Object value, String export) {
    Object input = HaraBox.unwrap(value);
    if ("utf8".equals(type)) {
      if (!(input instanceof String)) throw typeError(export, type);
      if (memory == null || allocator == null || !memory.hasBufferElements()) {
        throw new HaraException("extension/abi-memory-unavailable: " + manifest.namespace());
      }
      byte[] bytes = ((String) input).getBytes(StandardCharsets.UTF_8);
      long pointer = allocator.execute(bytes.length).asLong();
      if (pointer < 0 || pointer > Integer.MAX_VALUE) {
        throw new HaraException("extension/abi-memory-overflow: " + manifest.namespace());
      }
      try {
        if (!memory.isBufferWritable() || memory.getBufferSize() < pointer + bytes.length) {
          throw new HaraException("WASM memory is not writable or is too small");
        }
        for (int i = 0; i < bytes.length; i++) {
          memory.writeBufferByte(pointer + i, bytes[i]);
        }
      } catch (Exception error) {
        throw new HaraException(
            "extension/abi-memory-write-failed: "
                + manifest.namespace()
                + " ("
                + error.getMessage()
                + ")");
      }
      arguments.add((int) pointer);
      arguments.add(bytes.length);
      return;
    }
    if ("boolean".equals(type)) {
      if (!(input instanceof Boolean)) throw typeError(export, type);
      arguments.add((Boolean) input ? 1 : 0);
      return;
    }
    if (!(input instanceof Number)) throw typeError(export, type);
    Number number = (Number) input;
    if ("i32".equals(type)) arguments.add(number.intValue());
    else if ("i64".equals(type)) arguments.add(number.longValue());
    else if ("f32".equals(type)) arguments.add(number.floatValue());
    else if ("f64".equals(type)) arguments.add(number.doubleValue());
    else throw new HaraException("extension/abi-type-unsupported: " + type);
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
