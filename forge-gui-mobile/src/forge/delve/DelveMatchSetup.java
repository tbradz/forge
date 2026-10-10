package forge.delve;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import forge.LobbyPlayer;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.RegisteredPlayer;
import forge.item.IPaperCard;
import forge.model.FModel;

/**
 * Delve's duel rules with no screen attached: who plays which deck, starting life and lands, relic and perk cards,
 * Attune and the foe's mana. {@link DelveDuelScene} (the libGDX game) and the Godot bridge both build fights here,
 * so the two can never drift apart.
 */
public final class DelveMatchSetup {
    private DelveMatchSetup() { }

    /** A dungeon fight, ready to start: players[0] is you. */
    public static final class Fight {
        public final DelveRun run;
        public final DelveRun.Node node;
        public final Deck enemyDeck;
        public final int playerLife, enemyLife;
        public final String aiProfile;
        public final FoeMana foeMana;
        public final boolean boss;

        Fight(DelveRun run, DelveRun.Node node, Deck enemyDeck, int playerLife, int enemyLife, String aiProfile, FoeMana foeMana) {
            this.run = run;
            this.node = node;
            this.enemyDeck = enemyDeck;
            this.playerLife = playerLife;
            this.enemyLife = enemyLife;
            this.aiProfile = aiProfile;
            this.foeMana = foeMana;
            this.boss = node.type == DelveRun.NodeType.BOSS;
        }

        /** You and the foe as Forge players, with every Delve rule applied. */
        public List<RegisteredPlayer> players(LobbyPlayer you, LobbyPlayer foe) {
            EnumSet<GameType> variants = EnumSet.of(GameType.Adventure);
            RegisteredPlayer human = RegisteredPlayer.forVariants(2, variants, (Deck) run.deck.copyTo("Delve Deck"), null, false, null, null);
            human.setPlayer(you);
            human.setTeamNumber(0);
            human.setStartingLife(playerLife);
            applyRelics(run, human);
            RegisteredPlayer ai = RegisteredPlayer.forVariants(2, variants, enemyDeck, null, false, null, null);
            ai.setPlayer(foe);
            ai.setTeamNumber(1);
            ai.setStartingLife(enemyLife);
            if (node.perk != null) applyPerk(ai, node.perk, node.perk2, enemyDeck);
            if (run.channel) applyFoeMana(ai, enemyDeck, foeMana);
            List<RegisteredPlayer> out = new ArrayList<>();
            out.add(human);
            out.add(ai);
            return out;
        }

        public GameRules rules() {
            return DelveMatchSetup.rules(run);
        }
    }

    /** How deep the run is: 0 = first third of the dungeon, 1 = middle, 2 = last third. */
    public static int depth(DelveRun run) {
        return DelveMapGen.depth(run, run.step);
    }

    /** Everything about a dungeon fight that doesn't depend on the screen. */
    public static Fight fight(DelveRun run, DelveRun.Node node) {
        int depth = DelveMapGen.depth(run, run.step);
        DelveDifficulty diff = DelveDifficulty.current();
        DelveDay.Tier tier = node.type == DelveRun.NodeType.BOSS ? DelveDay.Tier.BOSS
                : node.type == DelveRun.NodeType.ELITE ? DelveDay.Tier.ELITE
                : diff.fightDeck(depth == 0 ? DelveDay.Tier.EARLY : depth == 2 ? DelveDay.Tier.LATE : DelveDay.Tier.FIGHT);
        Deck enemyDeck = run.day.enemyDeck(node.enemy, tier); // era cards in the enemy's colors
        if (run.channel) enemyDeck = DelveDay.landless(enemyDeck); // Attune runs: foes get starting lands and ramp instead
        int foeLife = run.foeLife(node);
        run.nextFoeLife = 0; // blessings and curses last for one fight
        int life = run.life + (run.has(DelveRelic.IRON_BUCKLER) ? 4 : 0);
        // the opening fights get a careless opponent; everyone else plays Forge's best AI (difficulty can change this)
        String ai = diff.fightAi(depth == 0, node.type == DelveRun.NodeType.FIGHT);
        return new Fight(run, node, enemyDeck, life, foeLife, ai, foeMana(node.type, depth));
    }

