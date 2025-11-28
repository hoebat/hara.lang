package hara.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.kernel.Command;
import hara.kernel.Foundation;

@Command.Fn(name = "JVM")
public class Jvm {

    @Command.Sub(name = "BOOTPATH")
    public static Object bootpath(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_PROPS, "sun.boot.library.path");
    }

    @Command.Sub(name = "CP")
    public static Object cp(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_PROPS, "java.class.path");
    }

    @Command.Sub(name = "CLASSPATH")
    public static Object classpath(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_PROPS, "java.class.path");
    }

    @Command.Sub(name = "CLASSLOADER")
    public static Object classloader(Foundation F, List<Object> args) {
        return ClassLoader.getSystemClassLoader().toString();
    }

    @Command.Sub(name = "ENV")
    public static Object env(Foundation F, List<Object> args) {
        List<String> sArgs = Foundation.toStringList(args);
        return Foundation.run(Foundation.Fn::JVM_ENV, sArgs);
    }

    @Command.Sub(name = "HOME")
    public static Object home(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_PROPS, "java.home");
    }

    @Command.Sub(name = "PROPS")
    public static Object props(Foundation F, List<Object> args) {
        List<String> sArgs = Foundation.toStringList(args);
        return Foundation.run(Foundation.Fn::JVM_PROPS, sArgs);
    }

    @Command.Sub(name = "VENDOR")
    public static Object vendor(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_PROPS, "java.vendor");
    }

    @Command.Sub(name = "VERSION")
    public static Object version(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_PROPS, "java.version");
    }
}
