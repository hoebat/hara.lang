package hara.lang.kernel;

import java.io.*;
import java.lang.ref.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.net.*;

import hara.lang.base.*;

@SuppressWarnings("rawtypes")
public class Server implements I.Component {

	final ConcurrentHashMap<Thread, Reference<Conn>> CLIENTS = new ConcurrentHashMap<Thread, Reference<Conn>>();
	final ReferenceQueue<Conn> RQ = new ReferenceQueue<Conn>();

	public final Foundation _F;
	public final String _key;
	public final int _port;
	public ServerSocket _socket;
	public Thread _thread;

	public Server(Foundation F, String key) {
		this(F, key, Foundation.DEFAULT_PORT);
	}

	public Server(Foundation F, String key, int port) {
		_F = F;
		_key = key;
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
			System.out.println("PORT OPENED: " + _port);
			Loop loop = new Loop(this, _socket);
			_thread = new Thread(loop);
			_thread.start();

		} catch (Throwable t) {
			G.sneakyThrow(t);
		}
		return this;
	}

	@Override
	public I.Component stop() {
		_thread.interrupt();
		_thread = null;
		for (var entry : CLIENTS.entrySet()) {
			try {
				entry.getValue().get().close();
				entry.getKey().interrupt();
			} catch (IOException e) {
			}
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
				while (!_socket.isClosed()) {
					Socket s = _socket.accept();
					Handler h = new Handler(_instance, s);
					Thread th = new Thread(h);
					h.setThread(th);
					_instance.register(th, h._conn);
					th.start();
				}
			} catch (IOException e) {
			}

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
		
		private void writeReturn(Conn conn, Object ret) throws IOException {
			if (ret instanceof List) {
				conn.write((List)ret);
			} else if (ret instanceof String) {
				conn.writeString((String)ret);
			} else if (ret instanceof Long) {
				conn.write(((Long)ret).longValue());
			} else if (ret instanceof Throwable) {
				conn.write((Throwable) ret);
			} else if (ret instanceof byte[]) {
				conn.write((byte[])ret);
			}
		}

		@SuppressWarnings("unchecked")
		@Override
		public void run() {
			try {
				while (!_conn.socket.isClosed()) {
					try {
						Object obj = _conn.read();
						if (obj instanceof List) {
							List args = (List) ((List) obj).stream()
											.map(b -> new String((byte[])b))
											.collect(Collectors.toList());
							String arg0 = (String) args.get(0);
							args.remove(0);
							var cmd = Foundation.COMMAND.valueOf(arg0);
							var ret = Foundation.runCommand(cmd, args);
							writeReturn(_conn, ret);
						} else {
							_conn.write("NOT PROCESSED: " + obj);
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