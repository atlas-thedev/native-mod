#!/usr/bin/env bash
# Boots a real Fabric client with the Native jar against mock.py on Xvfb :99 (left running for x.py to drive).
#   ci/ui/launch.sh <minecraft> <java-home> <native-jar> [fabric-loader]
set -euo pipefail
MC="$1"; JH="$2"; JAR="$(readlink -f "$3")"; LOADER="${4:-0.19.5}"
HERE="$(cd "$(dirname "$0")" && pwd)"
export WORK="${WORK:-/tmp/native-client}"; mkdir -p "$WORK"
python3 "$HERE/../client/setup.py" "$MC" "$LOADER" >/dev/null
python3 "$HERE/../client/assets.py" "$MC" >/dev/null
R="$WORK/mc-$MC"; G="$R/game"
rm -rf "$G/mods"; mkdir -p "$G/mods" "$G/.native"; cp "$JAR" "$G/mods/"
echo '{"ticket":"nmt1.test","api":"http://127.0.0.1:8099","account":{"name":"TestAlice"}}' > "$G/.native/session.json"
CP=$(python3 -c "import json;print(':'.join(json.load(open('$R/launch.json'))['cp']))")
AI=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['assetIndex'])")
MAIN=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['main'])")
cd "$G"
DISPLAY=:99 LIBGL_ALWAYS_SOFTWARE=1 nohup "$JH/bin/java" -Xmx2G -Djava.library.path="$R/natives" -Dorg.lwjgl.librarypath="$R/natives" \
  -Dnative.api=http://127.0.0.1:8099 -cp "$CP" "$MAIN" --username TestAlice --version "$MC" --gameDir "$G" \
  --assetsDir "$R/assets" --assetIndex "$AI" --accessToken 0 --uuid 00000000000000000000000000000001 \
  --userType legacy --versionType release --width 1280 --height 720 > "$G/out.log" 2>&1 &
echo $! > "$WORK/client-$MC.pid"; echo "started $MC pid $(cat "$WORK/client-$MC.pid") log $G/out.log"
