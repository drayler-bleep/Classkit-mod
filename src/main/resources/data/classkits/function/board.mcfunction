execute if entity @a[scores={board=1}] run scoreboard objectives setdisplay sidebar ck_kills
execute if entity @a[scores={board=2}] run scoreboard objectives setdisplay sidebar ck_deaths
execute if entity @a[scores={board=3}] run scoreboard objectives setdisplay sidebar ck_blocks
execute if entity @a[scores={board=4}] run scoreboard objectives setdisplay sidebar
scoreboard players set @a[scores={board=1..}] board 0
