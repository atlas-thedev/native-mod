package xyz.nativelaunch.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerJoinTest {
	public static class Screen {
	}

	public static class Client {
	}

	public static final class Addr {
		final String host;

		Addr(String host) {
			this.host = host;
		}

		public static Addr parse(String s) {
			return new Addr(s);
		}
	}

	public enum Type { LAN, REALM, OTHER }

	public static final class Data {
		final String ip;
		final Object type;

		public Data(String name, String ip, Type type) {
			this.ip = ip;
			this.type = type;
		}
	}

	public static final class OldData {
		final String ip;

		public OldData(String name, String ip, boolean lan) {
			this.ip = ip;
		}
	}

	/** 26.x style. */
	public static final class Connect {
		static Object[] last;

		public static void startConnecting(Screen parent, Client mc, Addr addr, Data data, boolean quick, Object transfer) {
			last = new Object[] {parent, mc, addr, data, quick, transfer};
		}
	}

	/** 1.16 style. */
	public static final class OldConnect {
		final OldData data;

		public OldConnect(Screen parent, Client mc, OldData data) {
			this.data = data;
		}
	}

	@Test
	void modernStaticMethod() {
		Screen parent = new Screen();
		Client client = new Client();
		Object r = ServerJoin.start(client, parent, Screen.class, Connect.class.getName(), Addr.class.getName(), "parse", Data.class.getName(), "play.example.net");
		assertEquals(Boolean.TRUE, r);
		assertSame(parent, Connect.last[0]);
		assertSame(client, Connect.last[1]);
		assertEquals("play.example.net", ((Addr) Connect.last[2]).host);
		assertEquals(Type.OTHER, ((Data) Connect.last[3]).type);
		assertEquals(Boolean.FALSE, Connect.last[4]);
	}

	@Test
	void legacyConstructor() {
		Object r = ServerJoin.start(new Client(), new Screen(), Screen.class, OldConnect.class.getName(), "no.such.Addr", "parse", OldData.class.getName(), "mc.x.net:25566");
		assertTrue(r instanceof OldConnect);
		assertEquals("mc.x.net:25566", ((OldConnect) r).data.ip);
	}
}
