package forge.delve;

import forge.adventure.data.EnemyData;
import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * State of one Delve run: the deck being built, life carried between fights,
 * gold, and the dungeon map. Runs are in memory; only the collection is saved.
 */
public class DelveRun {
    public enum NodeType {
        FIGHT("Fight"), ELITE("Elite"), REST("Rest"), EVENT("Event"), MERCHANT("Merchant"), BOSS("Boss"),
        TREASURE("Treasure"), SHRINE("Shrine");
        public final String label;
        NodeType(String label) { this.label = label; }
    }

    public static class Node {
        public final NodeType type;
        public final EnemyData enemy;            // fights only
        public int enemyLife;                   // set by the map generator from the room's depth
        public final DelveEvents.Event event;    // events only
        public List<PaperCard> stock;            // merchant only, filled on first visit
        public DelvePerk perk;                   // bosses and elites
        public DelvePerk perk2;                  // bosses get a second, milder perk
        Node(NodeType type, EnemyData enemy, int enemyLife, DelveEvents.Event event) {
            this.type = type;
            this.enemy = enemy;
            this.enemyLife = enemyLife;
            this.event = event;
        }
    }

    public static final int MAX_LIFE = 20;
    public static final int REST_HEAL = 7;
    /** A run deck can never go below this many cards. */
    public static final int MIN_DECK = 40;
    /** Returned by an event choice to ask the map to show a pick-1-of-3. */
    public static final String PICK_CARD = "\u0000pick";
    /** event result: let the player choose a card in the deck to copy */
    public static final String PICK_COPY = "\u0000copy";
    /** event result: let the player choose a card in the deck to remove (above the minimum) */
    public static final String PICK_REMOVE = "\u0000remove";

    private static DelveRun current;

    public final long seed;
    /** map generator version that built this run (see {@link DelveMapGen#CURRENT_GEN}); saved runs keep theirs */
    public final int gen;
    public final Random rng;
    public final DelveDay day;
    public final Deck deck;
    public int life = MAX_LIFE;
    /** relics found this run (see {@link DelveRelic}) */
    public final List<DelveRelic> relics = new ArrayList<>();
    /** the relic a merchant offers, per merchant room ("step.index" -> relic) */
    public final java.util.Map<String, DelveRelic> merchantRelics = new java.util.HashMap<>();

    /** Maximum life, including relics. */
    public int maxLife() {
        return MAX_LIFE + (relics.contains(DelveRelic.VITALITY_CHARM) ? 5 : 0);
    }

    public boolean has(DelveRelic r) {
        return relics.contains(r);
    }

    /** Gain a relic (Vitality Charm also heals 5 right away). */
    public String gainRelic(DelveRelic r) {
        if (r == null || relics.contains(r)) return "";
        relics.add(r);
        if (r == DelveRelic.VITALITY_CHARM) life = Math.min(maxLife(), life + 5);
        return "You gain the " + r.title + ": " + r.description;
    }
    public int gold = 0;
    /** layers.get(i) = the choices on step i of the map */
    public final List<List<Node>> layers = new ArrayList<>();
    /** index of the layer the player is about to choose from */
    public int step = 0;
    public Node currentNode;
    public int fightsWon = 0;
    /** added to the next foe's starting life (blessings negative, curses positive), then cleared */
    public int nextFoeLife = 0;
    public boolean over = false;
    public boolean cleared = false;
    public final List<PaperCard> picked = new ArrayList<>();
    /** index of the room chosen on each completed step */
    public final List<Integer> chosen = new ArrayList<>();
    /** Cards you may pick from the run deck as a clear reward. */
    public static final int CLEAR_KEEPS = 5;

    private DelveRun(DelveDay day, Deck deck, long seed, int gen) {
        this.day = day;
        this.deck = deck;
        this.seed = seed;
        this.gen = gen;
        this.rng = new Random(seed);
        DelveMapGen.build(this);
    }

