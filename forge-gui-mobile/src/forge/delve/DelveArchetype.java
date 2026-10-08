package forge.delve;

import forge.item.PaperCard;

import java.util.function.Predicate;

/**
 * A run's deck plan, chosen at the Gate instead of Jumpstart half-decks (Tyler, 2026-10-07).
 * Each set offers 3 of the archetypes its cards support (picked at random when it supports more);
 * the deck uses only that set's cards, and rewards during the run lean toward the archetype.
 * {@link #payoff} decides whether a set supports it; {@link #fits} is everything the deck wants.
 */
public enum DelveArchetype {
    TOKENS("Go Wide", "Make lots of creature tokens and pump the team.",
            o -> o.matches("(?s).*create[^.]*creature token.*") || o.contains("creatures you control get +"),
            null),
    COUNTERS("+1/+1 Counters", "Grow your creatures with +1/+1 counters.",
            o -> o.contains("+1/+1 counter"), null),
    FLYERS("Skies", "Win in the air with flying creatures.",
            o -> o.matches("(?s)(^|.*\\n)flying.*") || o.contains("creatures with flying") || o.startsWith("flying"), null),
    GRAVEYARD("Graveyard", "Fill your graveyard and use what's in it.",
            o -> o.contains("from your graveyard") || o.contains("mill") || o.contains("cards in your graveyard"), null),
    LIFEGAIN("Lifegain", "Gain life and get rewarded for it.",
            o -> o.contains("whenever you gain life") || o.matches("(?s).*you gain \\d+ life.*") || o.contains("lifelink"), null),
    EQUIPMENT("Equipment", "Suit up your creatures with equipment.",
            o -> o.contains("equipped creature") || o.contains("equip "),
            pc -> pc.getRules().getType().hasSubtype("Equipment")),
    SPELLS("Spellslinger", "Cast instants and sorceries; your creatures reward you for it.",
            o -> o.contains("prowess") || o.contains("whenever you cast an instant or sorcery")
                    || o.contains("whenever you cast a noncreature spell") || o.contains("instant and sorcery"),
            pc -> pc.getRules().getType().isInstant() || pc.getRules().getType().isSorcery()),
    LANDFALL("Landfall", "Every land you play sets off your creatures.",
            o -> o.contains("landfall") || o.contains("whenever a land you control enters")
                    || o.contains("whenever a land enters the battlefield under your control"), null),
    ARTIFACTS("Artifacts", "Fill the board with artifacts and use them.",
            o -> o.contains("artifact you control") || o.contains("artifacts you control") || o.contains("for each artifact"),
            pc -> pc.getRules().getType().isArtifact()),
    ENCHANTMENTS("Enchantments", "Cast enchantments; your creatures care about them.",
            o -> o.contains("enchantment you control") || o.contains("enchantments you control") || o.contains("constellation"),
            pc -> pc.getRules().getType().isEnchantment()),
    SACRIFICE("Sacrifice", "Sacrifice creatures for value when they die.",
            o -> o.contains("sacrifice a creature") || o.contains("sacrifice another creature")
                    || (o.contains("whenever") && o.contains("dies")), null);

    public final String title, description;
    private final Predicate<String> payoffText;
    private final Predicate<PaperCard> enabler;

    DelveArchetype(String title, String description, Predicate<String> payoffText, Predicate<PaperCard> enabler) {
        this.title = title;
        this.description = description;
        this.payoffText = payoffText;
        this.enabler = enabler;
    }

    private static String oracle(PaperCard pc) {
        String o = pc.getRules().getOracleText();
        return o == null ? "" : o.toLowerCase();
    }

    /** A card that rewards this plan (used to measure whether a set supports it). */
    public boolean payoff(PaperCard pc) {
        return payoffText.test(oracle(pc));
    }

    /** Anything this deck wants: payoffs and the cards that feed them (equipment, spells, artifacts...). */
    public boolean fits(PaperCard pc) {
        return payoff(pc) || (enabler != null && enabler.test(pc));
    }

    public static DelveArchetype parse(String s) {
        try {
            return s == null || s.isEmpty() ? null : valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
