# Native client mods: handoff and TODO

Next feature for `atlas-thedev/native-mod`: **Lunar / Feather-style client features inside the Native UI**. That means toggleable
modules, a HUD editor, and a cosmetics wardrobe with a full 3D player preview. It must work on Minecraft **1.16 to 1.21.11**
(intermediary names). 26.x (official names) comes later.

Status: **1.7.0 client mods are done and on `main`** (built, unit tests pass, tested on real 1.21.4 and 1.16.5 clients under Xvfb).
Not released yet: no tag, `release.bat` has not been run (only when the owner asks). Left: the 26.x port, and 1.20.1 / 1.21.11 smoke tests.

Menu design (final): floating icon rail (logo, Mods / Cosmetics / Settings, Edit HUD, Close), a small toolbar pill (Edit HUD, grid / list),
one big panel. Mods = Feather-style tiles (big icon, name + switch, heart = favourite, saved as `favorite` in native-modules.json), click a tile
for its own page (settings left, live preview right). Cosmetics = launcher Locker layout (preview stage + Equip / Take off button on the left,
slot tabs + "Your locker" grid with a "Nothing" card on the right; selecting a card tries it on).

---

## 1. Design system (copy the launcher look exactly)

Everything is drawn by our own GL layer (`ui/gfx/*`), not by vanilla widgets. The look is a dark, monochrome glass style with soft hairlines,
the same as the Native launcher.

### Colours (`ui/Theme.java`, ARGB)
| Token | Value | Use |
|---|---|---|
| PAGE | `#FF000000` | full-screen background |
| SURFACE | `#FF050608` | big panels |
| PANEL / PANEL_HOVER / PANEL_PRESSED | `#FF08090C` / `#FF0E1014` / `#FF14171D` | cards, buttons |
| HAIRLINE / HAIRLINE_STRONG | white 8 % / 16 % | 1 px outlines, dividers |
| BORDER_HOVER | white 28 % | hovered outline |
| SUBTLE / SUBTLE_HOVER | white 6 % / 12 % | ghost fills, chips |
| TEXT / TEXT_STRONG / TEXT_SECONDARY / TEXT_MUTED | `#D2D2D2` / `#FFFFFF` / `#A1A1AA` / `#75717A` | text hierarchy |
| ACCENT | `#FFD9A6DA` | rare highlight (pink-lilac) |
| ONLINE / IDLE / DANGER | `#22C55E` / `#F59E0B` / `#EF4444` | status, destructive |
| SOLID_FG | `#FF09090B` | text on the white primary button |
- Glass card: fill `0xB808090C` (hover `0xD80E1014`) + 1 px HAIRLINE outline (hover BORDER_HOVER).
- Primary button: white `#F4F4F5` (hover `#FFFFFF`, pressed `#D4D4D8`) with SOLID_FG text.
- Danger button: fill red 15 %, border red 30 %, text `#FCA5A5`.
- Shadows: `c.shadow(x, y+2, w, h, r, 8..24, 0x55000000..0x88000000)`.
- Use `Theme.mix(a, b, t)` for hover colours and `Theme.alpha(c, a)` for fades. Never hard-code a new grey; pick a token.

### Type and icons
- Font: **Poppins** (`Fonts.REGULAR / MEDIUM / SEMIBOLD / BOLD`), subset to Latin. Sizes: 34 bold (brand), 18 to 20 semibold (panel titles),
  14 semibold (buttons ≥ 40 px), 12.5 (normal buttons/body), 11 to 11.5 (captions/tooltips).
- Icons: **Lucide** font, drawn with `c.icon(codepoint, size, cx, cy, argb)`. Codepoints live in `Theme.I_*`.
  The bundled `lucide.ttf` is a **subset**. If you need a new icon, add its codepoint to Theme **and** regenerate the subset from the full
  lucide.ttf (pyftsubset with `--unicodes=` for every `I_*` value), then base64 it into `src/main/uiassets/lucide.ttf.b64`.
- Corner radius: 10 for buttons (or h/2 for pills), 12 to 16 for cards/panels, 6 for tooltips and small chips.

### Layout and units
- Coordinates are **logical px**: `scale = max(0.75, min(fbW/1280, fbH/760)) * config.scale`, so the design canvas is about 1280×760.
- Spacing scale is 4, 8, 12, 16, 24. Buttons are 38 (secondary) or 46 (menu) tall. Menu column width is 300.
- Motion: everything eases with `ui.anim(id, target, speed)` (exponential; 12 to 16 for hover, 20 to 22 for press).
  Entrance animations are a staggered fade plus slide (see TitleView: 45 ms stagger, cubic ease-out, 26 px slide).

