package hara.lang.task;

@FunctionalInterface
public interface TaskFunction {
  Object apply(Object[] arguments) throws Exception;
}