    public static DelveRun start(DelveDay day, Deck starter) {
        current = new DelveRun(day, starter, day.seed ^ System.nanoTime(), DelveMapGen.CURRENT_GEN);
        DelveProfile.get().markDelved();
        DelveRunSave.save(current);
        return current;
    }

    /** Rebuild a run from its seed and generator version (same map) — used by DelveRunSave. */
    static DelveRun restore(DelveDay day, Deck deck, long seed, int gen) {
        current = new DelveRun(day, deck, seed, gen);
        return current;
    }

    public static DelveRun current() {
        return current;
    }

    /** Drop the in-memory run without deleting its save (switching save slots). */
    static void forget() {
        current = null;
    }

    public static void clear() {
        current = null;
        DelveRunSave.delete();
    }

    /** Share of the dungeon completed, 0..1 (steps done / total steps). */
    public double completion() {
        return layers.isEmpty() ? 0 : Math.min(1.0, step / (double) layers.size());
    }

    /**
     * Whether room j of layer s-1 has a path to room k of layer s. Paths join rooms whose
     * vertical positions are close (like Slay the Spire), so the map reads as lanes instead of
     * everything connecting to everything. Pure layout maths, so saved runs get the same paths.
     */
    public boolean connected(int s, int j, int k) {
        if (s <= 0) return true; // the entrance reaches every first room
        int n = layers.get(s - 1).size(), m = layers.get(s).size();
        float a = n == 1 ? 0.5f : j / (float) (n - 1), b = m == 1 ? 0.5f : k / (float) (m - 1);
        return Math.abs(a - b) <= 0.5f + 1e-4f;
    }

    /** Whether room k of the current step can be entered from where the player stands. */
    public boolean reachable(int k) {
        if (step == 0) return true;
        int from = step - 1 < chosen.size() ? chosen.get(step - 1) : -1;
        return from < 0 || connected(step, from, k);
    }

    public List<Node> nextChoices() {
        return step < layers.size() ? layers.get(step) : new ArrayList<>();
    }

    public int deckSize() {
        return deck.getMain().countAll();
    }

    /** How many cards can be removed before hitting the minimum. */
    public int removableCount() {
        return Math.max(0, deckSize() - MIN_DECK);
    }

    /** Colors of the non-land cards in the deck (what reward picks lean toward). */
    public ColorSet deckColors() {
        int mask = 0;
        for (Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (!e.getKey().getRules().getType().isLand())
                mask |= e.getKey().getRules().getColor().getColor();
        }
        return ColorSet.fromMask(mask);
    }

    // ---- effects used by events, rests and the merchant -------------------------------

    public String heal(int amount) {
        int before = life;
        life = Math.min(maxLife(), life + amount);
        return "You heal " + (life - before) + " (life " + life + ").";
    }

    /** Events can hurt but never kill: life stops at 1. */
    public String damage(int amount) {
        int before = life;
        life = Math.max(1, life - amount);
        return "You lose " + (before - life) + " life (life " + life + ").";
    }

    /** Blessing (negative) or curse (positive) on the next foe's starting life. */
    public String nextFoe(int lifeChange) {
        nextFoeLife += lifeChange;
        return lifeChange < 0 ? "Your next foe starts with " + (-lifeChange) + " less life."
                : "Your next foe starts with " + lifeChange + " more life.";
    }

    public String gainGold(int amount) {
        gold += amount;
        return "+" + amount + " gold.";
    }

    public String spendGold(int amount) {
        gold = Math.max(0, gold - amount);
        return "-" + amount + " gold.";
    }

    public String gainRandomCard(DelveEvents.RarityTier tier) {
        PaperCard pc = randomCard(tier, true);
        if (pc == null) return "";
        deck.getMain().add(pc);
        picked.add(pc);
        return "You gain " + pc.getName() + ".";
    }

