package forge.delve;

import forge.item.PaperCard;

import java.util.Random;

/**
 * Every gold number in Delve, in one place so balance can be tuned later.
 *
 * Model: you enter the dungeon with nothing, and all gold found inside comes
 * home with you (even if you die) into your town wallet. A typical run earns
 * roughly 250-300 gold: ~7-8 fights at 12-18, two elites at 45, the boss at 75, and
 * a little from events and selling cards. (Before the longer floors - map generator 1 -
 * it was ~4-5 fights at 25-35 and one or two elites at 60; old saved runs still pay that.)
 *
 * Merchant prices were cut when fights started paying less per fight (Tyler: the first merchant
 * should always have something you can afford): common 10, uncommon 24, rare 50, mythic 70.
 * A good run affords a few upgrades, still a real choice against saving for the town.
 * Sell prices are well under buy prices so buying and re-selling can't farm gold.
 */
public final class DelveEconomy {
    private DelveEconomy() {}

    // ---- earning ------------------------------------------------------------------
    public static final int FIGHT_GOLD_MIN = 12;
    public static final int FIGHT_GOLD_MAX = 18;
    public static final int ELITE_GOLD = 45;
    public static final int BOSS_GOLD = 75;
    /** map generator 1 (6-9 step floors) */
    private static final int GEN1_FIGHT_MIN = 25, GEN1_FIGHT_MAX = 35, GEN1_ELITE = 60;

    public static int fightGold(DelveRun.NodeType type, Random rng, int gen) {
        boolean old = gen < 2;
        switch (type) {
            case BOSS: return BOSS_GOLD;
            case ELITE: return old ? GEN1_ELITE : ELITE_GOLD;
            default:
                int lo = old ? GEN1_FIGHT_MIN : FIGHT_GOLD_MIN, hi = old ? GEN1_FIGHT_MAX : FIGHT_GOLD_MAX;
                return lo + rng.nextInt(hi - lo + 1);
        }
    }

    // ---- relics at the dungeon merchant ------------------------------------------------
    public static final int RELIC_PRICE = 40;
    public static final int RELIC_PRICE_RARE = 60;

    // ---- Card Shop prerelease (once a day): 6 packs you keep + prizes by record --------
    public static final int PRERELEASE_ENTRY = 150;

    // ---- Tavern bets: about one dungeon fight's worth at most -------------------------
    public static final int[] TAVERN_BETS = {5, 10, 20};   // per opponent
    public static final int TAVERN_BET_MAX_TOTAL = 30;      // across all opponents (Commander)

    // ---- clearing a tier (choose one reward) ----------------------------------------
    public static final int CLEAR_PACKS = 3;      // boosters of the tier's set
    public static final int CLEAR_GOLD = 120;     // on top of the gold found

    // ---- dungeon merchant ---------------------------------------------------------
    public static int buyPrice(PaperCard pc) {
        return buyPrice(pc.getRarity());
    }

    public static int buyPrice(forge.card.CardRarity rarity) {
        switch (rarity) {
            case MythicRare: return 70;
            case Rare: return 50;
            case Uncommon: return 24;
            default: return 10;
        }
    }

    // ---- town Card Shop (paid from town gold) ---------------------------------------
    public static final int PACK_PRICE = 60;
    /** Pai Gow at the Card Shop: you buy your own pack (PACK_PRICE), 3 matches a day, winner takes 2 cards. */
    public static final int PAI_GOW_PER_DAY = 3, PAI_GOW_TAKE = 2;
    public static final int PACKS_PER_DAY = 3; // of each pack type

    /** The Card Shop's cut on every card it buys from you. */
    public static final int SHOP_SELL_FEE = 5;

    /** The Card Shop only buys rares and mythics; commons and uncommons are bulk (no use for bulk yet). */
    public static boolean shopBuys(PaperCard pc) {
        return pc.getRarity() == forge.card.CardRarity.Rare || pc.getRarity() == forge.card.CardRarity.MythicRare;
    }

    /** What the Card Shop pays for a card you sell it: its sell value less the counter fee. */
    public static int shopSellPayout(PaperCard pc) {
        return Math.max(1, sellPrice(pc) - SHOP_SELL_FEE);
    }

    public static int shopPrice(PaperCard pc) {
        switch (pc.getRarity()) {
            case MythicRare: return 100;
            case Rare: return 70;
            case Uncommon: return 35;
            default: return 15;
        }
    }

    // ---- Castle tournaments ------------------------------------------------------
    public static final int CASTLE_ENTRY = 25;
    public static final int CASTLE_SEMIFINAL = 30;   // lost in the semifinal
    public static final int CASTLE_FINALIST = 75;    // lost in the final
    public static final int CASTLE_CHAMPION = 150;   // plus a booster of today's set
    public static final int POD_ENTRY = 25;
    public static final int POD_WIN = 125;           // last one standing in a Commander pod, plus a booster

    public static int sellPrice(PaperCard pc) {
        if (pc.getRules().getType().isBasicLand()) return 1;
        switch (pc.getRarity()) {
            case MythicRare: return 35;
            case Rare: return 22;
            case Uncommon: return 10;
            default: return 4;
        }
    }
}
