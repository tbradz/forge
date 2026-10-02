package forge.delve;

import com.badlogic.gdx.utils.Array;
import forge.adventure.data.EnemyData;
import forge.adventure.data.WorldData;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The card world for one tier on one in-game day.
 *
 * Tiers: every regular expansion/core set (150+ cards) from mid-2003 on is a tier,
 * in release order. The player starts at tier 0 and unlocks the next tier by
 * clearing a dungeon run at their highest tier; any unlocked tier can be replayed.
 * A run's draft, rewards and enemy decks come from its tier's set. The town (shop,
 * Castle, Tavern) uses the player's highest unlocked tier.
 *
 * The day number still seeds daily things (shop stock, enemy roster order).
 */
public class DelveDay {
    /** Tier 0 is the first set released on or after this date. */
    static final String ERA_START = "2003-07-01";
    /** Sets (ending at the tier's set) used for the shop's "era" stock and second pack type. */
    static final int ERA_WIDTH = 3;
    /** Sets (ending at the tier's set) used for Commander decks, which need a deeper pool. */
    static final int COMMANDER_WIDTH = 6;

    public final int dayNumber;
    public final int tier;
    public final long seed;
    public final CardEdition edition;
    /** the tier's set plus a couple before it */
    public final List<CardEdition> eraSets = new ArrayList<>();
    private final List<CardEdition> commanderSets = new ArrayList<>();
    public final List<PaperCard> commons = new ArrayList<>();
    public final List<PaperCard> uncommons = new ArrayList<>();
    public final List<PaperCard> rares = new ArrayList<>(); // rare + mythic
    public final List<EnemyData> weakEnemies = new ArrayList<>();
    public final List<EnemyData> eliteEnemies = new ArrayList<>();
    public final List<EnemyData> bossEnemies = new ArrayList<>();

    private static DelveDay cached;
    private static List<CardEdition> tierCache;

    /** The town's card world: today, at the player's highest unlocked tier. */
    public static DelveDay today() {
        return forTier(DelveProfile.get().topTier());
    }

    /** Today at a given tier (a dungeon run). */
    public static DelveDay forTier(int tier) {
        return forTier(DelveProfile.get().day(), tier);
    }

    public static DelveDay forTier(int dayNumber, int tier) {
        tier = Math.max(0, Math.min(tier, tiers().size() - 1));
        if (cached == null || cached.dayNumber != dayNumber || cached.tier != tier)
            cached = new DelveDay(dayNumber, tier);
        return cached;
    }

    /** Every tier's set, oldest first. */
    public static List<CardEdition> tiers() {
        if (tierCache == null) {
            List<CardEdition> all = allSets();
            java.util.Date startDate = java.sql.Date.valueOf(ERA_START);
            tierCache = new ArrayList<>();
            for (CardEdition e : all) if (!e.getDate().before(startDate)) tierCache.add(e);
            if (tierCache.isEmpty()) tierCache.addAll(all);
        }
        return tierCache;
    }

    public static String tierName(int tier) {
        CardEdition e = tiers().get(Math.max(0, Math.min(tier, tiers().size() - 1)));
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(e.getDate());
        return "Tier " + (tier + 1) + ": " + e.getName() + " (" + c.get(java.util.Calendar.YEAR) + ")";
    }

    private DelveDay(int dayNumber, int tier) {
        this.dayNumber = dayNumber;
        this.tier = tier;
        this.seed = dayNumber * 0x9E3779B97F4A7C15L + tier * 0x632BE59BD9B4E019L + 12345L;
        Random rng = new Random(seed);
        List<CardEdition> all = allSets();
        this.edition = tiers().get(tier);
        int at = all.indexOf(edition);
        for (int i = Math.max(0, at - ERA_WIDTH + 1); i <= at; i++) eraSets.add(all.get(i));
        for (int i = Math.max(0, at - COMMANDER_WIDTH + 1); i <= at; i++) commanderSets.add(all.get(i));
        buildPool();
        buildEnemies(rng);
    }

    /** Regular expansions and core sets with a real rarity spread, oldest first. */
    static List<CardEdition> allSets() {
        List<CardEdition> out = new ArrayList<>();
        for (CardEdition e : FModel.getMagicDb().getEditions()) {
            if (e.getType() != CardEdition.Type.EXPANSION && e.getType() != CardEdition.Type.CORE)
                continue;
            if (e.getDate() == null || e.getDate().after(new java.util.Date()))
                continue;
            if (e.getCards() == null || e.getCards().size() < 150)
                continue;
            out.add(e);
        }
        out.sort(Comparator.comparing(CardEdition::getDate).thenComparing(CardEdition::getCode));
        return out;
    }

