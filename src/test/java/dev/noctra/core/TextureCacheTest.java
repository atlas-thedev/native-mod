package dev.noctra.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TextureCacheTest {
	@Test
	void readsTheLauncherCacheAndVerifiesHashes(@TempDir Path game, @TempDir Path shared) throws Exception {
		Files.createDirectories(game.resolve(".noctra"));
		String json = "{\"v\":1,\"textureCache\":" + new com.google.gson.JsonPrimitive(shared.toString()) + "}";
		Files.write(game.resolve(".noctra").resolve("launcher.json"), json.getBytes(StandardCharsets.UTF_8));
		TextureCache.init(game);

		byte[] png = "not really a png but long enough to count".getBytes(StandardCharsets.UTF_8);
		String hash = TextureCache.sha256(png);
		Files.write(shared.resolve(hash + ".png"), png);
		assertArrayEquals(png, TextureCache.read(hash));

		String other = TextureCache.sha256("other".getBytes(StandardCharsets.UTF_8));
		Files.write(shared.resolve(other + ".png"), png); // wrong bytes for that name
		assertNull(TextureCache.read(other));
		assertNull(TextureCache.read("../escape"));
	}

	@Test
	void writesOnlyMatchingBytes(@TempDir Path game) throws Exception {
		TextureCache.init(game);
		byte[] png = "some texture bytes for the cache test".getBytes(StandardCharsets.UTF_8);
		String hash = TextureCache.sha256(png);
		TextureCache.write(hash, png);
		assertArrayEquals(png, TextureCache.read(hash));
		String wrong = TextureCache.sha256("x".getBytes(StandardCharsets.UTF_8));
		TextureCache.write(wrong, png);
		assertNull(TextureCache.read(wrong));
	}
}
