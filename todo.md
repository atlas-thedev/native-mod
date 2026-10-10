# Native mod — todo

Current release: **1.8.0** (Minecraft 1.16 – 26.3).

## 1. Fix the black title screen on 26.3 — blocking

On 26.3 the Native UI renders a pure black window. No errors; the log shows
`[Native] Native UI ready` and `[Native] Native UI renderer ready (Minecraft GUI pipeline…)`.
Reproduced in a headless 26.3 Fabric instance (Vulkan/lavapipe).

### What was ruled out
Every 26.3 API the renderer touches still matches 26.2:
`ToastManager.extractRenderState`, `Gui.setScreen`, `Screen.extractRenderState` /
`extractBackground`, `GuiElementRenderState` (4 abstract methods + `bounds()` from
`ScreenArea`), `TextureSetup.singleTexture`, `SamplerCache.getClampToEdge`,
`RenderPipelines.GUI_TEXTURED`, `GuiRenderState.addGuiElement`, `NativeImage.getPointer`,
`DynamicTexture.upload`, `nextStratum`, vertex format `POSITION_TEX_COLOR`.
The only rename is `com.mojang.blaze3d.pipeline` → `com.mojang.renderpearl.api.pipeline`,
which is already handled reflectively. Not a mod conflict either — black with only
Fabric API + Native installed.

Diagnostics proved the elements *are* submitted and the vertices *are* built:
`batches=4 quads=110 guiScale=3 gui=427x240 atlas=1024`, full-screen coordinates,
`buildVertices called, count=4` — yet every pixel is `#000000`.

### Leading hypothesis: quad winding / face culling
Vanilla `ColoredRectangleRenderState.buildVertices` emits
`x0y0 → x0y1 → x1y1 → x1y0` (counter-clockwise).
`ui26/.../GuiRenderer26.java` emits `x0y0 → x1y0 → x1y1 → x0y1` (clockwise).
26.3 also swapped the GUI pipeline's bind group layout from
`MATRICES_PROJECTION` to `BindGroupLayouts.DYNAMIC_TRANSFORMS`. If 26.3 turned on
back-face culling, our clockwise quads are culled — invisible, no error.

**Fix to apply in `src/ui26/java/xyz/nativelaunch/ui26/GuiRenderer26.java`:**
- Standard quad path: write
  `(x0,y0,u0,v0,col[k4])` → `(x0,y1,u0,v1,col[k4+3])` → `(x1,y1,u1,v1,col[k4+2])` → `(x1,y0,u1,v0,col[k4+1])`.
- Free-geometry path: iterate `for (int j = 0; j < 4; j++) { int i = j == 0 ? 0 : 4 - j; … }`
  (order 0, 3, 2, 1).

**Still to do:** build, run on a real 26.3 instance and confirm the UI draws.
If it is still black, bisect further — submit a vanilla `ColoredRectangleRenderState`
red rectangle as a control, and check whether `withCull` is set on the 26.3
`GLOBALS_SNIPPET`. Remove any temporary `DIAG` logging before release.

## 2. 26.3 replaced GLFW with SDL — confirmed broken

26.3 logs `Created window using SDL video driver: …`; `org.lwjgl.glfw.GLFW` no longer
exists. Current fallout: `[Native] Module bgfps failed and was switched off:
NoClassDefFoundError: org/lwjgl/glfw/GLFW`.

Hard references to clean up:
- `src/main/java/xyz/nativelaunch/ui/GlfwInput.java` — the whole file. Only used when
  `mc.handlesInput()` is false, i.e. never on 26.x, but it still has to load.
- `src/main/java/xyz/nativelaunch/ui/mod/Keys.java` lines 16, 18, 62 — wrapped in
  try/catch, so it degrades silently: keybind polling is dead on 26.3.
- `src/main/java/xyz/nativelaunch/ui/mod/PerformanceModules.java` lines 35–36 —
  **not guarded**, this is what kills the Background FPS module.

Plan: move every GLFW touch behind the version bridge (`MojangMc` / `IntermediaryMc`)
so 26.3 uses SDL (or the vanilla key mapping API) and older versions keep GLFW.

## 3. Avatar in the username pill

`MenuView.java` (~lines 153–166) draws a pill with a green dot and
`UiRuntime.mc().username()`. Show the player's head next to the name.
`Avatars.draw(c, api, name, skinHash, x, y, size, radius)` already renders faces and
`Avatars.mojangSkinUrl(uuid)` resolves the texture, so this is mostly layout work.
Worth doing the same for `NameTags`, which today only prefixes a U+E000 glyph.

## 4. Redesign the scoreboard sidebar

`ui/mod/OverlayModules.Scoreboard.paint` / `measure` is the "Native look" renderer
(settings: look, numbers, title, colours). Rework the visual design — spacing,
background, number alignment, title treatment — in the same style as the rest of the
Native UI.

## 5. Essentials-style pause screen

`PauseView.draw` is currently one centred 340px panel (Back to game / Relay chat /
Mods and cosmetics / Options / Quit). Replace it with a two-column layout:
- the player model with its cosmetics on one side, via
  `PlayerPreview.draw(ui, id, x, y, w, h, hoverItem)`;
- a button under the model that opens the Locker, i.e. `UiRuntime.openMenu(parent)`
  followed by `MenuView.openTab(1)` (which already triggers `Wardrobe.refresh(false)`).

## 6. Prefetch cosmetic assets

Today cosmetic textures are fetched while rendering, so the model pops in. Warm the
cache at startup (and right after login) with `CosmeticLibrary.preload(refs, base)`,
backed by the existing `TextureCache` on disk, so `PlayerPreview` and the Locker open
instantly.

## Release checklist
- Remove temporary `DIAG` logging from `GuiRenderer26`.
- Bump to **1.8.1** in `gradle.properties`.
- Build and smoke-test on 1.16, 1.21.x and 26.3.
- Publish the jar to `atlas-thedev/native-mod-releases`.
