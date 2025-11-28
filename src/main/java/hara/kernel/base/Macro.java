package hara.kernel.base;
import hara.kernel.protocol.IEnv;
import hara.kernel.protocol.IRuntime;

import java.util.Iterator;
import hara.lang.base.*;
import hara.kernel.base.Module;
import hara.lang.data.*;

import static hara.kernel.base.Builtin.Runtime.*;
import static hara.kernel.base.Builtin.Struct.*;
import static hara.kernel.base.Builtin.Basic.*;
import static hara.kernel.base.Builtin.Check.*;


@SuppressWarnings({ "rawtypes", "unchecked" })
public interface Macro {
	
	public static class Recur {
		public final Object[] args;

		public Recur(Object[] args) {
			this.args = args;
		}
	}

	public interface Java {

		public static <R, AST> R dotExpr(IRuntime rt, Object o, AST cmd) {
			if(cmd instanceof Symbol) {
				return (R) Reflect.getInstanceField(o, G.display(cmd));
			} else if(cmd instanceof List) {
				var l = (List)cmd;
				var method = l.peekFirst();
				return (R) Reflect.invokeInstanceMethod(
						o, 
						G.display(method),
						It.toArray(
								It.map(It.drop(It.iter(l), 1),
										(expr) -> rt.eval(expr))));
				
			} else if(cmd instanceof Vector || cmd instanceof Std.T.Tup1) {
				var idx = rt.eval(((I.Nth)cmd).nth(0));
				if(o.getClass().isArray()) {
					return (R) ((Object[])o)[(int)idx];
				} else if(o instanceof I.Lookup){
					return (R) Builtin.Collection.get((I.Lookup)o, idx);
				} else if(o instanceof Iterable) {
					return (R) Builtin.Collection.nth((Iterable)o, (long)idx);
				} else if(o instanceof String) {
					return (R) Builtin.Collection.nth((String)o, (long)idx);
				}
			}
			throw new Ex.Unsupported();
		}
		
		
		@Module.Fn(name = ".", complete = true, vargs = true, env = true)
		@Module.Var(control = true)
		public static <R, AST, ITR> R dotExpr(IEnv env, AST expr, AST cmd, ITR more) {
			Object o = Eval.eval(expr, env);
			return (R) It.reduce(
					It.iter(more),
					dotExpr(env.getRuntime(), o, cmd),
					(acc, c) -> dotExpr(env.getRuntime(), acc, c));
			
		}
		
		@Module.Fn(name = "new", complete = true, vargs = true, env = true)
		@Module.Var(control = true)
		public static <R, ITR> R newExpr(IEnv env, Symbol clsym, ITR args) {
			Class cls = (Class) Eval.eval(clsym, env);

			// Evaluate args
			Object[] evalArgs = It.toArray(It.map(It.iter(args), arg -> Eval.eval(arg, env)));
			return (R) Reflect.invokeConstructor(cls, evalArgs);
		}
	}
	
	public interface Control {
		
		@Module.Fn(name = "quote")
		@Module.Var(control = true)
		public static Object quoteExpr(Object expr) {
			return expr;
		}

		@Module.Fn(name = "def", complete = true, env = true)
		@Module.Var(control = true)
		public static Var defExpr(IEnv env, Symbol sym, Object expr) {
			var val = Eval.eval(expr, env);
			Var v = (Var) new Var(sym.getName(), val).withMeta(sym.meta());
			env.getRuntime().setObj(sym, v);
			return v;
		}

