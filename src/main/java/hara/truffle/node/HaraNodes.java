package hara.truffle.node;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import hara.kernel.builtin.BuiltinStruct;
import hara.lang.base.primitive.Num;
import hara.lang.data.Symbol;
import hara.lang.protocol.IFn;
import hara.truffle.HaraBox;
import hara.truffle.HaraContext;
import hara.truffle.HaraException;
import hara.truffle.HaraFunction;
import hara.truffle.HaraLanguage;
import hara.truffle.HaraProtocol;
import hara.truffle.HaraProtocolImplementation;
import hara.truffle.HaraStruct;
import hara.truffle.HaraType;
import hara.truffle.HaraVar;

public final class HaraNodes {
  private HaraNodes() {}

  public static final class Literal extends HaraExpressionNode {
    private final Object value;

    public Literal(Object value) {
      this.value = value;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return value;
    }
  }

  /** Evaluates the contents of a reader collection and rebuilds its Java-backed value. */
  public static final class CollectionLiteral extends HaraExpressionNode {
    public enum Kind {
      TUPLE,
      VECTOR,
      QUEUE,
      MAP,
      ORDERED_MAP,
      SORTED_MAP,
      SET,
      ORDERED_SET,
      SORTED_SET
    }

    private final Kind kind;
    @Children private final HaraExpressionNode[] elements;

    public CollectionLiteral(Kind kind, HaraExpressionNode[] elements) {
      this.kind = kind;
      this.elements = elements;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] values = new Object[elements.length];
      for (int i = 0; i < elements.length; i++) {
        values[i] = elements[i].execute(frame);
      }
      return construct(kind, values);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object construct(Kind kind, Object[] values) {
      switch (kind) {
        case TUPLE:
          return BuiltinStruct.tuple(values);
        case VECTOR:
          return BuiltinStruct.vector(values);
        case QUEUE:
          return BuiltinStruct.queue(values);
        case MAP:
          return BuiltinStruct.hashMap(values);
        case ORDERED_MAP:
          return BuiltinStruct.orderedMap(values);
        case SORTED_MAP:
          return BuiltinStruct.sortedMap(values);
        case SET:
          return BuiltinStruct.hashSet(values);
        case ORDERED_SET:
          return BuiltinStruct.orderedSet(values);
        case SORTED_SET:
          return BuiltinStruct.sortedSet(values);
        default:
          throw new IllegalStateException("Unknown collection literal: " + kind);
      }
    }
  }

  public static final class ReadLocal extends HaraExpressionNode {
    private final int slot;

    public ReadLocal(int slot) {
      this.slot = slot;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return frame.getValue(slot);
    }
  }

  public static final class ReadGlobal extends HaraExpressionNode {
    private final Symbol symbol;

    public ReadGlobal(Symbol symbol) {
      this.symbol = symbol;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      HaraVar var = HaraLanguage.currentContext().resolve(symbol);
      if (var == null) {
        throw new HaraException("Unbound symbol: " + symbol.display(), this);
      }
      return var.get();
    }
  }

  public static final class Do extends HaraExpressionNode {
    @Children private final HaraExpressionNode[] expressions;

    public Do(HaraExpressionNode[] expressions) {
      this.expressions = expressions;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object result = null;
      for (HaraExpressionNode expression : expressions) {
        result = expression.execute(frame);
      }
      return result;
    }
  }

  public static final class If extends HaraExpressionNode {
    @Child private HaraExpressionNode condition;
    @Child private HaraExpressionNode consequent;
    @Child private HaraExpressionNode alternative;

    public If(
        HaraExpressionNode condition,
        HaraExpressionNode consequent,
        HaraExpressionNode alternative) {
      this.condition = condition;
      this.consequent = consequent;
      this.alternative = alternative;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object value = condition.execute(frame);
      return value == null || Boolean.FALSE.equals(value)
          ? alternative.execute(frame)
          : consequent.execute(frame);
    }
  }

  public static final class Let extends HaraExpressionNode {
    private final int[] slots;
    @Children private final HaraExpressionNode[] initializers;
    @Child private HaraExpressionNode body;

    public Let(int[] slots, HaraExpressionNode[] initializers, HaraExpressionNode body) {
      this.slots = slots;
      this.initializers = initializers;
      this.body = body;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object[] values = new Object[initializers.length];
      for (int i = 0; i < initializers.length; i++) {
        values[i] = initializers[i].execute(frame);
      }
      for (int i = 0; i < slots.length; i++) {
        frame.setObject(slots[i], values[i]);
      }
      return body.execute(frame);
    }
  }

