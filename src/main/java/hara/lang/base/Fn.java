package hara.lang.base;

import hara.lang.data.List;
import hara.lang.data.Tuple;
import hara.lang.protocol.IFn;
import hara.lang.protocol.IMetadata;
import hara.lang.protocol.IOFn;
import hara.lang.base.fn.*;

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
    return new Fn2(meta, f);
  }

  public static IFn toFn(IMetadata meta, BiPredicate p) {
    return new Pred2(meta, p);
  }

  public static IFn toFn(IMetadata meta, Consumer c) {
    return new Consume1(meta, c);
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
    return new Fn1(meta, f);
  }

  public static IFn toFn(IMetadata meta, Predicate p) {
    return new Pred1(meta, p);
  }

  public static IFn toFn(IMetadata meta, Supplier f) {
    return new Fn0(meta, f);
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
    return new FnVargs(meta, f);
  }

  public static <FN> IFn toFnVargs(Function f) {
    return new FnVargs(null, f);
  }
}
