# Keine Villager, keine fahrenden Haendler
kill @e[type=minecraft:villager]
kill @e[type=minecraft:wandering_trader]
kill @e[type=minecraft:trader_llama]

# Gelaufene Bloecke = Zentimeter / 100
scoreboard players operation @a ck_blocks = @a ck_walk
scoreboard players operation @a ck_blocks /= #100 ck_const

# /trigger board und /trigger team freischalten
scoreboard players enable @a board
scoreboard players enable @a team
function classkits:board
function classkits:team

# Nether sperren
execute as @a at @s if dimension minecraft:overworld unless block ~ ~ ~ minecraft:nether_portal store result score @s ck_x run data get entity @s Pos[0]
execute as @a at @s if dimension minecraft:overworld unless block ~ ~ ~ minecraft:nether_portal store result score @s ck_y run data get entity @s Pos[1]
execute as @a at @s if dimension minecraft:overworld unless block ~ ~ ~ minecraft:nether_portal store result score @s ck_z run data get entity @s Pos[2]
execute as @a at @s if dimension minecraft:the_nether run function classkits:nether_back

# Mace ist verboten: aus Inventaren und vom Boden entfernen
execute as @a store result score @s ck_mace run clear @s minecraft:mace
execute as @a[scores={ck_mace=1..}] run tellraw @s {"text":"Die Mace ist verboten.","color":"red"}
kill @e[type=minecraft:item,nbt={Item:{id:"minecraft:mace"}}]