  public static final class DefineGlobal extends HaraExpressionNode {
    private final Symbol symbol;
    @Child private HaraExpressionNode initializer;

    public DefineGlobal(Symbol symbol, HaraExpressionNode initializer) {
      this.symbol = symbol;
      this.initializer = initializer;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return HaraLanguage.currentContext().define(symbol, initializer.execute(frame));
    }
  }

  public static final class SetNamespace extends HaraExpressionNode {
    private final Symbol symbol;

    public SetNamespace(Symbol symbol) {
      this.symbol = symbol;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      HaraLanguage.currentContext().setCurrentNamespace(symbol);
      return symbol;
    }
  }

  public static final class DefineProtocol extends HaraExpressionNode {
    private final Symbol symbol;
    private final HaraProtocol protocol;

    public DefineProtocol(Symbol symbol, HaraProtocol protocol) {
      this.symbol = symbol;
      this.protocol = protocol;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return HaraLanguage.currentContext().define(symbol, protocol);
    }
  }

  public static final class ProtocolMethodImplementation {
    private final String name;
    private final HaraExpressionNode function;

    public ProtocolMethodImplementation(String name, HaraExpressionNode function) {
      this.name = name;
      this.function = function;
    }
  }

  public static final class ExtendType extends HaraExpressionNode {
    @Child private HaraExpressionNode type;
    @Child private HaraExpressionNode protocol;
    @Children private final HaraExpressionNode[] functions;
    private final String[] names;

    public ExtendType(
        HaraExpressionNode type,
        HaraExpressionNode protocol,
        ProtocolMethodImplementation[] implementations) {
      this.type = type;
      this.protocol = protocol;
      this.functions = new HaraExpressionNode[implementations.length];
      this.names = new String[implementations.length];
      for (int i = 0; i < implementations.length; i++) {
        functions[i] = implementations[i].function;
        names[i] = implementations[i].name;
      }
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object typeValue = type.execute(frame);
      Object protocolValue = protocol.execute(frame);
      if (!(typeValue instanceof HaraType)) {
        throw new HaraException("extend-type expects a struct type", this);
      }
      if (!(protocolValue instanceof HaraProtocol)) {
        throw new HaraException("extend-type expects a protocol", this);
      }
      HaraFunction[] methodFunctions = new HaraFunction[functions.length];
      for (int i = 0; i < functions.length; i++) {
        Object functionValue = functions[i].execute(frame);
        if (!(functionValue instanceof HaraFunction)) {
          throw new HaraException("protocol implementation must be a function", this);
        }
        methodFunctions[i] = (HaraFunction) functionValue;
      }
      HaraProtocol haraProtocol = (HaraProtocol) protocolValue;
      for (int i = 0; i < names.length; i++) {
        haraProtocol.extend((HaraType) typeValue, names[i], methodFunctions[i]);
      }
      return haraProtocol;
    }
  }

  public static final class ReadField extends HaraExpressionNode {
    @Child private HaraExpressionNode target;
    private final String field;

    public ReadField(HaraExpressionNode target, String field) {
      this.target = target;
      this.field = field;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object value = target.execute(frame);
      if (!(value instanceof HaraStruct)) {
        throw new HaraException("field expects a struct", this);
      }
      try {
        return ((HaraStruct) value).read(field);
      } catch (com.oracle.truffle.api.interop.UnknownIdentifierException exception) {
        throw new HaraException("Unknown struct field: " + field, this);
      }
    }
  }

  public static final class HostSymbol extends HaraExpressionNode {
    private final String name;

    public HostSymbol(String name) {
      this.name = name;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      requireHostInterop(this);
      try {
        return HaraLanguage.currentContext().lookupHostSymbol(name);
      } catch (RuntimeException exception) {
        throw new HaraException("Unable to resolve host symbol " + name, this);
      }
    }
  }

  public static final class HostGet extends HaraExpressionNode {
    @Child private HaraExpressionNode target;
    private final String member;

    public HostGet(HaraExpressionNode target, String member) {
      this.target = target;
      this.member = member;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      requireHostInterop(this);
      Object targetValue = target.execute(frame);
      try {
        return HaraLanguage.currentContext()
            .asGuestValue(InteropLibrary.getUncached().readMember(targetValue, member));
      } catch (UnsupportedMessageException | UnknownIdentifierException exception) {
        throw new HaraException("Unable to read host member " + member, this);
      }
    }
  }

