package forge.delve;

import forge.item.PaperCard;

import java.util.Random;

/**
 * Every gold number in Delve, in one place so balance can be tuned later.
 *
 * Model: you enter the dungeon with nothing, and all gold found inside comes
 * home with you (even if you die) into your town wallet. A typical Standard run
 * earns roughly 150-250 gold: ~5 fights at 20-30, an elite at ~45, and a little
 * from events and selling cards.
 *
 * Prices are set so a merchant purchase is a real choice against saving for the
 * town: a good run affords one rare or two or three commons, not everything.
 * Sell prices are well under buy prices so buying and re-selling can't farm gold.
 */
public final class DelveEconomy {
    private DelveEconomy() {}

    // ---- earning ------------------------------------------------------------------
    public static final int FIGHT_GOLD_MIN = 20;
    public static final int FIGHT_GOLD_MAX = 30;
    public static final int ELITE_GOLD = 45;
    public static final int BOSS_GOLD = 75;

    public static int fightGold(DelveRun.NodeType type, Random rng) {
        switch (type) {
            case BOSS: return BOSS_GOLD;
            case ELITE: return ELITE_GOLD;
            default: return FIGHT_GOLD_MIN + rng.nextInt(FIGHT_GOLD_MAX - FIGHT_GOLD_MIN + 1);
        }
    }

    // ---- relics at the dungeon merchant ------------------------------------------------
    public static final int RELIC_PRICE = 60;
    public static final int RELIC_PRICE_RARE = 90;

    // ---- clearing a tier (choose one reward) ----------------------------------------
    public static final int CLEAR_PACKS = 3;      // boosters of the tier's set
    public static final int CLEAR_GOLD = 120;     // on top of the gold found

    // ---- dungeon merchant ---------------------------------------------------------
    public static int buyPrice(PaperCard pc) {
        switch (pc.getRarity()) {
            case MythicRare: return 110;
            case Rare: return 80;
            case Uncommon: return 40;
            default: return 18;
        }
    }

    // ---- town Card Shop (paid from town gold) ---------------------------------------
    public static final int PACK_PRICE = 60;
    public static final int PACKS_PER_DAY = 3; // of each pack type

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
