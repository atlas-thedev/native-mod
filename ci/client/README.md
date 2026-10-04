# Animated cape client test

`run.sh` boots a **real Minecraft client** (Fabric, Mesa software OpenGL under Xvfb) with the built
Native jar. `mock.py` stands in for the Native API and serves one player with a 4-frame animated cape
(red, green, blue, yellow). `ClientProbe.java` (test-only mod) registers a 64×32 cape texture under the
id the game gives that downloaded cape, then reads the texture back from the GPU a few times a second.

The test passes only when all four frames are seen, i.e. the mod's animator found the texture through
the game's own texture manager and wrote the frames into it. It covers both animator paths on
1.16 – 1.21.x (GL texture id up to 1.21.4, GPU texture from 1.21.5).
