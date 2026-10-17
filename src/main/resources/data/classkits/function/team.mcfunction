execute as @a[scores={team=1}] run team join ck_rot @s
execute as @a[scores={team=2}] run team join ck_blau @s
execute as @a[scores={team=3}] run team join ck_gruen @s
execute as @a[scores={team=4}] run team join ck_gelb @s
execute as @a[scores={team=9}] run team leave @s
execute as @a[scores={team=1..}] run tellraw @s {"text":"Team gewechselt.","color":"green"}
scoreboard players set @a[scores={team=1..}] team 0
