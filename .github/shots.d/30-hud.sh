# The top bar, sized to crop: a sung line, the widest line, the song's name between lines, and GUI scales 1 to 3.
# Sourced by .github/scripts/screenshots.sh (hook, shot, $LOG). Small screens and long lines that scroll are
# covered by TopBarLayoutTest: the demo's lines all fit the bar at the virtual screen's size.

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

hook "guiscale 2" "set syncOffsetMs 0" "cmd difficulty easy"
sleep 2
