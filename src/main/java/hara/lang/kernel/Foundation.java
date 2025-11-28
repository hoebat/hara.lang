package hara.lang.kernel;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.lang.reflect.Method;

import hara.lang.base.Arr;
import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.base.I;
import hara.lang.base.It;
import hara.lang.compiler.Compiler;
import hara.lang.compiler.CompilerException;
import hara.lang.compiler.DynamicClassLoader;
import hara.lang.data.List;
import hara.lang.lib.*;
import hara.lang.lib.RT.Instance;
import hara.lang.lib.Read;

@SuppressWarnings("rawtypes")
public class Foundation implements I.Context {

	static final int DEFAULT_PORT = 4164;
	
	public static class Peer {
		public final String name;
		public final String host;
		public final int port;

		public Peer(String name, String host, int port) {
			this.name = name;
			this.host = host;
			this.port = port;
		}

		@Override
		public String toString() {
			return name + "@" + host + ":" + port;
		}
	}

	public final ConcurrentHashMap<String,Server> SERVERS = new ConcurrentHashMap<String,Server>();
	public final ConcurrentHashMap<String,I.Runtime> RTS = new ConcurrentHashMap<String,I.Runtime>();
	public final ConcurrentHashMap<String,Peer> PEERS = new ConcurrentHashMap<String,Peer>();
	public final ConcurrentHashMap<String, Cmd> REGISTRY = new ConcurrentHashMap<>();

	public Foundation() {
		loadCommands();
	}

	public void register(String name, Cmd cmd) {
		REGISTRY.put(name, cmd);
	}

	public void loadCommands() {
		for (Method method : Fn.class.getDeclaredMethods()) {
			Command annotation = method.getAnnotation(Command.class);
			if (annotation != null) {
				register(annotation.name(), (F, args) -> {
					try {
						return method.invoke(null, F, args);
					} catch (Exception e) {
						throw Ex.Sneaky(e);
					}
				});
			}
		}
	}
	
	public enum OS {
		HELP, PWD, LS, RUN
	}
	
	public enum JVM {
		HELP, HOME, PROPS, ENV, VERSION, VENDOR, BOOTPATH, 
		CLASSPATH, CP, CLASSLOADER,
	}
	
	public enum SERVER {
		HELP, NEW, EXISTS, LIST, INFO, STOP
	}
	
	public enum SESSION {
		HELP, NEW, GET, EXISTS, LIST, INFO, KILL, PATH
	}
	
	public enum CLASSPATH {
		ADD, LIST, REMOVE, PURGE
	}

	public enum PEER {
		HELP, ADD, LIST, REMOVE, PING
	}

	public enum MAVEN {
		HELP, LOAD
	}

	@SuppressWarnings("unchecked")
	public static java.util.List<String> toStringList(java.util.List<Object> args) {
		return args.stream().map(Object::toString).collect(Collectors.toList());
	}

	@SuppressWarnings("unchecked")
	public static Object mapToList(Object m) {
		if (m instanceof Map) {
			return ((Map) m).entrySet().stream()
					.map(e -> Arrays.asList(((Map.Entry) e).getKey(), mapToList(((Map.Entry) e).getValue())))
					.collect(Collectors.toList());
		} else {
			return m;
		}
	}

	@SuppressWarnings("unchecked")
	public static Object run(Function<java.util.List<String>, Object> f, Object... args) {
		java.util.List input = (args.length == 1 && args[0] instanceof java.util.List)
				? (java.util.List) args[0]
				: Arrays.asList(args);
		return f.apply(input);
	}


	@SuppressWarnings("unchecked")
	public static Object runIn(Function<java.util.List<String>, Object> f, I.Context c, Object... args) {
		java.util.List input = (args.length == 1 && args[0] instanceof java.util.List)
				? (java.util.List) args[0]
				: Arrays.asList(args);
		return f.apply(input);
	}

	public interface Fn {

		public static Object JVM_ENV(java.util.List<String> args) {
			return (args.size() == 0) ? mapToList(System.getenv()) : System.getenv(args.get(0));
		}

		public static Object JVM_PROPS(java.util.List<String> args) {
			return (args.size() == 0) ? mapToList(System.getProperties()) : System.getProperty(args.get(0));
		}

		public static java.util.List runDIR(Foundation F) {
			return Arrays.asList(
					"SERVERS", It.toArrayList(F.SERVERS.keys()),
					"RTS", It.toArrayList(F.RTS.keys()),
					"PEERS", It.toArrayList(F.PEERS.keys()));
		}

		public static Object runInfo(Foundation F) {
			return mapToList(Map.of(
				"java.version", System.getProperty("java.version"),
				"os.name", System.getProperty("os.name"),
				"sessions", F.RTS.size(),
				"servers", F.SERVERS.size(),
				"peers", F.PEERS.size()
			));
		}

