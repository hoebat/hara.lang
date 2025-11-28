package hara.lang.base;

import hara.lang.data.List;
import hara.lang.data.Tuple;
import hara.lang.protocol.IFn;
import hara.lang.protocol.IMetadata;
import hara.lang.protocol.IOFn;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.*;

@SuppressWarnings({"rawtypes", "unchecked"})
public interface Fn {

  public interface Reflect {

    public class InstanceField<R> extends Obj.FN implements IFn<R, Object, Object> {
      final Field _mh;

      InstanceField(Field mh) {
        this(null, mh);
      }

      InstanceField(IMetadata meta, Field mh) {
        super(meta);
        _mh = mh;
      }

      @Override
      public Function<Object, R> getArg1() {
        return (o) -> {
          try {
            return (R) _mh.get(o);
          } catch (IllegalArgumentException | IllegalAccessException t) {
            throw Ex.Sneaky(t);
          }
        };
      }

      @Override
      public BiFunction<Object, Object, R> getArg2() {
        return (o, arg) -> {
          try {
            _mh.set(o, arg);
          } catch (IllegalArgumentException | IllegalAccessException t) {
            throw Ex.Sneaky(t);
          }
          return (R) arg;
        };
      }
    }

    public class InstanceMethod<R> extends Obj.FN implements IFn<R, Object, Object> {
      final Method _mh;

      InstanceMethod(IMetadata meta, Method mh) {
        super(meta);
        _mh = mh;
      }

      InstanceMethod(Method mh) {
        this(null, mh);
      }

      public void checkArgs(int size) {
        if (size != _mh.getParameterCount()) {
          throw new Ex.Arity(size, "Only " + _mh.getParameterCount() + " Args supported");
        }
      }

      @Override
      public Function<Object, R> getArg1() {
        checkArgs(1);
        return (arg) -> invokeHandle(arg, Arrays.asList());
      }

      @Override
      public BiFunction<Object, Object, R> getArg2() {
        checkArgs(2);
        return (a1, a2) -> invokeHandle(a1, Arrays.asList(a2));
      }

      @Override
      public Function<Object, R> getArgN() {
        return (vargs) -> {
          Iterator it = It.iter(vargs);
          var o = it.next();
          java.util.List args = It.toArrayList(it);
          checkArgs(args.size());
          return invokeHandle(o, args);
        };
      }

      public R invokeHandle(Object o, java.util.List args) {
        try {
          return (R) _mh.invoke(o, args);
        } catch (Throwable t) {
          throw Ex.Sneaky(t);
        }
      }
    }

    public class StaticConstructor<R> extends Obj.FN implements IFn<R, Object, Object> {
      final Constructor _mh;

      StaticConstructor(Constructor mh) {
        this(null, mh);
      }

      StaticConstructor(IMetadata meta, Constructor mh) {
        super(meta);
        _mh = mh;
      }

      public void checkArgs(int size) {
        if (size != _mh.getParameterCount()) {
          throw new Ex.Arity(size, "Only " + _mh.getParameterCount() + " Args supported");
        }
      }

      @Override
      public Supplier<R> getArg0() {
        checkArgs(0);
        return () -> invokeHandle(new LinkedList());
      }

      @Override
      public Function<Object, R> getArg1() {
        checkArgs(1);
        return (arg) -> invokeHandle(Arrays.asList(arg));
      }

      @Override
      public BiFunction<Object, Object, R> getArg2() {
        checkArgs(2);
        return (a1, a2) -> invokeHandle(Arrays.asList(a1, a2));
      }

      @Override
      public Function<Object, R> getArgN() {
        return (vargs) -> {
          java.util.List args = It.toArrayList(It.iter(vargs));
          checkArgs(args.size());
          return invokeHandle(args);
        };
      }

      public R invokeHandle(java.util.List args) {
        try {
          return (R) _mh.newInstance(args.toArray());
        } catch (Throwable t) {
          throw Ex.Sneaky(t);
        }
      }
    }

    public class StaticField<R> extends Obj.FN implements IFn<R, Object, Object> {
      final Field _mh;

      StaticField(Field mh) {
        this(null, mh);
      }

      StaticField(IMetadata meta, Field mh) {
        super(meta);
        _mh = mh;
      }

      @Override
      public Supplier<R> getArg0() {
        return () -> {
          try {
            return (R) _mh.get(null);
          } catch (IllegalArgumentException | IllegalAccessException t) {
            throw Ex.Sneaky(t);
          }
        };
      }

      @Override
      public Function<Object, R> getArg1() {
        return (arg) -> {
          try {
            _mh.set(null, arg);
          } catch (IllegalArgumentException | IllegalAccessException t) {
            throw Ex.Sneaky(t);
          }
          return (R) arg;
        };
      }
    }

