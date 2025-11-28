package hara.lang.kernel.command;

import java.util.List;
import java.util.Arrays;
import hara.lang.base.Ex;
import hara.lang.kernel.Command;
import hara.lang.kernel.Foundation;

public class Peer {

    @Command(name = "PEER")
    public static Object run(Foundation F, List<Object> argsObj) {
        List<String> args = Foundation.toStringList(argsObj);
        String cmd = args.get(0);
        args.remove(0);
        switch(cmd) {
            case "HELP": return Arrays.asList("HELP", "ADD", "LIST", "REMOVE", "PING");
            case "LIST": return Foundation.mapToList(F.PEERS);
            case "ADD":
                // PEER ADD name host port
                String name = args.get(0);
                String host = args.get(1);
                int port = Integer.parseInt(args.get(2));
                F.PEERS.put(name, new Foundation.Peer(name, host, port));
                return name;
            case "REMOVE":
                return F.PEERS.remove(args.get(0)) != null;
            case "PING":
                // Minimal implementation: check if we have a record
                return F.PEERS.containsKey(args.get(0));
        }
        throw new Ex.Unsupported("Unknown PEER command: " + cmd);
    }
}
