package hara.lang.protocol;

public interface IComponentQuery {
  boolean started();
  boolean stopped();
  Object info(Object level);
  boolean remote();
  Object health();
}
