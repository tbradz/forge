package forge.delve;

import java.util.EnumSet;

/**
 * Run modifiers: curses you may take at the Dungeon Gate for more gold (Tyler, 2026-10-11). Each makes the run harder
 * and adds its share to the gold you find in fights and treasure rooms. Effects live where the rule is: foe life and
 * max life in {@link DelveRun}; prices, rests and the map's darkness in the screens that show them.
 */
public enum DelveCurse {
    HARDENED("Hardened Foes", "Every foe starts with 5 more life.", 25),
    FRAILTY("Frailty", "Your maximum life is 6 lower.", 30),
    GREED("Greedy Merchants", "Merchants charge half again as much.", 20),
    WEARINESS("Weariness", "Rests heal half as much.", 20),
    DARKNESS("Darkness", "You only see the next step of the map.", 15);

    public final String title, description;
    /** extra gold from fights and treasure, in percent */
    public final int goldPct;

    DelveCurse(String title, String description, int goldPct) {
        this.title = title;
        this.description = description;
        this.goldPct = goldPct;
    }

    /** "HARDENED,DARKNESS" (a run save) -> the set; unknown names are skipped. */
    public static EnumSet<DelveCurse> parseAll(String s) {
        EnumSet<DelveCurse> out = EnumSet.noneOf(DelveCurse.class);
        if (s == null) return out;
        for (String n : s.split(",")) {
            try {
                if (!n.trim().isEmpty()) out.add(valueOf(n.trim()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }
}
