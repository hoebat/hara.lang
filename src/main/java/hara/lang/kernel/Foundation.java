package hara.lang.kernel;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import hara.lang.base.Arr;
import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.base.I;
import hara.lang.base.It;
import hara.lang.lib.*;
import hara.lang.lib.RT.Instance;

@SuppressWarnings("rawtypes")
public class Foundation implements I.Context {

	static final int DEFAULT_PORT = 4164;
	
	public final ConcurrentHashMap<String,Server> SERVERS = new ConcurrentHashMap<String,Server>();
	public final ConcurrentHashMap<String,I.Runtime> RTS = new ConcurrentHashMap<String,I.Runtime>();

	public enum COMMAND {
		HELP, SHUTDOWN, DIR, PING, ECHO, OS, JVM, SERVER, SESSION, EVAL
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
	public static Object run(Function<List<String>, Object> f, Object... args) {
		List input = (args.length == 1 && args[0] instanceof List) 
				? (List) args[0] 
				: Arrays.asList(args);
		return f.apply(input);
	}


	@SuppressWarnings("unchecked")
	public static Object runIn(Function<List<String>, Object> f, I.Context c, Object... args) {
		List input = (args.length == 1 && args[0] instanceof List) 
				? (List) args[0] 
				: Arrays.asList(args);
		return f.apply(input);
	}

	public interface Fn {

		public static Object JVM_ENV(List<String> args) {
			return (args.size() == 0) ? mapToList(System.getenv()) : System.getenv(args.get(0));
		}

		public static Object JVM_PROPS(List<String> args) {
			return (args.size() == 0) ? mapToList(System.getProperties()) : System.getProperty(args.get(0));
		}

		public static List runDIR(Foundation F) {
			return Arrays.asList("SERVERS", It.toArrayList(F.SERVERS.keys()), "RTS",
					It.toArrayList(F.RTS.keys()));
		}

		public static String runProcess(List<String> args) {
			try {
				var p = new ProcessBuilder().command(args).start();
				return new String(p.getInputStream().readAllBytes());
			} catch(Throwable t) {
				throw Ex.Sneaky(t);
			} 
		}


		@SuppressWarnings("unchecked")
		public static List runHELP(Foundation F, Object enums) {
			return It.toArrayList(
					It.map(Arr.toIter(enums), (x) -> x.toString()));
		}

		public static Object runJVM(Foundation F, List<String> args) {
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
		
		public static Object runServer(Foundation F, List<String> args) {
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
		
		public static Object runOS(Foundation F, List<String> args) {
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
		
		public static Object runSessionFor(Foundation F, String key, Function<RT.Instance, Object> f) {
			RT.Instance s = (Instance) F.RTS.get(key);
			
			if (s != null) return f.apply(s);
			throw new Ex.Runtime("No Session: " + key);
		}
		
		public static Object runSessionCreate(Foundation F, List<String> args, boolean raise) {
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

		public static Object runSessionClasspath(RT.Instance rt, List<String> args) {
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
		

		public static Object runSession(Foundation F, List<String> args) {
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
	}
	
	@SuppressWarnings("unchecked")
	public static Object runCommand(Foundation F, List<String> args) {
		var cmd = COMMAND.valueOf(args.get(0));
		args.remove(0);
		
		switch (cmd) {
		case HELP: return Fn.runHELP(F, COMMAND.values());
		case PING: return "PONG";
		case ECHO: return args;
		case DIR:  return Fn.runDIR(F);
		case JVM:  return Fn.runJVM(F, args);
		case OS:   return Fn.runOS(F, args);
		case SERVER: return Fn.runServer(F, args);
		case SESSION: return Fn.runSession(F, args);
		case EVAL: 
			return Fn.runSessionFor(F, args.get(0), 
					rt -> G.display(rt.eval(rt.readString(args.get(1)))));
		case SHUTDOWN: 
			System.exit(1);
			return null;
		}
		throw new Ex.Unsupported();
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object call(Object... args) {
		var inputs = It.toArrayList(It.iter(args));
		return runCommand(this, inputs);
	}

}
