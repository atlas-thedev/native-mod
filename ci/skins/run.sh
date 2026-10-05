#!/usr/bin/env bash
# Real-client skin test: boots Minecraft (Fabric, software OpenGL under Xvfb) with the Native jar and asks the
# game itself which skin it would draw for the local player and for two other players on an offline server.
# Passes only when all three get their Native skin (not the default Steve/Alex).
#
#   ci/skins/run.sh <minecraft> <fabric-loader> <path/to/native-client.jar>
set -euo pipefail
MC="$1"; LOADER="$2"; JAR="$(readlink -f "$3")"
HERE="$(cd "$(dirname "$0")" && pwd)"
export WORK="${RUNNER_TEMP:-/tmp}/native-client"
mkdir -p "$WORK"
python3 "$HERE/../client/setup.py" "$MC" "$LOADER" >/dev/null
python3 "$HERE/../client/assets.py" "$MC" >/dev/null
R="$WORK/mc-$MC"
pick() { python3 -c "import json,sys;print([p for p in json.load(open('$R/launch.json'))['cp'] if sys.argv[1] in p][0])" "$1"; }
LOADER_JAR=$(pick fabric-loader); GUAVA=$(pick "/com/google/guava/guava/")
P="$WORK/sprobe-$MC"; rm -rf "$P" && mkdir -p "$P"
javac -nowarn --release 8 -cp "$LOADER_JAR:$GUAVA" -d "$P" "$HERE/SkinProbe.java" 2>&1 | grep -v "^Note:" || true
echo '{"schemaVersion":1,"id":"sprobe","version":"1.0.0","environment":"client","entrypoints":{"client":["sprobe.SkinProbe"]}}' > "$P/fabric.mod.json"
G="$R/game"; rm -rf "$G/mods" "$G/.native"; mkdir -p "$G/mods"
(cd "$P" && zip -qr "$G/mods/sprobe.jar" .)
cp "$JAR" "$G/mods/"
python3 "$HERE/mock.py" > "$WORK/skin-mock.log" 2>&1 &
MOCK=$!
if [ -z "${DISPLAY:-}" ]; then Xvfb :98 -screen 0 1280x720x24 +extension GLX +extension COMPOSITE -pixdepths 24 32 > "$WORK/xvfb-skins.log" 2>&1 & XVFB=$!; export DISPLAY=:98; fi
trap 'kill $MOCK ${XVFB:-} 2>/dev/null || true' EXIT
sleep 2
CP=$(python3 -c "import json;print(':'.join(json.load(open('$R/launch.json'))['cp']))")
AI=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['assetIndex'])")
MAIN=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['main'])")
# the offline-mode UUID Minecraft itself derives from the name (version 3), as the launcher passes it
ALICE=$(python3 -c "import hashlib;b=bytearray(hashlib.md5(b'OfflinePlayer:TestAlice').digest());b[6]=b[6]&0x0f|0x30;b[8]=b[8]&0x3f|0x80;print(bytes(b).hex())")
cd "$G"
set +e
SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1 LIBGL_ALWAYS_SOFTWARE=1 timeout 300 java -Xmx2G -Djava.library.path="$R/natives" -Dorg.lwjgl.librarypath="$R/natives" \
  -Dnative.api=http://127.0.0.1:8098 -cp "$CP" "$MAIN" --username TestAlice --version "$MC" --gameDir "$G" \
  --assetsDir "$R/assets" --assetIndex "$AI" --accessToken 0 --uuid "$ALICE" \
  --userType legacy --versionType release > "$G/skins.log" 2>&1
set -e
grep -E "SPROBE|\[Native\]" "$G/skins.log" || true
fail=0
for who in alice bob carol; do
  grep -E "SPROBE $who .*skins/" "$G/skins.log" >/dev/null || { echo "::error::Minecraft $MC: $who does NOT get the Native skin"; fail=1; }
done
if [ "$fail" = 1 ]; then tail -60 "$G/skins.log"; exit 1; fi
echo "OK: Native skins show for the local player and other players on Minecraft $MC"