    public class StaticMethod<R> extends Obj.FN implements IFn<R, Object, Object> {
      final Method _mh;

      StaticMethod(IMetadata meta, Method mh) {
        super(meta);
        _mh = mh;
      }

      StaticMethod(Method mh) {
        this(null, mh);
      }

      public void checkArgs(int size) {
        if (size != _mh.getParameterCount()) {
          throw new Ex.Arity(size, "Only " + _mh.getParameterCount() + " Args supported");
        }
      }

      @Override
      public Supplier<R> getArg0() {
        checkArgs(0);
        return () -> invokeHandle(new LinkedList());
      }

      @Override
      public Function<Object, R> getArg1() {
        checkArgs(1);
        return (arg) -> invokeHandle(Arrays.asList(arg));
      }

      @Override
      public BiFunction<Object, Object, R> getArg2() {
        checkArgs(2);
        return (a1, a2) -> invokeHandle(Arrays.asList(a1, a2));
      }

      @Override
      public Function<Object, R> getArgN() {
        return (vargs) -> {
          java.util.List args = It.toArrayList(It.iter(vargs));
          checkArgs(args.size());
          return invokeHandle(args);
        };
      }

      public R invokeHandle(java.util.List args) {
        try {
          return (R) _mh.invoke(null, args);
        } catch (Throwable t) {
          throw Ex.Sneaky(t);
        }
      }
    }
  }

  public interface T {

    public class Comp<FN, ITR> implements IOFn {

      final IFn[] _fns;

      public Comp(ITR fns) {
        _fns = (IFn[]) It.toArray(It.map(It.iter(fns), Fn::toFn), IFn.class);
      }

      @Override
      public Function getArg1() {
        return (x) -> It.reduce(Arr.toRevIter(_fns), x, (acc, f) -> ((IFn) f).invoke(acc));
      }
    }

    public class Consume1<T1> extends Obj.FN implements IFn<Void, T1, Object> {

      final Consumer<T1> _c1;

      Consume1(Consumer<T1> p1) {
        this(null, p1);
      }

      Consume1(IMetadata meta, Consumer<T1> c1) {
        super(meta);
        _c1 = c1;
      }

      @Override
      public Function<T1, Void> getArg1() {
        return (e) -> {
          _c1.accept(e);
          return null;
        };
      }
    }

    public class Fn0<R> extends Obj.FN implements IFn<R, Object, Object> {

      final Supplier<R> _f0;

      Fn0(IMetadata meta, Supplier<R> f0) {
        super(meta);
        _f0 = f0;
      }

      Fn0(Supplier<R> f0) {
        this(null, f0);
      }

      @Override
      public Supplier<R> getArg0() {
        return _f0;
      }
    }

    public class Fn1<R, T1> extends Obj.FN implements IFn<R, T1, Object> {

      final Function<T1, R> _f1;

      Fn1(Function<T1, R> f1) {
        this(null, f1);
      }

      Fn1(IMetadata meta, Function<T1, R> f1) {
        super(meta);
        _f1 = f1;
      }

      @Override
      public Function<T1, R> getArg1() {
        return _f1;
      }
    }

    public class Fn2<R, T1, T2> extends Obj.FN implements IFn<R, T1, T2> {

      final BiFunction<T1, T2, R> _f2;

      Fn2(BiFunction<T1, T2, R> f2) {
        this(null, f2);
      }

      Fn2(IMetadata meta, BiFunction<T1, T2, R> f2) {
        super(meta);
        _f2 = f2;
      }

      @Override
      public BiFunction<T1, T2, R> getArg2() {
        return _f2;
      }
    }

    public class FnHandle<R> extends Obj.FN implements IFn<R, Object, Object> {
      final MethodHandle _mh;
      final int _num;

      FnHandle(IMetadata meta, MethodHandle mh, int num) {
        super(meta);
        _mh = mh;
        _num = num;
      }

      FnHandle(MethodHandle mh, int num) {
        this(null, mh, num);
      }

      public void checkArgs(int size) {
        if (size != _num) {
          throw new Ex.Arity(size, "Only " + _num + " Args supported");
        }
      }

      @Override
      public Supplier<R> getArg0() {
        checkArgs(0);
        return () -> invokeHandle(new LinkedList());
      }

      @Override
      public Function<Object, R> getArg1() {
        checkArgs(1);
        return (arg) -> invokeHandle(Arrays.asList(arg));
      }

      @Override
      public BiFunction<Object, Object, R> getArg2() {
        checkArgs(2);
        return (a1, a2) -> invokeHandle(Arrays.asList(a1, a2));
      }

