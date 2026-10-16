package xyz.nativelaunch.core;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodCacheTest {
	public static final class Old {
		public String getName() {
			return "old";
		}
	}

	public static final class New {
		public String name() {
			return "new";
		}
	}

	@Test
	void callsWhicheverSpellingTheClassHas() throws Exception {
		MethodCache cache = new MethodCache("name", "getName");
		assertEquals("old", cache.call(new Old()));
		assertEquals("new", cache.call(new New()));
	}

	@Test
	void resolvesOncePerClass() {
		MethodCache cache = new MethodCache("name", "getName");
		assertSame(cache.methods(Old.class), cache.methods(Old.class));
		assertEquals(1, cache.methods(Old.class).length);
	}

	@Test
	void missingGetterThrowsLikeGetMethodDid() {
		MethodCache cache = new MethodCache("nothing", "nowhere");
		assertThrows(NoSuchMethodException.class, () -> cache.call(new Old()));
		assertTrue(cache.methods(Old.class).length == 0);
	}

	@Test
	void profileAccessStillReadsBothShapes() {
		assertEquals("old", ProfileAccess.name(new Old()));
		assertEquals("new", ProfileAccess.name(new New()));
		assertEquals(null, ProfileAccess.id(new Old()));
		UUID id = UUID.randomUUID();
		assertEquals(id, ProfileAccess.id(new Object() {
			public UUID getId() {
				return id;
			}
		}));
	}
}
