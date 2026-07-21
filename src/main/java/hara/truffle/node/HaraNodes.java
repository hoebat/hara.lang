package hara.truffle.node;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import hara.lang.base.primitive.Num;
import hara.truffle.HaraException;
import hara.truffle.HaraFunction;

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
        return Num.add(asLong(leftValue), asLong(rightValue));
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

  public static final class FunctionLiteral extends HaraExpressionNode {
    private final HaraFunction function;

    public FunctionLiteral(HaraFunction function) {
      this.function = function;
    }

    @Override
    public Object execute(VirtualFrame frame) {
      return function;
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
      if (!(target instanceof HaraFunction)) {
        throw notCallable(target);
      }
      HaraFunction haraFunction = (HaraFunction) target;
      if (arguments.length != haraFunction.arity()) {
        throw arityError(haraFunction.arity(), arguments.length);
      }

      Object[] values = new Object[arguments.length];
      for (int i = 0; i < arguments.length; i++) {
        values[i] = arguments[i].execute(frame);
      }

      if (haraFunction == cachedFunction) {
        return directCall.call(values);
      }
      if (cachedFunction == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedFunction = haraFunction;
        directCall = insert(DirectCallNode.create(haraFunction.callTarget()));
        return directCall.call(values);
      }
      return indirectCall.call(haraFunction.callTarget(), values);
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
}
