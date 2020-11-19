package hara.lang.base;


import java.util.concurrent.Callable;

public interface IFn<R, T1, T2, T3> extends Callable<R>, Runnable{

	@Override
	default R call() {
		return invoke();
	}

	@Override
	default void run() {
		invoke();
	}

	default R invoke() {
		return throwArity(0);
	}

	default R invoke(T1 a1) {
		return throwArity(1);
	}

	default R invoke(T1 a1, T2 a2) {
		return throwArity(2);
	}

	default R invoke(T1 a1, T2 a2, T3 a3) {
		return throwArity(3);
	}

	@SuppressWarnings("unchecked")
	default R invoke(T1 a1, T2 a2, T3 a3, Object... args) {
		I.Seq<Object> vargs 
			= (I.Seq<Object>) Arr.toSeq(args)
			.cons((Object)a3)
			.cons((Object)a2)
			.cons((Object)a1);
		return applyTo(vargs);
	}
	
	default R apply(T1 a1, I.Seq<Object> args) {
		return applyTo((I.Seq<Object>) args.cons(a1));
	}

	default R apply(T1 a1, T2 a2, I.Seq<Object> args) {
		return applyTo((I.Seq<Object>) args.cons(a2).cons(a1));
	}

	default R apply(T1 a1, T2 a2, T3 a3, I.Seq<Object> args) {
		return applyTo((I.Seq<Object>) args.cons(a3).cons(a2).cons(a1));
	}
	
	@SuppressWarnings("unchecked")
	default R apply(T1 a1, T2 a2, T3 a3, Object... args) {
		I.Seq<Object> vargs
			= (I.Seq<Object>) Arr.toSeq(args)
			.cons((Object)a3)
			.cons((Object)a2)
			.cons((Object)a1);
		return applyTo(vargs);
	}
	

	default R applyTo(I.Seq<Object> args) {
		return throwArity((int)args.count());
	}

	default R throwArity(int n) {
		String name = getClass().getName();
		throw new Ex.Arity(n, name);
	}

	static public Object ret1(Object ret, Object nil){
		return ret;
	}

	static public <E> I.Seq<E> ret1(I.Seq<E> ret, Object nil){
		return ret;
	}
}