    /** Removes a random non-basic card; if the deck is at the minimum it is replaced instead. */
    public String loseRandomCard() {
        PaperCard lost = randomDeckCard();
        if (lost == null) return "";
        deck.getMain().remove(lost);
        if (deckSize() < MIN_DECK) {
            PaperCard replacement = sameColorCard(DelveEvents.RarityTier.COMMON, lost);
            deck.getMain().add(replacement);
            return "You lose " + lost.getName() + ". Your deck can't drop below " + MIN_DECK
                    + ", so " + replacement.getName() + " takes its place.";
        }
        return "You lose " + lost.getName() + ".";
    }

    /** Swap a random non-basic card for a random card of the same or better rarity. */
    public String transformRandomCard() {
        PaperCard old = randomDeckCard();
        if (old == null) return "Nothing happens.";
        DelveEvents.RarityTier tier = old.getRarity() == CardRarity.Common
                ? DelveEvents.RarityTier.UNCOMMON : DelveEvents.RarityTier.RARE;
        PaperCard neu = betterSameColor(old);
        if (neu == null) neu = sameColorCard(tier, old);
        deck.getMain().remove(old);
        deck.getMain().add(neu);
        return old.getName() + " becomes " + neu.getName() + ".";
    }

    /** A random card of the same colours ranked better than {@code like}, or null. */
    PaperCard betterSameColor(PaperCard like) {
        byte want = like.getRules().getColorIdentity().getColor();
        double sc = DelveRank.score(like);
        List<PaperCard> match = new ArrayList<>();
        for (List<PaperCard> src : List.of(day.commons, day.uncommons, day.rares))
            for (PaperCard pc : src)
                if (pc.getRules().getColorIdentity().getColor() == want && DelveRank.score(pc) > sc
                        && !pc.getName().equals(like.getName()) && !pc.getRules().getType().isLand())
                    match.add(pc);
        return match.isEmpty() ? null : match.get(rng.nextInt(match.size()));
    }

    /** A random card of the given rarity with the same colors as {@code like} (colorless stays colorless). */
    PaperCard sameColorCard(DelveEvents.RarityTier tier, PaperCard like) {
        byte want = like.getRules().getColorIdentity().getColor();
        for (DelveEvents.RarityTier t : new DelveEvents.RarityTier[]{tier, DelveEvents.RarityTier.UNCOMMON, DelveEvents.RarityTier.COMMON}) {
            List<PaperCard> match = new ArrayList<>();
            for (PaperCard pc : DelveEvents.tierPool(day, t))
                if (pc.getRules().getColorIdentity().getColor() == want && !pc.getName().equals(like.getName())
                        && pc.getRules().getType().isLand() == like.getRules().getType().isLand())
                    match.add(pc);
            if (!match.isEmpty()) return match.get(rng.nextInt(match.size()));
        }
        return randomCard(tier, true);
    }

    PaperCard randomDeckCard() {
        List<PaperCard> flat = new ArrayList<>();
        for (PaperCard pc : deck.getMain().toFlatList())
            if (!pc.getRules().getType().isBasicLand()) flat.add(pc);
        return flat.isEmpty() ? null : flat.get(rng.nextInt(flat.size()));
    }

    /** A random card from today's pool at the given rarity, preferring the deck's colors. */
    PaperCard randomCard(DelveEvents.RarityTier tier, boolean onColor) {
        List<PaperCard> pool = DelveEvents.tierPool(day, tier);
        if (pool.isEmpty()) pool = day.commons;
        if (pool.isEmpty()) return null;
        ColorSet colors = deckColors();
        for (int i = 0; i < 60; i++) {
            PaperCard pc = pool.get(rng.nextInt(pool.size()));
            ColorSet id = pc.getRules().getColorIdentity();
            if (!onColor || id.isColorless() || colors.containsAllColorsFrom(id.getColor()))
                return pc;
        }
        return pool.get(rng.nextInt(pool.size()));
    }

    // ---- merchant prices (see DelveEconomy) ------------------------------------------

    public static int sellPrice(PaperCard pc) {
        return DelveEconomy.sellPrice(pc);
    }

    public static int buyPrice(PaperCard pc) {
        return DelveEconomy.buyPrice(pc);
    }
}
