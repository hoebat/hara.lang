package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

public class Server {

    @Command(name = "SERVER")
    public static Object run(Foundation F, List<Object> argsObj) {
        List<String> args = Foundation.toStringList(argsObj);
        String cmd = args.get(0);
        args.remove(0);

        switch(cmd) {
            case "HELP": return Arrays.asList("HELP", "NEW", "EXISTS", "LIST", "INFO", "STOP");
            case "INFO":
                break;
            case "LIST":
                break;
            case "NEW":
                break;
            case "STOP":
                break;
            case "EXISTS":
                break;
        }
        throw new Ex.Unsupported("Unknown SERVER command: " + cmd);
    }
}
