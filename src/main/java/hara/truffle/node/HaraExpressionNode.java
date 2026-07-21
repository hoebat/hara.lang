package hara.truffle.node;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;

public abstract class HaraExpressionNode extends Node {
  public abstract Object execute(VirtualFrame frame);
}
