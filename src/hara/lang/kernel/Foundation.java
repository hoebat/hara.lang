package hara.lang.kernel;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("rawtypes")
public class Foundation {

	static final int DEFAULT_PORT = 4164;

	public static final ConcurrentHashMap<String, Reference<Server>> SERVERS = new ConcurrentHashMap<String, Reference<Server>>();

	public static final ConcurrentHashMap<String, Reference<Session>> SESSIONS = new ConcurrentHashMap<String, Reference<Session>>();

	public enum COMMAND {
		//
		//
		SHUTDOWN, HELP, DIR, PING, ECHO, SLURP,

		//
		//
		OS_PWD, OS_CD, OS_LS, OS_RUN,

		//
		//
		JVM_HOME, JVM_PROPS, JVM_ENV, JVM_VERSION, JVM_VENDOR, JVM_BOOTPATH, JVM_CLASSPATH, JVM_CLASSLOADER,
		JVM_INVOKE,

		//
		//
		SERVER_START, SERVER_INFO, SERVER_LIST, SERVER_STOP,

		//
		//
		SESSION_START, SESSION_STOP, SESSION_LIST, SESSION_INFO, SESSION_LOAD, SESSION_CLASSPATH, SESSION_ENV,
		SESSION_EVAL,
	}

	@SuppressWarnings("unchecked")
	public static Object mapToList(Object m) {
		if (m instanceof Map) {
			return (List) ((Map) m).entrySet().stream()
					.map(e -> Arrays.asList(((Map.Entry) e).getKey(), mapToList(((Map.Entry) e).getValue())))
					.collect(Collectors.toList());
		} else {
			return m;
		}
	}

	@SuppressWarnings("unchecked")
	public static Object run(Function<List<String>, Object> f, Object... args) {
		List input = (args.length == 1 && args[0] instanceof List) ? (List) args[0] : Arrays.asList(args);
		return f.apply(input);
	}

	public interface Fn {

		public static Object JVM_ENV(List<String> args) {
			return (args.size() == 0) ? mapToList(System.getenv()) : System.getenv(args.get(0));
		}

		public static Object JVM_PROPS(List<String> args) {
			return (args.size() == 0) ? mapToList(System.getProperties()) : System.getProperty(args.get(0));
		}
	}

	public static Object runCommand(COMMAND cmd, List<String> args) {
		switch (cmd) {
		case DIR:
			return Arrays.asList("SERVERS", Collections.list(SERVERS.keys()), "SESSIONS",
					Collections.list(SESSIONS.keys()));
		case PING:
			return "PONG";
		case ECHO:
			return args;
		case HELP:
			return COMMAND.values();
		case JVM_ENV:
			return run(Fn::JVM_ENV, args);
		case JVM_HOME:
			return run(Fn::JVM_PROPS, "java.home");
		case JVM_PROPS:
			return run(Fn::JVM_PROPS, args);
		case JVM_VENDOR:
			return run(Fn::JVM_PROPS, "java.vendor");
		case JVM_VERSION:
			return run(Fn::JVM_PROPS, "java.version");
		case JVM_CLASSPATH:
			return run(Fn::JVM_PROPS, "java.class.path");
		case JVM_BOOTPATH:
			return run(Fn::JVM_PROPS, "sun.boot.library.path");
		case JVM_CLASSLOADER:
			return ClassLoader.getSystemClassLoader().toString();
		case JVM_INVOKE:
			return null;

		case SERVER_INFO:
			break;
		case SERVER_LIST:
			break;
		case SERVER_START:
			break;
		case SERVER_STOP:
			break;
		case SESSION_CLASSPATH:
			break;
		case SESSION_ENV:
			break;
		case SESSION_EVAL:
			break;
		case SESSION_INFO:
			break;
		case SESSION_LIST:
			break;
		case SESSION_LOAD:
			break;
		case SESSION_START:
			break;
		case SESSION_STOP:
			break;
		case SHUTDOWN:
			break;
		case SLURP:
			break;
		case OS_CD:
			break;
		case OS_LS:
			break;
		case OS_PWD:
			break;
		case OS_RUN:
			break;
		default:
			break;

		}
		return null;
	}

}
