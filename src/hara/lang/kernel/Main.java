package hara.lang.kernel;

public class Main {

	public static void main(String[] args){
		var server = new Server(null, null);
		System.out.println("STARTING SERVER...");
		server.start();
	}
}
