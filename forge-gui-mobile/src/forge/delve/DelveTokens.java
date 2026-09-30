package forge.delve;

import java.util.Random;

/**
 * Run tokens: town items that bend the end-of-run rules (and one that works
 * mid-run). Bought at the Card Shop, and earned by clearing Standard/Deep
 * dungeons or winning at the Castle. Stored as counts in the profile.
 */
public enum DelveTokens {
    KEEPSAKE("Keepsake", 60, "Keep one extra card at the end of a run."),
    DUPLICATE("Duplicate", 60, "After keeping cards, take a second copy of one of them."),
    INSURANCE("Insurance", 75, "If a run ends in defeat, keep as many cards as a clear would."),
    REROLL("Reroll", 25, "In the dungeon, swap a card reward for three new choices."),
    VAULT("Vault", 250, "After clearing a dungeon, add the whole run deck to your collection (you can still edit it).");

    public final String title;
    public final int price;
    public final String description;

    DelveTokens(String title, int price, String description) {
        this.title = title;
        this.price = price;
        this.description = description;
    }

    /** A random token for a reward (Vault is only ever bought). */
    public static DelveTokens randomReward(Random rng) {
        DelveTokens[] pool = {KEEPSAKE, DUPLICATE, INSURANCE, REROLL, REROLL};
        return pool[rng.nextInt(pool.length)];
    }

    /** Give the player {@code n} random reward tokens; returns e.g. "a Reroll token" / "Keepsake and Reroll tokens". */
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