### Immediate-mode API (`ui/Ui.java`, `ui/gfx/Canvas.java`)
- Canvas: `fill, gradientV/H, round, outline, shadow, circle, text, textWidth, lineHeight, ellipsize, icon, image, imageCover, stamp(key,img,...)`,
  `pushClip/popClip`, `pushAlpha/popAlpha`, **`freeQuad(Image, float[8] xy, float[8] uv, argb)`** (arbitrary textured quads, used for 3D).
- Ui: `hover, clicked(id,...), isPressing, key(code), anim, button(id,x,y,w,h,label,icon,BTN_*)`, `iconButton`, `toggle`, `dots` (spinner),
  `tooltips()` (call last). The text input is `TextField`, and scrolling lists use `Scroll` (`begin`/`end`).
- Widgets still to add (keep the same look): **slider** (track SUBTLE, fill white, 14 px white knob), **segmented choice** (pill group, selected = white
  with SOLID_FG text), **colour swatches** (8 presets + current colour), **keybind button** ("Press a key…" while listening, Esc cancels).

### Components already in use (reuse them)
- `TitleView`: brand, menu buttons (icon tile + label + chevron), quit/classic row, Relay card, footer.
- `RelayView`: sidebar list, chat bubbles, avatars (`Avatars.draw`), composer.
- `Toasts`: top-right message toasts.

---

## 2. Architecture you must know

- **Frame hook**: `UiFrameMixin` injects into `RenderSystem.flipFrame` HEAD, which calls `UiRuntime.onFrame()`. That runs every frame, and it
  must return immediately when nothing is on screen.
- **Screens**: `NativeHostScreen` is an empty vanilla screen (kinds: `McBridge.TITLE=1`, `RELAY=2`). It releases the cursor and pauses input,
  and our UI paints on top. Add **`MENU=3`** (mods/cosmetics/settings) and **`HUD=4`** (HUD editor). Remember: `NativeHostScreen`'s title string,
  `UiRuntime.frame()` branching, and the Esc handling in `UiRuntime.onKey`.
- **Input**: `GlfwInput` wraps Minecraft's GLFW callbacks. `UiRuntime.onButton/onScroll/onKey/onChar` return true to **consume** an event.
  `capturing` is true while a host screen is open.
- **Bridge**: `McBridge` (interface in main) and `uimc/IntermediaryMc` (1.16 to 1.21.11). The latter is compiled against **stubs** in `src/mcstub`
  (compile-only, intermediary names). For new game access, prefer **reflection with intermediary names** inside IntermediaryMc, cached in
  static fields. Wrap everything in try/catch, and return safe defaults on failure.
- **Assets**: binaries can't be pushed as-is. Store them as base64 in `src/main/uiassets/*.b64`; Gradle `decodeUiAssets` decodes them at build time.
- **Config**: `config/native-ui.json` (`UiConfig`: customTitle, notifications, relayKey=89 (Y), scale). Add `menuKey=344` (Right Shift).
  Module config goes in `config/native-modules.json`.
- **Errors**: any throwable calls `UiRuntime.fail()`, which disables the UI for the session. Never let a module crash the game.

---

## 3. Building blocks

- `ui/gfx/Boxes.java`: CPU orthographic box renderer for previews. `begin(cx,cy,scale,yaw,pitch)`, `push/pop/translate`,
  `rotate(pitch,yaw,roll)` (vanilla Rz·Ry·Rx), and `cube(tex,texW,texH,x,y,z,w,h,d,u,v,inflate,mirror,tint)` with box UV. It also does back-face cull,
  depth sort, simple lighting, and `end(canvas)` emits `freeQuad`s. **Check the x-orientation** with a real skin (the face must not be mirrored).
- `ui/gfx/Canvas.java` + `GlRenderer.java`: free quads (`free[]`, `freeGeo`, `freeQuad`).
- `ui/mod/Setting.java`: `Bool, Num(min,max,step,suffix; set(), text()), Color, Choice(options), Key`, plus Gson save/load/reset.
- `ui/mod/Module.java`: id, name, description, category (`HUD / Mechanic / Visual`), icon, enabled, settings, `isHud()`,
  `setEnabled → onEnable/onDisable`, `frame(Game)`.
