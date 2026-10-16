package xyz.nativelaunch.voice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.nativelaunch.presence.PresenceService;

class CallEndTest {
	private static Voice.Member member(String userId) {
		JsonObject o = new JsonObject();
		o.addProperty("sid", userId.hashCode());
		o.addProperty("userId", userId);
		o.addProperty("name", userId);
		return new Voice.Member(o);
	}

	private static List<Voice.Member> roster(String... ids) {
		List<Voice.Member> out = new ArrayList<Voice.Member>();
		for (String id : ids) {
			out.add(member(id));
		}
		return out;
	}

	@Test
	void aPrivateCallIsOverWhenYouAreLeftAloneAfterSomeoneWasThere() {
		assertTrue(Voice.callOver("dm:bob", roster("me"), "me", true), "Bob left: the call is over");
		assertFalse(Voice.callOver("dm:bob", roster("me"), "me", false), "nobody has joined yet: still ringing / waiting");
		assertFalse(Voice.callOver("dm:bob", roster("me", "bob"), "me", true), "both still there");
		assertFalse(Voice.callOver("g:squad", roster("me"), "me", true), "a group call carries on");
		assertFalse(Voice.callOver(null, roster("me"), "me", true));
		assertTrue(Voice.callOver("dm:bob", roster(), "me", true), "an empty roster after someone was there");
	}

	@Test
	void proximityVoiceIsOnlyForPublicServers() {
		assertTrue(PresenceService.isPublicAddress("mc.hypixel.net"));
		assertTrue(PresenceService.isPublicAddress("play.example.com:25566"));
		assertFalse(PresenceService.isPublicAddress("192.168.1.5"), "a LAN address is the same text on many networks");
		assertFalse(PresenceService.isPublicAddress("192.168.0.12:25565"));
		assertFalse(PresenceService.isPublicAddress("10.0.0.4"));
		assertFalse(PresenceService.isPublicAddress("localhost"));
		assertFalse(PresenceService.isPublicAddress("127.0.0.1:25565"));
		assertFalse(PresenceService.isPublicAddress("Singleplayer"));
	}
}
