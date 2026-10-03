#!/usr/bin/env bash
# Starts a REAL Minecraft client (Fabric, software OpenGL under Xvfb) with the Noctra jar and
# checks that an animated cape actually plays: a probe mod registers a cape texture under the
# id the game gives a downloaded Noctra cape, then reads it back from the GPU several times.
# Passes only when every frame of the 4-frame test strip (red, green, blue, yellow) shows up.
#
#   ci/client/run.sh <minecraft> <fabric-loader> <path/to/noctra-client.jar>
#
# Needs: java (the version Minecraft wants, JDK) on PATH, python3, zip, Xvfb + Mesa.
set -euo pipefail
MC="$1"; LOADER="$2"; JAR="$(readlink -f "$3")"
HERE="$(cd "$(dirname "$0")" && pwd)"
export WORK="${RUNNER_TEMP:-/tmp}/noctra-client"
mkdir -p "$WORK"
python3 "$HERE/setup.py" "$MC" "$LOADER"
python3 "$HERE/assets.py" "$MC"
R="$WORK/mc-$MC"
pick() { python3 -c "import json,sys;print([p for p in json.load(open('$R/launch.json'))['cp'] if sys.argv[1] in p][0])" "$1"; }
LOADER_JAR=$(pick fabric-loader); LWJGL=$(pick "/org/lwjgl/lwjgl/"); GL=$(pick lwjgl-opengl)
rm -rf "$WORK/probe" && mkdir -p "$WORK/probe/cprobe"
javac -nowarn -source 8 -target 8 -cp "$LOADER_JAR:$LWJGL:$GL" -d "$WORK/probe" "$HERE/ClientProbe.java" 2>/dev/null
echo '{"schemaVersion":1,"id":"cprobe","version":"1.0.0","environment":"client","entrypoints":{"client":["cprobe.ClientProbe"]}}' > "$WORK/probe/fabric.mod.json"
G="$R/game"; rm -rf "$G/mods"; mkdir -p "$G/mods"
(cd "$WORK/probe" && zip -qr "$G/mods/cprobe.jar" .)
cp "$JAR" "$G/mods/"

python3 "$HERE/mock.py" > "$WORK/mock.log" 2>&1 &
MOCK=$!
if [ -z "${DISPLAY:-}" ]; then Xvfb :99 -screen 0 1280x720x24 > "$WORK/xvfb.log" 2>&1 & XVFB=$!; export DISPLAY=:99; fi
trap 'kill $MOCK ${XVFB:-} 2>/dev/null || true' EXIT
sleep 2

CP=$(python3 -c "import json;print(':'.join(json.load(open('$R/launch.json'))['cp']))")
AI=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['assetIndex'])")
MAIN=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['main'])")
cd "$G"
set +e
LIBGL_ALWAYS_SOFTWARE=1 timeout 300 java -Xmx2G -Djava.library.path="$R/natives" -Dorg.lwjgl.librarypath="$R/natives" \
  -Dnoctra.api=http://127.0.0.1:8099 -cp "$CP" "$MAIN" --username TestAlice --version "$MC" --gameDir "$G" \
  --assetsDir "$R/assets" --assetIndex "$AI" --accessToken 0 --uuid 00000000000000000000000000000001 \
  --userType legacy --versionType release > "$G/out.log" 2>&1
set -e
grep -E "CPROBE (registered|distinct|FATAL)|\[Noctra\]" "$G/out.log" || true
if ! grep -q "CPROBE distinct=.*ff0000ff/ff0000.*" "$G/out.log" \
  || ! grep "CPROBE distinct=" "$G/out.log" | grep -q "00ff00ff/00ff00" \
  || ! grep "CPROBE distinct=" "$G/out.log" | grep -q "0000ffff/0000ff" \
  || ! grep "CPROBE distinct=" "$G/out.log" | grep -q "ffff00ff/ffff00"; then
  echo "::error::The animated cape did NOT play on Minecraft $MC"; tail -80 "$G/out.log"; exit 1
fi
echo "OK: animated cape plays on Minecraft $MC"
