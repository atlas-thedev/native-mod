package xyz.nativelaunch.core;

/** Mod version shown in the User-Agent: read from the jar manifest (Implementation-Version), "dev" outside a built jar. */
final class Version {
	static final String MOD = detect();

	private Version() {
	}

	private static String detect() {
		Package pkg = Version.class.getPackage();
		String version = pkg == null ? null : pkg.getImplementationVersion();
		return version == null || version.isEmpty() ? "dev" : version;
	}
}