		public static String runProcess(java.util.List<String> args) {
			try {
				var p = new ProcessBuilder().command(args).start();
				return new String(p.getInputStream().readAllBytes());
			} catch(Throwable t) {
				throw Ex.Sneaky(t);
			} 
		}


		@SuppressWarnings("unchecked")
		public static java.util.List runHELP(Foundation F, Object enums) {
			return It.toArrayList(
					It.map(Arr.toIter(enums), (x) -> x.toString()));
		}

		@Command(name = "HELP")
		public static Object cmdHELP(Foundation F, java.util.List<Object> args) {
			return runHELP(F, F.REGISTRY.keySet());
		}

		@Command(name = "SHUTDOWN")
		public static Object cmdSHUTDOWN(Foundation F, java.util.List<Object> args) {
			System.exit(1);
			return null;
		}

		@Command(name = "PING")
		public static Object cmdPING(Foundation F, java.util.List<Object> args) {
			return "PONG";
		}

		@Command(name = "ECHO")
		public static Object cmdECHO(Foundation F, java.util.List<Object> args) {
			return args;
		}

		@Command(name = "DIR")
		public static Object cmdDIR(Foundation F, java.util.List<Object> args) {
			return runDIR(F);
		}

		@Command(name = "JVM")
		public static Object runJVM(Foundation F, java.util.List<Object> argsObj) {
			java.util.List<String> args = toStringList(argsObj);
			JVM cmd = JVM.valueOf(args.get(0));
			args.remove(0);
			
			switch(cmd) {
			case HELP: 			return Fn.runHELP(F, JVM.values());
			case BOOTPATH:  	return run(Fn::JVM_PROPS, "sun.boot.library.path");
			case CP:
			case CLASSPATH: 	return run(Fn::JVM_PROPS, "java.class.path");
			case CLASSLOADER: 	return ClassLoader.getSystemClassLoader().toString();
			case ENV:     		return run(Fn::JVM_ENV, args);
			case HOME:    		return run(Fn::JVM_PROPS, "java.home");
			case PROPS:   		return run(Fn::JVM_PROPS, args);
			case VENDOR:  		return run(Fn::JVM_PROPS, "java.vendor");
			case VERSION: 		return run(Fn::JVM_PROPS, "java.version");
			}
			throw new Ex.Unsupported();
		}
		
		@Command(name = "SERVER")
		public static Object runServer(Foundation F, java.util.List<Object> argsObj) {
			java.util.List<String> args = toStringList(argsObj);
			SERVER cmd = SERVER.valueOf(args.get(0));
			args.remove(0);
			switch(cmd) {			
			case HELP: return Fn.runHELP(F, SERVER.values());
			case INFO:
				break;
			case LIST:
				break;
			case NEW:
				break;
			case STOP:
				break;
			case EXISTS:
				break;
			}
			throw new Ex.Unsupported();
		}
		
		@Command(name = "OS")
		public static Object runOS(Foundation F, java.util.List<Object> argsObj) {
			java.util.List<String> args = toStringList(argsObj);
			OS cmd = OS.valueOf(args.get(0));
			args.remove(0);
			switch(cmd) {
			case HELP:  return Fn.runHELP(F, OS.values());
			case LS: 	args.add(0, "ls");
						return runProcess(args);
			case PWD: 	return run(Fn::JVM_ENV, "PWD");
			case RUN: 	return runProcess(args);
			}

			throw new Ex.Unsupported();
		}
		
		@Command(name = "PEER")
		public static Object runPeer(Foundation F, java.util.List<Object> argsObj) {
			java.util.List<String> args = toStringList(argsObj);
			PEER cmd = PEER.valueOf(args.get(0));
			args.remove(0);
			switch(cmd) {
			case HELP: return Fn.runHELP(F, PEER.values());
			case LIST: return mapToList(F.PEERS);
			case ADD:
				// PEER ADD name host port
				String name = args.get(0);
				String host = args.get(1);
				int port = Integer.parseInt(args.get(2));
				F.PEERS.put(name, new Peer(name, host, port));
				return name;
			case REMOVE:
				return F.PEERS.remove(args.get(0)) != null;
			case PING:
				// Minimal implementation: check if we have a record
				return F.PEERS.containsKey(args.get(0));
			}
			throw new Ex.Unsupported();
		}

		public static Object runSessionFor(Foundation F, String key, Function<RT.Instance, Object> f) {
			RT.Instance s = (Instance) F.RTS.get(key);
			
			if (s != null) return f.apply(s);
			throw new Ex.Runtime("No Session: " + key);
		}
		
