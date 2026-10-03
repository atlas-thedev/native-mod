package dev.noctra.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * How Minecraft names a downloaded cape texture: the skin manager registers it
 * under {@code <prefix>/<sha1>} where the sha1 is Guava's
 * {@code Hashing.sha1().hashUnencodedChars(texture.getHash())}. The prefix is
 * {@code skins/} up to 1.20.1 and {@code capes/} from 1.20.2, so callers match
 * the hash part only.
 */
public final class CapeTextureIds {
	private CapeTextureIds() {
	}

	/** Guava-compatible {@code sha1().hashUnencodedChars(value)}: every char as two little-endian bytes. */
	public static String sha1OfChars(String value) {
		byte[] bytes = new byte[value.length() * 2];
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			bytes[i * 2] = (byte) c;
			bytes[i * 2 + 1] = (byte) (c >>> 8);
		}
		try {
			byte[] digest = MessageDigest.getInstance("SHA-1").digest(bytes);
			StringBuilder out = new StringBuilder(40);
			for (byte b : digest) {
				out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
			}
			return out.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	/** authlib's {@code MinecraftProfileTexture.getHash()}: the URL's file name without its extension. */
	public static String textureHash(String url) {
		String name = url;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		int dot = name.lastIndexOf('.');
		return dot >= 0 ? name.substring(0, dot) : name;
	}

	/** True when {@code textureId} ("minecraft:capes/<sha1>") is the texture the game made for that sha1. */
	public static boolean matches(String textureId, String sha1) {
		if (textureId == null || sha1 == null) {
			return false;
		}
		return textureId.endsWith("/" + sha1) && (textureId.contains("capes/") || textureId.contains("skins/") || textureId.contains("elytra/"));
	}
}
