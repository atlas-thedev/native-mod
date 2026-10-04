#!/usr/bin/env bash
# Boots a real Fabric dedicated server for one Minecraft version with the Noctra mod
# loaded, then asks the game's own authlib session service for a player's textures.
# Passes only when Noctra's skin + cape come back (and an unknown player is untouched).
#
#   ci/compat/run.sh <minecraft> <fabric-loader> <path/to/native-client.jar>
#
# Needs: java (the version Minecraft wants) on PATH, node, python3, curl, unzip.
set -euo pipefail
MC="$1"; LOADER="$2"; MOD_JAR="$(readlink -f "$3")"
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${RUNNER_TEMP:-/tmp}/noctra-compat-$MC"
rm -rf "$WORK"; mkdir -p "$WORK/mods" "$WORK/probe/out" "$WORK/mod"
cd "$WORK"

LOADER_JAR="fabric-loader-$LOADER.jar"
curl -fsSL -o "$LOADER_JAR" "https://maven.fabricmc.net/net/fabricmc/fabric-loader/$LOADER/$LOADER_JAR"

# 1. the probe mod (test-only)
javac -nowarn -source 8 -target 8 -cp "$LOADER_JAR" -d probe/out "$HERE/Probe.java" 2>/dev/null
echo '{"schemaVersion":1,"id":"probe","version":"1.0.0","name":"Probe","environment":"*","entrypoints":{"main":["probe.Probe"]}}' > probe/out/fabric.mod.json
(cd probe/out && python3 - <<'PY'
import zipfile, os
with zipfile.ZipFile('../../mods/probe.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    for root, _, files in os.walk('.'):
        for f in files:
            p = os.path.join(root, f)
            z.write(p, os.path.relpath(p, '.'))
PY
)

# 2. our mod, switched to environment "*" so it also loads on a dedicated server
python3 - "$MOD_JAR" mods/noctra-test.jar <<'PY'
import sys, zipfile
src, dst = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        data = zin.read(item.filename)
        if item.filename == 'fabric.mod.json':
            data = data.replace(b'"environment": "client"', b'"environment": "*"')
        zout.writestr(item, data)
PY

# 3. a stand-in Noctra API
node "$HERE/mock-api.js" > mock.log 2>&1 &
MOCK=$!
trap 'kill $MOCK 2>/dev/null || true' EXIT
sleep 1

# 4. the real server
INSTALLER=$(curl -fsSL https://meta.fabricmc.net/v2/versions/installer | python3 -c "import json,sys;print([x for x in json.load(sys.stdin) if x['stable']][0]['version'])")
curl -fsSL -o server.jar "https://meta.fabricmc.net/v2/versions/loader/$MC/$LOADER/$INSTALLER/server/jar"
echo "eula=true" > eula.txt
printf "online-mode=false\nserver-port=0\nview-distance=2\nmax-tick-time=-1\n" > server.properties
set +e
timeout 600 java -Xmx900M -Dnoctra.api=http://127.0.0.1:8099 -jar server.jar nogui > server.log 2>&1
set -e

grep -E "PROBE|\[Noctra\]" server.log || true
if ! grep -q "PROBE TestAlice .*SKIN=.*aaaa.*model=slim.*CAPE=.*bbbb" server.log && ! grep -q "PROBE TestAlice shape=B skin=.*aaaa.* model=slim cape=.*bbbb" server.log; then
  echo "::error::Noctra skin/cape were NOT applied on Minecraft $MC"; tail -60 server.log; exit 1
fi
if grep -E "PROBE Nobody" server.log | grep -q "aaaa"; then
  echo "::error::An unknown player was modified on Minecraft $MC"; exit 1
fi
echo "OK: Minecraft $MC"
