package hara.lang.lib;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.data.*;
import static hara.lang.lib.Builtin.Struct.*;

@SuppressWarnings({ "rawtypes", "unchecked" })
public interface Eval {

	// A simple environment for local bindings
	static class LocalEnv implements I.Env {
		private final I.Env parent;
		private final java.util.Map<Symbol, Object> bindings;

		public LocalEnv(I.Env parent) {
			this.parent = parent;
			this.bindings = new java.util.HashMap<>();
		}

		public void addBinding(Symbol sym, Object val) {
			bindings.put(sym, val);
		}

		@Override
		public Entry find(Object key) {
			if (bindings.containsKey(key)) {
				return new java.util.AbstractMap.SimpleEntry<>((Symbol) key, bindings.get(key));

			}
			return parent.find(key);
		}

		@Override
		public I.Env getParent() {
			return parent;
		}

		@Override
		public I.Lookup getMap() {
			return new I.Lookup<Symbol, Object>() {
				@Override
				public Entry<Symbol, Object> find(Symbol key) {
					if (bindings.containsKey(key)) {
						return new java.util.AbstractMap.SimpleEntry<>(key, bindings.get(key));
					}
					return null;
				}

				@Override
				public Iterator<Symbol> keys() {
					return bindings.keySet().iterator();
				}

				@Override
				public Iterator<Object> vals() {
					return bindings.values().iterator();
				}
			};
		}

		@Override
		public I.Runtime getRuntime() {
			return parent.getRuntime();
		}

		@Override
		public Iterator keys() {
			return It.concat(bindings.keySet().iterator(), parent.keys());
		}

		@Override
		public Iterator vals() {
			return It.concat(bindings.values().iterator(), parent.vals());
		}
	}

	public static Object apply(Object f, Iterator it) {
		return Fn.toFn(f).apply(it);
	}

	public static Object apply(Object f, Object[] arr) {
		return Fn.toFn(f).apply(arr);
	}

	public static Object eval(Object ast, I.Env env) {
		if (ast instanceof Symbol) {
			Entry e = env.find(ast);
			if (e == null) {
				throw new Ex.Runtime("Cannot find symbol: " + G.display(ast));
			} else if (e.getValue() instanceof Var) {
				var v = (Var)e.getValue();
				if (v.isMacro() || v.isControl()) {
					throw new Ex.Runtime("Cannot return a macro/control value: "+ G.display(ast));
				} else {
					return v.deref();
				}
			} else {
				return e.getValue();
			}
		} else if (ast instanceof List) {
			return evalList((List) ast, env);
		} else if (ast instanceof Data.MapType) {
			Function<Entry, Entry> mf = (e) -> pair(eval(e.getKey(), env), eval(e.getValue(), env));
			return hara.lang.data.Map.Standard.into(It.map(It.iter(ast), mf));
		} else if (ast instanceof Data.LinearType) {
			var v = (Data.LinearType) ast;
			var it = It.map(It.iter(ast), obj -> eval(obj, env));
			if (v.count() > 5) {
				return vector(it);
			} else {
				var arr = It.toArray(it);
				return tuple(arr);
			}
		} else {
			return ast;
		}
	}

