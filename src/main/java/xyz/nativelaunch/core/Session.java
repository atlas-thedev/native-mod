package xyz.nativelaunch.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The game's sign-in to Native, shared by Relay, the locker, presence and skins.
 *
 * The launcher writes a short-lived game ticket to {@code .native/session.json} and keeps it
 * fresh while the game runs. This class checks the ticket, retries when the network is down,
 * notices a refreshed ticket on disk, and reports a rejected one instead of silently dropping to
 * guest mode. Screens that need an account read {@link #status()} and lock themselves with
 * {@link #lockTitle()} / {@link #lockBody()} when it is not {@link Status#SIGNED_IN}.
 */
public final class Session {
	public enum Status {
		/** No ticket from the launcher (started outside the Native Client, or an offline account). */
		GUEST,
		/** Checking the ticket with Native. */
		CHECKING,
		SIGNED_IN,
		/** Native refused the ticket and the launcher has not written a new one yet. */
		EXPIRED,
		/** Native could not be reached; retrying. */
		OFFLINE
	}

	private static final long WATCH_MS = 15_000;
	private static final long RECHECK_MS = 10 * 60_000;

	private static volatile Status status = Status.GUEST;
	private static volatile String ticket;
	private static volatile AccountInfo account;
	private static volatile String api = NativeState.DEFAULT_API;
	private static volatile Path gameDir;
	private static volatile long fileStamp = -1;
	private static volatile long verifiedAt;
	private static volatile long nextTry;
	private static volatile int failures;
	private static final Object WAKE = new Object();
	private static Thread worker;

	private Session() {
	}

	public static Status status() {
		return status;
	}

	public static boolean signedIn() {
		return status == Status.SIGNED_IN;
	}

	/** The ticket to send, or null when there is none worth sending (guest or rejected). */
	public static String ticket() {
		Status s = status;
		return s == Status.GUEST || s == Status.EXPIRED ? null : ticket;
	}

	/** The Native account the ticket belongs to (null until it has been checked once). */
	public static AccountInfo account() {
		return account;
	}

	/** Starts checking the hand-off the launcher left (idempotent). */
	static synchronized void start(Path dir, String apiRoot, Handoff handoff) {
		if (worker != null) {
			return;
		}
		gameDir = dir;
		api = apiRoot;
		fileStamp = stamp();
		adopt(handoff);
		worker = new Thread(Session::loop, "Native-Session");
		worker.setDaemon(true);
		worker.setPriority(Thread.MIN_PRIORITY);
		worker.start();
	}

	/**
	 * Called by anything that got HTTP 401 with {@code used}. Looks for a newer ticket from the
	 * launcher first; only when there is none is the session marked expired.
	 */
	public static void rejected(String used) {
		if (used == null || !used.equals(ticket)) {
			return; // an older ticket: the current one is already being handled
		}
		Handoff fresh = Handoff.read(gameDir);
		fileStamp = stamp();
		if (fresh != null && !fresh.ticket.equals(used)) {
			adopt(fresh);
		} else if (status != Status.EXPIRED) {
			status = Status.EXPIRED;
			Log.warn("Native refused the game ticket. Waiting for the launcher to sign in again.");
		}
		wake();
	}

	/** Called by anything whose request failed for network reasons while signed in. */
	public static void unreachable() {
		if (status == Status.SIGNED_IN && System.currentTimeMillis() - verifiedAt > 30_000) {
			status = Status.CHECKING;
			nextTry = 0;
			wake();
		}
	}

	/** "Try again" in the UI. */
	public static void retry() {
		Handoff fresh = Handoff.read(gameDir);
		fileStamp = stamp();
		if (fresh != null && (ticket == null || !fresh.ticket.equals(ticket) || status != Status.EXPIRED)) {
			adopt(fresh);
		}
		failures = 0;
		nextTry = 0;
		wake();
	}

	public static String lockTitle() {
		switch (status) {
			case CHECKING:
				return "Signing in…";
			case EXPIRED:
				return "Your Native sign-in expired";
			case OFFLINE:
				return "Can't reach Native";
			default:
				return "Not signed in";
		}
	}

	public static String lockBody() {
		switch (status) {
			case CHECKING:
				return "Connecting this game to your Native account.";
			case EXPIRED:
				return "Sign in again in the Native Client. This page unlocks by itself once you do.";
			case OFFLINE:
				return "Check your connection. Retrying automatically.";
			default:
				return "Start Minecraft from the Native Client with a Native or Microsoft account to use this.";
		}
	}

	// ── internals ────────────────────────────────────────────────────────

	private static void adopt(Handoff handoff) {
		if (handoff == null || handoff.ticket == null) {
			ticket = null;
			status = Status.GUEST;
			return;
		}
		ticket = handoff.ticket;
		status = Status.CHECKING;
		failures = 0;
		nextTry = 0;
	}

	private static void wake() {
		synchronized (WAKE) {
			WAKE.notifyAll();
		}
	}

	private static long stamp() {
		try {
			Path dir = gameDir;
			if (dir == null) {
				return -1;
			}
			Path file = dir.resolve(".native").resolve("session.json");
			return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : 0;
		} catch (Exception e) {
			return -1;
		}
	}

	private static void loop() {
		while (true) {
			try {
				// The launcher rewrote session.json (a fresh ticket, another account, or signed out).
				long now = stamp();
				if (now != fileStamp) {
					fileStamp = now;
					Handoff fresh = Handoff.read(gameDir);
					if (fresh == null ? ticket != null : !fresh.ticket.equals(ticket)) {
						Log.info("The launcher refreshed the game sign-in.");
						adopt(fresh);
					}
				}
				Status s = status;
				boolean due = s == Status.CHECKING || s == Status.OFFLINE
						|| (s == Status.SIGNED_IN && System.currentTimeMillis() - verifiedAt > RECHECK_MS);
				if (due && System.currentTimeMillis() >= nextTry) {
					verify();
				}
				synchronized (WAKE) {
					WAKE.wait(status == Status.CHECKING ? 1000 : WATCH_MS);
				}
			} catch (InterruptedException e) {
				return;
			} catch (Throwable t) {
				Log.debug("Session check failed: {}", t.toString());
			}
		}
	}

	private static void verify() {
		String used = ticket;
		if (used == null) {
			status = Status.GUEST;
			return;
		}
		try {
			String body = Http.getJson(api + "/v1/mod/me", used);
			JsonObject root = new JsonParser().parse(body).getAsJsonObject();
			JsonObject a = root.getAsJsonObject("account");
			if (!used.equals(ticket)) {
				return; // replaced while we were asking
			}
			account = new AccountInfo(text(a, "id"), text(a, "name"), text(a, "uuid"), text(a, "model"));
			verifiedAt = System.currentTimeMillis();
			failures = 0;
			if (status != Status.SIGNED_IN) {
				Log.info("Connected to Native account {}", account.name);
			}
			status = Status.SIGNED_IN;
		} catch (Exception e) {
			if (!used.equals(ticket)) {
				return;
			}
			if (Http.unauthorized(e)) {
				rejected(used);
				return;
			}
			failures++;
			// 5 s, 10 s, 20 s ... capped at a minute.
			nextTry = System.currentTimeMillis() + Math.min(60_000L, 5_000L << Math.min(failures - 1, 4));
			if (status != Status.SIGNED_IN || failures > 1) {
				status = Status.OFFLINE;
			}
			Log.debug("Could not verify the Native account ({}). Retrying.", e.getMessage());
		}
	}

	private static String text(JsonObject o, String key) {
		if (o == null || !o.has(key)) {
			return null;
		}
		JsonElement value = o.get(key);
		return value.isJsonPrimitive() ? value.getAsString() : null;
	}
}
