package xyz.nativelaunch.voice;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Who hears you: your call by your talk mode, the players near you only when you mean it. */
class NearbyTalkTest {
	private static final int CALL = 1, PROX = 2;

	@Test
	void onAServerWithoutACallNobodyHearsYouUnlessYouHoldTheKey() {
		// voice activation picks you up, but you are in no call: nothing goes out
		assertEquals(0, Voice.targets(true, false, false, true, Voice.NEARBY_HOLD));
		assertEquals(PROX, Voice.targets(false, true, false, true, Voice.NEARBY_HOLD), "holding push-to-talk talks to nearby players");
	}

	@Test
	void aPrivateCallIsNotHeardByWhoeverStandsNextToYou() {
		assertEquals(CALL, Voice.targets(true, false, true, true, Voice.NEARBY_HOLD));
		assertEquals(CALL | PROX, Voice.targets(true, true, true, true, Voice.NEARBY_HOLD), "unless you hold the key");
	}

	@Test
	void nearbyCanFollowTheTalkModeOrBeOff() {
		assertEquals(PROX, Voice.targets(true, false, false, true, Voice.NEARBY_TALK_MODE));
		assertEquals(CALL | PROX, Voice.targets(true, false, true, true, Voice.NEARBY_TALK_MODE));
		assertEquals(0, Voice.targets(true, true, false, true, Voice.NEARBY_OFF));
		assertEquals(CALL, Voice.targets(true, true, true, true, Voice.NEARBY_OFF));
	}

	@Test
	void roomsYouAreNotInGetNothing() {
		assertEquals(0, Voice.targets(true, true, false, false, Voice.NEARBY_TALK_MODE));
		assertEquals(PROX, Voice.targets(true, true, false, true, Voice.NEARBY_HOLD));
	}
}
