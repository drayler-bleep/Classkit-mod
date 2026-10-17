# Class Kits (Fabric, Minecraft 1.21.11)

Version 1.1.0 – Quellcode mit allen Änderungen. Zum Bauen:

    gradle build        (Java 21, Gradle 8.14+ oder `gradle wrapper` vorher ausführen)

Die fertige Jar liegt danach in `build/libs/class-kits-1.1.0.jar`.
Alternativ: Projekt auf GitHub hochladen – der Workflow `.github/workflows/build.yml` baut die Jar automatisch
(Actions → build → Artifacts).

Falls Gradle die Fabric-API-Version nicht findet: in `gradle.properties` `fabric_version` auf die aktuelle
Version für 1.21.11 setzen (https://fabricmc.net/develop/).

Daten werden in `config/classkits-players.txt` und `config/classkits-teams.txt` gespeichert.

Zentrale Werte (Cooldowns, Dash-Weite, Heilung ...) stehen oben in `CombatAbilities.java`.
