package xyz.nativelaunch.core;

/**
 * Mod version shown in the User-Agent. Fabric does not expose jar manifests through Package, so the version
 * comes from the loader's metadata for the "native" mod; the manifest is the fallback, "dev" outside a jar.
 */
final class Version {
	static final String MOD = detect();

	private Version() {
	}

	private static String detect() {
		try {
			String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("native")
					.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
			if (version != null && !version.isEmpty() && !version.contains("$")) {
				return version;
			}
		} catch (Throwable ignored) {
			// no loader (tests): fall through
		}
		Package pkg = Version.class.getPackage();
		String version = pkg == null ? null : pkg.getImplementationVersion();
		return version == null || version.isEmpty() ? "dev" : version;
	}
}
