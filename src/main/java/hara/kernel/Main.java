package hara.kernel;

import hara.kernel.base.RT;
import hara.kernel.redirect.FileRedirect;

import java.io.FileNotFoundException;

public class Main {

  public static void main(String[] args) throws FileNotFoundException, java.io.IOException {
    var F = new Foundation();
    var redirect = new FileRedirect("log/in.txt", "log/out.txt");
    var server = new Server(F, "PRIMARY", Foundation.DEFAULT_PORT, redirect);
    var rt = new RT.Instance<>(F, "ROOT");

    F.SERVERS.put(server._key, server);
    F.RTS.put(rt._key, rt);

    server.start();

    // Start REPL
    Repl repl = new Repl(rt);
    repl.run();
  }
}
