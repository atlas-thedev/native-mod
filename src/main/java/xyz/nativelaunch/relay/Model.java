package xyz.nativelaunch.relay;

import java.util.Collections;
import java.util.List;

/** Plain data the in-game Relay chat shows. Immutable snapshots: the UI thread reads them without locks. */
public final class Model {
	private Model() {
	}

	public static final class Friend {
		public final String id, name, nickname, status, activity, serverAddress, skin;
		public final boolean online;
		public final int unread;

		public Friend(String id, String name, String nickname, String status, String activity, String serverAddress, String skin, boolean online, int unread) {
			this.id = id;
			this.name = name;
			this.nickname = nickname;
			this.status = status;
			this.activity = activity;
			this.serverAddress = serverAddress;
			this.skin = skin;
			this.online = online;
			this.unread = unread;
		}

		/** Presence dot colour: green online, amber idle/away, red do-not-disturb, grey offline. */
		public int dot() {
			if (!online) {
				return 0xFF52525B;
			}
			if ("idle".equals(status) || "away".equals(status)) {
				return 0xFFF59E0B;
			}
			return "dnd".equals(status) || "busy".equals(status) ? 0xFFEF4444 : 0xFF22C55E;
		}

		/** One-line status: activity, else Online / Idle / Do not disturb / Offline. */
		public String line() {
			if (!online) {
				return "Offline";
			}
			if (activity != null && !activity.isEmpty()) {
				return activity;
			}
			if ("idle".equals(status) || "away".equals(status)) {
				return "Idle";
			}
			return "dnd".equals(status) || "busy".equals(status) ? "Do not disturb" : "Online";
		}

		public String display() {
			return nickname != null && !nickname.isEmpty() ? nickname : name;
		}

		Friend withPresence(String status, String activity, String serverAddress) {
			return new Friend(id, name, nickname, status, activity, serverAddress, skin, !"offline".equals(status), unread);
		}

		Friend withUnread(int unread) {
			return new Friend(id, name, nickname, status, activity, serverAddress, skin, online, unread);
		}
	}

	public static final class Group {
		public final String id, name, lastText, lastSender;
		public final long lastAt;
		public final int unread, members;
		public final List<String[]> memberList; // {id, name}
		/** Your role here: "owner", "admin" or "member" (owners and admins can delete anyone's message). */
		public String role = "member";

		public Group(String id, String name, String lastText, String lastSender, long lastAt, int unread, int members, List<String[]> memberList) {
			this.id = id;
			this.name = name;
			this.lastText = lastText;
			this.lastSender = lastSender;
			this.lastAt = lastAt;
			this.unread = unread;
			this.members = members;
			this.memberList = memberList == null ? Collections.<String[]>emptyList() : memberList;
		}

		Group withUnread(int unread) {
			Group g = new Group(id, name, lastText, lastSender, lastAt, unread, members, memberList);
			g.role = role;
			return g;
		}

		Group withLast(String text, String sender, long at, int unread) {
			Group g = new Group(id, name, text, sender, at, unread, members, memberList);
			g.role = role;
			return g;
		}

		public boolean canModerate() {
			return "owner".equals(role) || "admin".equals(role);
		}
	}

	public static final class Message {
		public final String id, senderId, senderName, content;
		public final long createdAt;
		public final boolean pending, failed, system, deleted;
		/** Attached picture (own upload or GIF), null for plain text. */
		public final String mediaUrl, mediaName;
		/** Locally pasted picture shown while it uploads. */
		public final String localKey;
		/** The message this one answers (null when it is not a reply). */
		public Reply reply;
		/** Reactions: {userId, emoji} pairs, groups only. */
		public List<String[]> reactions = Collections.emptyList();
		public boolean edited, read;

		public Message(String id, String senderId, String senderName, String content, long createdAt, boolean pending, boolean failed, boolean system, boolean deleted) {
			this(id, senderId, senderName, content, createdAt, pending, failed, system, deleted, null, null, null);
		}

		public Message(String id, String senderId, String senderName, String content, long createdAt, boolean pending, boolean failed, boolean system, boolean deleted,
				String mediaUrl, String mediaName, String localKey) {
			this.mediaUrl = mediaUrl;
			this.mediaName = mediaName;
			this.localKey = localKey;
			this.id = id;
			this.senderId = senderId;
			this.senderName = senderName;
			this.content = content;
			this.createdAt = createdAt;
			this.pending = pending;
			this.failed = failed;
			this.system = system;
			this.deleted = deleted;
		}
	}

	/** What a reply points at: enough to draw the quote and to jump to the original. */
	public static final class Reply {
		public final String id, senderName, content;
		public final boolean deleted, media;

		public Reply(String id, String senderName, String content, boolean media, boolean deleted) {
			this.id = id;
			this.senderName = senderName;
			this.content = content == null ? "" : content;
			this.media = media;
			this.deleted = deleted;
		}
	}

	/** A copy of {@code m} with its sending state changed (keeps the reply, reactions and the rest). */
	public static Message withState(Message m, boolean pending, boolean failed) {
		Message n = new Message(m.id, m.senderId, m.senderName, m.content, m.createdAt, pending, failed, m.system, m.deleted, m.mediaUrl, m.mediaName, m.localKey);
		n.reply = m.reply;
		n.reactions = m.reactions;
		n.edited = m.edited;
		n.read = m.read;
		return n;
	}

	/** One chat (a DM or a group) and the messages loaded so far, oldest first. */
	public static final class Conversation {
		public final String key; // "dm:<friendId>" or "g:<groupId>"
		public volatile List<Message> messages = Collections.emptyList();
		public volatile boolean loading, loaded, hasMore;
		public volatile String error;

		Conversation(String key) {
			this.key = key;
		}

		public boolean isGroup() {
			return key.startsWith("g:");
		}

		public String targetId() {
			return key.substring(key.indexOf(':') + 1);
		}
	}

	/** Something to pop up in game (a new message while the chat is closed). */
	public static final class Notice {
		public final String convKey, title, body, avatarName, skin;
		public final long at = System.currentTimeMillis();

		public Notice(String convKey, String title, String body, String avatarName, String skin) {
			this.convKey = convKey;
			this.title = title;
			this.body = body;
			this.avatarName = avatarName;
			this.skin = skin;
		}
	}
}
