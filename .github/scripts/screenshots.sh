#!/usr/bin/env bash
# Plays the built-in demo song in a real game on a virtual screen and takes pictures through the
# test hook (DevTestHook), so changes can be looked at without a computer that runs Minecraft.
# Run from the project folder on Linux with xvfb-run and Mesa installed. Pictures end up in $OUT.
set -euo pipefail

MC_VERSION=$(grep '^minecraft_version=' gradle.properties | cut -d= -f2)
OUT=${OUT:-shots}
RUN=run-prod
mkdir -p "$OUT" "$RUN/saves" "$RUN/config"

# A world to stand in. A dedicated server makes one (its worlds open fine in single player).
if [ ! -d "$RUN/saves/World" ]; then
	manifest=$(curl -sSf https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
	version_url=$(echo "$manifest" | jq -r --arg v "$MC_VERSION" '.versions[] | select(.id == $v) | .url')
	server_url=$(curl -sSf "$version_url" | jq -r '.downloads.server.url')
	mkdir -p server && cd server
	curl -sSfo server.jar "$server_url"
	echo "eula=true" > eula.txt
	printf 'level-name=World\nlevel-seed=skylyrics\nonline-mode=false\nview-distance=4\n' > server.properties
	echo stop | timeout 600 java -Xmx2G -jar server.jar --nogui > server.log 2>&1 || { tail -50 server.log; exit 1; }
	cd ..
	cp -r server/World "$RUN/saves/World"
fi

# The settings to show: the demo song, the clock and the bigger current word
cat > "$RUN/config/skylyrics.json" <<'EOF'
{ "demoMode": true, "barClock": true, "growWord": true }
EOF
# No first-start screens, small render distance (the virtual screen draws in software), GUI scale 2
cat > "$RUN/options.txt" <<'EOF'
onboardAccessibility:false
skipMultiplayerWarning:true
tutorialStep:none
renderDistance:6
simulationDistance:5
guiScale:2
soundCategory_master:0.0
pauseOnLostFocus:false
EOF

LOG="$RUN/logs/latest.log"
rm -f "$LOG"
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 1920x1080x24" \
	./gradlew --no-configuration-cache runProductionClient -PquickPlay=World -PwindowSize=1600x900 > game.log 2>&1 &
GAME=$!

wait_for() { # pattern, seconds
	for _ in $(seq "$2"); do
		if grep -q "$1" "$LOG" 2>/dev/null; then return 0; fi
		if ! kill -0 "$GAME" 2>/dev/null; then echo "game stopped"; tail -80 game.log; exit 1; fi
		sleep 1
	done
	echo "timed out waiting for: $1"; tail -80 game.log; exit 1
}
hook() { # commands, one per argument; waits until the game has read them
	printf '%s\n' "$@" > "$RUN/skylyrics-test.txt"
	for _ in $(seq 30); do [ -f "$RUN/skylyrics-test.txt" ] || return 0; sleep 1; done
	echo "the game didn't read: $*"; exit 1
}
shot() { # name, then waits for the file
	hook "shot $1"
	for _ in $(seq 30); do [ -f "$RUN/screenshots/$1.png" ] && return 0; sleep 1; done
	echo "no picture: $1"; exit 1
}

wait_for "joined the game" 900
sleep 20 # chunks and the demo song settle in
hook "cmd weather clear" "cmd time set midnight" "cmd gamerule doDaylightCycle false" "cmd gamerule advance_time false" "look 0 -28"
sleep 12
shot night-sky
sleep 3
shot night-sky-2
hook "cmd time set noon"
sleep 8
shot daytime
hook "set placement FLOATING" "look 0 0"
sleep 6
shot floating
hook "set placement SKY" "cmd time set midnight"
for scale in 1 2 3 4; do
	hook "guiscale $scale" "gui 0"
	sleep 4
	shot "settings-scale-$scale"
done
hook "guiscale 2" "gui 3"
sleep 3
shot settings-display
hook "close"

kill "$GAME" 2>/dev/null || true
pkill -f 'net.fabricmc' 2>/dev/null || true
cp "$RUN"/screenshots/*.png "$OUT"/
cp "$LOG" "$OUT/latest.log"
ls -la "$OUT"
