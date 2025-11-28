package hara.kernel.base;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.*;

import hara.lang.base.*;
import hara.lang.data.*;
import static hara.kernel.base.Builtin.Struct.*;

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

		Object f;
		if (fst instanceof Symbol) {
			Entry e = env.find(fst);
			if (e == null) {
				throw new Ex.Runtime("Cannot find symbol: " + G.display(fst));
			} else if (e.getValue() instanceof Var) {
				var v = (Var) e.getValue();
				if (v.isMacro()) {
					f = v.deref();
					var args = It.iter(ast.popFirst());
					var m = ((I.ObjType)f).meta();

					if (m != null) {
						if ((Boolean)((I.Lookup)m).lookup(Keyword.create("env"), false)) {
							args = It.concat(It.objects(env), args);
						} else if ((Boolean)((I.Lookup)m).lookup(Keyword.create("rt"), false)) {
							args = It.concat(It.objects(env.getRuntime()), args);
						}
					}
					var ret = apply(f, args);
					return eval(ret, env);
				} else if (v.isControl()) {
					f = v.deref();
					var args = It.iter(ast.popFirst());
					var m = ((I.ObjType)f).meta();

					if (m != null) {
						if ((Boolean)((I.Lookup)m).lookup(Keyword.create("env"), false)) {
							args = It.concat(It.objects(env), args);
						} else if ((Boolean)((I.Lookup)m).lookup(Keyword.create("rt"), false)) {
							args = It.concat(It.objects(env.getRuntime()), args);
						}
					}
					return apply(f, args);
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
