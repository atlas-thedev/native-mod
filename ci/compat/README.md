# Compatibility smoke test

`run.sh` starts a **real Fabric dedicated server** for one Minecraft version with the
built Native jar loaded, then asks the game's own authlib session service for a
player's textures. The mod must swap in Native's skin and cape (served by
`mock-api.js`) and leave an unknown player alone.

It exercises the exact production path (Fabric Loader → Mixin → authlib), so a Minecraft
release that renames the hooked authlib methods fails CI *before* a release is published.

The matrix of Minecraft versions lives in `.github/workflows/build.yml`.