		public static Object runSessionCreate(Foundation F, java.util.List<String> args, boolean raise) {
			var key = args.get(0);
			var s = F.RTS.get(key);
			if (s != null) {
				if (raise) {
					throw new Ex.Runtime("Session already exists: " + key);
				}
			} else {
				F.RTS.put(key, new RT.Instance(F, key));
			}
			return key;
		}

		public static Object runSessionClasspath(RT.Instance rt, java.util.List<String> args) {
			CLASSPATH cmd = (args.size() == 1)
								? CLASSPATH.LIST
								: CLASSPATH.valueOf(args.get(1));
			if(cmd == CLASSPATH.LIST) {
				System.out.println("LIST");
				return rt.pathCache();
			}
		
			
			args.remove(0);
			args.remove(0); 
			switch(cmd) {
			case ADD:    return rt.pathAdd(args.toArray(new String[] {}));
			case REMOVE: return rt.pathRemove(args.toArray(new String[] {}));
			case PURGE:  return rt.pathCache().empty();
			case LIST:   return rt.pathCache();
			}
			throw new Ex.Unsupported();
		}
		
		@Command(name = "SESSION")
		public static Object runSession(Foundation F, java.util.List<Object> argsObj) {
			java.util.List<String> args = toStringList(argsObj);
			SESSION cmd = SESSION.valueOf(args.get(0));
			args.remove(0);
			
			switch(cmd) {
			case HELP:   return Fn.runHELP(F, SESSION.values());
			case EXISTS: return runSessionFor(F, args.get(0), (rt) -> rt != null);
			case GET:    return runSessionCreate(F, args, false);
			case NEW:    return runSessionCreate(F, args, true);
			case PATH:   return runSessionFor(F, args.get(0), (rt) -> runSessionClasspath(rt, args));
			case LIST:   return It.toArrayList(F.RTS.keys());
			case KILL:   return runSessionFor(F, args.get(0), (rt) -> F.RTS.remove(args.get(0)));
			case INFO:   throw new Ex.TODO();
			}
			throw new Ex.Unsupported();
		}

		@Command(name = "MAVEN")
		public static Object runMaven(Foundation F, java.util.List<Object> argsObj) {
			java.util.List<String> args = toStringList(argsObj);
			MAVEN cmd = MAVEN.valueOf(args.get(0));
			args.remove(0);

			switch(cmd) {
			case HELP:   return Fn.runHELP(F, MAVEN.values());
			case LOAD:
				return runSessionFor(F, args.get(0),
					(rt) -> hara.lang.lib.Maven.load.invoke(rt, args.get(1))
				);
			}
			throw new Ex.Unsupported();
		}

		@Command(name = "INFO")
		public static Object runInfo(Foundation F, java.util.List<Object> args) {
			return runInfo(F);
		}

		@Command(name = "EVAL")
		public static Object runEval(Foundation F, java.util.List<Object> args) {
			return Fn.runSessionFor(F, args.get(0).toString(),
					rt -> G.display(rt.eval(rt.readString(args.get(1).toString()))));
		}

		@Command(name = "COMPILE")
		public static Object runCompile(Foundation F, java.util.List<Object> args) {
			try {
				hara.lang.data.List expression = (hara.lang.data.List) Read.LispReader.readString(args.get(0).toString(), null);
				Compiler compiler = new Compiler();
				byte[] bytecode = compiler.compile(expression);
				DynamicClassLoader loader = new DynamicClassLoader(Foundation.class.getClassLoader());
				Class<?> clazz = loader.defineClass(null, bytecode);
				return clazz.getConstructor().newInstance();
			} catch (CompilerException e) {
				throw new RuntimeException(e);
			} catch (Exception e) {
				throw Ex.Sneaky(e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public static Object runCommand(Foundation F, java.util.List<Object> args) {
		if (args.isEmpty()) {
			throw new Ex.Runtime("No command specified");
		}

		Object cmdObj = args.get(0);
		String name;

		if (cmdObj instanceof I.Namespaced) {
			name = ((I.Namespaced) cmdObj).getName();
		} else if (cmdObj instanceof I.Display) {
			String display = ((I.Display) cmdObj).display();
			name = display.startsWith(":") ? display.substring(1) : display;
		} else {
			name = cmdObj.toString();
		}

		name = name.toUpperCase();

		args.remove(0);

		Cmd cmd = F.REGISTRY.get(name);
		if (cmd != null) {
			return cmd.apply(F, args);
		}

		throw new Ex.Unsupported("Unknown command: " + name);
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object call(Object... args) {
		java.util.List<Object> inputs = It.toArrayList(It.iter(args));
		return runCommand(this, inputs);
	}

}
