package xyz.nativelaunch.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Thin wrapper so the rest of the mod never touches the logging API directly. */
public final class Log {
	private static final Logger LOGGER = LogManager.getLogger("Native");

	private Log() {
	}

	public static void info(String message, Object... args) {
		LOGGER.info("[Native] " + message, args);
	}

	public static void warn(String message, Object... args) {
		LOGGER.warn("[Native] " + message, args);
	}

	public static void debug(String message, Object... args) {
		LOGGER.debug("[Native] " + message, args);
	}
}
