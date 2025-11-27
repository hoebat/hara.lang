package hara.lang.base;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class Graph {

    public static long sizeOf(Object obj) {
        return sizeOf(obj, Collections.emptySet());
    }

    public static long sizeOf(Object obj, Set<String> excludedFields) {
        if (obj == null) return 0;

        IdentityHashMap<Object, Boolean> visited = new IdentityHashMap<>();
        Stack<Object> stack = new Stack<>();
        stack.push(obj);

        long size = 0;

        while (!stack.isEmpty()) {
            Object current = stack.pop();

            if (current == null || visited.containsKey(current)) {
                continue;
            }

            visited.put(current, true);

            // Special handling for Strings
            if (current instanceof String) {
                size += sizeOfString((String) current);
                continue;
            }

            if (current instanceof Class) {
		continue;
            }

            // Special handling for Collections (Iterable)
            if (current instanceof Iterable) {
                size += shallowSizeOf(current);
                for (Object element : (Iterable<?>) current) {
                    if (element != null && !visited.containsKey(element)) {
                        stack.push(element);
                    }
                }
            } else if (current instanceof Map) {
                size += shallowSizeOf(current);
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) current).entrySet()) {
                    Object key = entry.getKey();
                    Object val = entry.getValue();
                    if (key != null && !visited.containsKey(key)) stack.push(key);
                    if (val != null && !visited.containsKey(val)) stack.push(val);
                }
            }

            Class<?> clazz = current.getClass();
            if (clazz.isArray()) {
                size += shallowSizeOf(current);
                if (!clazz.getComponentType().isPrimitive()) {
                    int length = Array.getLength(current);
                    for (int i = 0; i < length; i++) {
                        Object element = Array.get(current, i);
                        if (element != null && !visited.containsKey(element)) {
                            stack.push(element);
                        }
                    }
                }
            } else {
                if (!(current instanceof Iterable) && !(current instanceof Map)) {
                     size += shallowSizeOf(current);
                }

                while (clazz != null) {
			if (clazz == Object.class) {
				clazz = null;
				continue;
			}

                    Field[] fields = clazz.getDeclaredFields();
                    for (Field field : fields) {
                        if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                            continue;
                        }

                        // Check if field is excluded
                        // Only exclude fields on the *original* object passed, or globally?
                        // If we want to exclude `_root` on `RT.Instance`, we should check if `current` is the object having that field.
                        // But here we pass a Set<String>. If `excludedFields` contains the field name, we skip it?
                        // This might be too broad if other classes have same field name.
                        // But for `_root` and `_loader` it's likely fine as they are internal names.
                        // To be precise, we could pass Map<Class, Set<String>>.
                        // For simplicity, we check field name.

                        if (excludedFields.contains(field.getName())) {
                            // Verify it's the specific case if needed, but for now simple name check.
				// Actually, if we exclude `_root` everywhere, we might miss data.
				// But `_root` is typically a back-reference or root context.
				continue;
                        }

                        try {
				field.setAccessible(true);
                            Object value = field.get(current);
                            if (value != null && !visited.containsKey(value)) {
                                stack.push(value);
                            }
                        } catch (Throwable e) {
                           // Ignore inaccessible fields
                        }
                    }
                    clazz = clazz.getSuperclass();
                }
            }
        }
        return size;
    }

    private static long sizeOfString(String s) {
	// Approximation for Java 9+ Compact Strings
	long strObj = 24;
	long arraySize = 16 + s.length();
	return strObj + align(arraySize);
    }

    private static long shallowSizeOf(Object obj) {
        if (obj == null) return 0;
        Class<?> clazz = obj.getClass();
        if (clazz.isArray()) {
            long header = 16;
            long elementSize;
            Class<?> componentType = clazz.getComponentType();
            if (componentType == boolean.class || componentType == byte.class) elementSize = 1;
            else if (componentType == char.class || componentType == short.class) elementSize = 2;
            else if (componentType == int.class || componentType == float.class) elementSize = 4;
            else if (componentType == long.class || componentType == double.class) elementSize = 8;
            else elementSize = 4;

            return align(header + Array.getLength(obj) * elementSize);
        } else {
            long size = 12; // Header
            while (clazz != null) {
                for (Field field : clazz.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    Class<?> type = field.getType();
                    if (type == boolean.class || type == byte.class) size += 1;
                    else if (type == char.class || type == short.class) size += 2;
                    else if (type == int.class || type == float.class) size += 4;
                    else if (type == long.class || type == double.class) size += 8;
                    else size += 4; // Reference
                }
                clazz = clazz.getSuperclass();
            }
            return align(size);
        }
    }

    private static long align(long size) {
        return (size + 7) & ~7;
    }
}
