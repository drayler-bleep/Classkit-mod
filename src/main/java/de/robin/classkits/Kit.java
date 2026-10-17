package de.robin.classkits;

public enum Kit {
    BLOCK("Block-Klasse", "Block", "gold"),
    DAGGER("Dolch-Klasse", "Dolch", "red"),
    HAMMER("Hammer-Klasse", "Hammer", "dark_aqua");

    private final String displayName;
    private final String shortName;
    private final String color;

    Kit(String displayName, String shortName, String color) {
        this.displayName = displayName;
        this.shortName = shortName;
        this.color = color;
    }

    public String displayName() {
        return displayName;
    }

    /** Kurzer Name, der gross auf dem Bildschirm erscheint. */
    public String shortName() {
        return shortName;
    }

    /** Textfarbe fuer den Titel (Minecraft-Farbname). */
    public String color() {
        return color;
    }

    /** Modell-ID des Items (pro Level eine eigene Textur, sofern vorhanden). */
    public String modelId(int level) {
        int l = Math.max(1, Math.min(level, 5));
        if (this == BLOCK) return "block_" + l;
        if (this == DAGGER) return "dagger_" + l;
        return "hammer_" + l;
    }

    public static Kit parse(String text) {
        if (text == null) return null;
        switch (text.toLowerCase()) {
            case "block":
                return BLOCK;
            case "dolch":
            case "dagger":
                return DAGGER;
            case "hammer":
                return HAMMER;
            default:
                return null;
        }
    }

    /** Was ein Level freischaltet bzw. aendert (Kurzbeschreibung). */
    public String unlock(int level) {
        if (this == BLOCK) {
            switch (level) {
                case 1: return "Block 2 s. Ein geblockter Treffer erlaubt einen Punch (Linksklick).";
                case 2: return "Block 3 s. Mehr Knockback beim Punch.";
                case 3: return "Block 4 s. Beim Aktivieren heilst du dich.";
                case 4: return "Dein Gegenschlag nach einem Block friert Spieler 2 s ein. Noch mehr Knockback.";
                default: return "Dornen III: Angreifer bekommen Dornen-Schaden.";
            }
        }
        if (this == HAMMER) {
            switch (level) {
                case 1: return "4 Schaden, 0,9 Angriffstempo.";
                case 2: return "5 Schaden. Sneak + Rechtsklick auf Werkzeug/Ruestung am Boden: +100 Haltbarkeit (1x pro Item, 30 s Cooldown).";
                case 3: return "6 Schaden. Sneak + Linksklick: Blitz von oben, halbes Herz Schaden, 1 s Stun (12 s Cooldown).";
                case 4: return "Nach dem Blitz-Stun gehen 3 s lang 15% deines Schadens durch die Ruestung. Stun 2,5 s.";
                default: return "9 Schaden. Ab 2 Bloecken Fallhoehe stoesst die Landung alles in 3 Bloecken weg, kein Fallschaden mit Hammer in der Hand.";
            }
        }
        switch (level) {
            case 1: return "5 Schaden, 1,6 Angriffstempo. Noch kein Dash.";
            case 2: return "+10% Lauftempo in der Hand. Dash (Rechtsklick, 8 Bloecke, auch beim Laufen und Springen, 20 s Cooldown).";
            case 3: return "Dash-Treffer vergiftet (Gift I, 3 s).";
            case 4: return "Dash-Treffer setzt den Gegner in Brand. Kill = volle Heilung.";
            default: return "7 Schaden. Treffer auf vom Dash brennende oder eingefrorene Spieler ignoriert die Ruestung.";
        }
    }
}
