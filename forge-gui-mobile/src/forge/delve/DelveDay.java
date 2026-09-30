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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Today's dungeon. Everything here is derived from the date, so every player
 * gets the same theme, card pool, starters and enemy roster on the same day.
 */
public class DelveDay {
    private static final String[] STARTER_DECKS = {
            "Azorius", "Dimir", "Rakdos", "Gruul", "Selesnya",
            "Orzhov", "Izzet", "Golgari", "Boros", "Simic"
    };

    public final LocalDate date;
    public final long seed;
    public final CardEdition edition;
    public final List<PaperCard> commons = new ArrayList<>();
    public final List<PaperCard> uncommons = new ArrayList<>();
    public final List<PaperCard> rares = new ArrayList<>(); // rare + mythic
    public final List<String> starterNames = new ArrayList<>();
    public final List<EnemyData> weakEnemies = new ArrayList<>();
    public final List<EnemyData> eliteEnemies = new ArrayList<>();
    public final List<EnemyData> bossEnemies = new ArrayList<>();

    private static DelveDay cached;

    public static DelveDay today() {
        return forDate(LocalDate.now());
    }

    /** The dungeon for a given date (used to resume a saved run from an earlier day). */
    public static DelveDay forDate(LocalDate date) {
        if (cached == null || !cached.date.equals(date))
            cached = new DelveDay(date);
        return cached;
    }

    private DelveDay(LocalDate date) {
        this.date = date;
        this.seed = date.toEpochDay() * 0x9E3779B97F4A7C15L;
        Random rng = new Random(seed);
        this.edition = pickEdition(rng);
        buildPool();
        List<String> decks = new ArrayList<>(List.of(STARTER_DECKS));
        Collections.shuffle(decks, rng);
        starterNames.addAll(decks.subList(0, 3));
        buildEnemies(rng);
    }

    /** A regular expansion or core set with a real common/uncommon/rare spread. */
    private static CardEdition pickEdition(Random rng) {
        List<CardEdition> candidates = new ArrayList<>();
        for (CardEdition e : FModel.getMagicDb().getEditions()) {
            if (e.getType() != CardEdition.Type.EXPANSION && e.getType() != CardEdition.Type.CORE)
                continue;
            if (e.getDate() == null || e.getDate().after(new java.util.Date()))
                continue;
            if (e.getCards() == null || e.getCards().size() < 150)
                continue;
            candidates.add(e);
        }
        candidates.sort(Comparator.comparing(CardEdition::getCode));
        // Lean toward newer sets: 70% of days use a set from the last RECENT_YEARS years.
        List<CardEdition> recent = new ArrayList<>();
        for (CardEdition e : candidates)
            if (isRecent(e)) recent.add(e);
        if (!recent.isEmpty() && rng.nextDouble() < RECENT_SET_CHANCE)
            return recent.get(rng.nextInt(recent.size()));
        return candidates.get(rng.nextInt(candidates.size()));
    }

    /** How far back counts as "newer cards" for daily sets and starter decks. */
    static final int RECENT_YEARS = 6;
    static final double RECENT_SET_CHANCE = 0.7;

