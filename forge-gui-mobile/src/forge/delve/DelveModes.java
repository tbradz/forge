package forge.delve;

/**
 * Optional modes kept per save and changed from Your House > Options (difficulty lives in
 * {@link DelveDifficulty}): how long a dungeon floor is, and how your starting deck is picked.
 * A run keeps the length it started with (saved with the run).
 */
public final class DelveModes {
    private DelveModes() {}

    public enum Length {
        SHORT("Short", "7-9 rooms", 7, 3),
        STANDARD("Standard", "10-13 rooms", 10, 4),
        LONG("Long", "14-17 rooms", 14, 4);

        public final String title, rooms;
        /** fewest steps and how many more a floor may roll */
        final int min, spread;

        Length(String title, String rooms, int min, int spread) {
            this.title = title;
            this.rooms = rooms;
            this.min = min;
            this.spread = spread;
        }

        static Length parse(String s) {
            try {
                return valueOf(s);
            } catch (Exception e) {
                return STANDARD;
            }
        }
    }

    public static Length length() {
        return Length.parse(DelveProfile.get().option("dungeonLength", "STANDARD"));
    }

    public static void setLength(Length l) {
        DelveProfile.get().setOption("dungeonLength", l.name());
    }

    /**
     * Compact runs (the default, Tyler 2026-10-04): a 20-card starting deck (6 best spells of each
     * half-deck + 8 basics) that may grow; Classic runs start at 40.
     */
    public static boolean compactRuns() {
        return Boolean.parseBoolean(DelveProfile.get().option("compactRuns", "true"));
    }

    public static void setCompactRuns(boolean on) {
        DelveProfile.get().setOption("compactRuns", String.valueOf(on));
    }

    /** Chaos: the game picks your two starting half-decks for you, sight unseen. */
    public static boolean chaosDecks() {
        return Boolean.parseBoolean(DelveProfile.get().option("chaosDecks", "false"));
    }

    public static void setChaosDecks(boolean on) {
        DelveProfile.get().setOption("chaosDecks", String.valueOf(on));
    }
}
