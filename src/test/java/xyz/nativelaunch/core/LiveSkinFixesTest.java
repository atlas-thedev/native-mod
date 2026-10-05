package xyz.nativelaunch.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Premium-name handling, stable texture properties, reset notifications and the live refresh. */
public class LiveSkinFixesTest {
	private static final UUID PREMIUM = UUID.fromString("091abb03-ebfc-4c4f-af71-5efc69eb945d");
	private static final String S = "a".repeat(64), C = "b".repeat(64), PC = "c".repeat(64);

	private static SkinDirectory directoryWith(String json) {
		SkinDirectory d = new SkinDirectory();
		SkinSync.applySnapshot(d, "{\"epoch\":1,\"rev\":1,\"full\":true,\"textureBase\":\"https://t/\",\"entries\":[" + json + "]}");
		return d;
	}

	@Test
	void premiumBlockIsParsed() {
		SkinEntry e = SkinSync.entry(JsonParser.parseString("{\"n\":\"Sok\",\"s\":\"" + S + "\",\"u\":\"091abb03ebfc4c4faf715efc69eb945d\",\"r\":4,"
				+ "\"p\":{\"c\":\"" + PC + "\",\"a\":null}}").getAsJsonObject());
		assertTrue(e.hasPremium);
		assertEquals(PC, e.premiumCapeHash);
		assertNull(e.premiumStripHash);
	}

	@Test
	void premiumSessionKeepsMojangSkinAndWearsPremiumCape() {
		SkinDirectory d = directoryWith("{\"n\":\"Sok\",\"s\":\"" + S + "\",\"c\":\"" + C + "\",\"u\":\"091abb03ebfc4c4faf715efc69eb945d\",\"p\":{\"c\":\"" + PC + "\"}}");
		SkinOverride premium = d.find("Sok", PREMIUM, true);
		assertNull(premium.skinUrl, "Mojang skin stays");
		assertEquals("https://t/" + PC, premium.capeUrl);
		// the same person launched with their Native account (no Mojang textures): full Native look
		SkinOverride nativeSession = d.find("Sok", PREMIUM, false);
		assertEquals("https://t/" + S, nativeSession.skinUrl);
		assertEquals("https://t/" + C, nativeSession.capeUrl);
	}

	@Test
	void premiumSessionWithoutPremiumCapeIsLeftAlone() {
		SkinDirectory d = directoryWith("{\"n\":\"Sok\",\"s\":\"" + S + "\",\"u\":\"091abb03ebfc4c4faf715efc69eb945d\",\"p\":{\"c\":null}}");
		assertNull(d.find("Sok", PREMIUM, true));
	}

	@Test
	void entriesWithoutPremiumBlockBehaveAsBefore() {
		SkinDirectory d = directoryWith("{\"n\":\"OhLlama\",\"s\":\"" + S + "\",\"u\":\"091abb03ebfc4c4faf715efc69eb945d\"}");
		assertEquals("https://t/" + S, d.find("OhLlama", PREMIUM, true).skinUrl);
		assertEquals("https://t/" + S, d.find("ohllama", UUID.nameUUIDFromBytes("OfflinePlayer:OhLlama".getBytes()), false).skinUrl);
	}

	@Test
	void texturesPropertyIsStableUntilTheLookChanges() {
		SkinOverride a = new SkinOverride("https://t/" + S, null, false);
		String one = Textures.pack(null, a, PREMIUM, "Sok");
		String two = Textures.pack(null, new SkinOverride("https://t/" + S, null, false), PREMIUM, "Sok");
		assertEquals(one, two, "same look -> same property, so the game's skin cache stays warm");
		assertNotEquals(one, Textures.pack(null, new SkinOverride("https://t/" + S, null, true), PREMIUM, "Sok"));
		assertNotEquals(one, Textures.pack(null, new SkinOverride("https://t/" + C, null, false), PREMIUM, "Sok"));
	}

