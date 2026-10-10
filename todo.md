# Native mod — todo

Current release: **1.8.2** (Minecraft 1.16 – 26.3).

Everything on the 1.8.1 list is done; see `Shipped in 1.8.1` below for what changed and
how it was verified. Add new items under `Next`.

## Next
- More for the right-shift menu — players keep asking for extra modules and settings there.

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
  `org.lwjgl.glfw.GLFW`), so keyboard and mouse input work on 26.3 and are untouched on 26.1/26.2.

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
