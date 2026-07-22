package hara.truffle.node;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.SourceSection;
import hara.truffle.HaraBox;
import hara.truffle.HaraException;
import hara.truffle.HaraLanguage;

public final class HaraRootNode extends RootNode {
  @Child private HaraExpressionNode body;
  private final int[] parameterSlots;
  private final int[] captureSlots;
  private final int[] captureSourceSlots;
  private final SourceSection sourceSection;
  private final boolean exportResult;

  public HaraRootNode(
      HaraLanguage language,
      FrameDescriptor descriptor,
      HaraExpressionNode body,
      int[] parameterSlots,
      int[] captureSlots,
      int[] captureSourceSlots,
      SourceSection sourceSection,
      boolean exportResult) {
    super(language, descriptor);
    this.body = body;
    this.parameterSlots = parameterSlots;
    this.captureSlots = captureSlots;
    this.captureSourceSlots = captureSourceSlots;
    this.sourceSection = sourceSection;
    this.exportResult = exportResult;
  }

  @Override
  public SourceSection getSourceSection() {
    return sourceSection;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    Object[] arguments = frame.getArguments();
    int argumentOffset = exportResult ? 0 : 1;
    if (arguments.length != parameterSlots.length + argumentOffset) {
      throw arityError(parameterSlots.length, arguments.length - argumentOffset);
    }

    if (!exportResult) {
      MaterializedFrame closure = (MaterializedFrame) arguments[0];
      for (int i = 0; i < captureSlots.length; i++) {
        frame.setObject(captureSlots[i], closure.getValue(captureSourceSlots[i]));
      }
    }
    for (int i = 0; i < parameterSlots.length; i++) {
      frame.setObject(parameterSlots[i], arguments[i + argumentOffset]);
    }

    Object result = body.execute(frame);
    return exportResult ? HaraBox.export(result) : result;
  }

  @TruffleBoundary
  private HaraException arityError(int expected, int actual) {
    return new HaraException("Expected " + expected + " arguments, received " + actual, this);
  }
}
