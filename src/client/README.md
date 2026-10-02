# Noctra title screen (Minecraft 26.3)

Player preview on the left, Noctra toolbar on the right, account line and branding.
Compiled as the `ui` source set (Java 25 bytecode) against Minecraft 26.3's own classes, which Gradle
downloads from Mojang (`./gradlew fetchMinecraft`). It is loaded **only** on 26.3: `NoctraPreLaunch`
registers `noctra.client.mixins.json` at runtime after checking the game version, so every other
version never touches these classes.
