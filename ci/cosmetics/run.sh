#!/usr/bin/env bash
# Real-client 3D cosmetics test: boots a Fabric dedicated server (flat, offline) and a REAL Minecraft client (Mesa
# software OpenGL under Xvfb) with the Native jar, joins as TestAlice who wears one solid-colour test cosmetic
# per slot, switches to the third-person front camera and screenshots the window. Passes only when the hat,
# glasses, back item and shoes all show up on screen.
#
#   ci/cosmetics/run.sh <minecraft> <fabric-loader> <path/to/native-client.jar>
set -euo pipefail
MC="$1"; LOADER="$2"; JAR="$(readlink -f "$3")"
HERE="$(cd "$(dirname "$0")" && pwd)"
export WORK="${RUNNER_TEMP:-/tmp}/native-client"
mkdir -p "$WORK"
python3 "$HERE/../client/setup.py" "$MC" "$LOADER" >/dev/null
python3 "$HERE/../client/assets.py" "$MC" >/dev/null
R="$WORK/mc-$MC"
pick() { python3 -c "import json,sys;print([p for p in json.load(open('$R/launch.json'))['cp'] if sys.argv[1] in p][0])" "$1"; }
LOADER_JAR=$(pick fabric-loader)
P="$WORK/cosprobe-$MC"; rm -rf "$P" && mkdir -p "$P"
javac -nowarn -source 8 -target 8 -cp "$LOADER_JAR" -d "$P" "$HERE/CosProbe.java" 2>/dev/null || { echo "::error::could not compile CosProbe"; exit 1; }
echo '{"schemaVersion":1,"id":"cosprobe","version":"1.0.0","environment":"client","entrypoints":{"client":["cosprobe.CosProbe"]}}' > "$P/fabric.mod.json"
G="$R/game-cos"; rm -rf "$G"; mkdir -p "$G/mods"
(cd "$P" && zip -qr "$G/mods/cosprobe.jar" .)
cp "$JAR" "$G/mods/"
# no first-launch screens in the way of joining the server
printf 'onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\npauseOnLostFocus:false\nrenderDistance:3\nnarrator:0\nsoundCategory_master:0.0\n' > "$G/options.txt"

# the server: flat creative world, always noon, nothing that moves
S="$WORK/server-$MC"; mkdir -p "$S"; rm -rf "$S/world" "$S/logs"
if [ ! -f "$S/server.jar" ]; then
  INSTALLER=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys;print([x for x in json.load(sys.stdin) if x['stable']][0]['version'])")
  curl -fsSL -o "$S/server.jar" "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"
fi
echo "eula=true" > "$S/eula.txt"
LEVEL=flat; case "$MC" in 1.16*|1.17*|1.18*) LEVEL=flat;; *) LEVEL='minecraft\:flat';; esac
case "$MC" in 1.16*|1.17*|1.18*|1.19*) SPORT=25598;; *) SPORT=25597;; esac
printf "online-mode=false\nserver-port=$SPORT\nlevel-type=%s\nview-distance=3\nsimulation-distance=3\ngamemode=creative\ndifficulty=peaceful\nspawn-monsters=false\nspawn-animals=false\nspawn-npcs=false\ngenerate-structures=false\nspawn-protection=0\nmax-tick-time=-1\nenable-command-block=false\nwhite-list=false\nenforce-whitelist=false\n" "$LEVEL" > "$S/server.properties"
mkfifo "$S/in" 2>/dev/null || true
(cd "$S" && exec java -Xmx1G -jar server.jar nogui < <(tail -f "$S/in") > "$S/server.log" 2>&1) &
SERVER=$!
python3 "$HERE/mock.py" > "$WORK/cos-mock.log" 2>&1 &
MOCK=$!
if [ -z "${DISPLAY:-}" ]; then Xvfb :97 -screen 0 1280x720x24 +extension GLX -pixdepths 24 32 > "$WORK/xvfb-cos.log" 2>&1 & XVFB=$!; export DISPLAY=:97; fi
# old clients join through a relay that holds the login until resources are loaded (see delay-proxy.py)
if [ "$SPORT" != 25597 ]; then python3 "$HERE/delay-proxy.py" 25 > "$WORK/cos-proxy.log" 2>&1 & PROXY=$!; fi
cleanup() { echo stop > "$S/in" 2>/dev/null & sleep 1; kill $SERVER $MOCK ${XVFB:-} ${CLIENT:-} ${PROXY:-} 2>/dev/null || true; pkill -f "tail -f $S/in" 2>/dev/null || true; }
trap cleanup EXIT
for i in $(seq 1 240); do grep -q 'Done (' "$S/server.log" 2>/dev/null && break; sleep 1; done
grep -q 'Done (' "$S/server.log" || { echo "::error::server did not start"; tail -40 "$S/server.log"; exit 1; }
printf 'gamerule doDaylightCycle false\ntime set noon\nweather clear 99999\ngamerule doWeatherCycle false\n' > "$S/in"

