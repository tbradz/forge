package forge.delve;

/**
 * Difficulty, an optional mode chosen per save (and changeable at Your House). It tunes
 * dungeon fights only: foes' starting life and deck strength, the gold fights pay, your
 * starting life, and whether the opening fights get a careless AI. Normal is the game as designed.
 */
public enum DelveDifficulty {
    EASY("Easy", "Foes have 20% less life and weaker decks, fights pay 25% more gold, and you start with 25 life.",
            80, 125, 5, -1),
    NORMAL("Normal", "The game as designed.", 100, 100, 0, 0),
    HARD("Hard", "Foes have 20% more life and stronger decks, and fights pay 10% less gold.", 120, 90, 0, 1),
    BRUTAL("Brutal", "Foes have 35% more life and stronger decks and play their best from the first room. "
            + "Fights pay 20% less gold and you start with 15 life.", 135, 80, -5, 1);

    public final String title, description;
    /** percent of normal foe life / fight gold; change to your max life; deck strength steps for fights */
    private final int lifePct, goldPct, playerLife, deckShift;

    DelveDifficulty(String title, String description, int lifePct, int goldPct, int playerLife, int deckShift) {
        this.title = title;
        this.description = description;
        this.lifePct = lifePct;
        this.goldPct = goldPct;
        this.playerLife = playerLife;
        this.deckShift = deckShift;
    }

    public static DelveDifficulty current() {
        return DelveProfile.get().difficulty();
    }

    public int foeLife(int base) {
        return Math.max(1, Math.round(base * lifePct / 100f));
    }

    public int fightGold(int base) {
        return Math.max(1, Math.round(base * goldPct / 100f));
    }

    public int playerLifeBonus() {
        return playerLife;
    }

    /** A regular fight's deck strength, a step weaker or stronger (elites and bosses keep theirs). */
    public DelveDay.Tier fightDeck(DelveDay.Tier tier) {
        DelveDay.Tier[] steps = {DelveDay.Tier.EARLY, DelveDay.Tier.FIGHT, DelveDay.Tier.LATE};
        for (int i = 0; i < steps.length; i++)
            if (steps[i] == tier) return steps[Math.max(0, Math.min(steps.length - 1, i + deckShift))];
        return tier;
    }

    /** The AI for a dungeon fight: careless opponents ease you in, except on Brutal (everywhere on Easy). */
    public String fightAi(boolean openingFight, boolean regularFight) {
        if (this == BRUTAL) return "Default";
        if (this == EASY && regularFight) return "Reckless";
        return openingFight && regularFight ? "Reckless" : "Default";
    }
}
