package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

@Command.Fn(name = "OS")
public class Os {

    @Command.Sub(name = "LS")
    public static Object ls(Foundation F, List<Object> args) {
        List<String> sArgs = Foundation.toStringList(args);
        sArgs.add(0, "ls");
        return Foundation.Fn.runProcess(sArgs);
    }

    @Command.Sub(name = "PWD")
    public static Object pwd(Foundation F, List<Object> args) {
        return Foundation.run(Foundation.Fn::JVM_ENV, "PWD");
    }

    @Command.Sub(name = "RUN")
    public static Object run(Foundation F, List<Object> args) {
        List<String> sArgs = Foundation.toStringList(args);
        return Foundation.Fn.runProcess(sArgs);
    }
}