	@Test
	void replacingTheDirectoryNotifiesResetListeners() {
		SkinDirectory d = new SkinDirectory();
		int[] resets = {0};
		d.onReset(() -> resets[0]++);
		SkinSync.applySnapshot(d, "{\"epoch\":1,\"rev\":1,\"full\":true,\"textureBase\":\"https://t/\",\"entries\":[]}");
		assertEquals(1, resets[0]);
	}

	/* ── live refresh against stand-ins shaped like each generation of PlayerInfo ── */

	public static final class Profile {
		final String name;
		Profile(String name) { this.name = name; }
		public String getName() { return name; }
	}

	/** 1.21.9+: lazily created, non-final lookup. */
	static final class LazyInfo {
		private final Profile profile;
		private Supplier<String> skinLookup = () -> "old";
		LazyInfo(Profile p) { profile = p; }
	}

	/** 1.20.2 - 1.21.8: final lookup created by a static factory. */
	static final class FinalInfo {
		static int created;
		private final Profile profile;
		private final Supplier<String> skinLookup;
		FinalInfo(Profile p) { profile = p; skinLookup = createSkinLookup(p); }
		private static Supplier<String> createSkinLookup(Profile p) { created++; final int n = created; return () -> "lookup" + n; }
	}

	/** 1.16 - 1.20.1: texture map + pending flag + model. */
	static final class LegacyInfo {
		private final Profile profile;
		private final Map<String, String> textureLocations = new HashMap<>();
		private boolean pendingTextures = true;
		private String skinModel = "slim";
		private int latency = 7;
		LegacyInfo(Profile p) { profile = p; textureLocations.put("SKIN", "x"); }
	}

	@Test
	void refreshResetsEveryGeneration() throws Exception {
		LazyInfo lazy = new LazyInfo(new Profile("A"));
		assertTrue(SkinRefresh.reset(lazy, lazy.profile, Profile.class));
		assertNull(lazy.skinLookup);

		FinalInfo fin = new FinalInfo(new Profile("B"));
		assertEquals("lookup" + FinalInfo.created, fin.skinLookup.get());
		int before = FinalInfo.created;
		assertTrue(SkinRefresh.reset(fin, fin.profile, Profile.class));
		assertEquals(before + 1, FinalInfo.created);
		assertEquals("lookup" + FinalInfo.created, fin.skinLookup.get());

		LegacyInfo legacy = new LegacyInfo(new Profile("C"));
		assertTrue(SkinRefresh.reset(legacy, legacy.profile, Profile.class));
		assertTrue(legacy.textureLocations.isEmpty());
		assertFalse(legacy.pendingTextures);
		assertNull(legacy.skinModel);
		assertEquals(7, legacy.latency);
	}

	/* a stand-in game: getConnection() returns an object holding Map<UUID, Info> */
	public static final class Connection {
		private final Map<UUID, LazyInfo> playerInfoMap = new HashMap<>();
	}

	public static final class Game {
		final Connection connection = new Connection();
		public Connection getConnection() { return connection; }
		public int getFps() { return 60; }
	}

	@Test
	void refreshFindsPlayersByTypeAndOnlyTouchesThoseNamed() throws Exception {
		// The real refresh looks up com.mojang.authlib.GameProfile; here the stand-in Profile plays that role.
		Game game = new Game();
		LazyInfo a = new LazyInfo(new Profile("Alice"));
		LazyInfo b = new LazyInfo(new Profile("Bob"));
		game.connection.playerInfoMap.put(UUID.randomUUID(), a);
		game.connection.playerInfoMap.put(UUID.randomUUID(), b);
		int n = SkinRefresh.refreshWith(game, Collections.singleton("alice"), Profile.class);
		assertEquals(1, n);
		assertNull(a.skinLookup);
		assertNotNull(b.skinLookup);
		assertEquals(2, SkinRefresh.refreshWith(game, null, Profile.class)); // everyone
	}
}
