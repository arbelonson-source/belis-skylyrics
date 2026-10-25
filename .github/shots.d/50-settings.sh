# The new settings rows: Layout on the Lyrics tab, then Font, Depth, Night sky and Constellations on
# the Look tab, at GUI scale 2 and 3, and once in Hebrew. A scroll step is 24 GUI px; big steps stop
# at the bottom of the tab.
for scale in 2 3; do
	hook "guiscale $scale" "gui 0"
	sleep 4
	shot "settings-$scale-lyrics"
	hook "scroll -20"
	sleep 2
	shot "settings-$scale-lyrics-end"
	hook "gui 1"
	sleep 3
	shot "settings-$scale-look"
	hook "scroll -5"
	sleep 2
	shot "settings-$scale-look-mid"
	hook "scroll -20"
	sleep 2
	shot "settings-$scale-look-end"
done

# The Lines layout brings back the previous and next line rows; World hides placement and direction
hook "guiscale 2" "set layout LINES" "gui 0"
sleep 3
hook "scroll -4" # the screen places itself on its first frame, so scroll after it shows
sleep 2
shot settings-lines-layout
hook "set layout WORLD" "gui 0"
sleep 3
shot settings-world-layout
hook "set layout ARC" "gui 3"
sleep 3
hook "scroll -4"
sleep 2
shot settings-display-bar
hook "close"

# Hebrew reads right to left: the new rows have to fit there too
hook "lang he_il"
sleep 15 # reloading the game's resources takes a while on a software screen
hook "gui 1"
sleep 4
shot hebrew-settings-look
hook "scroll -20"
sleep 2
shot hebrew-settings-look-end
hook "close" "lang en_us"
sleep 10
hook "guiscale 2"