    /** Game rules for a dungeon fight. */
    public static GameRules rules(DelveRun run) {
        GameRules rules = new GameRules(GameType.Adventure);
        rules.setAppliedVariants(EnumSet.of(GameType.Adventure));
        rules.setGamesPerMatch(1);
        rules.setPlayForAnte(false);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);
        if (run != null && !run.channel) rules.setSmoothOpeningHands(true); // lands runs: no 0-1 or 6-7 land opening hands
        return rules;
    }

    // ---- foe mana (Attune runs) -------------------------------------------------------------------------------

    /** How many lands an Attune-run foe starts with, and its ramp card. */
    public static final class FoeMana {
        public final int lands;
        public final String rampCard;
        FoeMana(int lands, String rampCard) { this.lands = lands; this.rampCard = rampCard; }
    }

    /**
     * Attune runs: foes can't Attune, so they start with lands by role and depth and gain more on a schedule
     * (Tyler 2026-10-08: starter foes low, the boss the most). Fights start with 2 lands in the first third of
     * the dungeon and 3 after, gaining one every third turn (up to 5); elites start with 3 (4 in the last third),
     * gaining one every second turn (up to 6); bosses start with 3 and gain one every turn (up to 7).
     */
    public static FoeMana foeMana(DelveRun.NodeType type, int depth) {
        if (type == DelveRun.NodeType.BOSS) return new FoeMana(3, "Delve Foe Ramp Surge");
        if (type == DelveRun.NodeType.ELITE) return new FoeMana(depth >= 2 ? 4 : 3, "Delve Foe Ramp Steady");
        return new FoeMana(depth == 0 ? 2 : 3, "Delve Foe Ramp Slow");
    }

    public static void applyFoeMana(RegisteredPlayer ai, Deck deck, FoeMana fm) {
        ai.addExtraCardsOnBattlefield(startingLands(deck, fm.lands, false));
        IPaperCard ramp = fm.rampCard == null ? null : FModel.getMagicDb().getCommonCards().getCard(fm.rampCard);
        if (ramp != null) ai.addExtraCardsInCommandZone(List.<IPaperCard>of(ramp));
        else System.err.println("Delve: foe ramp card missing: " + fm.rampCard);
    }

    // ---- your side ----------------------------------------------------------------------------------------------

    /** Relic cards start in your command zone; Lucky Coin adds a card to your opening hand. */
    public static void applyRelics(DelveRun run, RegisteredPlayer human) {
        List<IPaperCard> cmd = new ArrayList<>();
        for (DelveRelic r : run.relics) {
            IPaperCard c = r.card();
            if (c != null) cmd.add(c);
            else if (r.cardName != null) System.err.println("Delve: relic card missing: " + r.cardName);
        }
        // dungeon fights: you're always on the play (Tyler; Castle/Tavern/prerelease games keep the coin flip).
        // Power Play is a Conspiracy, so it lives in the variant card database (Pai Gow uses it the same way).
        IPaperCard powerPlay = FModel.getMagicDb().getVariantCards().getCard("Power Play");
        if (powerPlay == null) powerPlay = FModel.getMagicDb().getCommonCards().getCard("Power Play");
        if (powerPlay != null) cmd.add(powerPlay);
        else System.err.println("Delve: Power Play missing; the dungeon's starting player falls back to a coin flip");
        if (run.compact) { // a 20-card deck can run dry in a long game: skip draws from an empty library
            IPaperCard rules = FModel.getMagicDb().getCommonCards().getCard("Delve Compact Rules");
            if (rules != null) cmd.add(rules);
            else System.err.println("Delve: Compact rules card missing");
        }
        if (run.channel) { // no lands in the deck: starting lands and Attune
            IPaperCard mana = FModel.getMagicDb().getCommonCards().getCard("Delve Mana Rules");
            if (mana != null) cmd.add(mana);
            else System.err.println("Delve: mana rules card missing");
            if (run.boon == DelveBoon.DEEP_WELL) { // the first attuned land each fight enters untapped
                IPaperCard well = FModel.getMagicDb().getCommonCards().getCard("Delve Boon Deep Well");
                if (well != null) cmd.add(well);
            }
            // three lands to start, one per deck colour (colourless filler); relics add more
            int n = 3 + (run.has(DelveRelic.MANA_CRYSTAL) ? 1 : 0) + (run.has(DelveRelic.BROKEN_CROWN) ? 2 : 0);
            human.addExtraCardsOnBattlefield(startingLands(run.deck, n, true));
        }
        if (!cmd.isEmpty()) human.addExtraCardsInCommandZone(cmd);
        if (run.has(DelveRelic.LUCKY_COIN)) human.setStartingHand(human.getStartingHand() + 1);
        if (run.has(DelveRelic.BROKEN_CROWN)) human.setStartingHand(human.getStartingHand() - 1);
        // (a free basic in play at the start was tried 2026-10-04 and dropped: too strong, Tyler)
    }

    /**
     * Starting lands for a land-free (Attune) deck: one basic of each of the deck's colours, most-used colour first;
     * any extra lands are Wastes ({@code colourlessFill}, the player) or more of the deck's colours (foes).
     */
    public static List<IPaperCard> startingLands(Deck deck, int n, boolean colourlessFill) {
        int[] pips = new int[5];
        for (forge.item.PaperCard pc : deck.getMain().toFlatList()) {
            if (pc.getRules().getType().isLand()) continue;
            int[] counts = pc.getRules().getManaCost().getColorShardCounts(); // WUBRGC
            for (int i = 0; i < 5; i++) pips[i] += counts[i];
        }
        List<Integer> colours = new ArrayList<>();
        for (int i = 0; i < 5; i++) if (pips[i] > 0) colours.add(i);
        colours.sort((a, b) -> pips[b] - pips[a]);
        String[] basics = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        List<IPaperCard> out = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            String name = k < colours.size() ? basics[colours.get(k)]
                    : colourlessFill || colours.isEmpty() ? "Wastes" : basics[colours.get(k % colours.size())];
            IPaperCard land = FModel.getMagicDb().getCommonCards().getCard(name);
            if (land != null) out.add(land);
        }
        return out;
    }

    // ---- the foe's perks ----------------------------------------------------------------------------------------

    /** The perk cards start in the foe's command zone; Rampant also starts with a land in play. */
    public static void applyPerk(RegisteredPlayer ai, DelvePerk perk, DelvePerk perk2, Deck deck) {
        if (perk2 != null && perk2.card() != null) ai.addExtraCardsInCommandZone(List.<IPaperCard>of(perk2.card()));
        IPaperCard card = perk.card();
        if (card == null) {
            System.err.println("Delve: perk card missing: " + perk.cardName);
            return;
        }
        ai.addExtraCardsInCommandZone(List.<IPaperCard>of(card));
        if (perk == DelvePerk.RAMPANT) {
            forge.item.PaperCard land = null;
            for (java.util.Map.Entry<forge.item.PaperCard, Integer> e : deck.getMain())
                if (e.getKey().getRules().getType().isBasicLand()) { land = e.getKey(); break; }
            if (land != null) ai.addExtraCardsOnBattlefield(List.<IPaperCard>of(land));
        }
    }
}
