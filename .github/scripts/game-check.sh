#!/usr/bin/env bash
# Plays a SkyLyrics check on a virtual screen: starts one Minecraft version with the mod built from the current
# folder, always with the demo song, plays a plain list of test hook commands (DevTestHook) and keeps the pictures
# and the game's log. Used by .github/workflows/screenshots.yml; needs Linux with xvfb-run, Mesa, curl, jq and unzip.
#
# Usage, from the mod's folder (after ./gradlew :<version>:assemble):
#   OUT=out bash .github/scripts/game-check.sh 26.3 .github/checks/example.txt
# OUT is where the pictures and logs go (default check-out), SIZE the window (default 1280x720), GRAPHICS the
# graphics backend 26.x asks for (opengl, the default; vulkan; or default, the game's own pick).
#
# The script file has one test hook command per line (the list is in the Javadoc of DevTestHook.java), plus:
#   # ...              a comment; blank lines are skipped too
#   sleep <seconds>    waits (decimals are fine)
#   wait <text>        waits up to 120 s for a game log line with that text, e.g. "wait world line starts over"
# Commands on lines next to each other go to the game together and run in the same game tick, in order; a sleep or
# wait line (or the end of the file) sends what came before it. After sending, this waits until the game has read
# them, every `shot` picture is saved and every `burst` is written, and warns about commands the hook couldn't run.
# The game always plays the demo song: a script that turns demoMode off is refused before the game starts.
set -euo pipefail

VER=${1:?usage: game-check.sh <minecraft version> <script file>}
SCRIPT=${2:?usage: game-check.sh <minecraft version> <script file>}
OUT=$(realpath -m "${OUT:-check-out}")
SIZE=${SIZE:-1280x720}
GRAPHICS=${GRAPHICS:-opengl}
SUMMARY=${GITHUB_STEP_SUMMARY:-/dev/null}
WORK=$(realpath -m .check)
# build.gradle.kts: runProductionClient plays 1.21.11 in run-prod and every other version in run-prod-<version>
if [ "$VER" = 1.21.11 ]; then RUN=$(realpath -m run-prod); else RUN=$(realpath -m "run-prod-$VER"); fi
LOG=$RUN/logs/latest.log
GAME=""
mkdir -p "$OUT" "$WORK" "$RUN/saves" "$RUN/config"
: > "$OUT/hook-warnings.txt"

annotate() { # text: one line for a GitHub annotation
	sed 's/%/%25/g; s/\r//g' | sed ':a;N;$!ba;s/\n/%0A/g'
}

# The game's java process: started with the test hook, running in the run folder (never any other java)
game_java() {
	local p
	for p in /proc/[0-9]*; do
		[ "$(cat "$p/comm" 2>/dev/null)" = java ] || continue
		tr '\0' ' ' < "$p/cmdline" 2>/dev/null | grep -q 'skylyrics.testHook' || continue
		[ "$(readlink "$p/cwd" 2>/dev/null)" = "$RUN" ] || continue
		echo "${p#/proc/}"
		return 0
	done
	return 1
}

stop_game() {
	[ -n "$GAME" ] || return 0
	local java
	java=$(game_java || true)
	if [ -n "$java" ]; then
		kill "$java" 2>/dev/null || true
		for _ in $(seq 30); do kill -0 "$java" 2>/dev/null || break; sleep 1; done
		kill -9 "$java" 2>/dev/null || true
	fi
	for _ in $(seq 30); do kill -0 "$GAME" 2>/dev/null || break; sleep 1; done
	kill "$GAME" 2>/dev/null || true
	GAME=""
}

collect() { # copies the pictures, logs and crash reports to $OUT
	if [ -d "$RUN/screenshots" ]; then
		find "$RUN/screenshots" -maxdepth 1 -name '*.png' -exec cp {} "$OUT/" \;
	fi
	if [ -f "$LOG" ]; then cp "$LOG" "$OUT/latest.log"; fi
	if [ -d "$RUN/crash-reports" ]; then cp -r "$RUN/crash-reports" "$OUT/"; fi
	if [ -f "$WORK/server/server.log" ]; then cp "$WORK/server/server.log" "$OUT/server.log"; fi
	return 0
}

fail() { # message [log file]: stops with an error annotation carrying the end of the log
	local tail=""
	if [ -f "${2:-}" ]; then tail=$(tail -n 40 "$2" | annotate); fi
	echo "::error title=game check $VER::$1%0A$tail"
	echo "**Failed:** $1" >> "$SUMMARY"
	stop_game
	collect
	exit 1
}

warn() {
	echo "$1" >> "$OUT/hook-warnings.txt"
	echo "::warning title=game check $VER::$(printf '%s' "$1" | annotate)"
}

