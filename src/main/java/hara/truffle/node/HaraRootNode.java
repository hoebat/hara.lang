package hara.truffle.node;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import hara.truffle.HaraBox;
import hara.truffle.HaraException;
import hara.truffle.HaraLanguage;

public final class HaraRootNode extends RootNode {
  @Child private HaraExpressionNode body;
  private final int[] parameterSlots;
  private final boolean exportResult;

  public HaraRootNode(
      HaraLanguage language,
      FrameDescriptor descriptor,
      HaraExpressionNode body,
      int[] parameterSlots,
      boolean exportResult) {
    super(language, descriptor);
    this.body = body;
    this.parameterSlots = parameterSlots;
    this.exportResult = exportResult;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    if (arguments.length != parameterSlots.length) {
      throw arityError(parameterSlots.length, arguments.length);
    }
    for (int i = 0; i < parameterSlots.length; i++) {
      frame.setObject(parameterSlots[i], arguments[i]);
    }

    Object result = body.execute(frame);
    return exportResult ? HaraBox.export(result) : result;
  }

  @TruffleBoundary
  private HaraException arityError(int expected, int actual) {
    return new HaraException("Expected " + expected + " arguments, received " + actual, this);
  }
}
