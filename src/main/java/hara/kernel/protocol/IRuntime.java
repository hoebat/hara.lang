package hara.kernel.protocol;

import java.net.URL;
import java.util.Map.Entry;
import hara.lang.base.I;

@SuppressWarnings("rawtypes")
public interface IRuntime<AST, K, V> extends I.Context {
	I.Fn         findFn(Class cls, String name);
	I.Fn         findFn(Class cls, String name, int args);
	Object       eval(AST ast);
	Object       eval(AST ast, IEnv env);
	IEnv<K, V>    getEnv();
	V            getObj(K key);
	V            setObj(K key, V value);
	AST          readString(String input);
	Class        classFor(String name);
	ClassLoader  classLoader();
	I.Coll<Entry<String, Class>> classCache();
	I.Context      getRoot();
	I.Coll<URL>    pathCache();
	I.Coll<URL>    pathAdd(URL url);
	I.Coll<URL>    pathAdd(String[] paths);
	I.Coll<URL>    pathRemove(String[] paths);
	Class        aliasAdd(K key, Class v);
	Class        aliasRemove(K key);
	I.Coll<Entry<String, Class>>  aliasCache();
}
