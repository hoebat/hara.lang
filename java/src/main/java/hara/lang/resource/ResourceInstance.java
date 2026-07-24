package hara.lang.resource;

import hara.lang.protocol.ComponentSupport;
import hara.lang.protocol.IComponent;
import hara.lang.protocol.IComponentOptions;
import hara.lang.protocol.IComponentProps;
import hara.lang.protocol.IComponentQuery;
import hara.lang.protocol.IComponentTrack;
import java.util.Map;

public final class ResourceInstance implements IComponentQuery, IComponentProps, IComponentOptions, IComponentTrack {
  private final String type;
  private final String variant;
  private final Object key;
  private final Object value;
  private final Map<String, Object> config;

  ResourceInstance(String type, String variant, Object key, Object value, Map<String, Object> config) {
    this.type = type; this.variant = variant; this.key = key; this.value = value;
    this.config = config == null ? Map.of() : Map.copyOf(config);
  }

  public String type() { return type; }
  public String variant() { return variant; }
  public Object key() { return key; }
  public Object value() { return value; }
  public Map<String, Object> config() { return config; }

  @Override public boolean started() { return ComponentSupport.started(value); }
  @Override public boolean stopped() { return ComponentSupport.stopped(value); }
  @Override public Object info(Object level) { return ComponentSupport.info(value, level); }
  @Override public boolean remote() { return ComponentSupport.remote(value); }
  @Override public Object health() { return ComponentSupport.health(value); }
  @Override public Object props() { return ComponentSupport.props(value); }
  @Override public Object options() { return ComponentSupport.options(value); }
  @Override public Object trackPath() { return ComponentSupport.trackPath(value); }
  @Override public String toString() { return "#resource[" + type + " " + variant + " " + key + "]"; }
}