      @Override
      public Function<Object, R> getArgN() {
        return (vargs) -> {
          java.util.List args = It.toArrayList(It.iter(vargs));
          checkArgs(args.size());
          return invokeHandle(args);
        };
      }

      public R invokeHandle(java.util.List args) {
        try {
          return (R) _mh.invokeWithArguments(args);
        } catch (Throwable t) {
          throw Ex.Sneaky(t);
        }
      }
    }

    public class FnVargs<R, ITR> extends Obj.FN implements IFn<R, Object, Object> {

      final Function<ITR, R> _f;

      FnVargs(Function<ITR, R> f) {
        this(null, f);
      }

      FnVargs(IMetadata meta, Function<ITR, R> f) {
        super(meta);
        _f = f;
      }

      @Override
      public Supplier<R> getArg0() {
        return () -> _f.apply((ITR) new Object[] {});
      }

      @Override
      public Function<Object, R> getArg1() {
        return (arg) -> _f.apply((ITR) new Object[] {arg});
      }

      @Override
      public BiFunction<Object, Object, R> getArg2() {
        return (a0, a1) -> _f.apply((ITR) new Object[] {a0, a1});
      }

      @Override
      public Function<Object, R> getArgN() {
        return (Function<Object, R>) _f;
      }
    }

    public class Partial<FN, ITR> implements IOFn {

      final List.Standard _args;
      final IFn _f;

      public Partial(FN f, ITR vars) {
        _f = Fn.toFn(f);
        _args = List.Standard.into(It.iter(vars));
      }

      @Override
      public Supplier getArg0() {
        return () -> _f.apply(_args);
      }

      @Override
      public Function getArg1() {
        return (x) -> _f.apply(_args.conj(x));
      }

      @Override
      public BiFunction getArg2() {
        return (x, y) -> _f.apply(_args.conj(x).conj(y));
      }

      @Override
      public Function getArgN() {
        return (args) -> _f.apply(It.concat(It.iter(_args), It.iter(args)));
      }
    }

    public class Pred1<T1> extends Obj.FN implements IFn<Boolean, T1, Object> {

      final Predicate<T1> _p1;

      Pred1(IMetadata meta, Predicate<T1> p1) {
        super(meta);
        _p1 = p1;
      }

      Pred1(Predicate<T1> p1) {
        this(null, p1);
      }

      @Override
      public Function<T1, Boolean> getArg1() {
        return (e) -> _p1.test(e);
      }
    }

    public class Pred2<T1, T2> extends Obj.FN implements IFn<Boolean, T1, T2> {

      final BiPredicate<T1, T2> _p2;

      Pred2(BiPredicate<T1, T2> p2) {
        this(null, p2);
      }

      Pred2(IMetadata meta, BiPredicate<T1, T2> p2) {
        super(meta);
        _p2 = p2;
      }

      @Override
      public BiFunction<T1, T2, Boolean> getArg2() {
        return (o, e) -> _p2.test(o, e);
      }
    }

    public class ReduceArray<E, FN> extends Obj.FN implements IFn<E, E, E> {
      final BiFunction<E, E, E> _f2;
      final E _init;

      public ReduceArray(E init, FN f) {
        this(null, init, f);
      }

      public ReduceArray(IMetadata meta, E init, FN f) {
        super(meta);
        _init = init;
        _f2 = Fn.toFn(f).getArg2();
      }

      @Override
      public Supplier<E> getArg0() {
        return () -> _init;
      }

      @Override
      public Function<E, E> getArg1() {
        return (e) -> _f2.apply(_init, e);
      }

      @Override
      public BiFunction<E, E, E> getArg2() {
        return (e0, e1) -> _f2.apply(e0, e1);
      }

      @Override
      public Function getArgN() {
        return (es) -> It.reduce(It.iter(es), _f2);
      }
    }

    public class ReduceCompare<E, FN> extends Obj.FN implements IFn<Boolean, E, E> {
      final BiFunction<E, E, Boolean> _c2;
      final boolean _def;

      public ReduceCompare(boolean def, FN f) {
        this(null, def, f);
      }

      public ReduceCompare(IMetadata meta, boolean def, FN f) {
        super(meta);
        _def = def;
        _c2 = Fn.toFn(f).getArg2();
      }

      @Override
      public BiFunction<E, E, Boolean> getArg2() {
        return (e0, e1) -> _c2.apply(e0, e1);
      }

