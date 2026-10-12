package forge.delve;

/**
 * Optional modes kept per save and changed from Your House > Options (difficulty lives in
 * {@link DelveDifficulty}): how long a dungeon floor is, and how your starting deck is picked.
 * A run keeps the length it started with (saved with the run).
 */
public final class DelveModes {
    private DelveModes() {}

    public enum Length {
        SHORT("Short", "11 steps", 7, 3),
        STANDARD("Standard", "15 steps", 10, 4),
        LONG("Long", "19 steps", 14, 4);

        public final String title, rooms;
        /** fewest steps and how many more a floor may roll */
        final int min, spread;

        Length(String title, String rooms, int min, int spread) {
            this.title = title;
            this.rooms = rooms;
            this.min = min;
            this.spread = spread;
        }

        /** Generator 8+ (the web of paths, Slay the Spire's 15-floor acts): a fixed number of steps before the boss. */
        public int webSteps() {
            return this == SHORT ? 11 : this == LONG ? 19 : 15;
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

    /** Chaos: the game picks your archetype for you, sight unseen. */
    /**
     * Attune mana (default, Tyler 2026-10-08; first called Channel, renamed to avoid Magic's own Channel keyword):
     * dungeon decks have no lands; you start each fight with 3 colourless Mana Sparks and may Attune one card a turn
     * into a mana source of its colours (Delve Mana Rules). Stored as option "channelMana".
     * Off: the old lands-in-deck dungeon.
     */
    public static boolean channelMana() {
        return Boolean.parseBoolean(DelveProfile.get().option("channelMana", "true"));
    }

    public static void setChannelMana(boolean on) {
        DelveProfile.get().setOption("channelMana", String.valueOf(on));
    }

    public static boolean chaosDecks() {
        return Boolean.parseBoolean(DelveProfile.get().option("chaosDecks", "false"));
    }

    public static void setChaosDecks(boolean on) {
        DelveProfile.get().setOption("chaosDecks", String.valueOf(on));
    }
}