  public static final class HostCall extends HaraExpressionNode {
    @Child private HaraExpressionNode target;
    @Children private final HaraExpressionNode[] arguments;
    private final String member;

    public HostCall(HaraExpressionNode target, String member, HaraExpressionNode[] arguments) {
      this.target = target;
      this.member = member;
      this.arguments = arguments;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      requireHostInterop(this);
      Object targetValue = target.execute(frame);
      Object[] values = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        values[i] = arguments[i].execute(frame);
      }
      try {
        return HaraLanguage.currentContext()
            .asGuestValue(InteropLibrary.getUncached().invokeMember(targetValue, member, values));
      } catch (UnsupportedMessageException
          | UnknownIdentifierException
          | UnsupportedTypeException
          | ArityException exception) {
        throw new HaraException("Unable to call host member " + member, this);
      }
    }
  }

  private static void requireHostInterop(HaraExpressionNode location) {
    if (!HaraLanguage.currentContext().hostInteropAllowed()) {
      throw new HaraException("Host interop is disabled for this Hara context", location);
    }
  }

  public static final class Add extends HaraExpressionNode {
    @Child private HaraExpressionNode left;
    @Child private HaraExpressionNode right;

    public Add(HaraExpressionNode left, HaraExpressionNode right) {
      this.left = left;
      this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object leftValue = left.execute(frame);
      Object rightValue = right.execute(frame);
      if (isLongLike(leftValue) && isLongLike(rightValue)) {
        return Num.addP(asLong(leftValue), asLong(rightValue));
      }
      if (leftValue instanceof Number && rightValue instanceof Number) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        return addGeneric(leftValue, rightValue);
      }
      throw new HaraException("+ expects two numbers", this);
    }

    private static boolean isLongLike(Object value) {
      return value instanceof Byte
          || value instanceof Short
          || value instanceof Integer
          || value instanceof Long;
    }

    private static long asLong(Object value) {
      if (value instanceof Long) return (Long) value;
      if (value instanceof Integer) return (Integer) value;
      if (value instanceof Short) return (Short) value;
      return (Byte) value;
    }

    @TruffleBoundary
    private static Number addGeneric(Object left, Object right) {
      return Num.add(left, right);
    }
  }

  public static final class Numeric extends HaraExpressionNode {
    public enum Operator {
      SUBTRACT,
      MULTIPLY,
      DIVIDE;

      private String symbol() {
        switch (this) {
          case SUBTRACT:
            return "-";
          case MULTIPLY:
            return "*";
          case DIVIDE:
            return "/";
          default:
            throw new AssertionError(this);
        }
      }

      private Number applyLong(long left, long right) {
        switch (this) {
          case SUBTRACT:
            return Num.minusP(left, right);
          case MULTIPLY:
            return Num.multiplyP(left, right);
          case DIVIDE:
            return Num.divide(left, right);
          default:
            throw new AssertionError(this);
        }
      }

      @TruffleBoundary
      private Number applyGeneric(Object left, Object right) {
        switch (this) {
          case SUBTRACT:
            return Num.minus(left, right);
          case MULTIPLY:
            return Num.multiply(left, right);
          case DIVIDE:
            return Num.divide(left, right);
          default:
            throw new AssertionError(this);
        }
      }
    }

    @Child private HaraExpressionNode left;
    @Child private HaraExpressionNode right;
    private final Operator operator;

    public Numeric(Operator operator, HaraExpressionNode left, HaraExpressionNode right) {
      this.operator = operator;
      this.left = left;
      this.right = right;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object leftValue = left.execute(frame);
      Object rightValue = right.execute(frame);
      if (!(leftValue instanceof Number) || !(rightValue instanceof Number)) {
        throw new HaraException(operator.symbol() + " expects two numbers", this);
      }
      if (isLongLike(leftValue) && isLongLike(rightValue)) {
        long leftLong = asLong(leftValue);
        long rightLong = asLong(rightValue);
        return operator.applyLong(leftLong, rightLong);
      }
      CompilerDirectives.transferToInterpreterAndInvalidate();
      return operator.applyGeneric(leftValue, rightValue);
    }

    private static boolean isLongLike(Object value) {
      return value instanceof Byte
          || value instanceof Short
          || value instanceof Integer
          || value instanceof Long;
    }

    private static long asLong(Object value) {
      if (value instanceof Long) return (Long) value;
      if (value instanceof Integer) return (Integer) value;
      if (value instanceof Short) return (Short) value;
      return (Byte) value;
    }
  }

  public static final class FunctionLiteral extends HaraExpressionNode {
    private final RootCallTarget callTarget;
    private final int arity;
    private final boolean captures;

    public FunctionLiteral(RootCallTarget callTarget, int arity, boolean captures) {
      this.callTarget = callTarget;
      this.arity = arity;
      this.captures = captures;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      MaterializedFrame closure = captures ? frame.materialize() : null;
      return new HaraFunction(callTarget, arity, closure);
    }
  }

  public static final class Invoke extends HaraExpressionNode {
    @Child private HaraExpressionNode function;
    @Children private final HaraExpressionNode[] arguments;
    @Child private DirectCallNode directCall;
    @Child private IndirectCallNode indirectCall = IndirectCallNode.create();

    @CompilerDirectives.CompilationFinal private HaraFunction cachedFunction;

    public Invoke(HaraExpressionNode function, HaraExpressionNode[] arguments) {
      this.function = function;
      this.arguments = arguments;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object target = function.execute(frame);
      if (target instanceof HaraStruct) {
        Object[] values = evaluateArguments(frame);
        return HaraLanguage.currentContext().ifnProtocol().invoke("invoke", target, values);
      }
      if (target instanceof IFn) {
        Object[] values = evaluateArguments(frame);
        return HaraLanguage.currentContext().ifnProtocol().invoke("invoke", target, values);
      }
      if (target instanceof HaraType) {
        HaraType haraType = (HaraType) target;
        if (arguments.length != haraType.arity()) {
          throw arityError(haraType.arity(), arguments.length);
        }
        Object[] values = evaluateArguments(frame);
        return new HaraStruct(haraType, values);
      }
      if (!(target instanceof HaraFunction)) {
        throw notCallable(target);
      }
      HaraFunction haraFunction = (HaraFunction) target;
      if (arguments.length != haraFunction.arity()) {
        throw arityError(haraFunction.arity(), arguments.length);
      }

      Object[] values = evaluateArguments(frame);

      if (haraFunction == cachedFunction) {
        return directCall.call(haraFunction.callArguments(values));
      }
      if (cachedFunction == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedFunction = haraFunction;
        directCall = insert(DirectCallNode.create(haraFunction.callTarget()));
        return directCall.call(haraFunction.callArguments(values));
      }
      return indirectCall.call(haraFunction.callTarget(), haraFunction.callArguments(values));
    }

    private Object[] evaluateArguments(VirtualFrame frame) {
      Object[] values = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        values[i] = arguments[i].execute(frame);
      }
      return values;
    }

    @TruffleBoundary
    private HaraException notCallable(Object value) {
      return new HaraException("Value is not callable: " + value, this);
    }

    @TruffleBoundary
    private HaraException arityError(int expected, int actual) {
      return new HaraException("Expected " + expected + " arguments, received " + actual, this);
    }
  }

  public static final class ProtocolInvoke extends HaraExpressionNode {
    @Child private HaraExpressionNode protocol;
    @Child private HaraExpressionNode receiver;
    @Children private final HaraExpressionNode[] arguments;
    private final String method;
    @Child private DirectCallNode directCall;
    @Child private IndirectCallNode indirectCall = IndirectCallNode.create();
    private HaraProtocol cachedProtocol;
    private HaraProtocolImplementation cachedImplementation;
    private HaraFunction cachedFunction;
    private Assumption cachedAssumption;
    private static final int DISPATCH_CACHE_LIMIT = 4;
    private final HaraProtocol[] cachedDispatchProtocols = new HaraProtocol[DISPATCH_CACHE_LIMIT];
    private final Object[] cachedDispatchShapes = new Object[DISPATCH_CACHE_LIMIT];
    private final HaraProtocolImplementation[] cachedDispatchImplementations =
        new HaraProtocolImplementation[DISPATCH_CACHE_LIMIT];
    private final Assumption[] cachedDispatchAssumptions = new Assumption[DISPATCH_CACHE_LIMIT];
    private int cachedDispatchSize;
    private int cachedDispatchNext;
    private static final Object NIL_DISPATCH_SHAPE = new Object();

    public ProtocolInvoke(
        HaraExpressionNode protocol,
        String method,
        HaraExpressionNode receiver,
        HaraExpressionNode[] arguments) {
      this.protocol = protocol;
      this.method = method;
      this.receiver = receiver;
      this.arguments = arguments;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      Object protocolValue = protocol.execute(frame);
      Object receiverValue = HaraBox.unwrap(receiver.execute(frame));
      HaraContext context = HaraLanguage.currentContext();
      if (context.isHostObject(receiverValue)) {
        receiverValue = context.asHostObject(receiverValue);
      }
      if (!(protocolValue instanceof HaraProtocol)) {
        throw new HaraException("protocol-call expects a protocol", this);
      }
      HaraProtocol haraProtocol = (HaraProtocol) protocolValue;
      HaraProtocol.HaraProtocolMethod descriptor = haraProtocol.method(method);
      if (descriptor == null) {
        throw new HaraException("Unknown protocol method: " + method, this);
      }
      if (!descriptor.acceptsCallArity(arguments.length + 1)) {
        throw new HaraException(
            "Expected "
                + descriptor.expectedCallArguments()
                + " protocol arguments, received "
                + arguments.length,
            this);
      }
      HaraProtocolImplementation implementation =
          cachedImplementation(haraProtocol, receiverValue);
      if (implementation == null) {
        throw new HaraException(
            "No " + haraProtocol.name() + "/" + method + " implementation for " + receiverValue,
            this);
      }
      Object[] values = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        values[i] = arguments[i].execute(frame);
      }
      HaraFunction haraFunction = implementation.function();
      if (haraFunction == null) {
        return implementation.invoke(receiverValue, values);
      }
      Object[] callArguments = haraFunction.callArguments(prependReceiver(receiverValue, values));
      if (haraProtocol == cachedProtocol
          && implementation == cachedImplementation
          && cachedAssumption != null
          && cachedAssumption.isValid()) {
        return directCall.call(callArguments);
      }
      if (cachedFunction == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedProtocol = haraProtocol;
        cachedImplementation = implementation;
        cachedFunction = haraFunction;
        cachedAssumption = haraProtocol.implementationsStable();
        directCall = insert(DirectCallNode.create(haraFunction.callTarget()));
        return directCall.call(callArguments);
      }
      if (haraProtocol == cachedProtocol && implementation == cachedImplementation) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedFunction = haraFunction;
        cachedImplementation = implementation;
        cachedAssumption = haraProtocol.implementationsStable();
        directCall = insert(DirectCallNode.create(haraFunction.callTarget()));
        return directCall.call(callArguments);
      }
      return indirectCall.call(haraFunction.callTarget(), callArguments);
    }

    private HaraProtocolImplementation cachedImplementation(
        HaraProtocol haraProtocol, Object receiverValue) {
      Object shape = dispatchShape(receiverValue);
      for (int i = 0; i < cachedDispatchSize; i++) {
        if (haraProtocol == cachedDispatchProtocols[i]
            && shape == cachedDispatchShapes[i]
            && cachedDispatchAssumptions[i] != null
            && cachedDispatchAssumptions[i].isValid()) {
          return cachedDispatchImplementations[i];
        }
      }
      CompilerDirectives.transferToInterpreterAndInvalidate();
      HaraProtocolImplementation implementation =
          haraProtocol.implementation(receiverValue, method);
      if (implementation == null) {
        return null;
      }

      int slot = -1;
      for (int i = 0; i < cachedDispatchSize; i++) {
        if (cachedDispatchAssumptions[i] == null || !cachedDispatchAssumptions[i].isValid()) {
          slot = i;
          break;
        }
      }
      if (slot < 0) {
        if (cachedDispatchSize < DISPATCH_CACHE_LIMIT) {
          slot = cachedDispatchSize++;
        } else {
          slot = cachedDispatchNext++ % DISPATCH_CACHE_LIMIT;
        }
      }
      cachedDispatchProtocols[slot] = haraProtocol;
      cachedDispatchShapes[slot] = shape;
      cachedDispatchImplementations[slot] = implementation;
      cachedDispatchAssumptions[slot] = haraProtocol.implementationsStable();
      return implementation;
    }

    private Object dispatchShape(Object receiverValue) {
      if (receiverValue == null) {
        return NIL_DISPATCH_SHAPE;
      }
      if (receiverValue instanceof HaraStruct) {
        return ((HaraStruct) receiverValue).type();
      }
      return receiverValue.getClass();
    }

    private Object[] prependReceiver(Object receiver, Object[] arguments) {
      Object[] values = new Object[arguments.length + 1];
      values[0] = receiver;
      System.arraycopy(arguments, 0, values, 1, arguments.length);
      return values;
    }
  }
}
