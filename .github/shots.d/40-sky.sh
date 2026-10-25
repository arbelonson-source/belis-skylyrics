# Night sky effects: the synthwave sky, the eclipse and the constellations. The lyrics are pinned to the
# north, so the suns sit just right of and below them as in the reference pictures. Seen from a glass
# block 40 blocks up so hills don't hide the suns.
hook "cmd weather clear" "cmd time set midnight" "close" "set direction NORTH" \
	"cmd execute as @a at @s run fill ~-1 ~39 ~-1 ~1 ~39 ~1 minecraft:glass" \
	"cmd execute as @a at @s run tp @s ~ ~40 ~"
sleep 3

hook "set sky SYNTHWAVE" "look 192 -16"
sleep 8
shot sky-synthwave
hook "look 206 -5"
sleep 6
shot sky-synthwave-sun

hook "set sky ECLIPSE" "look 184 -20"
sleep 8
shot sky-eclipse
hook "look 150 -32"
sleep 6
shot sky-eclipse-milky-way

hook "set sky OFF" "look 196 -26"
sleep 8
shot sky-constellations
hook "set sky AURORA" "look 258 -34"
sleep 6
shot sky-aurora-constellations

# Back down, glass gone, the usual settings and view
hook "cmd execute as @a at @s run tp @s ~ ~-40 ~" \
	"cmd execute as @a at @s run fill ~-1 ~39 ~-1 ~1 ~39 ~1 minecraft:air" \
	"set sky AURORA" "set direction FOLLOW" "look 0 -28"
sleep 3
