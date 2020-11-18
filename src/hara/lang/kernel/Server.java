package hara.lang.kernel;

import java.io.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;

import hara.lang.base.*;

@SuppressWarnings("rawtypes")
public class Server implements I.Component {

	static final int DEFAULT_PORT = 4164;

	final ConcurrentHashMap<Thread, Reference<Conn>> CLIENTS = new ConcurrentHashMap<Thread, Reference<Conn>>();
	final ReferenceQueue<Conn> RQ = new ReferenceQueue<Conn>();

	public ServerSocket _socket;
	public int _port;
	public Thread _thread;
	
	public Server() {
		this(DEFAULT_PORT);
	}

	public Server(int port){
		_port = port;
	}

	public void register(Thread th, Conn client) {
		CLIENTS.put(th, new WeakReference<Conn>(client, RQ));
	}

	public void deregister(Thread th) {
		CLIENTS.remove(th);
		G.clearCache(RQ, CLIENTS);
	}
	
	public int Clients() {
		return CLIENTS.size();
	}

	@Override
	public I.Component start() {
		try {
			_socket = new ServerSocket(_port);
			Loop loop = new Loop(this, _socket);
			_thread = new Thread(loop);
			_thread.start();
			
		} catch(Throwable t) {
			G.sneakyThrow(t);
		}
		return this;
	}

	@Override
	public I.Component stop() {
		_thread.interrupt();
		_thread = null;
		for(var entry : CLIENTS.entrySet()) {
			try {
				entry.getValue().get().close();
				entry.getKey().interrupt();
			} catch (IOException e) {}
		}
		return this;
	}

	@Override
	public boolean isStopped() {
		return !isStarted();
	}

	@Override
	public boolean isStarted() {
		return (_thread != null) && _thread.isAlive();
	}

	@Override
	public I.Metadata getStatus() {
		return null;
	}

	@Override
	public I.Metadata getProps() {
		return null;
	}
	
	private class Loop implements Runnable {
		
		Server _instance;
		ServerSocket _socket;
		
		public Loop(Server instance, ServerSocket socket) {
			_instance = instance;
			_socket = socket;
		}

		@Override
		public void run() {
			try {
				while(!_socket.isClosed()) {
					Socket s = _socket.accept();
					Handler h = new Handler(_instance, s);
					Thread th = new Thread(h);
					h.setThread(th);
					_instance.register(th, h._conn);
					th.start();
				}
			} catch (IOException e) {}
			
		}
		
	}
	

	// ClientHandler class
	private class Handler implements Runnable {

		public final Server _instance;
		public final Conn _conn;
		private Thread _thread;
		
		// constructor
		public Handler(Server instance, Socket s) throws IOException {
			_instance = instance;
			_conn = new Conn(s);
		}
		
		public void setThread(Thread thread) {
			_thread = thread;
		}

		@Override
		public void run() {
			try {
				while (!_conn.socket.isClosed()) {
					try {
						Object obj = _conn.read();
						if (obj instanceof List) {
							Object cmd = ((List) obj).get(0);
							if (cmd instanceof String) {
								switch ((String) cmd) {
								case "QUIT": _conn.close();
									break;
								default:
									_conn.write("NOT SUPPORTED: " + cmd);
								}
							}
						}
					} catch (Throwable t) {
						_conn.write("ERR: " + t.getMessage());
					}
				}
			} catch (Throwable t) {
				_instance.deregister(_thread);
			}

		}
	}

}