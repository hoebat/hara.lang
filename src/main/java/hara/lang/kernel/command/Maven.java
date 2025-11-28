package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

public class Maven {

    @Command(name = "MAVEN")
    public static Object run(Foundation F, List<Object> argsObj) {
        List<String> args = Foundation.toStringList(argsObj);
        String cmd = args.get(0);
        args.remove(0);

        switch(cmd) {
            case "HELP":   return Arrays.asList("HELP", "LOAD");
            case "LOAD":
                return Foundation.Fn.runSessionFor(F, args.get(0),
                    (rt) -> hara.lang.lib.Maven.load.invoke(rt, args.get(1))
                );
        }
        throw new Ex.Unsupported("Unknown MAVEN command: " + cmd);
    }
}
