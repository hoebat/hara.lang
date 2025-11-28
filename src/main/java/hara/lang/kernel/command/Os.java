package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

public class Os {

    @Command(name = "OS")
    public static Object run(Foundation F, List<Object> argsObj) {
        List<String> args = Foundation.toStringList(argsObj);
        String cmd = args.get(0);
        args.remove(0);

        switch(cmd) {
            case "HELP": return Arrays.asList("HELP", "PWD", "LS", "RUN");
            case "LS":   args.add(0, "ls");
                         return Foundation.Fn.runProcess(args);
            case "PWD":  return Foundation.run(Foundation.Fn::JVM_ENV, "PWD");
            case "RUN":  return Foundation.Fn.runProcess(args);
        }

        throw new Ex.Unsupported("Unknown OS command: " + cmd);
    }
}
