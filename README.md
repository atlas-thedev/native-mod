# Native Client (Fabric mod)

Shows every Native player's **skin and cape in real time** on Minecraft **1.16 → 26.3** (Fabric / Quilt).
No Fabric API needed, one jar for every version.

* Fetches the live skin directory from `https://api.nativelaunch.xyz` (snapshot + server-sent events), so skin and
  cape changes show up for everyone without a restart of the server or a file download per player.
* Works by hooking the game's own authlib session service, so skins/capes appear everywhere the game normally
  shows them (tab list, nametag, player model, inventory).
* Signs in with your Native account automatically when started from the Native Client launcher (short-lived launch
  ticket in `<gameDir>/.native/session.json`). Without the launcher it runs as a guest: you still see everyone's skins.
* **Animated capes play on every supported version** (1.16 → 26.3). Players on other launchers or versions see the
  cape's first frame as a normal cape.
* Versions older than 1.16 (and Forge/NeoForge) keep using CustomSkinLoader through the launcher.

## How it hooks (why one jar works)

| Minecraft | authlib | Hook |
|-----------|---------|------|
| 1.16 – 1.20.2 | ≤ 5.0 | `YggdrasilMinecraftSessionService.getTextures(GameProfile, boolean)` |
| 1.20.3 – 26.2 | 6 – 10 | `getPackedTextures` / `unpackTextures` |
| 26.3 | 10+ | the same on `MinecraftServicesSessionService` |

Animated capes:

| Minecraft | How frames are shown |
|-----------|----------------------|
| 1.16 – 1.21.4 | the frame is written into the cape's OpenGL texture (`AbstractTexture.getId()`) |
| 1.21.5 – 1.21.x | the same, through the GPU texture (`AbstractTexture.getTexture()` → `GlTexture.glId()`) |
| 26.x | the cape texture is replaced by a ticking `DynamicTexture` built against the game's own classes |

On 1.16 – 1.21.x the mod reaches the game through Fabric intermediary names, which are stable across
versions, so the same jar works everywhere. GL state is saved and restored around every upload.

A premium (online) UUID is only overridden when the Native account is linked to that exact Minecraft account;
offline UUIDs are matched by name.

## Releasing

Double-click **`release.bat`** (pick patch / minor / major). It bumps `gradle.properties`, commits, tags `vX.Y.Z`
and pushes. GitHub Actions then:

1. builds the jar and runs the unit tests,
2. boots a real Fabric server for 1.16.5 … 26.3 with the jar and checks skins and capes arrive (`ci/compat`),
   and a real Minecraft client (1.16.5 … 1.21.11, software OpenGL) to check animated capes play (`ci/client`),
3. publishes a GitHub Release with `native-client-X.Y.Z.jar` and `manifest.json`.

The launcher reads `releases/latest/download/manifest.json` (sha256-verified) and installs the jar into Fabric/Quilt instances.

## Local build

```
./gradlew build      # needs JDK 17+ ; output: build/libs/native-client-<version>.jar
```

License: CC0-1.0.
