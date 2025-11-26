package hara.lang.kernel;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.net.*;

import hara.lang.base.*;

@SuppressWarnings("rawtypes")
public class Server implements I.Component {

	final Ut.RefCache<Thread, Conn> CLIENTS = new Ut.RefCache<>();

	public final Foundation _F;
	public final String _key;
	public final int _port;
	public final PrintStream _logIn;
	public final PrintStream _logOut;
	public ServerSocket _socket;
	public Thread _thread;

	public Server(Foundation F, String key) {
		this(F, key, Foundation.DEFAULT_PORT, null, null);
	}

	public Server(Foundation F, String key, int port) {
		this(F, key, port, null, null);
	}

	public Server(Foundation F, String key, int port, String logInPath, String logOutPath) {
		_F = F;
		_key = key;
		_port = port;
		try {
			if (logInPath != null) {
				File logInFile = new File(logInPath);
				File parentDir = logInFile.getParentFile();
				if (parentDir != null) {
					parentDir.mkdirs();
				}
				_logIn = new PrintStream(new FileOutputStream(logInFile, true));
			} else {
				_logIn = null;
			}
			if (logOutPath != null) {
				File logOutFile = new File(logOutPath);
				File parentDir = logOutFile.getParentFile();
				if (parentDir != null) {
					parentDir.mkdirs();
				}
				_logOut = new PrintStream(new FileOutputStream(logOutFile, true));
			} else {
				_logOut = null;
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}
	
	public long count() {
		return CLIENTS.count();
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
			throw Ex.Sneaky(t);
		}
		return this;
	}

	@Override
	public I.Component stop() {
		_thread.interrupt();
		_thread = null;
		for (var entry : CLIENTS.getLookup().entrySet()) {
			try {
				entry.getValue().get().close();
				entry.getKey().interrupt();
			} catch (IOException e) {
			}
		}
		if (_logIn != null) {
			_logIn.close();
		}
		if (_logOut != null) {
			_logOut.close();
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
					_instance.CLIENTS.register(th, h._conn);
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
			_conn = new Conn(s, 1 << 16, 1 << 16, _instance._logIn, _instance._logOut);
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
			} else {
				conn.writeString(ret.toString());
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
							var ret = Foundation.runCommand(_instance._F, args);
							writeReturn(_conn, ret);
						} else {
							_conn.write("NOT PROCESSED: " + obj);
						}
					} catch (Throwable t) {
						_conn.write("ERR: " + t.getMessage());
						t.printStackTrace();
					}
				}
			} catch (Throwable t) {
				_instance.CLIENTS.deregister(_thread);
			}

		}
	}

}