	public static Object evalList(List ast, I.Env env) {
		if (ast.count() == 0)
			return ast;

		var fst = ast.peekFirst();
		if (fst instanceof Symbol) {
			String symbolName = ((Symbol) fst).getName();
			if ("if".equals(symbolName)) {
				if (ast.count() != 3 && ast.count() != 4) {
					throw new Ex.Runtime("'if' special form requires 2 or 3 arguments, got " + (ast.count() - 1));
				}
				Object testExpr = ast.nth(1);
				Object testResult = eval(testExpr, env);

				// Clojure's truthiness: false and nil are false, everything else is true.
				// In Java, we'll represent nil as null.
				boolean isTruthy = testResult != null && !Boolean.FALSE.equals(testResult);

				if (isTruthy) {
					return eval(ast.nth(2), env);
				} else {
					if (ast.count() == 4) {
						return eval(ast.nth(3), env);
					} else {
						return null; // Return null for the missing else branch
					}
				}
			} else if ("do".equals(symbolName)) {
				List exprs = (List) ast.popFirst();
				Object result = null;
				for (Object expr : exprs) {
					result = eval(expr, env);
				}
				return result;
			} else if ("def".equals(symbolName)) {
				if (ast.count() != 3) {
					throw new Ex.Runtime("'def' special form requires 2 arguments, got " + (ast.count() - 1));
				}
				Symbol symbol = (Symbol) ast.nth(1);
				Object value = eval(ast.nth(2), env);
				Var v = new Var(symbol.pathString(), value);
				env.getRuntime().setObj(symbol, v);
				return v;
			} else if ("let".equals(symbolName)) {
				if (ast.count() < 3) {
					throw new Ex.Runtime("'let' special form requires at least 2 arguments");
				}
        
				Vector bindings = Vector.Standard.into(((Data.LinearType) ast.nth(1)).iterator());

				if (bindings.count() % 2 != 0) {
					throw new Ex.Runtime("let bindings must have an even number of forms");
				}

				LocalEnv localEnv = new LocalEnv(env);
				java.util.List<Object> values = new java.util.ArrayList<>();
				for (int i = 1; i < bindings.count(); i += 2) {
					values.add(eval(bindings.nth(i), env));
				}

				for (int i = 0; i < bindings.count(); i += 2) {
					Symbol symbol = (Symbol) bindings.nth(i);
					localEnv.addBinding(symbol, values.get(i / 2));
				}

				Object result = null;
				for (int i = 2; i < ast.count(); i++) {
					result = eval(ast.nth(i), localEnv);
				}
				return result;
			} else if ("fn".equals(symbolName)) {
				if (ast.count() < 3) {
					throw new Ex.Runtime("'fn' special form requires at least 2 arguments");
				}
				Data.LinearType params = (Data.LinearType) ast.nth(1);
				Object body;
				if (ast.count() > 3) {
					List bodyExprs = (List) ast.popFirst().popFirst();
					body = List.Standard.from(null, Symbol.create("do")).conjAll(bodyExprs.iterator());
				} else {
					body = ast.nth(2);
				}
				return new Env.FnEval(null, env.getRuntime(), env, params, body);

			} else if ("quote".equals(symbolName)) {
				if (ast.count() != 2) {
					throw new Ex.Runtime("'quote' special form requires 1 argument, got " + (ast.count() - 1));
				}
				return ast.nth(1);
			}
		}

		Object f;
		if (fst instanceof Symbol) {
			Entry e = env.find(fst);
			if (e == null) {
				throw new Ex.Runtime("Cannot find symbol: " + G.display(fst));
			} else if (e.getValue() instanceof Var) {
				var v = (Var) e.getValue();
				if (v.isMacro()) {
					f = v.deref();
					var ret = apply(f, It.iter(ast.popFirst()));
					return eval(ret, env);
				} else if (v.isControl()) {
					f = v.deref();
					return apply(f, It.iter(ast.popFirst()));
				}
			} else {
				f = e.getValue();
			}
		}

		Iterator it = It.map(It.iter(ast), obj -> eval(obj, env));
		return apply(it.next(), It.toArray(it));
	}
	
	/*
	public static String PATHS = "(do (sys:add-paths [\"file:///Users/chris/.m2/repository/org/codehaus/janino/janino/3.1.2/janino-3.1.2.jar\" \"file:///Users/chris/.m2/repository/org/codehaus/janino/commons-compiler/3.1.2/commons-compiler-3.1.2.jar\"]) (class \"org.codehaus.janino.ExpressionEvaluator\"))";
	
	public static void main(String [] args) {
		G.prn(eval(Read.LispReader.readString("(class \"java.nio.file.Path\")", null), 
				new RT.RootEnv(null, new RT.Instance(null, "test"))));
	}
	*/
}
