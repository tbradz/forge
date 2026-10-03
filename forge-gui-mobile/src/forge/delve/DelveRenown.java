package forge.delve;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Castle Renown, titles and the champions board.
 *
 * Castle results earn Renown (kept for the whole save). Renown sets your title, and each
 * title unlocks perks for good: cheaper entries and shop prices, bigger prizes, small run
 * perks and title playmats. The champions board ranks you against seven rival duelists
 * who earn Renown of their own every night, so there's always someone to climb past.
 */
public final class DelveRenown {
    private DelveRenown() {}

    // ---- earning ------------------------------------------------------------------
    public static final int SEMIFINAL = 1, FINALIST = 2, CHAMPION = 4, POD_WIN = 3;

    // ---- titles ---------------------------------------------------------------------
    public enum Title {
        COMMONER(0, "Commoner", "No title yet. Earn Renown at the Castle."),
        SQUIRE(3, "Squire", "Castle entry 20 gold instead of 25."),
        KNIGHT(8, "Knight", "10% off singles and packs at the Card Shop. The Knight's playmat."),
        BARON(15, "Baron", "+2 max life on dungeon runs. The Baron's playmat."),
        COUNT(25, "Count", "Your first Reroll each run is free. Castle prizes +20%."),
        DUKE(40, "Duke", "Castle entry free. 20% off at the Card Shop. The Duke's playmat.");

        public final int renown;
        public final String title, unlocks;

        Title(int renown, String title, String unlocks) {
            this.renown = renown;
            this.title = title;
            this.unlocks = unlocks;
        }

        public boolean atLeast(Title t) {
            return ordinal() >= t.ordinal();
        }
    }

    public static Title titleFor(int renown) {
        Title out = Title.COMMONER;
        for (Title t : Title.values()) if (renown >= t.renown) out = t;
        return out;
    }

    public static Title title() {
        return titleFor(DelveProfile.get().renown());
    }

    /** The next title up, or null at the top. */
    public static Title next(Title t) {
        return t.ordinal() + 1 < Title.values().length ? Title.values()[t.ordinal() + 1] : null;
    }

    // ---- perks -------------------------------------------------------------------------

    public static int castleEntry(int base) {
        Title t = title();
        return t.atLeast(Title.DUKE) ? 0 : t.atLeast(Title.SQUIRE) ? Math.max(0, base - 5) : base;
    }

    public static int castlePrize(int base) {
        return title().atLeast(Title.COUNT) ? base * 6 / 5 : base;
    }

    /** Card Shop price after your title's discount. */
    public static int shopPrice(int base) {
        Title t = title();
        int off = t.atLeast(Title.DUKE) ? 20 : t.atLeast(Title.KNIGHT) ? 10 : 0;
        return Math.max(1, base * (100 - off) / 100);
    }

    public static int runLifeBonus() {
        return title().atLeast(Title.BARON) ? 2 : 0;
    }

    public static boolean freeRerollEachRun() {
        return title().atLeast(Title.COUNT);
    }

    // ---- rivals ----------------------------------------------------------------------

    /** The other duelists on the board, with the Renown they start at and how often they gain more. */
    static final String[] RIVALS = {"Ser Aldric", "Mira the Quick", "Old Tamsin", "Brother Hale",
            "Lady Corva", "Jory Two-Decks", "Vesna Ash"};
    private static final int[] START = {18, 14, 11, 8, 6, 4, 2};
    /** chance (percent) each night that a rival earns Renown; the strong ones are steadier */
    private static final int[] PACE = {60, 55, 50, 45, 40, 32, 25};

    public static int startingRenown(int rival) {
        return START[rival];
    }

    /** A night passes: each rival may earn a little Renown (1, sometimes 2). Seeded by day, so it's stable. */
    static void nightPassed(DelveProfile prof, int day) {
        Random r = new Random(day * 6151L + 97);
        for (int i = 0; i < RIVALS.length; i++)
            if (r.nextInt(100) < PACE[i]) prof.addRivalRenown(i, r.nextInt(3) == 0 ? 2 : 1);
    }

    /** One line of the champions board. */
    public static final class Standing {
        public final String name;
        public final int renown;
        public final boolean you;

        Standing(String name, int renown, boolean you) {
            this.name = name;
            this.renown = renown;
            this.you = you;
        }
    }

    /** Everyone on the board, highest Renown first (you ahead on ties). */
    public static List<Standing> board() {
        DelveProfile prof = DelveProfile.get();
        List<Standing> out = new ArrayList<>();
        out.add(new Standing("You", prof.renown(), true));
        for (int i = 0; i < RIVALS.length; i++) out.add(new Standing(RIVALS[i], prof.rivalRenown(i), false));
        out.sort((a, b) -> a.renown != b.renown ? b.renown - a.renown : Boolean.compare(b.you, a.you));
        return out;
    }

    /** Your place on the board (1 = top). */
    public static int yourRank() {
        List<Standing> b = board();
        for (int i = 0; i < b.size(); i++) if (b.get(i).you) return i + 1;
        return b.size();
    }

    /** "+4 Renown. You are now a Knight! ..." for the end of a Castle event, or "" for no Renown. */
    public static String award(int renown) {
        if (renown <= 0) return "";
        DelveProfile prof = DelveProfile.get();
        Title before = title();
        prof.addRenown(renown);
        Title after = title();
        String msg = "\n[GOLD]+" + renown + " Renown[WHITE] (" + prof.renown() + "), rank " + yourRank() + " on the champions board.";
        if (after != before) msg += "\n[GOLD]You are now a " + after.title + "![WHITE] " + after.unlocks;
        return msg;
    }
}