trim() {
	local s=$1
	s=${s#"${s%%[![:space:]]*}"}
	printf '%s' "${s%"${s##*[![:space:]]}"}"
}

# The window size and the script: checked before anything starts
[[ $SIZE =~ ^([0-9]+)x([0-9]+)$ ]] || fail "SIZE must look like 1280x720, not $SIZE"
SCREEN_W=$((BASH_REMATCH[1] > 1920 ? BASH_REMATCH[1] : 1920))
SCREEN_H=$((BASH_REMATCH[2] > 1080 ? BASH_REMATCH[2] : 1080))
[ -f "$SCRIPT" ] || fail "no script file $SCRIPT"
mapfile -t LINES < <(sed 's/\r$//' "$SCRIPT")
for raw in "${LINES[@]}"; do
	line=$(trim "$raw")
	if [[ ${line,,} =~ ^set[[:space:]]+demomode([[:space:]]+(.*))?$ ]] && [ "$(trim "${BASH_REMATCH[2]}")" != true ]; then
		fail "the script turns the demo song off (\"$line\"); checks always play the demo song"
	fi
	if [[ $line =~ ^sleep[[:space:]]+ ]] && ! [[ $line =~ ^sleep[[:space:]]+[0-9]+(\.[0-9]+)?$ ]]; then
		fail "bad sleep line in the script: \"$line\" (seconds, like sleep 2.5)"
	fi
done

# A world to stand in, made by the same version's dedicated server (its worlds open fine in single player).
# Peaceful, so nothing attacks the player at night; creative, like the SkyTest world of the local test games.
mkdir -p "$WORK/server"
meta=$(curl -sSf --retry 5 https://piston-meta.mojang.com/mc/game/version_manifest_v2.json \
	| jq -r --arg v "$VER" '.versions[] | select(.id == $v) | .url')
[ -n "$meta" ] || fail "Mojang's version list has no Minecraft $VER"
server_url=$(curl -sSf --retry 5 "$meta" | jq -r '.downloads.server.url')
curl -sSf --retry 5 -o "$WORK/server/server.jar" "$server_url"
WORLD_VERSION=$(unzip -p "$WORK/server/server.jar" version.json | jq -r '.world_version')
if [ ! -f "$RUN/saves/SkyTest/level.dat" ]; then
	(
		cd "$WORK/server"
		echo "eula=true" > eula.txt
		printf '%s\n' level-name=SkyTest level-seed=skylyrics online-mode=false view-distance=4 gamemode=creative \
			difficulty=peaceful spawn-protection=0 > server.properties
		# Keep the server's input open until the world is ready, then tell it to stop
		( for _ in $(seq 450); do grep -q 'Done (' server.log 2>/dev/null && break; sleep 2; done; echo stop; sleep 15 ) \
			| timeout 900 java -Xmx2G -jar server.jar --nogui > server.log 2>&1 || true
	)
	[ -f "$WORK/server/SkyTest/level.dat" ] || fail "the server didn't make a world" "$WORK/server/server.log"
	rm -rf "$RUN/saves/SkyTest"
	cp -r "$WORK/server/SkyTest" "$RUN/saves/SkyTest"
	rm -f "$RUN/saves/SkyTest/session.lock"
fi

# Demo song on, no update downloads. Everything else as a new player has it; the script changes the rest with `set`.
cat > "$RUN/config/skylyrics.json" <<'EOF'
{ "demoMode": true, "updates": "OFF" }
EOF
# No first-start screens, a small render distance (the virtual screen draws in software), GUI scale 2, no sound,
# no slowdown when the window has no focus. The version line keeps the game from "updating" these options (26.x
# would put the graphics backend back to its own pick).
cat > "$RUN/options.txt" <<EOF
version:$WORLD_VERSION
onboardAccessibility:false
skipMultiplayerWarning:true
joinedFirstServer:true
tutorialStep:none
renderDistance:6
simulationDistance:5
guiScale:2
soundCategory_master:0.0
pauseOnLostFocus:false
inactivityFpsLimit:"minimized"
preferredGraphicsBackend:"$GRAPHICS"
lang:en_us
EOF

rm -f "$LOG" "$RUN/skylyrics-test.txt"
LIBGL_ALWAYS_SOFTWARE=1 xvfb-run -a -s "-screen 0 ${SCREEN_W}x${SCREEN_H}x24" \
	./gradlew --no-configuration-cache ":$VER:runProductionClient" -PquickPlay=SkyTest -PwindowSize="$SIZE" \
	> "$OUT/game.log" 2>&1 &
GAME=$!

new_lines() { # from line: the game log after that line
	tail -n "+$(($1 + 1))" "$LOG" 2>/dev/null || true
}
log_lines() {
	if [ -f "$LOG" ]; then wc -l < "$LOG"; else echo 0; fi
}
alive() {
	kill -0 "$GAME" 2>/dev/null || fail "the game stopped" "$([ -f "$LOG" ] && echo "$LOG" || echo "$OUT/game.log")"
}
wait_log() { # extended regex, seconds, from line [-F for plain text]: waits for a new game log line that matches
	local i
	for ((i = 0; i < $2 * 5; i++)); do
		if new_lines "$3" | grep -q "${4:--E}" -- "$1"; then return 0; fi
		alive
		sleep 0.2
	done
	return 1
}

# The mod says whether the demo song is on as it starts; stop at once if it isn't
wait_log "Beli's SkyLyrics .* ready" 1200 0 || fail "the mod didn't start" "$OUT/game.log"
ready=$(grep -m1 "Beli's SkyLyrics .* ready" "$LOG")
case $ready in
	*"demo true"*) ;;
	*) fail "the demo song is off: $ready" ;;
