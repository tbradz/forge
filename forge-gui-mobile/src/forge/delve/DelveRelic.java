package forge.delve;

import forge.item.IPaperCard;
import forge.model.FModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Relics: items found during a dungeon run that buff you in every fight for the
 * rest of that run. Some are rules cards placed in your command zone (scripted in
 * res/adventure/common/custom_cards/delve_relic_*.txt, like boss perks); the rest
 * are handled in code (life, hand size, gold).
 *
 * Runs start with none: elites, Treasure Rooms and merchants offer them.
 */
public enum DelveRelic {
    // common: steady, run-long help
    VITALITY_CHARM("Vitality Charm", null, false, "+5 maximum life (and heal 5 now)."),
    IRON_BUCKLER("Iron Buckler", null, false, "Start every fight with 4 extra life (lost when the fight ends)."),
    LUCKY_COIN("Lucky Coin", null, false, "Draw an 8-card opening hand."),
    GOLD_IDOL("Gold Idol", null, false, "Fights pay 50% more gold."),
    LIFESTONE("Lifestone", "Delve Relic Lifestone", false, "Gain 1 life at the start of each of your turns."),
    SCHOLARS_LENS("Scholar's Lens", "Delve Relic Scholar's Lens", false, "Scry 1 at the start of each of your turns."),
    STONE_SKIN("Stone Skin", "Delve Relic Stone Skin", false, "Your creatures get +0/+1."),
    // rare: found from elites and treasure
    LODESTONE("Lodestone", "Delve Relic Lodestone", true, "You may play two lands each turn."),
    WAR_BANNER("War Banner", "Delve Relic War Banner", true, "Your attacking creatures get +1/+0."),
    EMBER_IDOL("Ember Idol", "Delve Relic Ember Idol", true, "Opponents lose 1 life at the start of each of your turns."),
    SWIFT_BOOTS("Swift Boots", "Delve Relic Swift Boots", true, "Your creature spells cost {1} less.");

    public final String title;
    /** the rules card placed in your command zone, or null for relics handled in code */
    public final String cardName;
    public final boolean rare;
    public final String description;

    DelveRelic(String title, String cardName, boolean rare, String description) {
        this.title = title;
        this.cardName = cardName;
        this.rare = rare;
        this.description = description;
    }

    public IPaperCard card() {
        return cardName == null ? null : FModel.getMagicDb().getCommonCards().getCard(cardName);
    }

    /** Up to {@code n} different relics the player doesn't have; {@code rareChance} 0..1 per slot. */
    public static List<DelveRelic> offer(Random rng, List<DelveRelic> owned, int n, double rareChance) {
        List<DelveRelic> common = new ArrayList<>(), rares = new ArrayList<>();
        for (DelveRelic r : values()) {
            if (owned.contains(r)) continue;
            (r.rare ? rares : common).add(r);
        }
        List<DelveRelic> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<DelveRelic> from = (rng.nextDouble() < rareChance && !rares.isEmpty()) || common.isEmpty() ? rares : common;
            if (from.isEmpty()) break;
            out.add(from.remove(rng.nextInt(from.size())));
        }
        return out;
    }

    public static DelveRelic byName(String name) {
        for (DelveRelic r : values()) if (r.name().equals(name)) return r;
        return null;
    }
}
