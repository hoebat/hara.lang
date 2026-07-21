package hara.truffle;

import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.interop.TruffleObject;

@ExportLibrary(InteropLibrary.class)
public final class HaraFunction implements TruffleObject {
  private final RootCallTarget callTarget;
  private final int arity;

  HaraFunction(RootCallTarget callTarget, int arity) {
    this.callTarget = callTarget;
    this.arity = arity;
  }

  public RootCallTarget callTarget() {
    return callTarget;
  }

  public int arity() {
    return arity;
  }

  @ExportMessage
  boolean isExecutable() {
    return true;
  }

  @ExportMessage
  Object execute(Object[] arguments) throws ArityException {
    if (arguments.length != arity) {
      throw ArityException.create(arity, arity, arguments.length);
    }
    return HaraBox.export(callTarget.call(arguments));
  }

  @Override
  public String toString() {
    return "#<fn/" + arity + ">";
  }
}
