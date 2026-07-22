package hara.truffle;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import hara.pod.v1.Function;
import hara.pod.v1.Manifest;
import hara.pod.v1.Namespace;
import java.util.HashMap;
import java.util.Map;

/** Member namespace exported by a pod. */
@ExportLibrary(InteropLibrary.class)
public final class HaraPodNamespace implements TruffleObject {
  private final Map<String, HaraPodFunction> functions;

  public HaraPodNamespace(HaraPodClient client, Namespace namespace) {
    this.functions = new HashMap<>();
    for (Function function : namespace.getFunctionsList()) {
      functions.put(
          function.getName(),
          new HaraPodFunction(
              client,
              namespace.getName() + "/" + function.getName(),
              function.getMinimumArity(),
              function.getMaximumArity(),
              function.getVariadic()));
    }
  }

  public static HaraPodNamespace from(HaraPodClient client, String namespace) {
    Manifest manifest = client.manifest();
    for (Namespace candidate : manifest.getNamespacesList()) {
      if (candidate.getName().equals(namespace)) {
        return new HaraPodNamespace(client, candidate);
      }
    }
    throw new IllegalArgumentException("Pod does not export namespace: " + namespace);
  }

  @ExportMessage
  boolean hasMembers() {
    return true;
  }

  @ExportMessage
  Object getMembers(boolean includeInternal) {
    return new HaraStruct.HaraMemberNames(functions.keySet().toArray(new String[0]));
  }

  @ExportMessage
  boolean isMemberReadable(String member) {
    return functions.containsKey(member);
  }

  @ExportMessage
  Object readMember(String member) throws UnknownIdentifierException {
    HaraPodFunction function = functions.get(member);
    if (function == null) {
      throw UnknownIdentifierException.create(member);
    }
    return function;
  }

  @ExportMessage
  boolean isMemberModifiable(String member) {
    return false;
  }

  @ExportMessage
  boolean isMemberInsertable(String member) {
    return false;
  }

  @ExportMessage
  boolean isMemberRemovable(String member) {
    return false;
  }

  @ExportMessage
  void writeMember(String member, Object value) throws UnsupportedMessageException {
    throw UnsupportedMessageException.create();
  }

  @ExportMessage
  void removeMember(String member) throws UnsupportedMessageException {
    throw UnsupportedMessageException.create();
  }
}
