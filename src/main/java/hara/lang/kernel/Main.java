package hara.lang.kernel;

import hara.lang.lib.RT;

@SuppressWarnings("rawtypes")
public class Main {

	public static void main(String[] args){
		var F = new Foundation();
		var server = new Server(F, "PRIMARY", Foundation.DEFAULT_PORT, "log/in.txt", "log/out.txt");
		var rt = new RT.Instance(F, "ROOT");

		F.SERVERS.put(server._key, server);
		F.RTS.put(rt._key, rt);
		
		server.start();
	}
}

