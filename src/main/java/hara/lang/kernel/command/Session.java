package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.base.It;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

public class Session {

    @Command(name = "SESSION")
    public static Object run(Foundation F, List<Object> argsObj) {
        List<String> args = Foundation.toStringList(argsObj);
        String cmd = args.get(0);
        args.remove(0);

        switch(cmd) {
            case "HELP":   return Arrays.asList("HELP", "NEW", "GET", "EXISTS", "LIST", "INFO", "KILL", "PATH");
            case "EXISTS": return Foundation.Fn.runSessionFor(F, args.get(0), (rt) -> rt != null);
            case "GET":    return Foundation.Fn.runSessionCreate(F, args, false);
            case "NEW":    return Foundation.Fn.runSessionCreate(F, args, true);
            case "PATH":   return Foundation.Fn.runSessionFor(F, args.get(0), (rt) -> Foundation.Fn.runSessionClasspath(rt, args));
            case "LIST":   return It.toArrayList(F.RTS.keys());
            case "KILL":   return Foundation.Fn.runSessionFor(F, args.get(0), (rt) -> F.RTS.remove(args.get(0)));
            case "INFO":   throw new Ex.TODO();
        }
        throw new Ex.Unsupported("Unknown SESSION command: " + cmd);
    }
}
