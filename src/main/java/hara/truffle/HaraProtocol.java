package hara.truffle;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ExportLibrary(InteropLibrary.class)
public final class HaraProtocol implements TruffleObject {
  private final String name;
  private final Map<String, HaraProtocolMethod> methods;
  private final Map<HaraType, Map<String, HaraFunction>> implementations =
      new ConcurrentHashMap<>();
  private volatile Assumption implementationsStable = Truffle.getRuntime().createAssumption();

  public HaraProtocol(String name, Map<String, Integer> methodArities) {
    this.name = name;
    Map<String, HaraProtocolMethod> descriptors = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> entry : methodArities.entrySet()) {
      descriptors.put(
          entry.getKey(), new HaraProtocolMethod(this, entry.getKey(), entry.getValue()));
    }
    methods = Collections.unmodifiableMap(descriptors);
  }

  public String name() {
    return name;
  }

  public HaraProtocolMethod method(String methodName) {
    return methods.get(methodName);
  }

  public Assumption implementationsStable() {
    return implementationsStable;
  }

  public void extend(HaraType type, String methodName, HaraFunction function) {
    HaraProtocolMethod method = method(methodName);
    if (method == null) {
      throw new HaraException("Unknown method " + name + "/" + methodName);
    }
    if (method.arity() >= 0 && function.arity() != method.arity()) {
      throw new HaraException(
          name
              + "/"
              + methodName
              + " expects "
              + method.arity()
              + " arguments, received "
              + function.arity());
    }
    implementations
        .computeIfAbsent(type, ignored -> new ConcurrentHashMap<>())
        .put(methodName, function);
    Assumption previous = implementationsStable;
    previous.invalidate();
    implementationsStable = Truffle.getRuntime().createAssumption();
  }

  public HaraFunction implementation(HaraType type, String methodName) {
    Map<String, HaraFunction> methodsForType = implementations.get(type);
    return methodsForType == null ? null : methodsForType.get(methodName);
  }

  public Object invoke(String methodName, Object receiver, Object[] arguments) {
    HaraProtocolMethod method = method(methodName);
    if (method == null) {
      throw new HaraException("Unknown method " + name + "/" + methodName);
    }
    if (!(receiver instanceof HaraStruct)) {
      throw new HaraException("No " + name + " implementation for " + receiver);
    }
    HaraFunction function = implementation(((HaraStruct) receiver).type(), methodName);
    if (function == null) {
      throw new HaraException(
          "No "
              + name
              + "/"
              + methodName
              + " implementation for "
              + ((HaraStruct) receiver).type().name());
    }
    Object[] callArguments = new Object[arguments.length + 1];
    callArguments[0] = receiver;
    System.arraycopy(arguments, 0, callArguments, 1, arguments.length);
    return function.callTarget().call(function.callArguments(callArguments));
  }

  @ExportMessage
  Object toDisplayString(boolean allowSideEffects) {
    return "#<protocol " + name + ">";
  }

  @Override
  public String toString() {
    return "#<protocol " + name + ">";
  }

  public static final class HaraProtocolMethod {
    private final HaraProtocol protocol;
    private final String name;
    private final int arity;

    private HaraProtocolMethod(HaraProtocol protocol, String name, int arity) {
      this.protocol = protocol;
      this.name = name;
      this.arity = arity;
    }

    public HaraProtocol protocol() {
      return protocol;
    }

    public String name() {
      return name;
    }

    public int arity() {
      return arity;
    }
  }
}
