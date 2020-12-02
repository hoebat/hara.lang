package hara.lang.lib;

import java.util.Iterator;
import hara.lang.base.*;
import hara.lang.base.Module;
import hara.lang.data.*;

import static hara.lang.lib.Builtin.Runtime.*;
import static hara.lang.lib.Builtin.Struct.*;
import static hara.lang.lib.Builtin.Basic.*;
import static hara.lang.lib.Builtin.Check.*;


@SuppressWarnings({ "rawtypes", "unchecked" })
public interface Macro {
	
	public interface Java {

		public static <R, AST> R dotExpr(I.Runtime rt, Object o, AST cmd) {
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
		
		
		@Module.Fn(name = ".", complete = true, vargs = true, rt = true)
		@Module.Var(control = true)
		public static <R, AST, ITR> R dotExpr(I.Runtime rt, AST expr, AST cmd, ITR more) {
			Object o = eval(rt, expr);
			return (R) It.reduce(
					It.iter(more),
					dotExpr(rt, o, cmd),
					(acc, c) -> dotExpr(rt, acc, c));
			
		}
		
		@Module.Fn(name = "new", complete = true, vargs = true, rt = true)
		@Module.Var(control = true)
		public static <R, ITR> R newExpr(I.Runtime rt, Symbol clsym, ITR args) {
			Class cls = (Class) eval(rt, clsym);
			return (R) Reflect.invokeConstructor(cls, It.toArray(args));
		}
	}
	
	public interface Control {
		
		@Module.Fn(name = "quote")
		@Module.Var(control = true)
		public static Object quoteExpr(Object expr) {
			return expr;
		}

		@Module.Fn(name = "def", complete = true, rt = true)
		@Module.Var(control = true)
		public static Var defExpr(I.Runtime rt, Symbol sym, Object expr) {
			var val = rt.eval(expr);
			Var v = (Var) new Var(sym.getName(), val).withMeta(sym.meta());
			rt.setObj(sym, v);
			return v;
		}

		@Module.Fn(name = "do", complete = true, rt = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object doExpr(I.Runtime rt, ITR exprs) {
			return It.reduce(
					It.iter(exprs),
					null,
					(out, expr) -> rt.eval(expr));
		}
		
		@Module.Fn(name = "fn", complete = true, rt = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> I.Fn fnExpr(I.Runtime rt, Data.LinearType bindings, ITR args) {
			return new Env.FnEval(null, rt, bindings, list(args).cons(symbol("do")));
		}
		
		@Module.Fn(name = "if", complete = true, rt = true)
		@Module.Var(control = true)
		public static <EXPR> Object ifExpr(I.Runtime rt, EXPR check, EXPR then, EXPR otherwise) {
			Object val = rt.eval(check);
			if(isTruthy(val)) {
				return rt.eval(then);
			}
			return rt.eval(otherwise);
		}
		
		@Module.Fn(name = "cond", complete = true, rt = true, vargs = true)
		@Module.Var(control = true)
		public static <ITR> Object conjExpr(I.Runtime rt, ITR pairs) {
			Iterator<I.Pair> branches = It.partitionPair(It.iter(pairs));
			I.Pair p = It.some(branches, (e) -> isTruthy(rt.eval(e.getKey())));
			return (p != null) ? rt.eval(p.getValue()) : null;
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
	}
	

}
