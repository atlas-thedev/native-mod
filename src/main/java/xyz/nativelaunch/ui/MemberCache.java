package xyz.nativelaunch.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-class lookup tables for the reflective Minecraft bridges. They read the game every frame, and keying one map
 * with {@code owner.getName() + "#" + name} built a new String for each read. Here the class picks the table
 * ({@link ClassValue}) and the member name (a literal, whose hash is already known) picks the entry: no allocation.
 * Used from the render thread only, like the single map it replaces.
 */
public final class MemberCache {
	/** Marks a member that was looked for and does not exist. */
	public static final Object MISSING = new Object();
	private static final int MAX_PARAMS = 8;

	private static final class Table {
		final Map<String, Object> fields = new HashMap<String, Object>();
		@SuppressWarnings("unchecked")
		final Map<String, Object>[] methods = new Map[MAX_PARAMS + 1];
		@SuppressWarnings("unchecked")
		final Map<String, Object>[] named = new Map[MAX_PARAMS + 1];
	}

	private final ClassValue<Table> tables = new ClassValue<Table>() {
		@Override
		protected Table computeValue(Class<?> type) {
			return new Table();
		}
	};

	/** Fields of {@code owner} by name. */
	public Map<String, Object> fields(Class<?> owner) {
		return tables.get(owner).fields;
	}

	/** Methods of {@code owner} found with an exact parameter list, by name (one table per parameter count). */
	public Map<String, Object> methods(Class<?> owner, int params) {
		return slot(tables.get(owner).methods, params);
	}

	/** Methods of {@code owner} found by name and parameter count only, by name. */
	public Map<String, Object> named(Class<?> owner, int params) {
		return slot(tables.get(owner).named, params);
	}

	private static Map<String, Object> slot(Map<String, Object>[] by, int params) {
		int i = Math.min(params, MAX_PARAMS);
		Map<String, Object> map = by[i];
		if (map == null) {
			map = new HashMap<String, Object>();
			by[i] = map;
		}
		return map;
	}
}
