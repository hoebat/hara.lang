package hara.lang.base;

import hara.lang.base.primitive.Array;
import hara.lang.protocol.Constant;
import hara.lang.protocol.IDisplay;
import hara.lang.protocol.IHash;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Pattern;

public interface G {

  public static class Exception extends RuntimeException {
    public Exception() {}

    public Exception(String msg) {
      super(msg);
    }
  }

  public static final Constant.HashType DEFAULT_HASH = Constant.HashType.RAPID;

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static String displayList(java.util.List l) {
    return "#j" + Iter.display(l.iterator());
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static String displayMap(java.util.Map m) {
    return "#j"
        + Iter.toString(
            m.entrySet().iterator(),
            "{",
            "}",
            ",",
            (entry) ->
                display(((Entry) entry).getKey()) + " " + display(((Entry) entry).getValue()));
  }

  @SuppressWarnings("rawtypes")
  public static String displayMapEntry(Entry e) {
    return "[" + display(e.getKey()) + " " + display(e.getValue()) + "]";
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  public static String display(Object e) {
    if (e == null) {
      return "nil";
    } else if (e instanceof IDisplay) {
      return ((IDisplay) e).display();
    } else if (e instanceof String) {
      return "\"" + Str.escapeJava((String) e) + "\"";
    } else if (e instanceof Character) {
      return "\\" + e.toString();
    } else if (e instanceof Pattern) {
      return "#\"" + Str.escapeJava(e.toString()) + "\"";
    } else if (e instanceof Class) {
      return ((Class) e).getName();
    } else if (e instanceof java.util.List) {
      return displayList((java.util.List) e);
    } else if (e instanceof java.util.Map) {
      return displayMap((java.util.Map) e);
    } else if (e instanceof Entry) {
      return displayMapEntry((Entry) e);
    } else if (e instanceof Iterator) {
      return "#i" + Iter.display((Iterator) e);
    } else if (e.getClass().isArray()) {
      return "#arr" + Iter.display(Array.toIter(e));
    } else {
      return e.toString();
    }
  }

  public static String getLineNumber() {
    var curr = Thread.currentThread().getStackTrace()[3];
    return curr.getClassName() + "/" + curr.getMethodName() + " - L" + curr.getLineNumber();
  }

  public static void prn(Object... arr) {
    System.out.println(getLineNumber() + ":\n" + Array.display(arr));
  }

  public static long hashRapid(Object o) {
    if (o instanceof IHash) {
      return ((IHash) o).hashGet(Constant.HashType.RAPID);
    } else if (o == null) {
      return 0;
    } else {
      return o.hashCode();
    }
  }

  public static long hashMurmur(Object o) {
    if (o instanceof IHash) {
      return ((IHash) o).hashGet(Constant.HashType.MURMUR3);
    } else if (o == null) {
      return 0;
    } else {
      return o.hashCode();
    }
  }

  public static long hashSip(Object o) {
    if (o == null) return 0;
    return o.hashCode();
  }

  public static Function<Object, Long> hashFn(Constant.HashType t) {

    switch (t) {
      case RAPID:
        return item -> Long.valueOf(hashRapid(item));
      case MURMUR3:
        return item -> Long.valueOf(hashMurmur(item));
      case SIP:
        return item -> Long.valueOf(hashSip(item));
      case SYSTEM:
        return item -> Long.valueOf(item.hashCode());
      default:
        throw new UnsupportedOperationException("Not Supported");
    }
  }

  public static long hashCalc(Constant.HashType t, Object o) {
    return hashFn(t).apply(o);
  }
}
