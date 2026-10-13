#!/usr/bin/env bash
# Check that a built Ideomod jar actually loads.
#
#   scripts/check_jar.sh [path/to/ideomod.jar]      (default: the jar in build/libs)
#
# Installs a real Forge server (the version in gradle.properties) into
# build/jar-check/, puts the jar in its mods folder, starts it, and waits.
#   PASS: the server reaches "Done", i.e. the mod loaded and a world was made.
#   FAIL: Forge reports a mod loading error, the server crashes, or it never
#         finishes starting.
# The verdict comes from the server log, not from exit codes: a server that
# fails to load a mod can still exit cleanly.
#
# Needs Java 17 and curl. The first run downloads Forge and Minecraft's server
# libraries (a few hundred MB); later runs reuse them.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
prop() { grep -E "^$1=" "$ROOT/gradle.properties" | cut -d= -f2 | tr -d '\r'; }
MC="$(prop minecraft_version)"
FORGE="$(prop forge_version)"
JAR="${1:-$(ls "$ROOT"/build/libs/ideomod-*.jar 2>/dev/null | grep -v -- '-sources' | head -n 1)}"
TIMEOUT="${CHECK_TIMEOUT:-300}"

[ -f "$JAR" ] || { echo "FAIL: no jar found (build first: ./gradlew build)"; exit 1; }
echo "Checking $JAR against Forge $MC-$FORGE"

# 1. The jar itself: a Forge mod with the right id.
toml="$(unzip -p "$JAR" META-INF/mods.toml 2>/dev/null || true)"
grep -q 'modId="ideomod"' <<< "$toml" || { echo "FAIL: META-INF/mods.toml missing or has no modId \"ideomod\""; exit 1; }
listing="$(unzip -l "$JAR")"
grep -q 'com/ideomancer/ideomod/Ideomod.class' <<< "$listing" || { echo "FAIL: main mod class missing from the jar"; exit 1; }
echo "ok: jar contents"

# 2. A real Forge server, cached between runs.
DIR="$ROOT/build/jar-check/forge-$MC-$FORGE"
if [ ! -f "$DIR/.installed" ]; then
    mkdir -p "$DIR"
    url="https://maven.minecraftforge.net/net/minecraftforge/forge/$MC-$FORGE/forge-$MC-$FORGE-installer.jar"
    # Reuse the vanilla server jar ForgeGradle already downloaded for the build, if any
    # (saves a download, and gets past networks that corrupt the installer's own).
    cached="$(ls "$HOME"/.gradle/caches/forge_gradle/mcp_repo/de/oceanlabs/mcp/mcp_config/"$MC"-*/joined/downloadServer/server.jar 2>/dev/null | head -n 1 || true)"
    if [ -n "$cached" ]; then
        mkdir -p "$DIR/libraries/net/minecraft/server/$MC"
        cp "$cached" "$DIR/libraries/net/minecraft/server/$MC/server-$MC.jar"
    fi
    echo "Downloading Forge installer..."
    (cd "$DIR" && curl -fsSL -o installer.jar "$url")
    (cd "$DIR" && java -jar installer.jar --installServer > install.log 2>&1) || { tail -n 20 "$DIR/install.log"; echo "FAIL: Forge server install failed"; exit 1; }
    touch "$DIR/.installed"
fi

# 3. A fresh world with only this jar in mods.
rm -rf "$DIR/mods" "$DIR/world" "$DIR/logs" "$DIR/crash-reports" "$DIR/config"
mkdir -p "$DIR/mods"
cp "$JAR" "$DIR/mods/"
echo "eula=true" > "$DIR/eula.txt"
printf 'online-mode=false\nspawn-protection=0\nmax-tick-time=-1\n' > "$DIR/server.properties"

args="$(ls "$DIR"/libraries/net/minecraftforge/forge/$MC-$FORGE/unix_args.txt)"
[ "${OS:-}" = "Windows_NT" ] && args="$(ls "$DIR"/libraries/net/minecraftforge/forge/$MC-$FORGE/win_args.txt)"
args="${args#"$DIR"/}"

log="$DIR/server-output.log"
cd "$DIR"
# Feed "stop" once the server is up; until then stdin stays open.
(
    for _ in $(seq "$TIMEOUT"); do
        sleep 1
        grep -qE '\]: Done \(' "$log" 2>/dev/null && { echo stop; sleep 30; break; }
        grep -qE 'has failed to load correctly|Failed to start the minecraft server|Crash report saved' "$log" 2>/dev/null && break
    done
) | java -Xmx2G ${JAVA_CHECK_OPTS:-} @"$args" nogui > "$log" 2>&1 &
pid=$!
waited=0
while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt $((TIMEOUT + 40)) ]; do sleep 1; waited=$((waited + 1)); done
kill "$pid" 2>/dev/null || true

# 4. The verdict, from the log.
if grep -qE 'has failed to load correctly|Failed to create mod instance|Mod Loading has failed' "$log"; then
    grep -E 'Failure message|Exception message|failed to load' "$log" | head -n 10
    echo "FAIL: Forge could not load the mod (full log: $log)"; exit 1
fi
if ls "$DIR"/crash-reports/*.txt >/dev/null 2>&1; then
    head -n 30 "$(ls "$DIR"/crash-reports/*.txt | head -n 1)"
    echo "FAIL: the server crashed (crash report in $DIR/crash-reports)"; exit 1
fi
grep -qE '\]: Done \(' "$log" || { tail -n 30 "$log"; echo "FAIL: the server did not finish starting within ${TIMEOUT}s"; exit 1; }
grep -qiE 'ideomod.*0\.1|Ideomod initializing' "$log" || { echo "FAIL: the server started but there is no sign Ideomod loaded"; exit 1; }
echo "ok: Forge $MC-$FORGE server started with the mod loaded ($(grep -oE 'Done \([0-9.]+s\)' "$log" | head -n 1))"
echo "PASS"