      @Override
      public Function getArgN() {
        return (es) -> {
          Iterator<E> it = It.iter(es);
          Tuple.Tup2.L<Boolean, E> init = new Tuple.Tup2.L(null, _def, it.next());
          return It.reduce(
                  it,
                  init,
                  (p, e) -> new Tuple.Tup2.L(null, _c2.apply(p.B(), e), e),
                  (p) -> p.A() != _def)
              .A();
        };
      }
    }

    public class ReduceInit<E, FN> extends Obj.FN implements IFn<E, E, E> {
      final BiFunction<E, E, E> _f2;
      final E _init;

      public ReduceInit(E init, FN f) {
        this(null, init, f);
      }

      public ReduceInit(IMetadata meta, E init, FN f) {
        super(meta);
        _init = init;
        _f2 = Fn.toFn(f).getArg2();
      }

      @Override
      public Supplier<E> getArg0() {
        return () -> _init;
      }

      @Override
      public Function<E, E> getArg1() {
        return (e) -> _f2.apply(_init, e);
      }

      @Override
      public BiFunction<E, E, E> getArg2() {
        return (e0, e1) -> _f2.apply(_f2.apply(_init, e0), e1);
      }

      @Override
      public Function getArgN() {
        return (es) -> It.reduce(It.iter(es), _init, _f2);
      }
    }

    public class ReduceSelf<R, E, FN> extends Obj.FN implements IFn<R, R, E> {
      final BiFunction<R, E, R> _f2;
      final R _init;

      public ReduceSelf(IMetadata meta, R init, FN f) {
        super(meta);
        _init = init;
        _f2 = Fn.toFn(f).getArg2();
      }

      public ReduceSelf(R init, FN f) {
        this(null, init, f);
      }

      @Override
      public Supplier<R> getArg0() {
        return () -> _init;
      }

      @Override
      public Function<R, R> getArg1() {
        return (e) -> e;
      }

      @Override
      public BiFunction<R, E, R> getArg2() {
        return (e0, e1) -> _f2.apply(e0, e1);
      }

      @Override
      public Function getArgN() {
        return (es) -> It.reduceIn(It.iter(es), _init, _f2);
      }
    }
  }

  public enum UnitType {
    COMPLEX,
    FILTER,
    MAP,
    MAPCAT
  }

  public static <FN> IFn toFn(FN f) {
    return toFn(null, f);
  }

  public static IFn toFn(IMetadata meta, BiFunction f) {
    return new T.Fn2(meta, f);
  }

  public static IFn toFn(IMetadata meta, BiPredicate p) {
    return new T.Pred2(meta, p);
  }

  public static IFn toFn(IMetadata meta, Consumer c) {
    return new T.Consume1(meta, c);
  }

  public static <FN> IFn toFn(IMetadata meta, FN f) {
    if (f instanceof IFn) {
      return (IFn) f;
    } else if (f instanceof Supplier) {
      return toFn(meta, (Supplier) f);
    } else if (f instanceof Function) {
      return toFn(meta, (Function) f);
    } else if (f instanceof Consumer) {
      return toFn(meta, (Consumer) f);
    } else if (f instanceof Predicate) {
      return toFn(meta, (Predicate) f);
    } else if (f instanceof BiFunction) {
      return toFn(meta, (BiFunction) f);
    } else if (f instanceof BiPredicate) {
      return toFn(meta, (BiPredicate) f);
    } else if (f instanceof MethodHandle) {
      return toFn(meta, (MethodHandle) f);
    } else if (f instanceof Method) {
      return toFn(meta, (Method) f);
    } else {
      throw new Ex.Unsupported();
    }
  }

  public static IFn toFn(IMetadata meta, Function f) {
    return new T.Fn1(meta, f);
  }

  public static IFn toFn(IMetadata meta, Predicate p) {
    return new T.Pred1(meta, p);
  }

  public static IFn toFn(IMetadata meta, Supplier f) {
    return new T.Fn0(meta, f);
  }

  public static IFn toFn(IMetadata meta, Method f) {
    if (Modifier.isStatic(f.getModifiers())) {
      return new Reflect.StaticMethod(meta, f);
    } else {
      return new Reflect.InstanceMethod(meta, f);
    }
  }

  public static IFn toFn(IMetadata meta, Field f) {
    if (Modifier.isStatic(f.getModifiers())) {
      return new Reflect.StaticField(meta, f);
    } else {
      return new Reflect.InstanceField(meta, f);
    }
  }

  public static IFn toFn(IMetadata meta, Constructor f) {
    return new Reflect.StaticConstructor(meta, f);
  }

  public static <FN> IFn toFnVargs(IMetadata meta, Function f) {
    return new T.FnVargs(meta, f);
  }

  public static <FN> IFn toFnVargs(Function f) {
    return new T.FnVargs(null, f);
  }
}
