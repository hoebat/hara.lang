package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

public class Jvm {

    @Command(name = "JVM")
    public static Object run(Foundation F, List<Object> argsObj) {
        List<String> args = Foundation.toStringList(argsObj);
        String cmd = args.get(0);
        args.remove(0);

        switch(cmd) {
            case "HELP":        return Arrays.asList("HELP", "HOME", "PROPS", "ENV", "VERSION", "VENDOR", "BOOTPATH", "CLASSPATH", "CP", "CLASSLOADER");
            case "BOOTPATH":    return Foundation.run(Foundation.Fn::JVM_PROPS, "sun.boot.library.path");
            case "CP":
            case "CLASSPATH":   return Foundation.run(Foundation.Fn::JVM_PROPS, "java.class.path");
            case "CLASSLOADER": return ClassLoader.getSystemClassLoader().toString();
            case "ENV":         return Foundation.run(Foundation.Fn::JVM_ENV, args);
            case "HOME":        return Foundation.run(Foundation.Fn::JVM_PROPS, "java.home");
            case "PROPS":       return Foundation.run(Foundation.Fn::JVM_PROPS, args);
            case "VENDOR":      return Foundation.run(Foundation.Fn::JVM_PROPS, "java.vendor");
            case "VERSION":     return Foundation.run(Foundation.Fn::JVM_PROPS, "java.version");
        }
        throw new Ex.Unsupported("Unknown JVM command: " + cmd);
    }
}
