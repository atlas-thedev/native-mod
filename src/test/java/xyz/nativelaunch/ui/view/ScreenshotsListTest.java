package xyz.nativelaunch.ui.view;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotsListTest {
	@Test
	void listsPicturesNewestFirstAndIgnoresOtherFiles(@TempDir Path dir) throws Exception {
		File a = dir.resolve("2026-01-01_a.png").toFile(), b = dir.resolve("2026-01-02_b.PNG").toFile(), c = dir.resolve("old.jpg").toFile();
		for (File f : new File[] {a, b, c, dir.resolve("notes.txt").toFile()}) {
			Files.write(f.toPath(), new byte[2048]);
		}
		Files.createDirectory(dir.resolve("sub.png"));
		a.setLastModified(3_000_000L);
		b.setLastModified(9_000_000L);
		c.setLastModified(6_000_000L);
		List<ScreenshotsView.Shot> list = ScreenshotsView.list(dir);
		assertEquals(3, list.size(), "pictures only, no folders or text files");
		assertEquals("2026-01-02_b.PNG", list.get(0).name);
		assertEquals("old.jpg", list.get(1).name);
		assertEquals("2026-01-01_a.png", list.get(2).name);
		assertEquals("2 KB", list.get(0).size);
		assertTrue(list.get(0).when.contains("1970"), list.get(0).when);
	}

	@Test
	void aMissingFolderIsJustEmpty(@TempDir Path dir) {
		assertTrue(ScreenshotsView.list(dir.resolve("nope")).isEmpty());
		assertTrue(ScreenshotsView.list(null).isEmpty());
	}
}
