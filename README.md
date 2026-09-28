# ChuStatsu

ChuStatsu is an Ornithe/Fabric Gen2 mod for Minecraft 1.8.9 that displays PikaNetwork BedWars statistics in a replacement TAB list, a movable HUD, and optional nametags. It uses OneConfig v1 for settings and the HUD editor. PikaStats is a reference for observable behavior; ChuStatsu has its own implementation.

## Features

- Ordered, checkable columns for both tables, with player heads, dynamic names, level, FKDR, WLR, winstreak, wins, beds, guild, ping, and in-game health.
- BedWars waiting-room stat sorting, optional fixed match-team order, party and friend highlighting, denick hints, and team-colored italic names while spectating.
- Profile and leaderboard caching through one paced API service. Requests can be limited to matches and waiting lobbies. Successful results survive temporary failures; HTTP 429 responses honor `Retry-After`, and rate-limited entries retry when the cooldown ends.
- Rounded panels, optional glass, local or Catbox PNG backgrounds, Minecraft/Poppins/custom fonts, table motion, and separate OneConfig HUD handles.
- `/chustatsu` opens settings. `/stats <player>` uses the same cached API service. Debug mode can save a raw TAB snapshot and log rate limits and denick decisions to the console and `minecraft/config/chustatsu/debug/denick.log`.

## Build

Use Java 25 and the project-local Gradle cache:

```sh
JAVA_HOME=/home/user/.local/share/PrismLauncher/java/java-runtime-epsilon \
GRADLE_USER_HOME="$PWD/.gradle" ./gradlew build
```

The remapped mod jar is `build/libs/chustatsu-0.1.0+mc1.8.9.jar`. The build runs the JUnit and local HTTP tests.

## Local OneClient beta

The install and launch scripts default to the maintainer's PrismLauncher instance at `/mnt/a400/Games/PrismLauncher/instances/OneClient 1.8.9-alpha.5-Chustatsu-dev`. To use another instance, set `CHUSTATSU_INSTANCE_DIR` to its absolute directory. Keep its existing mods and configuration files.

```sh
./scripts/install-local.sh
./scripts/launch-local.sh
```

Close a running game before installing the new jar. The scripts use the full installed modlist; an authenticated account is needed for live PikaNetwork checks. Launcher files, game instances, Gradle caches, build output, and local research are excluded from Git.

## Implementation notes

- Minecraft 1.8.9 uses Feather Gen2 mappings, Fabric Loader 0.19.5, OSL 0.21.0, and the installed OneConfig 1.2.6 API. OneConfig v1 is the sole configuration system.
- The API worker has four threads and one shared 250 ms request schedule. A 429 response pauses all workers for the server's `Retry-After` period. Rate-limited entries retry on the first normal refresh after the cooldown; other errors retain a short retry delay. Cached profile data also supplies friend names.
- The head column has room around the first separator, and names have a three-pixel inset. API errors show one status across the affected stat area, including HTTP errors and rate limits; cached successful stats remain visible during transient failures. HUD and TAB use PikaStats-style translucent white alternating rows, with row text lowered one pixel.
- Denicking correlates an unambiguous same-team removal and addition. The default-on `Waiting room only` switch limits attempts to the pre-game waiting room; turning it off permits attempts in other PikaNetwork phases. Debug logging records the packet context and each accepted or rejected candidate.
- HUD/TAB editor handles are single-instance. Older saved profiles may contain a duplicate handle that OneConfig rejects; leave the saved files intact.

## References

- [Ornithe Gen2 template](https://github.com/OrnitheMC/ornithe-mod-template/tree/gen2/fabric)
- [OneConfig v1](https://github.com/Polyfrost/OneConfig/tree/v1) and [indexed source](https://deepwiki.com/Polyfrost/OneConfig)
- [PikaStats behavior reference](https://github.com/liwwyy/PikaStats)

The bundled Poppins font files include their OFL notice at `src/main/resources/assets/chustatsu/fonts/OFL.txt`. The source icon is `icons/icon.png`; the packaged copy is `src/main/resources/assets/chustatsu/icon.png`.

## Latest local verification

The required Gradle build passed all 34 tests. The remapped jar was installed in the moved beta instance; the built and installed copies both have SHA-256 `a6a16a0db5af77202c2ea5dbbecec3af70b899b8c1f5bcb6ec57a6591e37505a`. The previous project-local game process was closed before installation. The maintainer will launch and test this build manually; no launch was attempted during this update.