		@Module.Fn(name = "do", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object doExpr(IEnv env, ITR exprs) {
			return It.reduce(
					It.iter(exprs),
					null,
					(out, expr) -> Eval.eval(expr, env));
		}
		
		@Module.Fn(name = "fn", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> I.Fn fnExpr(IEnv env, Data.LinearType bindings, ITR args) {
			return new Env.FnEval(null, env.getRuntime(), env, bindings, list(args).cons(symbol("do")));
		}
		
		@Module.Fn(name = "let", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object letExpr(IEnv env, Data.LinearType bindings, ITR body) {
			if (bindings.count() % 2 != 0) {
				throw new Ex.Runtime("let bindings must have an even number of forms");
			}

			Eval.LocalEnv localEnv = new Eval.LocalEnv(env);
			java.util.List<Object> values = new java.util.ArrayList<>();
			for (int i = 1; i < bindings.count(); i += 2) {
				values.add(Eval.eval(bindings.nth(i), env));
			}

			for (int i = 0; i < bindings.count(); i += 2) {
				Symbol symbol = (Symbol) bindings.nth(i);
				localEnv.addBinding(symbol, values.get(i / 2));
			}

			Object result = null;
			Iterator it = It.iter(body);
			while(it.hasNext()) {
				result = Eval.eval(it.next(), localEnv);
			}
			return result;
		}

		@Module.Fn(name = "if", complete = true, env = true)
		@Module.Var(control = true)
		public static <EXPR> Object ifExpr(IEnv env, EXPR check, EXPR then, EXPR otherwise) {
			Object val = Eval.eval(check, env);
			if(isTruthy(val)) {
				return Eval.eval(then, env);
			}
			return Eval.eval(otherwise, env);
		}

		@Module.Fn(name = "if", complete = true, env = true)
		@Module.Var(control = true)
		public static <EXPR> Object ifExpr(IEnv env, EXPR check, EXPR then) {
			return ifExpr(env, check, then, null);
		}
		
		@Module.Fn(name = "cond", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object conjExpr(IEnv env, ITR pairs) {
			Iterator<I.Pair> branches = It.partitionPair(It.iter(pairs));
			I.Pair p = It.some(branches, (e) -> isTruthy(Eval.eval(e.getKey(), env)));
			return (p != null) ? Eval.eval(p.getValue(), env) : null;
		}
		
		@Module.Fn(name = "->", complete = true, vargs = true)
		@Module.Var(macro = true)
		public static <R, ITR> R threadFirst(Object any, ITR args) {
			return (R) It.reduce(
					It.iter(args), 
					any,
					(acc, e) -> {
						if (!(e instanceof List.Standard)) {
							return list(Arr.objects(e, acc));
						} else {
							var l = (List.Standard)e;
							var changed = atomVolatile(false);
							var ret = list(It.map(It.iter(l), (i) -> {
								if(Eq.eq(i, Symbol.create("%"))) {
									changed.reset(true);
									return acc;
								}
								return i;
							}));
							if(changed.deref()) {
								return ret;
							} else {
								return l.popFirst().cons(acc).cons(l.peekFirst());
							}
						}
					});
		}
		
		@Module.Fn(name = "->>", complete = true, vargs = true)
		@Module.Var(macro = true)
		public static <R, ITR> R threadLast(Object any, ITR args) {
			return (R) It.reduce(
					It.iter(args), 
					any,
					(acc, e) -> {
						if (!(e instanceof List.Standard)) {
							return list(Arr.objects(e, acc));
						} else {
							var l = (List.Standard)e;
							return l.conj(acc);
						}
					});
		}

		@Module.Fn(name = "try", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object tryExpr(IEnv env, ITR body) {
			Iterator it = It.iter(body);
			Object res = null;
			List.Standard catches = List.Standard.EMPTY;
			List.Standard finallies = List.Standard.EMPTY;
			List.Standard exprs = List.Standard.EMPTY;

			while(it.hasNext()) {
				Object elem = it.next();
				if (elem instanceof List) {
					List l = (List) elem;
					Object head = l.peekFirst();
					if (head instanceof Symbol && ((Symbol)head).getName().equals("catch")) {
						catches = (List.Standard) catches.conj(l);
						continue;
					} else if (head instanceof Symbol && ((Symbol)head).getName().equals("finally")) {
						finallies = (List.Standard) finallies.conj(l);
						continue;
					}
				}
				exprs = (List.Standard) exprs.conj(elem);
			}

			try {
				Iterator eit = exprs.iterator();
				while(eit.hasNext()) {
					res = Eval.eval(eit.next(), env);
				}
			} catch (Throwable t) {
				Throwable cause = (t instanceof Exception) ? Reflect.getCauseOrElse((Exception)t) : t;

				Iterator cit = catches.iterator();
				boolean caught = false;
				while(cit.hasNext()) {
					List clause = (List) cit.next();
					Symbol typeSym = (Symbol) clause.nth(1);
					Symbol bindSym = (Symbol) clause.nth(2);
					Class type = (Class) Eval.eval(typeSym, env);

					if (type.isInstance(cause)) {
						Eval.LocalEnv localEnv = new Eval.LocalEnv(env);
						localEnv.addBinding(bindSym, cause);

						Iterator bit = It.drop(clause.iterator(), 3);
						while(bit.hasNext()) {
							res = Eval.eval(bit.next(), localEnv);
						}
						caught = true;
						break;
					}
				}
				if (!caught) {
					throw Ex.Sneaky(cause);
				}
			} finally {
				Iterator fit = finallies.iterator();
				while(fit.hasNext()) {
					List clause = (List) fit.next();
					Iterator bit = It.drop(clause.iterator(), 1);
					while(bit.hasNext()) {
						Eval.eval(bit.next(), env);
					}
				}
			}
			return res;
		}

		@Module.Fn(name = "throw", complete = true, env = true)
		@Module.Var(control = true)
		public static Object throwExpr(IEnv env, Object expr) {
			Object ex = Eval.eval(expr, env);
			if (ex instanceof Throwable) {
				throw Ex.Sneaky((Throwable) ex);
			} else {
				throw new Ex.Runtime("Throw requires a Throwable, got: " + ex);
			}
		}

		@Module.Fn(name = "recur", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object recurExpr(IEnv env, ITR args) {
			Object[] newVals = It.toArray(It.map(It.iter(args), arg -> Eval.eval(arg, env)));
			return new Recur(newVals);
		}

		@Module.Fn(name = "loop", complete = true, env = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object loopExpr(IEnv env, Data.LinearType bindings, ITR body) {
			if (bindings.count() % 2 != 0) {
				throw new Ex.Runtime("loop bindings must have an even number of forms");
			}

			Eval.LocalEnv localEnv = new Eval.LocalEnv(env);
			java.util.List<Object> values = new java.util.ArrayList<>();
			for (int i = 1; i < bindings.count(); i += 2) {
				values.add(Eval.eval(bindings.nth(i), env));
			}

			int bindingCount = (int) bindings.count() / 2;
			Symbol[] syms = new Symbol[bindingCount];
			for (int i = 0; i < bindings.count(); i += 2) {
				Symbol symbol = (Symbol) bindings.nth(i);
				syms[i / 2] = symbol;
				localEnv.addBinding(symbol, values.get(i / 2));
			}

			Object result = null;
			List bodyList = List.Standard.into(It.iter(body)); // materialize body to iterate multiple times

			while(true) {
				Iterator it = bodyList.iterator();
				boolean recurred = false;
				while(it.hasNext()) {
					result = Eval.eval(it.next(), localEnv);
					if (result instanceof Recur) {
						Recur r = (Recur) result;
						if (r.args.length != bindingCount) {
							throw new Ex.Arity(r.args.length, "loop requires " + bindingCount + " args");
						}
						for(int i=0; i<bindingCount; i++) {
							localEnv.addBinding(syms[i], r.args[i]);
						}
						recurred = true;
						break; // break inner loop, continue outer while(true)
					}
				}
				if (!recurred) {
					return result;
				}
			}
		}
	}
	

}
