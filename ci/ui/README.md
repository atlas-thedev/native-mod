# Native UI client test (manual)

Drives a **real Minecraft client** (Fabric, Mesa software OpenGL on Xvfb `:99`) to check the Native title screen
and the in-game Relay chat.

    Xvfb :99 -screen 0 1280x720x24 &
    python3 ci/ui/mock.py &                       # fake Native API: friends, a group, live events
    ci/ui/launch.sh 1.21.4 $JAVA_HOME build/libs/native-client-*.jar
    DISPLAY=:99 python3 ci/ui/x.py click 1035 519 sleep 2 shot /tmp/relay.png
    curl -X POST '127.0.0.1:8099/test/push?from=f1&text=hello'   # incoming DM -> toast / live message

Checked this way: 1.16.5, 1.20.1, 1.21.4, 1.21.11.
