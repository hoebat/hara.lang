package hara.truffle;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

/** An executable function exported from a pod namespace. */
@ExportLibrary(InteropLibrary.class)
public final class HaraPodFunction implements TruffleObject {
  private final HaraPodClient client;
  private final String qualifiedName;
  private final int minimumArity;
  private final int maximumArity;
  private final boolean variadic;

  public HaraPodFunction(
      HaraPodClient client,
      String qualifiedName,
      int minimumArity,
      int maximumArity,
      boolean variadic) {
    this.client = client;
    this.qualifiedName = qualifiedName;
    this.minimumArity = minimumArity;
    this.maximumArity = maximumArity;
    this.variadic = variadic;
  }

  @ExportMessage
  boolean isExecutable() {
    return true;
  }

  @ExportMessage
  Object execute(Object[] arguments)
      throws UnsupportedTypeException, ArityException, UnsupportedMessageException {
    if (arguments.length < minimumArity
        || (!variadic && maximumArity >= 0 && arguments.length > maximumArity)) {
      int expected = variadic ? minimumArity : maximumArity;
      throw ArityException.create(minimumArity, expected, arguments.length);
    }
    return HaraBox.export(client.call(qualifiedName, arguments));
  }

  @Override
  public String toString() {
    return "#<pod-fn " + qualifiedName + ">";
  }
}
