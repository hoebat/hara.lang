package hara.lang.lib;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.*;
import java.util.Map.Entry;

import hara.lang.base.*;
import hara.lang.base.Module;
import hara.lang.data.*;
import static hara.lang.lib.Builtin.Basic.*;
import static hara.lang.lib.Builtin.Lambda.*;
import static hara.lang.lib.Builtin.Struct.*;


@SuppressWarnings({ "unchecked", "rawtypes" })
public interface Directory {

	
	public interface S {

		public static Object reduceInit(Module.ReduceInit type) {
			switch (type) {
			case EMPTY_LIST:   return List.Standard.EMPTY;
			case EMPTY_MAP:    return Map.Standard.EMPTY;
			case EMPTY_QUEUE:  return Queue.Standard.EMPTY;
			case EMPTY_VECTOR: return Vector.Standard.EMPTY;
			case NIL:  return null;
			case ONE:  return 1;
			case ZERO: return 0;
			case NEG_ONE:  return -1;
			case TRUE: return true;
			case FALSE: return false;
			default:   throw new Ex.Unsupported();
			}
		}
		
		public static I.Fn reduceFn(I.Fn f, I.Metadata meta, Module.Reduce opts) {
			var init = reduceInit(opts.init());
			switch (opts.type()) {
			case ARRAY:   return new Fn.T.ReduceArray(meta, init, f);
			case INIT:    return new Fn.T.ReduceInit(meta, init, f);
			case SELF:    return new Fn.T.ReduceSelf(meta, init, f);
			case COMPARE: return new Fn.T.ReduceCompare(meta, (Boolean)init, f);
			default:    throw new Ex.Unsupported();
			}
		}

		public static Object getAnnotation(Annotation[] ann, Class c) {
			return Arr.some((a) -> a.annotationType() == c, ann);
		}

		public static Object getAnnotation(Method m, Class c) {
			return getAnnotation(m.getAnnotations(), c);
		}

		public static Module.Fn fnOpts(Method m) {
			return (Module.Fn)getAnnotation(m, Module.Fn.class);
		}

		public static Module.Reduce rdOpts(Method m) {
			return (Module.Reduce)getAnnotation(m, Module.Reduce.class);
		}

		public static Module.Var varOpts(Method m) {
			return (Module.Var)getAnnotation(m, Module.Var.class);
		}
	}

	public static HashMap<String, ArrayList<Method>> getMethods(Class cls, HashMap<String, ArrayList<Method>> map) {
		
		Function pairs 
			= It.keep((m) -> {
				Module.Fn v = S.fnOpts((Method)m);
				return (v != null && !v.helper()) ? Builtin.Struct.pair(v.name(), m) : null; });
		
		return It.groupBy(
					(Iterator)pairs.apply(It.iter(cls.getDeclaredMethods())), 
					(Function)p -> ((I.Pair) p).getKey(),
					(Function)p -> ((I.Pair) p).getValue(),
					(HashMap)map);
	}
	
	public static HashMap<String, ArrayList<Method>> getMethods(Class cls) {
		return getMethods(cls, new HashMap());
	}
	
	public static I.Fn createFn(Entry<String, ArrayList<Method>> p){
	
		ArrayList<Method> all = p.getValue();

		BiFunction<ArrayList<Method>, Predicate<Method>, ArrayList<Method>> filterList 
			= (list, pred) -> It.toArrayList(It.filter(It.iter(list), pred));
		BiFunction<ArrayList<Method>, Function<Method, ?>, ArrayList> keepList 
			= (list, f) -> It.toArrayList(It.keep(It.iter(list), f));
		
		var vArgs  = filterList.apply(all, (m) -> S.fnOpts(m).vargs());
		var nArgs  = filterList.apply(all, (m) -> !S.fnOpts(m).vargs());
		ArrayList<Module.Reduce> ropts  = keepList.apply(all, (m) -> S.rdOpts(m));
		ArrayList<Module.Var> vopts  = keepList.apply(all, (m) -> S.varOpts(m));
		
		boolean single = all.size() == 1;
		
		var meta = hashMap(Arr.objects(
				keyword("name"), p.getKey(),
				keyword("rt"), single && S.fnOpts(all.get(0)).rt()));
		
		if(!ropts.isEmpty()) {
			meta = meta.assoc(keyword("reduce"), ropts.get(0));
		}
		if(!vopts.isEmpty()) {
			if(vopts.get(0).macro()) {
				meta = meta.assoc(keyword("macro"), true);
			}
			if(vopts.get(0).dynamic()) {
				meta = meta.assoc(keyword("dynamic"), true);
			}
			if(vopts.get(0).control()) {
				meta = meta.assoc(keyword("control"), true);
			}
		}

		if(vArgs.isEmpty()) {
			var options = groupBy(
					(Function)
					(m) -> ((Method)m).getParameterCount() , all);
			var f = new Var.StaticLookup(meta, options);
			if(ropts.isEmpty()) {
				return f;
			} else {
				return S.reduceFn(f, meta, ropts.get(0));
			}
		} else if(nArgs.isEmpty()) {
			if(vArgs.size() >= 1) {
				return new Var.StaticVargs(meta, vArgs.get(0));
			} else {
				throw new Ex.Runtime("MORE than one VARG " + meta);
			}
		} else {
			throw new Ex.Runtime("TODO: " + meta);
		}
	}
	
	public static Map<Symbol, Var> loadStatic() {

		var raw = new HashMap();
		
		Arr.reduce((map, cls) -> getMethods(cls, map),
				raw,
				Builtin.class.getDeclaredClasses());
		
		Arr.reduce((map, cls) -> getMethods(cls, map),
				raw,
				Macro.class.getDeclaredClasses());
	
		Map<Symbol, Var> fns = mapEntries(
				(Function)(obj) -> {
					var p = (Entry)obj;
					var k = (String)p.getKey();
					var f = createFn(p);
					return pair(
							symbol(k), 
							new Var(k, f).withMeta(((I.ObjType)f).meta()));
				}, raw);
		
		return fns;
	}
	
	/*
	public static void main(String[] args) {
		var map = loadStatic();
		G.prn(map);
	}
	*/
}
