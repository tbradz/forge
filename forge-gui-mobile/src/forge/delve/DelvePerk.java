package forge.delve;

import forge.item.IPaperCard;
import forge.model.FModel;

import java.util.Random;

/**
 * Boss perks: each dungeon boss gets one special rule. The rule is a real card
 * (scripted in res/adventure/common/custom_cards/delve_perk_*.txt) that starts the
 * game in the boss's command zone, so Forge's rules engine enforces it and you
 * can inspect it during the duel.
 */
public enum DelvePerk {
    RELENTLESS("Relentless", "Delve Perk Relentless", "Draws an extra card each turn."),
    WARLORD("Warlord", "Delve Perk Warlord", "Its creatures get +1/+0."),
    BULWARK("Bulwark", "Delve Perk Bulwark", "Its creatures get +0/+1, and it gains 1 life each turn."),
    RAMPANT("Rampant", "Delve Perk Rampant", "Starts with an extra land in play and may play two lands each turn."),
    SPITEFUL("Spiteful", "Delve Perk Spiteful", "You lose 1 life at the start of each of its turns."),
    SUMMONER("Summoner", "Delve Perk Summoner", "Makes a 1/1 Spirit at the start of each of its turns."),
    ARCANE("Arcane", "Delve Perk Arcane", "Its spells cost {1} less.");

    public final String title;
    public final String cardName;
    public final String description;

    DelvePerk(String title, String cardName, String description) {
        this.title = title;
        this.cardName = cardName;
        this.description = description;
    }

    public static DelvePerk random(Random rng) {
        return values()[rng.nextInt(values().length)];
    }

    /** The perk's rules card, or null if the custom card isn't loaded. */
    public IPaperCard card() {
        return FModel.getMagicDb().getCommonCards().getCard(cardName);
    }
}
