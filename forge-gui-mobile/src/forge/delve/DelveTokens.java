package forge.delve;

import java.util.Random;

/**
 * Run tokens: town items that bend a run's rules. Bought at the Card Shop's token
 * counter, and earned by clearing dungeons and winning at the Castle. Stored as
 * counts in the profile. The game always asks before spending one.
 */
public enum DelveTokens {
    REROLL("Reroll", 25, "Swap a draft pack or a card reward for a fresh one."),
    TREASURE_MAP("Treasure Map", 90, "When you clear a dungeon, take two rewards instead of one."),
    INSURANCE("Insurance", 100, "If a run ends in defeat, bring home all the gold you found and still choose a clear reward (no tier unlock).");

    public final String title;
    public final int price;
    public final String description;

    DelveTokens(String title, int price, String description) {
        this.title = title;
        this.price = price;
        this.description = description;
    }

    /** A random token for a reward (Rerolls are the most common). */
    public static DelveTokens randomReward(Random rng) {
        DelveTokens[] pool = {REROLL, REROLL, REROLL, TREASURE_MAP, INSURANCE};
        return pool[rng.nextInt(pool.length)];
    }

    /** Give the player {@code n} random reward tokens; returns e.g. "a Reroll token" / "Reroll and Insurance tokens". */
    public static String grant(int n, Random rng) {
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < n; i++) {
            DelveTokens t = randomReward(rng);
            DelveProfile.get().addToken(t, 1);
            if (i > 0) names.append(i == n - 1 ? " and " : ", ");
            names.append(t.title);
        }
        return n == 1 ? "a " + names + " token" : names + " tokens";
    }
}
