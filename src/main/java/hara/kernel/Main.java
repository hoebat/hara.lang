package hara.kernel;

import hara.kernel.base.Builtin;
import hara.kernel.base.RT;
import hara.kernel.base.Parser;
import hara.kernel.base.Reader;
import hara.kernel.redirect.FileRedirect;
import hara.lang.base.Ex;
import hara.lang.base.G;

import java.io.FileNotFoundException;
import java.io.InputStreamReader;

@SuppressWarnings("rawtypes")
public class Main {

  public static void main(String[] args) throws FileNotFoundException {
    var F = new Foundation();
    var redirect = new FileRedirect("log/in.txt", "log/out.txt");
    var server = new Server(F, "PRIMARY", Foundation.DEFAULT_PORT, redirect);
    var rt = new RT.Instance(F, "ROOT");

    F.SERVERS.put(server._key, server);
    F.RTS.put(rt._key, rt);

    server.start();

    // REPL Loop
    System.out.println("Hara Runtime Environment (HRE)");
    System.out.println("Session: " + rt._key);

    Reader r = new Reader(new InputStreamReader(System.in));
    // Use hara.lang.data.Map from Builtin.Struct.hashMap
    hara.lang.data.Map opts = Builtin.Struct.hashMap(new Object[] {});
    Object EOF_SENTINEL = new Object();

    while (true) {
      try {
        System.out.print("> ");
        System.out.flush();
        // Pass EOF_SENTINEL as eofValue
        Object form = Parser.LispReader.read(r, false, EOF_SENTINEL, false, opts);

        if (form == EOF_SENTINEL) break;

        Object res = rt.eval(form);
        G.prn(res);
      } catch (Throwable t) {
        if (t instanceof Ex.Runtime) {
          System.out.println("Error: " + t.getMessage());
        } else {
          t.printStackTrace();
        }
      }
    }
  }
}