- Avatars from skins: `RelayClient.meSkin`, `skinRef(nativeSkin, mojangUuid)` → hash or `"mj:<uuid>"`. `Avatars` resolves `mj:` through the
  Mojang session server, and `RelayView` own messages use `meSkin`. **The server side is already deployed** (native-client 5b0c14d): `/v1/mod/account`
  returns `skin`, friends return `mcUuid`, and the game ticket (`nmt1.`) is accepted for `GET /v1/store/me` and `POST /v1/store/equip`
  (equipping an unowned item returns 403).

---

## 4. TODO

### 4.1 Game snapshot (`ui/mod/Game.java`)
- [x] fps (count frames in onFrame, 1 s window), x/y/z, yaw/pitch, speed (blocks/s from position delta), ping, server address,
      key states, lmb/rmb, **CPS** (count presses in `onButton` when not capturing, 1 s sliding window), memory used/max.
- [x] `Game.sample()` with fake values for previews when not in a world.

### 4.2 McBridge additions (default methods) + IntermediaryMc via reflection
Verified stable names, 1.16 to 1.21.11 (runtime = intermediary):
- player `class_310.field_1724` (class_746). Entity x/y/z `method_23317/23318/23321`. yaw `method_36454()` (1.17+), else field `field_6031` (1.16).
  uuid `method_5667`.
- server `class_310.method_1558()` → `class_642.field_3761` (address). ping: `class_310.method_1562()` → `class_634.method_2871(UUID)` → `class_640.method_2959()`.
- options `class_310.field_1690`: hudHidden `field_1842`. Debug screen: `field_1866` (≤1.20.1), else `class_310.method_53526()` → `class_340.method_53536()`.
- KeyBindings (class_304) on options: forward `field_1894`, jump `field_1903`, sprint `field_1867`. **Unverified:** left `field_1913`, back
  `field_1881`, right `field_1849`, attack `field_1886`, use `field_1904`. Use `setPressed` `method_23481(boolean)`. Bound key: `field_1655`
  (class_3675$class_306) `.method_1444()` → GLFW code (code ≤ 7 = mouse button).
- gamma: ≤1.18.2 `class_315.field_1840` (double). 1.19+ `method_42473()` → class_7172, value field `field_37868` (set directly; restore on disable).
- chat screen class `class_408`. Show the HUD when screen == null or the screen is chat.

### 4.3 Modules (`ui/mod/HudModule.java`, `ui/mod/Modules.java`)
- [x] `HudModule`: anchor as a fraction of the screen (x,y in 0..1), `scale` Num, text colour, background on/off + opacity, shadow,
      style Choice (Card / Text / Brackets), `size()` and `paint(ui, x, y, Game)`.
- [x] HUD modules: **FPS, CPS, Keystrokes** (WASD + LMB/RMB + Space, using the bound keys, pressed state animated), **Coordinates** (+ facing),
      **Direction** compass strip, **Clock** (12/24 h), **Memory**, **Ping**, **Server IP**, **Speed**, **Toggle Sprint** (status label).
- [x] Mechanic/Visual: **Toggle Sprint** (keeps sprint pressed through `method_23481`), **Zoom** (hold C=67, scroll to change, smooth,
      consume scroll while zooming), **Fullbright** (gamma 16, restore old value).
- [x] Zoom mixin (uimc): `@Pseudo @Mixin(targets="net.minecraft.class_757", remap=false)`, method `method_3196` (getFov)
      `@At("RETURN")`, `require=0`, `CallbackInfoReturnable<Object>`. It returns **double ≤1.21.1** and **float 1.21.4+**, so set the matching
      boxed type. Add it to `native.mixins.json`.
- [x] `Modules` registry, load/save `config/native-modules.json` (Gson: `{id: {enabled, settings{...}, x, y}}`).
- [x] Per-frame HUD drawing in `UiRuntime.frame()` while in a world (not when F1/F3 is on or a non-chat screen is open). Keep it cheap:
      no allocation per frame where possible.

### 4.4 Native menu (host kind MENU)
- [x] Opened with **Right Shift** in a world, and from the title screen (new menu item "Native Mods", `I_SPARKLES`).
- [x] Layout: a centred glass window (~920×600) with a left tab rail: **Mods**, **Cosmetics**, **Settings**. Header with a search field.
- [x] **Mods tab**: category chips (All / HUD / Mechanic / Visual), a grid of cards (icon tile, name, one-line description, toggle, gear),
      and a detail panel with the setting widgets, a **live preview** of the HUD module (`Game.sample()`), a "Reset" button, and an
      "Edit HUD layout" button.
