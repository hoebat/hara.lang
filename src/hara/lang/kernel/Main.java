package hara.lang.kernel;

public class Main {

	public static void main(String[] args){
		var F = new Foundation();
		var server = new Server(F, "PRIMARY");
		var session = new Session.RT(F, "ROOT");

		F.SERVERS.register(server._key, server);
		F.SESSIONS.register(session._key, session);
		
		server.start();
	}
}
