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
    public enum Tier { FIGHT, ELITE, BOSS, CASTLE }

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
            case ELITE: q = new int[]{13, 8, 2}; break;
            case BOSS: q = new int[]{9, 9, 5}; break;
            case CASTLE: q = new int[]{8, 10, 5}; break;
            default: q = new int[]{18, 5, 0}; // ordinary fights: a bit weaker than starters
        }
        Deck d = buildDeck(colors, q[0], q[1], q[2], true, rng);
        d.setName(enemy.getName());
        enemyDecks.put(key, d);
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
            for (PaperCard pc : src) if (fits.test(pc)) l.add(pc);
            Collections.shuffle(l, rng);
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
        // creature curve: 1-drop x1, 2 x5, 3 x4, 4 x3, 5+ x2
        int[][] curve = {{1, 1}, {2, 5}, {3, 4}, {4, 3}, {5, 2}};
        for (int[] slot : curve)
            for (int n = 0; n < slot[1]; n++)
                pickInto(picks, byRarity, quota, pc -> pc.getRules().getType().isCreature()
                        && (slot[0] == 5 ? pc.getRules().getManaCost().getCMC() >= 5 : pc.getRules().getManaCost().getCMC() == slot[0]));
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
        addRandom(out, recentRare, 2, rng);
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
