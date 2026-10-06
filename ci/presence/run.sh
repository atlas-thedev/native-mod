#!/usr/bin/env bash
# Boots a REAL Fabric dedicated server and a REAL Minecraft client (software OpenGL under Xvfb)
# with the Native jar, joins the server, then opens the server's world in singleplayer.
# Passes only when the mod reports both (presence.json for Relay + a fake Discord app's
# SET_ACTIVITY frames): multiplayer with a player count, then singleplayer with the world name.
#
#   ci/presence/run.sh <minecraft> <fabric-loader> <path/to/native-client.jar>
set -euo pipefail
MC="$1"; LOADER="$2"; JAR="$(readlink -f "$3")"
HERE="$(cd "$(dirname "$0")" && pwd)"
export WORK="${RUNNER_TEMP:-/tmp}/native-presence"
mkdir -p "$WORK"
python3 "$HERE/../client/setup.py" "$MC" "$LOADER" > /dev/null
python3 "$HERE/../client/assets.py" "$MC" > /dev/null
R="$WORK/mc-$MC"; G="$R/game"; S="$WORK/server-$MC"
rm -rf "$G/mods" "$G/.native" "$G/saves" "$S"; mkdir -p "$G/mods" "$S"
cp "$JAR" "$G/mods/"
# no first-run accessibility screen / multiplayer warning: they would block quick play
printf "onboardAccessibility:false\nskipMultiplayerWarning:true\njoinedFirstServer:true\ntutorialStep:none\npauseOnLostFocus:false\n" > "$G/options.txt"

# 1. the server (offline mode, no mods)
INSTALLER=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys;print([x for x in json.load(sys.stdin) if x['stable']][0]['version'])")
curl -fsSL -o "$S/server.jar" "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"
echo "eula=true" > "$S/eula.txt"
printf "online-mode=false\nserver-port=25599\nview-distance=2\nsimulation-distance=2\nmax-players=20\nspawn-protection=0\nlevel-name=world\n" > "$S/server.properties"
(cd "$S" && exec java -Xmx1G -jar server.jar nogui > server.log 2>&1) &
SERVER=$!
python3 "$HERE/discord.py" "$WORK/discord-ipc-0" "$WORK/discord.log" &
DISCORD=$!
if [ -z "${DISPLAY:-}" ]; then Xvfb :98 -screen 0 1280x720x24 > "$WORK/xvfb.log" 2>&1 & XVFB=$!; export DISPLAY=:98; fi
trap 'kill $SERVER $DISCORD ${XVFB:-} ${CLIENT:-} 2>/dev/null || true' EXIT
rm -f "$WORK/discord.log"
for i in $(seq 1 120); do grep -q "Done (" "$S/server.log" 2>/dev/null && break; sleep 2; done
grep -q "Done (" "$S/server.log" || { echo "::error::server did not start"; tail -40 "$S/server.log"; exit 1; }

CP=$(python3 -c "import json;print(':'.join(json.load(open('$R/launch.json'))['cp']))")
AI=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['assetIndex'])")
MAIN=$(python3 -c "import json;print(json.load(open('$R/launch.json'))['main'])")
# 1.20+ joins with --quickPlay*, older releases with --server/--port.
QUICK=$(python3 -c "
v=[int(x) for x in '$MC'.split('.')[:2]]
print('1' if v[0]>1 or v[1]>=20 else '0')")

client() {
  (cd "$G" && LIBGL_ALWAYS_SOFTWARE=1 exec java -Xmx2G -Djava.library.path="$R/natives" -Dorg.lwjgl.librarypath="$R/natives" \
    -Dnative.api=http://127.0.0.1:1 -Dnative.discord.ipc="$WORK/discord-ipc-0" -cp "$CP" "$MAIN" --username TestAlice --version "$MC" --gameDir "$G" \
    --assetsDir "$R/assets" --assetIndex "$AI" --accessToken 0 --uuid 00000000000000000000000000000001 \
    --userType legacy --versionType release "$@" > "$G/out-$PHASE.log" 2>&1) &
  CLIENT=$!
}
wait_for() { # <seconds> <python expression over p (presence.json) and d (discord activities)>
  python3 - "$1" "$2" "$G/.native/presence.json" "$WORK/discord.log" <<'PY'
import json, sys, time
limit, expr, pfile, dfile = int(sys.argv[1]), sys.argv[2], sys.argv[3], sys.argv[4]
end = time.time() + limit
while time.time() < end:
    try: p = json.load(open(pfile))
    except Exception: p = {}
    d = []
    try:
        for line in open(dfile):
            f = json.loads(line)
            a = f['body'].get('args', {}).get('activity') if f['op'] == 1 else None
            if a: d.append(a)
    except Exception: pass
    if eval(expr):
        print('presence:', json.dumps(p)); print('discord:', json.dumps(d[-1] if d else None)); sys.exit(0)
    time.sleep(2)
print('presence:', json.dumps(p)); print('discord:', json.dumps(d[-1] if d else None)); sys.exit(1)
PY
}

PHASE=multiplayer
if [ "$QUICK" = 1 ]; then client --quickPlayMultiplayer "127.0.0.1:25599"; else client --server 127.0.0.1 --port 25599; fi
if ! wait_for 240 "p.get('state')=='multiplayer' and p.get('online',0)>=1 and p.get('max')==20 and any(a.get('details')=='Playing on a private server' and a.get('party',{}).get('size')==[1,20] for a in d)"; then
  echo "::error::multiplayer presence missing on Minecraft $MC"; grep -E "\[Native\]|Exception" "$G/out-$PHASE.log" | tail -30; exit 1
fi
kill $CLIENT 2>/dev/null || true; wait $CLIENT 2>/dev/null || true
kill $SERVER 2>/dev/null || true; wait $SERVER 2>/dev/null || true

if [ "$QUICK" = 1 ]; then
  PHASE=singleplayer
  mkdir -p "$G/saves" && cp -r "$S/world" "$G/saves/PresenceWorld"
  client --quickPlaySingleplayer PresenceWorld
  if ! wait_for 300 "p.get('state')=='singleplayer' and any(a.get('details')=='Playing Singleplayer' for a in d)"; then
    echo "::error::singleplayer presence missing on Minecraft $MC"; grep -E "\[Native\]|Exception" "$G/out-$PHASE.log" | tail -30; exit 1
  fi
  kill $CLIENT 2>/dev/null || true; wait $CLIENT 2>/dev/null || true
fi
echo "OK: presence works on Minecraft $MC"
