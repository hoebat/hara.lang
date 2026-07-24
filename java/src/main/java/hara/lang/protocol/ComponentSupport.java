package hara.lang.protocol;

import java.util.Collections;

/** Default component queries for values that do not implement a component facet. */
public final class ComponentSupport {
  private ComponentSupport() {}

  public static boolean started(Object value) {
    return value instanceof IComponentQuery ? ((IComponentQuery) value).started()
        : value == null || !(value instanceof IComponent) || ((IComponent) value).isStarted();
  }

  public static boolean stopped(Object value) {
    return value instanceof IComponentQuery ? ((IComponentQuery) value).stopped()
        : value instanceof IComponent && ((IComponent) value).isStopped();
  }

  public static Object info(Object value, Object level) {
    if (value instanceof IComponentQuery) return ((IComponentQuery) value).info(level);
    return Collections.emptyMap();
  }

  public static boolean remote(Object value) {
    return value instanceof IComponentQuery ? ((IComponentQuery) value).remote()
        : value instanceof IComponent && ((IComponent) value).isRemote();
  }

  public static Object health(Object value) {
    if (value instanceof IComponentQuery) return ((IComponentQuery) value).health();
    return Collections.singletonMap("status", "ok");
  }

  public static Object props(Object value) {
    return value instanceof IComponentProps ? ((IComponentProps) value).props() : Collections.emptyMap();
  }

  public static Object options(Object value) {
    return value instanceof IComponentOptions ? ((IComponentOptions) value).options() : Collections.emptyMap();
  }

  public static Object trackPath(Object value) {
    return value instanceof IComponentTrack ? ((IComponentTrack) value).trackPath() : null;
  }
}
