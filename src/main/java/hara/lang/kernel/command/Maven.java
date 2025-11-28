package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

@Command.Fn(name = "MAVEN")
public class Maven {

    @Command.Sub(name = "LOAD")
    public static Object load(Foundation F, List<Object> args) {
        List<String> sArgs = Foundation.toStringList(args);
        return Foundation.Fn.runSessionFor(F, sArgs.get(0),
            (rt) -> hara.lang.lib.Maven.load.invoke(rt, sArgs.get(1))
        );
    }
}