- [x] **Cosmetics tab** (wardrobe):
  - 3D `PlayerPreview` using Boxes. Skin is 64×64 (or legacy 64×32: mirror the arms/legs), slim arms when the account model is slim.
    Drag to rotate, plus a slow idle sway.
  - Cape: 64×32 texture, cube (-5,0,-1, 10×16×1) at body `translate(0,0,2)`, Rx (slight tilt) then Ry π.
  - Worn cosmetics: build `CosmeticRef(id, modelHash, textureHash, slot, side)` from the catalog `modelUrl/textureUrl` hashes, then
    `CosmeticLibrary.get(ref, base)`. Draw them with a `CosmeticSink` implemented on Boxes (attach = vanilla bone pivots:
    head/body 0,0,0; arms ±5,2,0 (slim ±5,2.5,0); legs ±1.9,12,0). Walk with `CosmeticRenderer.render(...)` (handles `side` + animations).
    Get the texture through `TextureCache.getOrDownload(base, hash)` → `Image.decode`, with `linear=false`.
  - List of **owned** items grouped by slot: hats, glasses, back, shoes, hand, plus capes. Thumbnail = catalog `stillUrl`.
    Click to equip/unequip, with an equipped badge.
  - API: `GET /v1/store/me` (ticket) → `owned, equipped, wearing, sides`. `GET /v1/store/catalog` (public) → `items[{id,name,section,
    kind: cosmetic|cape, slot, stillUrl, modelUrl, textureUrl}]`. Equip: `POST /v1/store/equip {itemId}`. Unequip: `{slot}`
    (cape: `{itemId:null}`). Header: `Authorization: Bearer <ticket>` (same ticket as RelayClient). Run the calls off-thread.
  - After an equip, refresh the player's own in-game cosmetics (SkinRefresh / skin directory) so the change shows in the world too.
- [x] **Settings tab**: Native title screen on/off, notifications, chat key, menu key, UI size slider.

### 4.5 HUD editor (host kind HUD)
- [x] All enabled HUD modules shown with a dashed outline. Drag to move, with **snapping guides** to screen edges, the centre, and other modules.
- [x] Scroll over a module to scale it, right-click to open its settings popover. Toolbar: Done / Mods / Reset positions.
- [x] Dim the world behind it a little, and show a grid while dragging.
- [x] Corner resize handle (drag the bottom-right dot). Toolbar sits at the bottom.

### 4.5b Server overlays + performance (`ui/mod/Overlays.java`, `OverlayModules.java`, `PerformanceModules.java`)
- [x] Server **scoreboard sidebar** and **boss bars** are HUD modules. Look "Vanilla" = the game draws them, `SidebarMixin`/`BossBarMixin`
      (@Pseudo, intermediary names) push a pose translate+scale so they move/resize. Look "Native" = vanilla hidden, data read via
      `McBridge.sidebar()/bossBars()` (`IntermediaryMc`, reflection, legacy § + Text style colours via `Rich`) and drawn in Poppins.
      If reading fails it logs `[NativeSidebar]` once and vanilla stays.
- [x] Background FPS (Performance category): caps FPS when unfocused (30) / minimized (5).
- [x] Launcher: default RAM picked from total system memory, default JVM preset Aikar.
- [ ] Idea: optional launcher "performance pack" (Sodium, Lithium, FerriteCore, EntityCulling, ImmediatelyFast) for Fabric.

### 4.6 Testing + release
- [x] `./gradlew build --no-daemon -q`. The build is slow, so run it in the background and poll.
- [x] Run real clients under Xvfb with `ci/ui` (see its README). Extend `mock.py` with `/v1/store/me`, `/v1/store/catalog`, `/v1/store/equip`
      and the account `skin`. Test **1.21.4** and **1.16.5** at least (done), ideally also 1.20.1 and 1.21.11 (not yet). Take screenshots of the menu, cosmetics
      preview, HUD in world, and HUD editor.
- [x] Bump the version to **1.7.0**. Tag/release with `release.bat` only when the owner asks.
- [ ] Later: 26.x port (official names) of the UI + modules.

## 5. Gotchas
- Pushes go through the GitHub MCP `push_files` (≤ 1 MiB per call, **text only**). Binaries must be base64 `.b64`.
- The sandbox can lose installed packages: `sudo dnf install -y java-25-amazon-corretto-devel java-17-amazon-corretto java-21-amazon-corretto
  xorg-x11-server-Xvfb mesa-dri-drivers mesa-libGL` and `pip3 install python-xlib`.
- Old `src/client/` is unused (leftover). Don't build on it.
- Do not touch vanilla rendering state: GlRenderer saves/restores GL state. Keep doing that for anything new.