esac
wait_log "joined the game" 600 0 || fail "the world didn't open" "$LOG"
echo "In the world; letting the chunks and the demo song settle"
sleep 20

BATCH=()
SENT_AT=0
send() { # sends the commands gathered so far and waits until the game has done them
	((${#BATCH[@]})) || return 0
	local c name shots=() bursts=() i
	SENT_AT=$(log_lines)
	for c in "${BATCH[@]}"; do
		case $c in
			"shot "*)
				name=$(trim "${c#shot }")
				shots+=("$name")
				rm -f "$RUN/screenshots/$name.png"
				;;
			"burst "* | "burstat "*)
				read -r _ name _ <<< "$c"
				bursts+=("$name")
				;;
		esac
	done
	printf '%s\n' "${BATCH[@]}" > "$RUN/skylyrics-test.tmp"
	mv "$RUN/skylyrics-test.tmp" "$RUN/skylyrics-test.txt"
	printf '> %s\n' "${BATCH[@]}"
	for ((i = 0; i < 300; i++)); do
		[ -f "$RUN/skylyrics-test.txt" ] || break
		alive
		sleep 0.2
	done
	[ ! -f "$RUN/skylyrics-test.txt" ] || fail "the game didn't read: ${BATCH[*]}" "$LOG"
	for name in "${shots[@]}"; do
		for ((i = 0; i < 300; i++)); do
			[ -f "$RUN/screenshots/$name.png" ] && break
			alive
			sleep 0.2
		done
		[ -f "$RUN/screenshots/$name.png" ] || warn "no picture $name"
	done
	for name in "${bursts[@]}"; do
		wait_log "burst $name (wrote|gave up)|$name ignored" 180 "$SENT_AT" || warn "burst $name wasn't written in 3 minutes"
	done
	BATCH=()
	new_lines "$SENT_AT" | grep -E "Test hook command failed|Test hook: (unknown command|no key mapping)|Test hook: .*(ignored|gave up|could not)" \
		| while IFS= read -r bad; do warn "$bad"; done || true
}

# The frame rate the virtual screen manages, for the summary
BATCH=("fps 5")
send
wait_log "Test hook: fps average" 40 "$SENT_AT" || true
FPS=$(new_lines "$SENT_AT" | grep -m1 -o "fps average.*" || echo "not measured")

for raw in "${LINES[@]}"; do
	line=$(trim "$raw")
	case $line in
		"" | "#"*) ;;
		sleep\ * | sleep$'\t'*)
			send
			sleep "$(trim "${line#sleep}")"
			;;
		wait\ * | wait$'\t'*)
			send
			text=$(trim "${line#wait}")
			echo "waiting for: $text"
			wait_log "$text" 120 "$SENT_AT" -F || warn "the log never showed: $text"
			;;
		*) BATCH+=("$line") ;;
	esac
done
send
sleep 3 # pictures still being written

pictures=$(find "$RUN/screenshots" -maxdepth 1 -name '*.png' 2>/dev/null | wc -l)
demo_after=$(grep -o '"demoMode"[^,}]*' "$RUN/config/skylyrics.json" || true)
{
	echo "### Minecraft $VER"
	echo "- mod: $(git rev-parse --short HEAD 2>/dev/null || echo '?'), script: \`$SCRIPT\`, window $SIZE"
	echo "- graphics: $(grep -m1 -o "graphics backend.*" "$LOG" || true) $(grep -m1 -o "GPU: .*" "$LOG" || echo '?')"
	echo "- frame rate: $FPS"
	echo "- pictures: $pictures; settings file at the end: $demo_after"
	if [ -s "$OUT/hook-warnings.txt" ]; then
		echo "- hook warnings:"
		sed 's/^/  - /' "$OUT/hook-warnings.txt"
	fi
} >> "$SUMMARY"
stop_game
collect
ls -la "$OUT"
