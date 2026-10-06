import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

/**
 * Static link check for the per-era cosmetic renderers: every Minecraft class, field and method our classes
 * reference must exist (with the same descriptor, found the way the JVM resolves it) in the target game jar,
 * and every abstract method our renderers inherit must be implemented. Catches NoSuchMethodError,
 * NoSuchFieldError and AbstractMethodError for a version without starting it.
 *
 *   java -cp asm.jar:. Verify <our-classes.jar> <game.jar (intermediary or 26.x official)> [package/prefix/]
 */
public class Verify {
	static final Map<String, ClassNode> game = new HashMap<>();
	static final List<String> errors = new ArrayList<>();

	static boolean ours(String owner) {
		return owner.startsWith("net/minecraft/") || owner.startsWith("com/mojang/blaze3d/") || owner.startsWith("com/mojang/math/");
	}

	static Map<String, ClassNode> read(String jar, boolean shallow) throws IOException {
		Map<String, ClassNode> out = new HashMap<>();
		try (ZipFile z = new ZipFile(jar)) {
			for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
				ZipEntry entry = e.nextElement();
				if (!entry.getName().endsWith(".class")) continue;
				try (InputStream in = z.getInputStream(entry)) {
					ClassNode node = new ClassNode();
					new ClassReader(in).accept(node, shallow ? ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG : 0);
					out.put(node.name, node);
				}
			}
		}
		return out;
	}

	static Class<?> jdk(String name) {
		try {
			return Class.forName(name.replace('/', '.'), false, Verify.class.getClassLoader());
		} catch (Throwable t) {
			return null;
		}
	}

	static boolean classExists(String name) {
		while (name.startsWith("[")) name = name.substring(1);
		if (name.startsWith("L") && name.endsWith(";")) name = name.substring(1, name.length() - 1);
		if (!ours(name)) return true;
		return game.containsKey(name);
	}

	static void checkType(Type t, String where) {
		if (t.getSort() == Type.ARRAY) t = t.getElementType();
		if (t.getSort() == Type.OBJECT && !classExists(t.getInternalName())) errors.add(where + ": missing class " + t.getInternalName());
		if (t.getSort() == Type.METHOD) {
			checkType(t.getReturnType(), where);
			for (Type a : t.getArgumentTypes()) checkType(a, where);
		}
	}

	/** JVM-style resolution: the class, its superclasses, then its superinterfaces. */
	static boolean hasMember(String owner, String name, String desc, boolean field, Set<String> seen) {
		if (!seen.add(owner)) return false;
		ClassNode c = game.get(owner);
		if (c == null) {
			Class<?> k = jdk(owner);
			if (k == null) return ours(owner) ? false : true; // library outside the game jar: trust it
			if (field) {
				for (Class<?> x = k; x != null; x = x.getSuperclass())
					for (java.lang.reflect.Field f : x.getDeclaredFields()) if (f.getName().equals(name) && Type.getDescriptor(f.getType()).equals(desc)) return true;
				return false;
			}
			for (Class<?> x = k; x != null; x = x.getSuperclass()) {
				for (java.lang.reflect.Method m : x.getDeclaredMethods()) if (m.getName().equals(name) && Type.getMethodDescriptor(m).equals(desc)) return true;
				for (java.lang.reflect.Constructor<?> m : x.getDeclaredConstructors()) if (name.equals("<init>") && Type.getConstructorDescriptor(m).equals(desc)) return true;
			}
			for (Class<?> i : k.getInterfaces()) if (hasMember(Type.getInternalName(i), name, desc, field, seen)) return true;
			return false;
		}
		if (field) {
			for (FieldNode f : c.fields) if (f.name.equals(name) && f.desc.equals(desc)) return true;
		} else {
			for (MethodNode m : c.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
			if (name.equals("<init>")) return false;
		}
		if (c.superName != null && hasMember(c.superName, name, desc, field, seen)) return true;
		for (String i : c.interfaces) if (hasMember(i, name, desc, field, seen)) return true;
		return false;
	}

	static void member(String owner, String name, String desc, boolean field, String where) {
		if (owner.startsWith("[")) return;
		if (!ours(owner)) return;
		if (!game.containsKey(owner)) { errors.add(where + ": missing class " + owner); return; }
		if (!hasMember(owner, name, desc, field, new HashSet<>())) errors.add(where + ": missing " + (field ? "field " : "method ") + owner + "." + name + desc);
	}

	static void abstractsImplemented(ClassNode mine, Map<String, ClassNode> local) {
		if ((mine.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE)) != 0) return;
		Set<String> implemented = new HashSet<>();
		Set<String> needed = new LinkedHashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.add(mine.name);
		Set<String> seen = new HashSet<>();
		while (!queue.isEmpty()) {
			String n = queue.poll();
			if (!seen.add(n)) continue;
			ClassNode c = local.containsKey(n) ? local.get(n) : game.get(n);
			if (c == null) continue;
			for (MethodNode m : c.methods) {
				String key = m.name + m.desc;
				if ((m.access & Opcodes.ACC_ABSTRACT) != 0) { if (ours(c.name)) needed.add(c.name + "." + key); }
				else if ((m.access & Opcodes.ACC_STATIC) == 0) implemented.add(key);
			}
			if (c.superName != null) queue.add(c.superName);
			queue.addAll(c.interfaces);
		}
		for (String n : needed) if (!implemented.contains(n.substring(n.indexOf('.') + 1))) errors.add(mine.name + ": does not implement " + n);
	}

	public static void main(String[] args) throws Exception {
		Map<String, ClassNode> mine = read(args[0], false);
		if (args.length > 2) mine.keySet().removeIf(n -> !n.startsWith(args[2]));
		if (mine.isEmpty()) { System.out.println("FAILED no classes under " + (args.length > 2 ? args[2] : "")); System.exit(1); }
		game.putAll(read(args[1], true));
		for (ClassNode c : mine.values()) {
			String w = c.name;
			if (c.superName != null && !classExists(c.superName)) errors.add(w + ": missing superclass " + c.superName);
			for (String i : c.interfaces) if (!classExists(i)) errors.add(w + ": missing interface " + i);
			for (FieldNode f : c.fields) checkType(Type.getType(f.desc), w + "." + f.name);
			for (MethodNode m : c.methods) {
				String mw = w + "." + m.name;
				checkType(Type.getMethodType(m.desc), mw);
				for (AbstractInsnNode insn : m.instructions) {
					if (insn instanceof FieldInsnNode) {
						FieldInsnNode f = (FieldInsnNode) insn;
						member(f.owner, f.name, f.desc, true, mw);
					} else if (insn instanceof MethodInsnNode) {
						MethodInsnNode mi = (MethodInsnNode) insn;
						checkType(Type.getMethodType(mi.desc), mw);
						if (!mine.containsKey(mi.owner)) member(mi.owner, mi.name, mi.desc, false, mw);
					} else if (insn instanceof TypeInsnNode) {
						if (!classExists(((TypeInsnNode) insn).desc)) errors.add(mw + ": missing class " + ((TypeInsnNode) insn).desc);
					} else if (insn instanceof LdcInsnNode && ((LdcInsnNode) insn).cst instanceof Type) {
						checkType((Type) ((LdcInsnNode) insn).cst, mw);
					} else if (insn instanceof InvokeDynamicInsnNode) {
						for (Object a : ((InvokeDynamicInsnNode) insn).bsmArgs) {
							if (a instanceof Handle) {
								Handle h = (Handle) a;
								if (!mine.containsKey(h.getOwner())) member(h.getOwner(), h.getName(), h.getDesc(), h.getTag() <= Opcodes.H_PUTSTATIC, mw);
							} else if (a instanceof Type) checkType((Type) a, mw);
						}
					}
				}
			}
			abstractsImplemented(c, mine);
		}
		Set<String> unique = new LinkedHashSet<>(errors);
		for (String e : unique) System.out.println("ERROR " + e);
		System.out.println(unique.isEmpty() ? "OK " + mine.size() + " classes link against " + args[1] : "FAILED " + unique.size() + " problems");
		System.exit(unique.isEmpty() ? 0 : 1);
	}
}
