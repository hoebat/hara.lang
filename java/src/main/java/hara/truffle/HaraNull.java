package hara.truffle;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

@ExportLibrary(InteropLibrary.class)
final class HaraNull implements TruffleObject {
  static final HaraNull SINGLETON = new HaraNull();

  private HaraNull() {}

  @ExportMessage
  boolean isNull() {
    return true;
  }
}