    static boolean isRecent(CardEdition e) {
        if (e.getDate() == null) return false;
        java.util.Calendar cut = java.util.Calendar.getInstance();
        cut.add(java.util.Calendar.YEAR, -RECENT_YEARS);
        return e.getDate().after(cut.getTime()) && e.getDate().before(new java.util.Date());
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

    // ---- starter decks ---------------------------------------------------------

    private static final java.util.Map<String, String> GUILD_COLORS = java.util.Map.of(
            "Azorius", "WU", "Dimir", "UB", "Rakdos", "BR", "Gruul", "RG", "Selesnya", "GW",
            "Orzhov", "WB", "Izzet", "UR", "Golgari", "BG", "Boros", "RW", "Simic", "GU");

    private final java.util.Map<String, Deck> starters = new java.util.HashMap<>();
    private List<PaperCard> starterPool;

    /**
     * Today's fixed starter for a guild: 23 commons/uncommons from recent sets in the
     * guild's colors (plus today's set) and 17 basics. Seeded by the date, so everyone
     * gets the same three starters on a given day and new ones tomorrow.
     */
    public Deck loadStarter(String guildName) {
        Deck cached = starters.get(guildName);
        if (cached == null) {
            cached = buildStarter(guildName, new Random(seed ^ guildName.hashCode()));
            starters.put(guildName, cached);
        }
        Deck copy = new Deck(guildName + " Starter");
        copy.getMain().addAll(cached.getMain());
        return copy;
    }

    private List<PaperCard> starterPool() {
        if (starterPool != null) return starterPool;
        java.util.Set<String> codes = new java.util.HashSet<>();
        codes.add(edition.getCode());
        for (CardEdition e : FModel.getMagicDb().getEditions())
            if ((e.getType() == CardEdition.Type.EXPANSION || e.getType() == CardEdition.Type.CORE) && isRecent(e))
                codes.add(e.getCode());
        List<PaperCard> pool = FModel.getMagicDb().getCommonCards().getAllCards(pc ->
                codes.contains(pc.getEdition())
                        && (pc.getRarity() == CardRarity.Common || pc.getRarity() == CardRarity.Uncommon)
                        && !pc.getRules().getType().isLand()
                        && pc.getRules().getManaCost().getCMC() >= 1
                        && pc.getRules().getManaCost().getCMC() <= 6);
        dedupe(pool);
        starterPool = pool;
        return pool;
    }

    private Deck buildStarter(String guild, Random rng) {
        ColorSet colors = ColorSet.fromNames(GUILD_COLORS.get(guild).toCharArray());
        List<PaperCard> creatures = new ArrayList<>(), spells = new ArrayList<>();
        for (PaperCard pc : starterPool()) {
            ColorSet id = pc.getRules().getColorIdentity();
            if (id.isColorless() || !colors.containsAllColorsFrom(id.getColor())) continue; // colored, on-guild
            (pc.getRules().getType().isCreature() ? creatures : spells).add(pc);
        }
        Collections.shuffle(creatures, rng);
        Collections.shuffle(spells, rng);

        List<PaperCard> picks = new ArrayList<>();
        // creature curve: 1-drop x1, 2 x5, 3 x4, 4 x3, 5+ x2
        int[][] curve = {{1, 1}, {2, 5}, {3, 4}, {4, 3}, {5, 2}};
        int uncommons = 0;
        for (int[] slot : curve) {
            int need = slot[1];
            for (PaperCard pc : creatures) {
                if (need == 0) break;
                int cmc = pc.getRules().getManaCost().getCMC();
                boolean fits = slot[0] == 5 ? cmc >= 5 : cmc == slot[0];
                if (!fits || picks.contains(pc)) continue;
                if (pc.getRarity() == CardRarity.Uncommon && uncommons >= 3) continue;
                if (pc.getRarity() == CardRarity.Uncommon) uncommons++;
                picks.add(pc);
                need--;
            }
        }
        for (PaperCard pc : spells) {
            if (picks.size() >= 23) break;
            if (pc.getRarity() == CardRarity.Uncommon && uncommons >= 5) continue;
            if (pc.getRarity() == CardRarity.Uncommon) uncommons++;
            picks.add(pc);
        }
        for (PaperCard pc : creatures) { // top up if the spell pool ran short
            if (picks.size() >= 23) break;
            if (!picks.contains(pc)) picks.add(pc);
        }
        Deck d = DelveGateScene.buildDraftDeck(picks);
        d.setName(guild + " Starter");
        return d;
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

    /** A pack of three for drafting a starter. */
    public List<PaperCard> draftChoices(Random rng) {
        List<PaperCard> out = new ArrayList<>();
        int guard = 0;
        while (out.size() < 3 && guard++ < 100) {
            List<PaperCard> tier = pickTier(rng, 0.7, 0.95);
            if (tier.isEmpty()) tier = commons;
            PaperCard pc = tier.get(rng.nextInt(tier.size()));
            if (!out.contains(pc)) out.add(pc);
        }
        return out;
    }
}
