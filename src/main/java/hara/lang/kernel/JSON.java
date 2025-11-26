package hara.lang.kernel;

import com.sun.jna.Library;
import com.sun.jna.Native;

public class JSON {
    public interface NativeJSON extends Library {
        NativeJSON INSTANCE = Native.load("json", NativeJSON.class);
        double parse(String json);
    }

    public static double parse(String json) {
        return NativeJSON.INSTANCE.parse(json);
    }
}
