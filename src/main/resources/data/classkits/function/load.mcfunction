scoreboard objectives add ck_kills totalKillCount "Kills"
scoreboard objectives add ck_deaths deathCount "Tode"
scoreboard objectives add ck_walk minecraft.custom:minecraft.walk_one_cm
scoreboard objectives add ck_blocks dummy "Gelaufene Blöcke"
scoreboard objectives add ck_const dummy
scoreboard objectives add ck_x dummy
scoreboard objectives add ck_y dummy
scoreboard objectives add ck_z dummy
scoreboard objectives add board trigger
scoreboard objectives add team trigger
scoreboard players set #100 ck_const 100
team add ck_rot "Rot"
team add ck_blau "Blau"
team add ck_gruen "Grün"
team add ck_gelb "Gelb"
team modify ck_rot color red
team modify ck_blau color blue
team modify ck_gruen color green
team modify ck_gelb color yellow
team modify ck_rot friendlyFire false
team modify ck_blau friendlyFire false
team modify ck_gruen friendlyFire false
team modify ck_gelb friendlyFire false
scoreboard objectives add ck_mace dummy
