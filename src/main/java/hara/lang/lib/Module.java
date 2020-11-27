package hara.lang.lib;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

public interface Module {
	
	public enum ReduceType {
		SELF, INIT, ARRAY 
	}
	
	public enum ReduceInit {
		NIL, ZERO, ONE,
		EMPTY_MAP, EMPTY_LIST, EMPTY_VECTOR, EMPTY_QUEUE,
		EMPTY_ARRAY
	}

	@Retention(RUNTIME)
	@Target({METHOD})
	@Documented
	public @interface Var {
	    String name()     default "";
	    boolean macro()   default false;
	    boolean dynamic() default false;
	}

	@Retention(RUNTIME)
	@Target({METHOD})
	@Documented
	public @interface Fn {
	    boolean vargs()   default false; 
	}
	
	@Retention(RUNTIME)
	@Target({TYPE})
	@Documented
	public @interface Ns {
	    String name() default "";
	}
	
	@Retention(RUNTIME)
	@Target({METHOD})
	@Documented
	public @interface Reduce {
	    ReduceType type() default ReduceType.INIT;
	    ReduceInit init() default ReduceInit.NIL;
	}
}
