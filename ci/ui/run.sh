#!/usr/bin/env bash
# Starts the REAL Minecraft 26.3 client classes under Fabric Loader (no window, no GPU) with the
# Noctra jar and checks that the Noctra title-screen mixin was applied to TitleScreen.
#
#   ci/ui/run.sh <path/to/noctra-client.jar>      (run `./gradlew fetchMinecraft` first)
set -euo pipefail
JAR="$(readlink -f "$1")"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
MC="$ROOT/build/minecraft/26.3"
WORK="${RUNNER_TEMP:-/tmp}/noctra-ui-probe"
rm -rf "$WORK"; mkdir -p "$WORK/lib" "$WORK/out" "$WORK/game/mods"
cd "$WORK"

FABRIC=https://maven.fabricmc.net
CENTRAL=https://repo1.maven.org/maven2
get() { curl -fsSL -o "lib/$(basename "$1")" "$1"; }
get "$FABRIC/net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
get "$FABRIC/net/fabricmc/sponge-mixin/0.17.4+mixin.0.8.7/sponge-mixin-0.17.4+mixin.0.8.7.jar"
for a in asm asm-analysis asm-commons asm-tree asm-util; do get "$CENTRAL/org/ow2/asm/$a/9.10.1/$a-9.10.1.jar"; done

CP="$(ls lib/*.jar | tr '\n' ':')$(ls "$MC"/libs/*.jar | tr '\n' ':')$MC/client.jar"

javac -nowarn -cp "$CP:$JAR" -d out "$HERE/CProbe.java"
echo '{"schemaVersion":1,"id":"cprobe","version":"1.0.0","environment":"client","entrypoints":{"preLaunch":["cprobe.CProbe"]},"depends":{"noctra":"*"}}' > out/fabric.mod.json
(cd out && jar cf ../game/mods/cprobe.jar .)
cp "$JAR" game/mods/noctra.jar

set +e
timeout 300 java -Xmx1G -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --gameDir "$WORK/game" --assetsDir "$WORK/game/assets" --version 26.3 --accessToken x > client.log 2>&1
set -e
grep -E "CPROBE|\[Noctra\]" client.log || true
if ! grep -q "CPROBE TitleScreen init=true render=true" client.log; then
  echo "::error::The Noctra title screen was NOT applied to Minecraft 26.3's TitleScreen"; tail -60 client.log; exit 1
fi
echo "OK: Noctra title screen applies on Minecraft 26.3"
