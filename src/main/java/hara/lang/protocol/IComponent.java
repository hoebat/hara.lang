package hara.lang.protocol;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Map;
import hara.lang.base.*;
import java.util.function.*;
import java.util.regex.Pattern;

public interface IComponent {

	IMetadata getProps();

	IMetadata getStatus();

	boolean isStarted();

	boolean isStopped();

	IComponent start();

	IComponent stop();

	default IComponent kill() {
	  return this.stop();
	}

	default boolean isRemote() {
	  return false;
	}
}