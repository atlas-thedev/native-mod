# Noctra Client (Fabric mod)

Shows every Noctra player's **skin and cape in real time** on Minecraft **1.16 → 26.3** (Fabric / Quilt).
No Fabric API needed, one jar for every version.

* Fetches the live skin directory from `https://api.nativelaunch.xyz` (snapshot + server-sent events), so skin and
  cape changes show up for everyone without a restart of the server or a file download per player.
* Works by hooking the game's own authlib session service, so skins/capes appear everywhere the game normally
  shows them (tab list, nametag, player model, inventory).
* Signs in with your Noctra account automatically when started from the Noctra Launcher (short-lived launch
  ticket in `<gameDir>/.noctra/session.json`). Without the launcher it runs as a guest: you still see everyone's skins.
* On **Minecraft 26.3** it also replaces the title screen with the Noctra one (3D player preview, toolbar, account line). Other versions keep the vanilla menu.
* Versions older than 1.16 (and Forge/NeoForge) keep using CustomSkinLoader through the launcher.

## How it hooks (why one jar works)

| Minecraft | authlib | Hook |
|-----------|---------|------|
| 1.16 – 1.20.2 | ≤ 5.0 | `YggdrasilMinecraftSessionService.getTextures(GameProfile, boolean)` |
| 1.20.3 – 26.2 | 6 – 10 | `getPackedTextures` / `unpackTextures` |
| 26.3 | 10+ | the same on `MinecraftServicesSessionService` |

A premium (online) UUID is only overridden when the Noctra account is linked to that exact Minecraft account;
offline UUIDs are matched by name.

## Releasing

Double-click **`release.bat`** (pick patch / minor / major). It bumps `gradle.properties`, commits, tags `vX.Y.Z`
and pushes. GitHub Actions then:

1. builds the jar and runs the unit tests,
2. boots a real Fabric server for 1.16.5 … 26.3 with the jar and checks skins and capes arrive (`ci/compat`),
3. publishes a GitHub Release with `noctra-client-X.Y.Z.jar` and `manifest.json`.

The launcher reads `releases/latest/download/manifest.json` (sha256-verified) and installs the jar into Fabric/Quilt instances.

## Local build

```
./gradlew build      # needs JDK 25 (downloads Minecraft 26.3 once, for the title screen) ; output: build/libs/noctra-client-<version>.jar
```

License: CC0-1.0.