    /** Year of the era's newest set, for display. */
    public int eraYear() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.setTime(edition.getDate());
        return c.get(java.util.Calendar.YEAR);
    }

    static boolean isRecent(CardEdition e) {
        if (e.getDate() == null) return false;
        java.util.Calendar cut = java.util.Calendar.getInstance();
        cut.add(java.util.Calendar.YEAR, -6);
        return e.getDate().after(cut.getTime()) && e.getDate().before(new java.util.Date());
    }

    // ---- wider card pools ---------------------------------------------------------

    private List<PaperCard> eraCommons, eraUncommons, eraRares;     // eraSets
    private List<PaperCard> cmdCommons, cmdUncommons, cmdRares;     // commanderSets

    private void loadEraPool() {
        if (eraCommons != null) return;
        List<List<PaperCard>> p = pool(eraSets);
        eraRares = p.get(0); eraUncommons = p.get(1); eraCommons = p.get(2);
        p = pool(commanderSets);
        cmdRares = p.get(0); cmdUncommons = p.get(1); cmdCommons = p.get(2);
    }

    /** [rares+mythics, uncommons, commons] of the given sets, no basics, one printing per name. */
    private static List<List<PaperCard>> pool(List<CardEdition> sets) {
        java.util.Set<String> codes = new java.util.HashSet<>();
        for (CardEdition e : sets) codes.add(e.getCode());
        List<PaperCard> c = new ArrayList<>(), u = new ArrayList<>(), r = new ArrayList<>();
        for (PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards(x -> codes.contains(x.getEdition()))) {
            if (pc.getRules().getType().isBasicLand()) continue;
            CardRarity rr = pc.getRarity();
            if (rr == CardRarity.Common) c.add(pc);
            else if (rr == CardRarity.Uncommon) u.add(pc);
            else if (rr == CardRarity.Rare || rr == CardRarity.MythicRare) r.add(pc);
        }
        dedupe(c); dedupe(u); dedupe(r);
        return List.of(r, u, c);
    }

    private void buildPool() {
        String code = edition.getCode();
        for (PaperCard pc : FModel.getMagicDb().getCommonCards().getAllCards(c -> code.equals(c.getEdition()))) {
            if (pc.getRules().getType().isBasicLand())
                continue;
            CardRarity r = pc.getRarity();
            if (r == CardRarity.Common) commons.add(pc);
            else if (r == CardRarity.Uncommon) uncommons.add(pc);
            else if (r == CardRarity.Rare || r == CardRarity.MythicRare) rares.add(pc);
        }
        // de-duplicate reprints of the same card within the set (alternate arts)
        dedupe(commons);
        dedupe(uncommons);
        dedupe(rares);
    }

    private static void dedupe(List<PaperCard> list) {
        List<String> seen = new ArrayList<>();
        list.removeIf(pc -> {
            if (seen.contains(pc.getName())) return true;
            seen.add(pc.getName());
            return false;
        });
        list.sort(Comparator.comparing(PaperCard::getName));
    }

    private void buildEnemies(Random rng) {
        Array<EnemyData> all = WorldData.getAllEnemies();
        for (EnemyData e : all) {
            if (e.deck == null || e.deck.length == 0 || e.boss)
                continue;
            if (e.life <= 14) weakEnemies.add(e);
            else if (e.life <= 25) eliteEnemies.add(e);
            else bossEnemies.add(e);
        }
        Comparator<EnemyData> byName = Comparator.comparing(e -> e.name);
        weakEnemies.sort(byName);
        eliteEnemies.sort(byName);
        bossEnemies.sort(byName);
        Collections.shuffle(weakEnemies, rng);
        Collections.shuffle(eliteEnemies, rng);
        Collections.shuffle(bossEnemies, rng);
    }

    public String themeName() {
        return edition.getName();
    }

    /** Deck strength for generated opponents. */
    public enum Tier { EARLY, FIGHT, LATE, ELITE, BOSS, CASTLE }

    private final java.util.Map<String, Deck> enemyDecks = new java.util.HashMap<>();

    /**
     * A 40-card deck for an opponent, built from this era's cards in the enemy's own
     * colors. Stronger tiers get more uncommons and rares. Cards the AI can't play
     * well are left out. Same enemy + tier on the same day = same deck.
     */
    public Deck enemyDeck(EnemyData enemy, Tier tier) {
        String key = enemy.getName() + "|" + tier;
        Deck cached = enemyDecks.get(key);
        if (cached != null) return cached;
        Random rng = new Random(seed ^ key.hashCode());
        ColorSet colors = enemyColors(enemy, rng);
        int[] q; // commons, uncommons, rares (of 23 spells)
        switch (tier) {
            case EARLY: q = new int[]{21, 2, 0}; break;  // near the entrance: gentle
            case LATE: q = new int[]{15, 7, 1}; break;   // close to the boss
            case ELITE: q = new int[]{12, 8, 3}; break;
            case BOSS: q = new int[]{7, 9, 7}; break;
            case CASTLE: q = new int[]{8, 10, 5}; break;
            default: q = new int[]{18, 5, 0};
        }
        // quality ramps with the tier of foe: early fights play the set's weaker cards, bosses its best
        double lo = 0, hi = 1;
        boolean best = false;
        switch (tier) {
            case EARLY: hi = 0.55; break;
            case FIGHT: hi = 0.75; break;
            case LATE: lo = 0.2; hi = 0.9; break;
            case ELITE: lo = 0.3; break;
            default: best = true; // BOSS, CASTLE
        }
        Deck d = buildDeck(colors, q[0], q[1], q[2], true, rng,
                tier == Tier.BOSS ? bossTheme(enemy).fits : null, lo, hi, best); // bosses play to a plan
        d.setName(enemy.getName());
        enemyDecks.put(key, d);
        return d;
    }

    // ---- boss themes ------------------------------------------------------------------

    /** A deck plan for a boss: a name shown to the player and the cards that fit it. */
    public static final class Theme {
        public final String name;
        public final java.util.function.Predicate<PaperCard> fits;
        Theme(String name, java.util.function.Predicate<PaperCard> fits) { this.name = name; this.fits = fits; }
    }

    private final java.util.Map<String, Theme> bossThemes = new java.util.HashMap<>();

    private static String oracle(PaperCard pc) {
        String o = pc.getRules().getOracleText();
        return o == null ? "" : o.toLowerCase();
    }

    /**
     * The boss's plan, chosen from what this set offers in its colors: a creature type
     * (tribal), flyers, removal-heavy control, or go-wide aggression. Whichever theme
     * has the most cards wins (ties broken by the day's seed).
     */
    public Theme bossTheme(EnemyData enemy) {
        String key = enemy.getName();
        Theme cached = bossThemes.get(key);
        if (cached != null) return cached;
        Random rng = new Random(seed ^ (key + "|theme").hashCode());
        ColorSet colors = enemyColors(enemy, new Random(seed ^ (key + "|" + Tier.BOSS).hashCode()));
        loadEraPool();
        List<PaperCard> pool = new ArrayList<>();
        for (List<PaperCard> src : List.of(rares, uncommons, commons))
            for (PaperCard pc : src) {
                ColorSet id = pc.getRules().getColorIdentity();
                if (!pc.getRules().getType().isLand() && !id.isColorless() && colors.containsAllColorsFrom(id.getColor()))
                    pool.add(pc);
            }
        Theme best = themeFor(pool, rng);
        bossThemes.put(key, best);
        return best;
    }

    /** The strongest plan among {@code pool}: a creature type, flyers, control or swarm. */
    static Theme themeFor(List<PaperCard> pool, Random rng) {
        List<Theme> themes = new ArrayList<>();
        java.util.Map<String, Integer> types = new java.util.HashMap<>();
        for (PaperCard pc : pool)
            for (String t : pc.getRules().getType().getCreatureTypes())
                if (!t.equals("Human")) types.merge(t, 1, Integer::sum); // "Human" is rarely a real plan
        String tribe = null;
        for (java.util.Map.Entry<String, Integer> e : types.entrySet())
            if (e.getValue() >= Math.max(5, pool.size() / 12) && (tribe == null || e.getValue() > types.get(tribe))) tribe = e.getKey();
        if (tribe != null) {
            final String t = tribe;
            themes.add(new Theme(t + (t.endsWith("f") ? "s" : t.endsWith("s") ? "" : "s"),
                    pc -> pc.getRules().getType().getCreatureTypes().contains(t) || oracle(pc).contains(t.toLowerCase())));
        }
        themes.add(new Theme("Flyers", pc -> pc.getRules().getType().isCreature() && oracle(pc).contains("flying")));
        themes.add(new Theme("Control", pc -> {
            String o = oracle(pc);
            return !pc.getRules().getType().isCreature() && (o.contains("destroy target") || o.contains("exile target")
                    || (o.contains("deals") && o.contains("damage to target")) || o.contains("counter target")
                    || o.contains("return target creature"));
        }));
        themes.add(new Theme("Swarm", pc -> pc.getRules().getType().isCreature()
                && pc.getRules().getManaCost().getCMC() <= 2));
        Theme best = null;
        int bestCount = -1;
        Collections.shuffle(themes, rng);
        for (Theme th : themes) {
            int n = 0;
            for (PaperCard pc : pool) if (th.fits.test(pc)) n++;
            if (th.name.equals("Swarm")) n = n * 2 / 3; // only when nothing else stands out
            if (n > bestCount) { best = th; bestCount = n; }
        }
        return best;
    }

    // ---- Jumpstart half-decks: the run's starting deck ---------------------------------

    /** 20 cards for one half of a starting deck: 12 spells of one colour around a theme, plus 8 basics. */
    public static final class HalfDeck {
        public final String name;
        public final char color;            // W U B R G
        public final List<PaperCard> spells;
        public final PaperCard face;        // its best card, shown when choosing
        HalfDeck(String name, char color, List<PaperCard> spells, PaperCard face) {
            this.name = name; this.color = color; this.spells = spells; this.face = face;
        }
    }

    private static final String[] COLOR_NAMES = {"White", "Blue", "Black", "Red", "Green"};
    private static final String WUBRG = "WUBRG";
    private static final String[] BASICS = {"Plains", "Island", "Swamp", "Mountain", "Forest"};

    /**
     * Three half-decks of different colours from the tier's set, built from the lower-middle
     * of the set's quality rankings so every run starts solid but modest.
     */
    public List<HalfDeck> halfDecks(Random rng) {
        List<Integer> colors = new ArrayList<>(List.of(0, 1, 2, 3, 4));
        Collections.shuffle(colors, rng);
        List<HalfDeck> out = new ArrayList<>();
        for (int c : colors) {
            if (out.size() >= 3) break;
            HalfDeck h = halfDeck(c, rng);
            if (h != null) out.add(h);
        }
        return out;
    }

    private HalfDeck halfDeck(int c, Random rng) {
        byte mask = ColorSet.fromNames(String.valueOf(WUBRG.charAt(c)).toCharArray()).getColor();
        List<PaperCard> pool = new ArrayList<>();
        for (List<PaperCard> src : List.of(commons, uncommons, rares))
            for (PaperCard pc : src) {
                if (pc.getRules().getType().isLand() || pc.getRules().getAiHints().getRemAIDecks()) continue;
                int cmc = pc.getRules().getManaCost().getCMC();
                if (cmc < 1 || cmc > 6) continue;
                if (pc.getRules().getColorIdentity().getColor() != mask) continue; // mono-colour only
                pool.add(pc);
            }
        if (pool.size() < 16) return null;
        Theme theme = themeFor(pool, rng);
        // modest: skip the best quarter of the set and the very worst cards
        List<PaperCard> band = new ArrayList<>();
        for (double lo = 0.15, hi = 0.75; band.size() < 20 && hi <= 1.01; lo -= 0.05, hi += 0.05) {
            band.clear();
            for (PaperCard pc : pool) {
                double sc = DelveRank.score(pc);
                if (sc >= lo && sc <= hi) band.add(pc);
            }
        }
        Collections.shuffle(band, rng);
        band.sort((a, b) -> Boolean.compare(theme.fits.test(b), theme.fits.test(a))); // theme first (stable)
        List<PaperCard> spells = new ArrayList<>();
        int themed = 0;
        for (PaperCard pc : band) { // up to 6 theme cards
            if (themed >= 6 || !theme.fits.test(pc)) break;
            spells.add(pc);
            themed++;
        }
        int[][] curve = {{1, 1}, {2, 3}, {3, 2}, {4, 2}, {5, 1}}; // 9 creatures
        for (int[] slot : curve) {
            long have = spells.stream().filter(pc -> pc.getRules().getType().isCreature() && cmcSlot(pc) == slot[0]).count();
            for (PaperCard pc : band) {
                if (have >= slot[1]) break;
                if (spells.contains(pc) || !pc.getRules().getType().isCreature() || cmcSlot(pc) != slot[0]) continue;
                spells.add(pc);
                have++;
            }
        }
        for (PaperCard pc : band) if (spells.size() < 12 && !spells.contains(pc) && !pc.getRules().getType().isCreature()) spells.add(pc);
        for (PaperCard pc : band) if (spells.size() < 12 && !spells.contains(pc)) spells.add(pc);
        PaperCard face = spells.get(0);
        for (PaperCard pc : spells) if (DelveRank.score(pc) > DelveRank.score(face)) face = pc;
        return new HalfDeck(COLOR_NAMES[c] + " " + theme.name, WUBRG.charAt(c), spells, face);
    }

    private static int cmcSlot(PaperCard pc) {
        return Math.min(5, pc.getRules().getManaCost().getCMC());
    }

    /** Two half-decks shuffled together: 24 spells and 16 basics. */
    public static Deck combine(HalfDeck a, HalfDeck b) {
        Deck d = new Deck(a.name + " + " + b.name);
        d.getMain().add(a.spells);
        d.getMain().add(b.spells);
        for (HalfDeck h : List.of(a, b))
            for (int i = 0; i < 8; i++)
                d.getMain().add(FModel.getMagicDb().getCommonCards().getCard(BASICS[WUBRG.indexOf(h.color)]));
        return d;
    }

    private static ColorSet enemyColors(EnemyData enemy, Random rng) {
        String c = enemy.colors == null ? "" : enemy.colors.replaceAll("[^WUBRG]", "");
        if (c.length() > 2) c = c.substring(0, 2);
        if (c.isEmpty()) {
            String wubrg = "WUBRG";
            int a = rng.nextInt(5), b = (a + 1 + rng.nextInt(4)) % 5;
            c = "" + wubrg.charAt(a) + wubrg.charAt(b);
        }
        return ColorSet.fromNames(c.toCharArray());
    }

    /**
     * 23 spells on a creature curve plus 17 basics.
     *
     * @param forAI skip cards Forge marks as unplayable for the AI
     */
    Deck buildDeck(ColorSet colors, int nCommon, int nUncommon, int nRare, boolean forAI, Random rng) {
        return buildDeck(colors, nCommon, nUncommon, nRare, forAI, rng, null);
    }

    /** As above; cards matching {@code prefer} are picked first at every rarity (a deck theme). */
    Deck buildDeck(ColorSet colors, int nCommon, int nUncommon, int nRare, boolean forAI, Random rng,
                   java.util.function.Predicate<PaperCard> prefer) {
        return buildDeck(colors, nCommon, nUncommon, nRare, forAI, rng, prefer, 0, 1, false);
    }

    /**
     * Full version: only cards ranked between {@code lo} and {@code hi} come from the tier's
     * set (the wider era fills gaps), and {@code bestFirst} favours the set's top picks.
     */
    Deck buildDeck(ColorSet colors, int nCommon, int nUncommon, int nRare, boolean forAI, Random rng,
                   java.util.function.Predicate<PaperCard> prefer, double lo, double hi, boolean bestFirst) {
        loadEraPool();
        java.util.function.Predicate<PaperCard> fits = pc -> {
            if (pc.getRules().getType().isLand()) return false;
            int cmc = pc.getRules().getManaCost().getCMC();
            if (cmc < 1 || cmc > 7) return false;
            ColorSet id = pc.getRules().getColorIdentity();
            if (id.isColorless() || !colors.containsAllColorsFrom(id.getColor())) return false;
            return !forAI || !pc.getRules().getAiHints().getRemAIDecks();
        };
        List<List<PaperCard>> byRarity = new ArrayList<>(); // [rare, uncommon, common]: the tier's set
        for (List<PaperCard> src : List.of(rares, uncommons, commons)) {
            List<PaperCard> l = new ArrayList<>();
            for (PaperCard pc : src) {
                double sc = DelveRank.score(pc);
                if (fits.test(pc) && sc >= lo && sc <= hi) l.add(pc);
            }
            Collections.shuffle(l, rng);
            if (bestFirst) { // top picks first, with a little variety
                java.util.Map<PaperCard, Double> key = new java.util.HashMap<>();
                for (PaperCard pc : l) key.put(pc, DelveRank.score(pc) + rng.nextDouble() * 0.15);
                l.sort((a, b) -> Double.compare(key.get(b), key.get(a)));
            }
            if (prefer != null) l.sort((a, b) -> Boolean.compare(prefer.test(b), prefer.test(a))); // stable: theme first
            byRarity.add(l);
        }
        List<List<PaperCard>> backup = new ArrayList<>(); // the era around it, if the set is thin in these colors
        for (List<PaperCard> src : List.of(eraRares, eraUncommons, eraCommons)) {
            List<PaperCard> l = new ArrayList<>();
            for (PaperCard pc : src) if (fits.test(pc)) l.add(pc);
            Collections.shuffle(l, rng);
            backup.add(l);
        }
        int[] quota = {nRare, nUncommon, nCommon};
        List<PaperCard> picks = new ArrayList<>();
        if (prefer != null) { // the theme's core: up to 12 cards that fit the plan, curve permitting
            int[] perCost = new int[9];
            for (int n = 0; n < 12; n++)
                if (!pickInto(picks, byRarity, quota, pc -> prefer.test(pc)
                        && perCost[Math.min(8, pc.getRules().getManaCost().getCMC())] < 4)) break;
                else perCost[Math.min(8, picks.get(picks.size() - 1).getRules().getManaCost().getCMC())]++;
        }
        // creature curve: 1-drop x1, 2 x5, 3 x4, 4 x3, 5+ x2
        int[][] curve = {{1, 1}, {2, 5}, {3, 4}, {4, 3}, {5, 2}};
        for (int[] slot : curve) {
            java.util.function.Predicate<PaperCard> inSlot = pc -> pc.getRules().getType().isCreature()
                    && (slot[0] == 5 ? pc.getRules().getManaCost().getCMC() >= 5 : pc.getRules().getManaCost().getCMC() == slot[0]);
            long have = picks.stream().filter(inSlot).count(); // theme picks already fill some slots
            for (long n = have; n < slot[1]; n++)
                pickInto(picks, byRarity, quota, inSlot);
        }
        while (picks.size() < 23 && pickInto(picks, byRarity, quota, pc -> !pc.getRules().getType().isCreature())) { }
        while (picks.size() < 23 && pickInto(picks, byRarity, quota, pc -> true)) { }
        while (picks.size() < 23 && pickInto(picks, byRarity, new int[]{99, 99, 99}, pc -> true)) { } // quotas exhausted
        while (picks.size() < 23 && pickInto(picks, backup, new int[]{99, 99, 99}, pc -> true)) { }
        return DelveGateScene.buildDraftDeck(picks);
    }

    /** Take one card matching {@code want}, preferring the highest rarity with quota left. */
    private static boolean pickInto(List<PaperCard> picks, List<List<PaperCard>> byRarity, int[] quota,
                                    java.util.function.Predicate<PaperCard> want) {
        for (int r = 0; r < 3; r++) {
            if (quota[r] <= 0) continue;
            for (PaperCard pc : byRarity.get(r)) {
                if (picks.contains(pc) || !want.test(pc)) continue;
                picks.add(pc);
                quota[r]--;
                return true;
            }
        }
        return false;
    }

    // ---- Commander decks ----------------------------------------------------------

    private final java.util.Map<String, Deck> commanderDecks = new java.util.HashMap<>();

    /** Legendary creatures from this era that can lead a deck, 1-2 colors. */
    private List<PaperCard> eraCommanders(boolean forAI) {
        loadEraPool();
        List<PaperCard> out = new ArrayList<>();
        for (List<PaperCard> src : List.of(cmdRares, cmdUncommons, cmdCommons))
            for (PaperCard pc : src) {
                if (!pc.getRules().canBeCommander() || !pc.getRules().getType().isCreature()) continue;
                int n = pc.getRules().getColorIdentity().countColors();
                if (n < 1 || n > 2) continue;
                if (forAI && pc.getRules().getAiHints().getRemAIDecks()) continue;
                out.add(pc);
            }
        out.sort(Comparator.comparing(PaperCard::getName));
        return out;
    }

    /** Today's three borrowable Commander decks (same for everyone on a given day). */
    public List<Deck> loanerCommanders() {
        List<Deck> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Deck d = commanderDeck("loaner" + i, null, false, i);
            if (d != null) out.add(d);
        }
        return out;
    }

    /** A Commander deck for a Castle pod opponent, led by a legend in (or near) the enemy's colors. */
    public Deck enemyCommanderDeck(EnemyData enemy) {
        Deck d = commanderDeck("pod|" + enemy.getName(), enemyColors(enemy, new Random(seed ^ enemy.getName().hashCode())),
                true, 0);
        if (d != null) d.setName(enemy.getName());
        return d;
    }

    /**
     * 100-card singleton Commander deck from this era's cards: a legendary creature,
     * 62 spells in its color identity (about 30 creatures on a curve) and 37 basics.
     *
     * @param preferred colors to look for in a commander first (null = any)
     * @param variant   distinguishes decks that would otherwise share a seed
     */
    Deck commanderDeck(String key, ColorSet preferred, boolean forAI, int variant) {
        String cacheKey = key + "|" + forAI;
        Deck cached = commanderDecks.get(cacheKey);
        if (cached != null) return cached;
        Random rng = new Random(seed ^ cacheKey.hashCode() ^ (variant * 7919L));
        List<PaperCard> legends = eraCommanders(forAI);
        if (legends.isEmpty()) return null;
        Collections.shuffle(legends, rng);
        PaperCard commander = legends.get(0);
        if (preferred != null) {
            for (PaperCard pc : legends) { // exact colors first, then a subset
                if (pc.getRules().getColorIdentity().getColor() == preferred.getColor()) { commander = pc; break; }
            }
            if (commander.getRules().getColorIdentity().getColor() != preferred.getColor())
                for (PaperCard pc : legends)
                    if (preferred.containsAllColorsFrom(pc.getRules().getColorIdentity().getColor())) { commander = pc; break; }
            if ((commander.getRules().getColorIdentity().getColor() & preferred.getColor()) == 0)
                for (PaperCard pc : legends) // at least share a color
                    if ((pc.getRules().getColorIdentity().getColor() & preferred.getColor()) != 0) { commander = pc; break; }
        }
        final ColorSet identity = commander.getRules().getColorIdentity();
        final String commanderName = commander.getName();
        java.util.function.Predicate<PaperCard> fits = pc -> {
            if (pc.getRules().getType().isLand() || pc.getName().equals(commanderName)) return false;
            int cmc = pc.getRules().getManaCost().getCMC();
            if (cmc > 8) return false;
            if (!identity.containsAllColorsFrom(pc.getRules().getColorIdentity().getColor())) return false;
            return !forAI || !pc.getRules().getAiHints().getRemAIDecks();
        };
        List<List<PaperCard>> byRarity = new ArrayList<>();
        for (List<PaperCard> src : List.of(cmdRares, cmdUncommons, cmdCommons)) {
            List<PaperCard> l = new ArrayList<>();
            for (PaperCard pc : src) if (fits.test(pc)) l.add(pc);
            Collections.shuffle(l, rng);
            byRarity.add(l);
        }
        int[] quota = {10, 22, 30}; // rares, uncommons, commons among the 62 spells
        List<PaperCard> picks = new ArrayList<>();
        int[][] curve = {{1, 2}, {2, 7}, {3, 7}, {4, 6}, {5, 4}, {6, 4}}; // 30 creatures
        for (int[] slot : curve)
            for (int n = 0; n < slot[1]; n++)
                pickInto(picks, byRarity, quota, pc -> pc.getRules().getType().isCreature()
                        && (slot[0] == 6 ? pc.getRules().getManaCost().getCMC() >= 6 : pc.getRules().getManaCost().getCMC() == slot[0]));
        while (picks.size() < 62 && pickInto(picks, byRarity, quota, pc -> !pc.getRules().getType().isCreature())) { }
        while (picks.size() < 62 && pickInto(picks, byRarity, quota, pc -> true)) { }
        while (picks.size() < 62 && pickInto(picks, byRarity, new int[]{99, 99, 99}, pc -> true)) { }
        // de-duplicate by name (singleton) - reprints across era sets share names
        java.util.Set<String> names = new java.util.HashSet<>();
        picks.removeIf(pc -> !names.add(pc.getName()));

        Deck deck = new Deck(commanderName);
        deck.getOrCreate(forge.deck.DeckSection.Commander).add(commander);
        deck.getMain().add(picks);
        addBasics(deck, picks, commander, 99 - picks.size());
        commanderDecks.put(cacheKey, deck);
        return deck;
    }

    /** Basics split by colored pips, only in the commander's identity. */
    private static void addBasics(Deck deck, List<PaperCard> picks, PaperCard commander, int count) {
        int[] pips = new int[5];
        List<PaperCard> all = new ArrayList<>(picks);
        all.add(commander);
        for (PaperCard pc : all) {
            int[] c = pc.getRules().getManaCost().getColorShardCounts();
            for (int i = 0; i < 5; i++) pips[i] += c[i];
        }
        ColorSet id = commander.getRules().getColorIdentity();
        byte[] masks = {forge.card.MagicColor.WHITE, forge.card.MagicColor.BLUE, forge.card.MagicColor.BLACK,
                forge.card.MagicColor.RED, forge.card.MagicColor.GREEN};
        String[] basics = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        int total = 0;
        for (int i = 0; i < 5; i++) {
            if ((id.getColor() & masks[i]) == 0) pips[i] = 0;
            else if (pips[i] == 0) pips[i] = 1;
            total += pips[i];
        }
        int added = 0, most = 0;
        for (int i = 0; i < 5; i++) {
            if (pips[i] > pips[most]) most = i;
            int n = total == 0 ? 0 : Math.round(count * pips[i] / (float) total);
            for (int k = 0; k < n && added < count; k++, added++)
                deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard(basics[i]));
        }
        String fill = total == 0 ? "Wastes" : basics[most];
        while (added++ < count) deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard(fill));
    }

    // ---- town Card Shop stock ----------------------------------------------------

    private List<PaperCard> shopSingles;
    private CardEdition recentPackSet;

    /** Today's 8 singles: 5 from today's set (2C 2U 1R), 3 from the rest of the era (1U 2R/M). */
    public List<PaperCard> shopSingles() {
        if (shopSingles != null) return shopSingles;
        Random rng = new Random(seed ^ 0x5409L);
        List<PaperCard> out = new ArrayList<>();
        addRandom(out, commons, 2, rng);
        addRandom(out, uncommons, 2, rng);
        addRandom(out, rares, 1, rng);
        loadEraPool();
        List<PaperCard> recentUnc = eraUncommons, recentRare = eraRares;
        addRandom(out, recentUnc, 1, rng);
        addRandom(out, recentRare, 1, rng);
        // the last slot is always a legend you could lead a Commander deck with
        List<PaperCard> legends = eraCommanders(false);
        List<PaperCard> fresh = new ArrayList<>(legends);
        fresh.removeAll(out);
        if (!fresh.isEmpty()) out.add(fresh.get(rng.nextInt(fresh.size())));
        else addRandom(out, recentRare, 1, rng);
        shopSingles = out;
        return out;
    }

    private static void addRandom(List<PaperCard> out, List<PaperCard> from, int n, Random rng) {
        for (int i = 0, guard = 0; i < n && !from.isEmpty() && guard < 50; guard++) {
            PaperCard pc = from.get(rng.nextInt(from.size()));
            if (out.contains(pc)) continue;
            out.add(pc);
            i++;
        }
    }

    /** The second pack type on sale today: another set from the current era. */
    public CardEdition recentPackSet() {
        if (recentPackSet == null) {
            List<CardEdition> recent = new ArrayList<>();
            for (CardEdition e : eraSets)
                if (e != edition && forge.StaticData.instance().getBoosters().contains(e.getCode()))
                    recent.add(e);
            recent.sort(Comparator.comparing(CardEdition::getCode));
            recentPackSet = recent.isEmpty() ? edition : recent.get(new Random(seed ^ 0xB00L).nextInt(recent.size()));
        }
        return recentPackSet;
    }

    /** Open a booster of a set (Forge's real booster template when there is one). */
    public List<PaperCard> openPack(CardEdition set, Random rng) {
        forge.item.SealedTemplate t = forge.StaticData.instance().getBoosters().get(set.getCode());
        if (t != null) {
            List<PaperCard> cards = forge.item.generation.BoosterGenerator.getBoosterPack(t);
            if (cards != null && !cards.isEmpty()) return cards;
        }
        List<PaperCard> out = new ArrayList<>(); // fallback: 10C 3U 1R from today's pools
        for (int i = 0; i < 10 && !commons.isEmpty(); i++) out.add(commons.get(rng.nextInt(commons.size())));
        for (int i = 0; i < 3 && !uncommons.isEmpty(); i++) out.add(uncommons.get(rng.nextInt(uncommons.size())));
        if (!rares.isEmpty()) out.add(rares.get(rng.nextInt(rares.size())));
        return out;
    }

    // ---- reward generation --------------------------------------------------

    /**
     * Upgrade choices: three cards in the deck's colours, each ranked better than the deck's
     * weakest card, from a quality band that rises with depth (0 = early, 1 = middle,
     * 2 = late, 3 = elite/special). Elite offers include a rare when the set has one.
     */
    public List<PaperCard> upgradeChoices(Random rng, Deck deck, ColorSet colors, int depth) {
        return upgradeChoices(rng, deck, colors, depth, Integer.MAX_VALUE);
    }

    /** Upgrades that cost at most {@code maxPrice} at a dungeon merchant (for stock you can actually afford). */
    public List<PaperCard> upgradeChoices(Random rng, Deck deck, ColorSet colors, int depth, int maxPrice) {
        double worst = 1.0;
        java.util.Set<String> inDeck = new java.util.HashSet<>();
        for (PaperCard pc : deck.getMain().toFlatList()) {
            inDeck.add(pc.getName());
            if (!pc.getRules().getType().isLand()) worst = Math.min(worst, DelveRank.score(pc));
        }
        if (worst >= 1.0) worst = 0;
        double[][] bands = {{0.35, 0.80}, {0.50, 0.92}, {0.60, 1.0}, {0.72, 1.0}};
        double[] band = bands[Math.max(0, Math.min(3, depth))];
        List<PaperCard> all = new ArrayList<>();
        for (List<PaperCard> src : List.of(commons, uncommons, rares)) all.addAll(src);
        List<PaperCard> out = new ArrayList<>();
        // widen step by step if the set is thin in these colours
        for (int attempt = 0; attempt < 4 && out.size() < 3; attempt++) {
            double lo = Math.max(worst + 0.001, band[0] - attempt * 0.1), hi = Math.min(1.0, band[1] + attempt * 0.05);
            boolean onColorOnly = attempt < 3;
            List<PaperCard> cand = new ArrayList<>();
            for (PaperCard pc : all) {
                if (pc.getRules().getType().isLand() || inDeck.contains(pc.getName()) || out.contains(pc)) continue;
                if (DelveEconomy.buyPrice(pc) > maxPrice) continue;
                double sc = DelveRank.score(pc);
                if (sc < lo || sc > hi) continue;
                ColorSet id = pc.getRules().getColorIdentity();
                if (onColorOnly && !id.isColorless() && !colors.containsAllColorsFrom(id.getColor())) continue;
                cand.add(pc);
            }
            Collections.shuffle(cand, rng);
            if (depth >= 3 && out.isEmpty()) // elites: lead with a rare if there is one
                for (PaperCard pc : cand)
                    if (pc.getRarity() == CardRarity.Rare || pc.getRarity() == CardRarity.MythicRare) { out.add(pc); break; }
            for (PaperCard pc : cand) {
                if (out.size() >= 3) break;
                if (!out.contains(pc)) out.add(pc);
            }
        }
        return out;
    }

    /** The deck's lowest-ranked non-land card (the one an upgrade would replace). */
    public static PaperCard weakest(Deck deck) {
        PaperCard worst = null;
        for (PaperCard pc : deck.getMain().toFlatList()) {
            if (pc.getRules().getType().isLand()) continue;
            if (worst == null || DelveRank.score(pc) < DelveRank.score(worst)) worst = pc;
        }
        return worst;
    }

    /** Three cards to choose from after a fight; two lean toward the deck's colors. */
    public List<PaperCard> rewardChoices(Random rng, ColorSet deckColors, boolean elite) {
        List<PaperCard> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < 3 && guard++ < 200) {
            List<PaperCard> tier = elite ? pickTier(rng, 0.0, 0.45) : pickTier(rng, 0.65, 0.93);
            if (tier.isEmpty()) tier = commons;
            PaperCard pc = tier.get(rng.nextInt(tier.size()));
            if (out.contains(pc)) continue;
            forge.card.ColorSet identity = pc.getRules().getColorIdentity();
            boolean onColor = identity.isColorless() || deckColors.containsAllColorsFrom(identity.getColor());
            if (out.size() < 2 && !onColor && guard < 150) continue; // first two on-color
            out.add(pc);
        }
        return out;
    }

    /** rolls a rarity tier: below common-> commons, below unc -> uncommons, else rares */
    private List<PaperCard> pickTier(Random rng, double commonCut, double uncommonCut) {
        double r = rng.nextDouble();
        if (r < commonCut) return commons;
        if (r < uncommonCut) return uncommons;
        return rares;
    }

    /** A draft pack: a real booster of the tier's set, minus basic lands. */
    public List<PaperCard> draftPack(Random rng) {
        List<PaperCard> out = new ArrayList<>();
        for (PaperCard pc : openPack(edition, rng))
            if (!pc.getRules().getType().isBasicLand() && !out.contains(pc)) out.add(pc);
        return out;
    }
}
