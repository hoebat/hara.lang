package hara.lang.lib;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;

import hara.lang.base.I;
import hara.lang.base.I.Fn;
import hara.lang.lib.Builtin.Basic;

public class Apply {

	public static Object applyIn(I.IApplicable app, I.IContext rt, Object args) {
		Object input = app.transformIn(app, rt, args);
		Object output = app.applyIn(app, rt, input);

		if (output instanceof CompletableFuture) {
			return ((CompletableFuture<Object>) output).thenApply(resolved -> app.transformOut(app, rt, args, resolved));
		} else if (output instanceof Future) {
			return CompletableFuture.supplyAsync(() -> {
				return Basic.deref((Future) output);
			}).thenApply(resolved -> app.transformOut(app, rt, args, resolved));
		} else {
			return app.transformOut(app, rt, args, output);
		}
	}

	public static Object applyAs(I.IApplicable app, Object[] args) {
		Object rt = null;
		if (app instanceof HasRuntime) {
			rt = ((HasRuntime) app).getRuntime();
		}
		if (rt == null) {
			rt = app.applyDefault(app);
		}
		if (rt instanceof Fn) {
			rt = ((Fn) rt).invoke();
		}
		if (rt instanceof I.IContext) {
			return applyIn(app, (I.IContext) rt, args);
		} else {
			throw new RuntimeException("Runtime is not an instance of I.IContext");
		}
	}

	public static Object invokeAs(I.IApplicable app, Object... args) {
		return applyAs(app, args);
	}
}