CP=$(python3 -c "import json;print(':'.join(json.load(open('$R/launch.json'))['cp']))")
AI=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['assetIndex'])")
MAIN=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['main'])")
ALICE=$(python3 -c "import hashlib;b=bytearray(hashlib.md5(b'OfflinePlayer:TestAlice').digest());b[6]=b[6]&0x0f|0x30;b[8]=b[8]&0x3f|0x80;print(bytes(b).hex())")
case "$MC" in 1.16*|1.17*|1.18*|1.19*) JOIN=(--server 127.0.0.1 --port 25597);; *) JOIN=(--quickPlayMultiplayer 127.0.0.1:25597);; esac
# old clients ask Mojang whether multiplayer is allowed for the (fake) token: point authlib at the mock
M=http://127.0.0.1:8097
case "$MC" in 1.16*|1.17*|1.18*|1.19*) AUTHLIB=(-Dminecraft.api.auth.host=$M -Dminecraft.api.account.host=$M -Dminecraft.api.session.host=$M -Dminecraft.api.services.host=$M);; *) AUTHLIB=();; esac
cd "$G"
SDL_VIDEO_FORCE_EGL=1 SDL_VIDEO_X11_FORCE_EGL=1 LIBGL_ALWAYS_SOFTWARE=1 timeout 420 java -Xmx2G -Djava.library.path="$R/natives" -Dorg.lwjgl.librarypath="$R/natives" \
  -Dnative.api=http://127.0.0.1:8097 "${AUTHLIB[@]}" -cp "$CP" "$MAIN" --username TestAlice --version "$MC" --gameDir "$G" \
  --assetsDir "$R/assets" --assetIndex "$AI" --accessToken 0 --uuid "$ALICE" --width 854 --height 480 \
  --userType legacy --versionType release "${JOIN[@]}" > "$G/cos.log" 2>&1 &
CLIENT=$!
for i in $(seq 1 400); do grep -qE 'COSPROBE (ready|FATAL)' "$G/cos.log" 2>/dev/null && break; kill -0 $CLIENT 2>/dev/null || break; sleep 1; done
grep -E "COSPROBE|\[Native\]|osmetic" "$G/cos.log" || true
if ! grep -q 'COSPROBE ready' "$G/cos.log"; then echo "::error::Minecraft $MC never reached the world"; tail -80 "$G/cos.log"; exit 1; fi
SHOT="$WORK/cosmetics-$MC.png"
import -display "$DISPLAY" -window root "$SHOT"
touch "$G/shot.done"
convert "$SHOT" -depth 8 "rgb:$WORK/cosmetics-$MC.rgb" 2>/dev/null
W=$(identify -format '%w' "$SHOT"); H=$(identify -format '%h' "$SHOT")
if ! python3 "$HERE/check.py" "$WORK/cosmetics-$MC.rgb" "$W" "$H"; then
  echo "::error::3D cosmetics are NOT drawn on Minecraft $MC (screenshot: $SHOT)"; tail -60 "$G/cos.log"; exit 1
fi
echo "OK: hat, glasses, back item and shoes render on Minecraft $MC"
