package xyz.nativelaunch.relay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class FriendStatusTest {
	private static String nice(String activity, String status) {
		return Model.Friend.niceActivity(activity, status);
	}

	@Test
	void theLaunchersInGameWordingBecomesPlainEnglish() {
		assertEquals("Playing on Hypixel", nice("In-game: Hypixel", "in-game"));
		assertEquals("Playing on mc.example.net", nice("In-game: mc.example.net", "in-game"));
		assertEquals("Playing Singleplayer", nice("In-game: Singleplayer", "in-game"));
		assertEquals("Playing multiplayer", nice("In-game: Multiplayer", "in-game"));
		assertEquals("Playing on Realms", nice("In-game: Realms", "in-game"));
		assertEquals("In the menus", nice("In-game: Menus", "in-game"));
		assertEquals("In the menus", nice(null, "in-menus"));
		assertEquals("Playing Minecraft", nice(null, "in-game"));
		assertEquals("Playing Minecraft", nice("Ingame", "in-game"));
		assertEquals("Playing Minecraft", nice("In-game", "in-game"));
		assertEquals("Playing on Hypixel", nice("Ingame: Hypixel", "online"));
	}

	@Test
	void otherActivitiesStayAsTheyAre() {
		assertEquals("Playing Minecraft", nice("Playing Minecraft", "online"));
		assertEquals("In the launcher", nice("In Launcher", "online"));
		assertEquals("Streaming", nice("Streaming", "online"));
		assertNull(nice(null, "online"));
		assertNull(nice("", "online"));
	}

	@Test
	void theFriendLineUsesIt() {
		Model.Friend f = new Model.Friend("1", "Alex", null, "in-game", "In-game: Hypixel", "mc.hypixel.net", null, true, 0, false, 0);
		assertEquals("Playing on Hypixel", f.line());
		Model.Friend offline = new Model.Friend("1", "Alex", null, "offline", "In-game: Hypixel", null, null, false, 0, false, 0);
		assertEquals("Offline", offline.line());
		Model.Friend plain = new Model.Friend("1", "Alex", null, "online", null, null, null, true, 0, false, 0);
		assertEquals("Online", plain.line());
	}
}
