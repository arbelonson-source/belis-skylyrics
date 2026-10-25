# The top bar, sized to crop: a sung line, the song's name between lines, a long line that scrolls on a small
# window, and GUI scales 1 to 3. Sourced by .github/scripts/screenshots.sh (hook, shot, $LOG, $RUN).

# Moves the demo song so the given moment (ms into the song) is on screen when the next picture is taken. The
# demo starts when the mod loads ("ready" in the log, to the second), and the picture comes about 4 s from now.
hud_at() {
	local ready now pos offset
	ready=$(grep -m1 "Beli's SkyLyrics .* ready" "$LOG" | sed -E 's/^\[([0-9:]+)\].*/\1/')
	ready=$(( $(date -d "$ready" +%s) * 1000 + 500 ))
	now=$(( $(date +%s%N) / 1000000 + 3800 ))
	pos=$(( (now - ready) % 78000 ))
	# The bar looks 150 ms ahead of the song (LyricsRenderer.LEAD_MS)
	offset=$(( $1 - pos - 150 ))
	hook "set syncOffsetMs $offset"
	sleep 2.5
}

# Peaceful, so nothing kills the player mid-pictures at midnight
hook "cmd difficulty peaceful" "cmd time set midnight" "look 0 -28" "set barClock true" "set barStats true"

# GUI scale 3 is how the reference pictures were taken
hook "guiscale 3"
sleep 3
hud_at 49300 # "Build it up and let it go", halfway
shot hud-scale-3
hud_at 34800 # the longest line, near its end
shot hud-scale-3-long
hud_at 37500 # between lines: title · artist
shot hud-scale-3-between
for scale in 1 2; do
	hook "guiscale $scale"
	sleep 3
	hud_at 25500
	shot "hud-scale-$scale"
done

# A small window: the pill narrows and a long line scrolls to keep the word being sung in view
if sudo apt-get install -y -q xdotool > /dev/null 2>&1; then
	# xvfb-run gave the game its own display and key: read both from the game's environment (the script runs
	# with set -e, so nothing here may fail). What happened goes to hud-xdotool.txt next to the pictures.
	game_pid=$(pgrep -f net.fabricmc | head -1 || true)
	x_env() { tr '\0' '\n' < "/proc/$game_pid/environ" 2>/dev/null | sed -n "s/^$1=//p" | head -1 || true; }
	x_display=$(x_env DISPLAY)
	x_auth=$(x_env XAUTHORITY)
	xdo() { DISPLAY=$x_display XAUTHORITY=$x_auth xdotool "$@" 2>> "$OUT/hud-xdotool.txt" || true; }
	# The game's window: titled "Minecraft…", or else any window with a class
	x_windows() {
		local ids
		ids=$(xdo search --name 'Minecraft')
		[ -n "$ids" ] || ids=$(xdo search --class '.')
		echo "$ids"
	}
	x_old_size=""
	resize() { # width height
		local id size
		for id in $(x_windows); do
			size=$(xdo getwindowgeometry "$id" | sed -n 's/.*Geometry: //p')
			[ -n "$x_old_size" ] || x_old_size=$size
			echo "window $id '$(xdo getwindowname "$id")' $size -> $1x$2" >> "$OUT/hud-xdotool.txt"
			xdo windowsize "$id" "$1" "$2"
		done
	}
	resize 640 480
	hook "guiscale 2" "set uppercase true"
	sleep 4
	hud_at 33300 # early in the longest line
	shot hud-small-long-start
	hud_at 36100 # late in it: scrolled
	shot hud-small-long-end
	hud_at 37500
	shot hud-small-between
	# Back to the size it had
	[ -z "$x_old_size" ] || resize "${x_old_size%x*}" "${x_old_size#*x}"
	hook "set uppercase false"
	sleep 4
else
	echo "no xdotool: skipping the small-window pictures"
fi

hook "guiscale 2" "set syncOffsetMs 0" "cmd difficulty easy"
sleep 2
