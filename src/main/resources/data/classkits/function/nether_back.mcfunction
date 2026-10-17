execute if score @s ck_y matches 0 run scoreboard players set @s ck_y 100
execute store result storage classkits:tmp x int 1 run scoreboard players get @s ck_x
execute store result storage classkits:tmp y int 1 run scoreboard players get @s ck_y
execute store result storage classkits:tmp z int 1 run scoreboard players get @s ck_z
function classkits:tp_back with storage classkits:tmp
tellraw @s {"text":"Der Nether ist gesperrt.","color":"red"}
