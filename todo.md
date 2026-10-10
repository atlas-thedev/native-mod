# Native mod — todo

Current release: **1.8.8** (Minecraft 1.16 – 26.3).

Everything on the 1.8.1 list is done; see `Shipped in 1.8.1` below for what changed and
how it was verified. Add new items under `Next`.

## Next
- More for the right-shift menu — players keep asking for extra modules and settings there.

## Fixed (bug sweep, after 1.8.8)
- Skin lookups never block the render thread (the 1.5 s first-snapshot wait is gone; SkinRefresh re-applies skins).
- The launcher hand-off is read once at start and shared (`NativeState.handoff()`).
- The API address (and so the game ticket) only goes to playnative.fun / nativelaunch.xyz over https, or loopback.
- HTTP: JSON replies capped at 16 MB, POST reply limit checked before writing.
- Texture cache: unique temp names, temp files cleaned up, hash mismatches no longer delete the launcher's file.
- Account fields only accept strings/numbers.

## Shipped in 1.8.8
- Hoods / helmets / masks (a cosmetic box that wraps the whole head) hide the skin's hat layer and the vanilla
  helmet / head item, in game (1.16 - 26.x) and in the wardrobe preview; the hood no longer moves for a helmet

## Shipped in 1.8.7
- Cosmetics preview: every texel is depth-sorted on its own (no more capes / hoods / blades painting through the
  body); a back item hides the cape like in game
- Pause menu: vanilla layout (Back to Game, Advancements | Statistics, Relay | Native Mods, Options | Open to LAN,
  Save and Quit) with Native's own buttons
- Relay chat: opens at the latest message, never auto-loads older pages while opening, keeps the top visible
  message in place while pictures / older pages load, follows new content at once when at the bottom
- HUD editor: resize from any of the 4 corners (the opposite corner stays put), scoreboard + boss bar previews at
  their real vanilla size, compact movable Done bar

## Shipped in 1.8.6
- Settings page remade: cards (General, Controls, HUD tiles), profile card with stats + Open locker, about card
- Cosmetics: faces seen from behind are drawn too (no see-through holes in hats/wings in the preview)
- Title screen: ad banners crossfade; failed opens are logged; 26.x clicks use the fresh cursor position
- ui-probe clicks every title button + Native Mods tabs on 1.16.5 - 26.3

## Shipped in 1.8.5

- **Crash on 26.3 (exit 0xC0000409)**: UI textures were never freed. Every chat picture and GIF frame kept a
  `DynamicTexture` (GPU memory + a native pixel buffer) for the whole session, so the Relay GIF picker could
  exhaust the driver. `ui/gfx/TextureBudget` now frees textures unused for 20 s (and the oldest over 192),
  on 26.x (`GuiRenderer26`) and on GL (`GlRenderer`); never one drawn in the last 1.5 s.
- Huge pictures/GIFs are refused before stb decodes them (`GifHeader`, `stbi_info_from_memory`); the GIF delay
  array stb allocates is freed.
- Relay chat: **mouse wheel works from the bottom of a chat** (`Scroll` decided "pinned to bottom" from the
  animated offset after the wheel had moved, so every step up was undone).
- Relay chat: **no more jumping up and down**. Only on-screen pictures are fetched (the 80-entry media cache used
  to evict/re-download in a loop in long chats), picture heights are remembered, failed pictures have one height,
  and loading older messages keeps the view in place.
- Clicks on "Jump to latest" no longer open the picture underneath.

## Shipped in 1.8.4

- Only the game hooks for this version's naming load (26.x official names vs intermediary), so 26.3 no longer logs "Error loading class" warnings for the old hooks.
- Title screen: one still picture of the Minecraft version (from the launcher), no more rotating backgrounds.

## Shipped in 1.8.3
- **Ads on the title screen** — the same cards as the launcher's Home (under the Friends card,
  or bottom-right on narrow windows), rotating every 12 s, with ✕ to hide one for the session.
  The launcher writes `.native/ads.json` pointing at the banners it already downloaded and the
  picture of the player's own skin, so nothing is downloaded twice; without the launcher the mod
  reads `/v1/site/ads` itself and keeps banners in `.native/ads/`.
- Ad buttons: **open a link** or **join a server** (`McBridge.connect`, `ui/ServerJoin.java`,
  reflective so it covers 1.16 → 26.3).

## Shipped in 1.8.2
- HUD text modules have a **Show labels** switch (Appearance). Off shows the bare number,
  so FPS reads `144` and CPS `9 | 2`; the keystroke overlay drops the `CPS` unit as well.
- Bigger, clearer icons in the menu rail.

## Shipped in 1.8.1

### 1. Black title screen on 26.3 — fixed
26.3 culls back faces in the GUI pipeline, so our clockwise quads were dropped.
`ui26/GuiRenderer26.java` now emits counter-clockwise quads
(`x0y0 → x0y1 → x1y1 → x1y0`, colours `k4, k4+3, k4+2, k4+1`) and the free-geometry path
iterates `0, 3, 2, 1`. Verified on a real 26.3 Fabric instance: the full Native UI draws.

### 2. GLFW → SDL on 26.3 — fixed
Every GLFW touch now sits behind the version bridge:
- `McBridge` gained `keyState`, `keyLabel`, `windowFocused`, `windowMinimized`
  (implemented with GLFW in `IntermediaryMc`, reflectively via `InputConstants` /
  `Minecraft.isWindowActive` in `MojangMc`).
- `ui/mod/Keys.java` and `ui/mod/PerformanceModules.java` no longer import GLFW;
  `Keys` falls back to a bitset fed by `Keys.track(code, action)` and is cleared when the
  window loses focus.
- `GlfwInput` is loaded reflectively through the new `RawInput` interface, so the class is
  never linked on 26.3.
- New `ui/SdlKeys.java` translates SDL scancodes and mouse buttons to the GLFW numbering
  the UI and the keybind settings use (detected at runtime by probing for
  `org.lwjgl.glfw.GLFW`), so keyboard and mouse input work on 26.1/26.2.

### 3. Avatar in the username pill — done
The toolbar pill in `MenuView` draws the player head (`Avatars.self`, directory skin hash →
Relay `meSkin` → Mojang UUID) with an online dot.

### 4. Scoreboard sidebar redesign — done
`OverlayModules.Scoreboard` was re-spaced (padding, row air, title cap plus hairline divider)
and scores are drawn as chips; blank rows render as hairlines.

### 5. Essentials-style pause screen — done
`PauseView` is a two-column layout: `PlayerPreview` card with an **Open locker** button
(`UiRuntime.openMenu(screen, 1)`) on the left, menu on the right. Falls back to the old
one-column panel on small windows.

### 6. Prefetch cosmetic assets — done
`Wardrobe.warm()` preloads worn models/textures, dyes, the equipped cape and up to 64 owned
thumbnails after every successful refresh; `UiRuntime` refreshes at startup and whenever the
logged-in Relay account changes.

### 7. Menu polish
- Only one **Edit HUD** entry (the toolbar), the icon rail is icon-only with an active marker
  and tooltips.
- Toolbar and username pills use a 13px radius so the square avatar fits.
- Opening the HUD editor outside a world uses a real in-game screenshot
  (`uiassets/hudbg.jpg`) as the backdrop instead of the menu artwork.
