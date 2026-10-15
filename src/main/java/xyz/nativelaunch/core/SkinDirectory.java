package xyz.nativelaunch.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-memory copy of every skin and cape published on Native. Lookups are plain
 * hash-map reads, so they are safe to call from the render thread and from
 * Minecraft's texture workers without ever touching the network.
 */
public final class SkinDirectory {
	private volatile Map<String, SkinEntry> entries = new ConcurrentHashMap<String, SkinEntry>();
	private volatile String textureBase = "";
	private volatile long epoch = -1;
	private volatile long revision = -1;
	/** The offline account's own look (launcher.json), shown on this PC only. */
	private volatile SkinEntry local;
	/**
	 * The name of the signed-in Native account (null when guest or not yet checked). A player with that name is
	 * this player, so their look is never dropped because a server gave them an unusual UUID.
	 */
	volatile java.util.function.Supplier<String> selfName = new java.util.function.Supplier<String>() {
		@Override
		public String get() {
			AccountInfo account = Session.signedIn() ? Session.account() : null;
			return account == null ? null : account.name;
		}
	};
	/** Reasons already logged, so a skipped skin is reported once and not on every frame. */
	private final java.util.Set<String> reported = ConcurrentHashMap.newKeySet();
	private final CopyOnWriteArrayList<Consumer<SkinEntry>> listeners = new CopyOnWriteArrayList<Consumer<SkinEntry>>();
	private final CopyOnWriteArrayList<Runnable> resetListeners = new CopyOnWriteArrayList<Runnable>();

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
			// the animated Store cape a same-name premium player wears on the premium session
			if (entry.premiumStripHash != null) {
				out.add(new SkinEntry(entry.name, false, null, entry.premiumCapeHash, entry.minecraftUuid, entry.revision,
						entry.premiumStripHash, entry.premiumFrames, entry.premiumFps));
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

	/** Called after the whole directory was replaced (first sync, resync): every player may have changed. */
	public void onReset(Runnable listener) {
		resetListeners.add(listener);
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
		for (SkinEntry entry : next.values()) {
			warm(entry);
		}
		this.epoch = newEpoch;
		this.revision = newRevision;
		for (Runnable listener : resetListeners) {
			try {
				listener.run();
			} catch (RuntimeException ignored) {
				// a listener must never break the sync
			}
		}
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
			warm(entry);
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

	/** Starts loading a player's cosmetics in the background (shared launcher cache first). */
	private void warm(SkinEntry entry) {
		try {
			if (entry != null && entry.cosmetics != null && !entry.cosmetics.isEmpty()) {
				xyz.nativelaunch.cosmetic.CosmeticLibrary.preload(entry.cosmetics, textureBase);
			}
		} catch (Throwable ignored) {
			// warming is best-effort and must never break the sync
		}
	}

	/** Sets the offline player's local-only look (null clears it). */
	public void setLocal(SkinEntry entry) {
		this.local = entry != null && !entry.isEmpty() ? entry : null;
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
	 * The Native wardrobe to show for a player, or null.
	 *
	 * Premium players carry a random (version 4) UUID, so a Native profile only
	 * applies to them when its account is linked to exactly that UUID: nobody can
	 * take over a real player's look by registering the same name. Offline
	 * players (version 3 UUIDs, which are derived from the name) match by name.
	 */
	public SkinOverride find(String name, UUID id) {
		return find(name, id, false);
	}

	/**
	 * @param premiumSession true when the game already holds Mojang-signed textures for this player (a real
	 *                       premium session). When the Native name is also the linked premium name, such a
	 *                       player keeps the Mojang skin and only wears the cape picked for the premium name.
	 */
	public SkinOverride find(String name, UUID id, boolean premiumSession) {
		if (name == null || name.isEmpty()) {
			return null;
		}
		// Offline account: its own skin, for that offline name only (never a premium player).
		SkinEntry mine = local;
		if (mine != null && key(mine.name).equals(key(name)) && !premiumSession && (id == null || id.version() == 3)) {
			String base = textureBase.isEmpty() ? NativeState.DEFAULT_API + "/csl/textures/" : textureBase;
			// wings / backpacks worn on this name (from the launcher's look or the directory) replace the cape here too
			boolean hide = mine.hidesCape();
			for (xyz.nativelaunch.cosmetic.CosmeticRef ref : cosmetics(name, id)) {
				hide |= ref.isBackItem();
			}
			return new SkinOverride(
					mine.skinHash == null ? null : base + mine.skinHash,
					mine.capeHash == null ? null : base + mine.capeHash,
					mine.slim, null, 0, 0, hide);
		}
		SkinEntry entry = entries.get(key(name));
		boolean self = isSelf(name);
		if (entry == null) {
			if (self && !textureBase.isEmpty()) {
				report(name, "none", "Native has no published skin for " + name + " yet (set one in the launcher's Locker), so the game shows its default skin.");
			}
			return null;
		}
		if (id != null && id.version() != 3 && !self) {
			String linked = entry.minecraftUuid;
			if (linked == null || !linked.equals(id.toString().replace("-", "").toLowerCase(Locale.ROOT))) {
				return null;
			}
		}
		String base = textureBase;
		if (base.isEmpty()) {
			return null;
		}
		if (self && id != null && id.version() != 3) {
			report(name, id.toString(), "Showing the Native skin for your own account " + name + " although the server gave it UUID " + id + " (not a name-based UUID).");
		}
		boolean hideCape = entry.hidesCape();
		if (premiumSession && entry.hasPremium && id != null && id.version() != 3) {
			if (hideCape) {
				// keep the Mojang skin, drop every cape (Native's and Mojang's) under the back item
				return new SkinOverride(null, null, false, null, 0, 0, true);
			}
			if (entry.premiumCapeHash == null) {
				return null;
			}
			return new SkinOverride(null, base + entry.premiumCapeHash, false,
					entry.premiumStripHash == null ? null : base + entry.premiumStripHash,
					entry.premiumFrames, entry.premiumFps);
		}
		return new SkinOverride(
				entry.skinHash == null ? null : base + entry.skinHash,
				entry.capeHash == null ? null : base + entry.capeHash,
				entry.slim,
				entry.capeStripHash == null ? null : base + entry.capeStripHash,
				entry.capeFrames,
				entry.capeFps,
				hideCape);
	}

	/**
	 * Whether this player plays from Native (has a published look, or is the connected account). Same identity
	 * rule as {@link #find}: premium UUIDs only match the account linked to exactly that UUID, offline (version 3)
	 * players match by name.
	 */
	public boolean has(String name, UUID id) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		SkinEntry mine = local;
		if (mine != null && key(mine.name).equals(key(name)) && (id == null || id.version() == 3)) {
			return true;
		}
		AccountInfo account = NativeState.get().account();
		if (account != null && account.name != null && key(account.name).equals(key(name))
				&& (id == null || id.version() == 3 || (account.uuid != null && account.uuid.replace("-", "").equalsIgnoreCase(id.toString().replace("-", ""))))) {
			return true;
		}
		SkinEntry entry = entries.get(key(name));
		if (entry == null) {
			return false;
		}
		if (id != null && id.version() != 3 && !isSelf(name)) {
			String linked = entry.minecraftUuid;
			return linked != null && linked.equals(id.toString().replace("-", "").toLowerCase(Locale.ROOT));
		}
		return true;
	}

	/**
	 * The 3D cosmetics a player wears (never null). Same rule as {@link #find}: premium UUIDs only match the
	 * account linked to exactly that UUID, offline (version 3) players match by name.
	 */
	public java.util.List<xyz.nativelaunch.cosmetic.CosmeticRef> cosmetics(String name, UUID id) {
		if (name == null || name.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		SkinEntry entry = entries.get(key(name));
		if (entry == null || entry.cosmetics.isEmpty()) {
			return java.util.Collections.emptyList();
		}
		if (id != null && id.version() != 3 && !isSelf(name)) {
			String linked = entry.minecraftUuid;
			if (linked == null || !linked.equals(id.toString().replace("-", "").toLowerCase(Locale.ROOT))) {
				return java.util.Collections.emptyList();
			}
		}
		return entry.cosmetics;
	}

	/** The directory row of a player by name (case-insensitive), or null. */
	public SkinEntry entry(String name) {
		return name == null || name.isEmpty() ? null : entries.get(key(name));
	}

	private boolean isSelf(String name) {
		try {
			String mine = selfName.get();
			return mine != null && key(mine).equals(key(name));
		} catch (RuntimeException e) {
			return false;
		}
	}

	/** Logs a skipped or unusual skin once per distinct reason (it is looked up on every refresh). */
	private void report(String name, String detail, String message) {
		if (reported.add(key(name) + "|" + detail)) {
			Log.info(message);
		}
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}
}
