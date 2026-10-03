package dev.noctra.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory copy of every skin and cape published on Noctra. Lookups are plain
 * hash-map reads, so they are safe to call from the render thread and from
 * Minecraft's texture workers without ever touching the network.
 */
public final class SkinDirectory {
	private volatile Map<String, SkinEntry> entries = new ConcurrentHashMap<String, SkinEntry>();
	private volatile String textureBase = "";
	private volatile long epoch = -1;
	private volatile long revision = -1;
	private final CopyOnWriteArrayList<Consumer<SkinEntry>> listeners = new CopyOnWriteArrayList<Consumer<SkinEntry>>();

	public long epoch() {
		return epoch;
	}

	public long revision() {
		return revision;
	}

	/** Base URL every texture hash is appended to ("" until the first sync). */
	public String textureBase() {
		return textureBase;
	}

	/** Every player currently wearing an animated cape. */
	public List<SkinEntry> animated() {
		List<SkinEntry> out = new ArrayList<SkinEntry>();
		for (SkinEntry entry : entries.values()) {
			if (entry.hasAnimatedCape()) {
				out.add(entry);
			}
		}
		return out;
	}

	public int size() {
		return entries.size();
	}

	public void onChange(Consumer<SkinEntry> listener) {
		listeners.add(listener);
	}

	/** Replace everything (first sync, or the server restarted / we fell too far behind). */
	public void replaceAll(long newEpoch, long newRevision, String base, Iterable<SkinEntry> all) {
		Map<String, SkinEntry> next = new ConcurrentHashMap<String, SkinEntry>();
		for (SkinEntry entry : all) {
			if (!entry.isEmpty()) {
				next.put(key(entry.name), entry);
			}
		}
		this.textureBase = base == null ? "" : base;
		this.entries = next;
		this.epoch = newEpoch;
		this.revision = newRevision;
	}

	/** Apply one change. Entries with neither skin nor cape remove the player. */
	public void apply(SkinEntry entry, long newRevision) {
		if (entry == null || entry.name == null) {
			return;
		}
		if (entry.isEmpty()) {
			entries.remove(key(entry.name));
		} else {
			entries.put(key(entry.name), entry);
		}
		if (newRevision > revision) {
			revision = newRevision;
		}
		for (Consumer<SkinEntry> listener : listeners) {
			try {
				listener.accept(entry);
			} catch (RuntimeException ignored) {
				// a listener must never break the sync
			}
		}
	}

	public void setTextureBase(String base) {
		if (base != null && !base.isEmpty()) {
			this.textureBase = base;
		}
	}

	public void setRevision(long newRevision) {
		if (newRevision > revision) {
			revision = newRevision;
		}
	}

	/**
	 * The Noctra wardrobe to show for a player, or null.
	 *
	 * Premium players carry a random (version 4) UUID, so a Noctra profile only
	 * applies to them when its account is linked to exactly that UUID: nobody can
	 * take over a real player's look by registering the same name. Offline
	 * players (version 3 UUIDs, which are derived from the name) match by name.
	 */
	public SkinOverride find(String name, UUID id) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		SkinEntry entry = entries.get(key(name));
		if (entry == null) {
			return null;
		}
		if (id != null && id.version() != 3) {
			String linked = entry.minecraftUuid;
			if (linked == null || !linked.equals(id.toString().replace("-", "").toLowerCase(Locale.ROOT))) {
				return null;
			}
		}
		String base = textureBase;
		if (base.isEmpty()) {
			return null;
		}
		return new SkinOverride(
				entry.skinHash == null ? null : base + entry.skinHash,
				entry.capeHash == null ? null : base + entry.capeHash,
				entry.slim,
				entry.capeStripHash == null ? null : base + entry.capeStripHash,
				entry.capeFrames,
				entry.capeFps);
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
