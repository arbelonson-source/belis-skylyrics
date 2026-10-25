# The sky lyrics layouts: the arc by night and by day, words standing in the world, and the stacked lines.
# Arc lines appear where you look when they start, so each look is followed by a wait for the next line.

hook "set layout ARC" "cmd time set midnight" "look 0 -12"
sleep 9
shot lyrics-arc-night
sleep 3
shot lyrics-arc-night-2
hook "cmd time set noon" "look 0 -12"
sleep 8
shot lyrics-arc-day

# Words in the world: look a little down so they stand around the crosshair, then step aside and turn
hook "set layout WORLD" "look 0 8"
sleep 8
shot lyrics-world-day
hook "cmd execute as @p at @p run tp @s ~1.5 ~ ~1" "look 20 8"
sleep 1
shot lyrics-world-day-moved
sleep 5
shot lyrics-world-day-2
hook "cmd execute as @p at @p run tp @s ~-1.5 ~ ~-1"

hook "set layout LINES" "cmd time set midnight" "look 0 -28"
sleep 6
shot lyrics-lines

hook "set layout ARC" "look 0 -28"
sleep 2
