# Night sky effects: the synthwave sky, the eclipse and the constellations. The lyrics follow the view,
# so the suns sit in the west (yaw 90). Seen from a glass block 20 blocks up so hills don't hide the suns.
hook "cmd weather clear" "cmd time set midnight" "close" \
	"cmd execute as @a at @s run fill ~-1 ~19 ~-1 ~1 ~19 ~1 minecraft:glass" \
	"cmd execute as @a at @s run tp @s ~ ~20 ~"
sleep 3

hook "set sky SYNTHWAVE" "look 104 -14"
sleep 8
shot sky-synthwave
hook "look 116 -4"
sleep 6
shot sky-synthwave-sun

hook "set sky ECLIPSE" "look 93 -16"
sleep 8
shot sky-eclipse
hook "look 60 -30"
sleep 6
shot sky-eclipse-milky-way

hook "set sky OFF" "look 106 -26"
sleep 8
shot sky-constellations
hook "set sky AURORA" "look 168 -32"
sleep 6
shot sky-aurora-constellations

# Back down, glass gone, the usual view
hook "cmd execute as @a at @s run tp @s ~ ~-20 ~" \
	"cmd execute as @a at @s run fill ~-1 ~19 ~-1 ~1 ~19 ~1 minecraft:air" \
	"set sky AURORA" "look 0 -28"
sleep 3
