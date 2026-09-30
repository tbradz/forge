package forge.delve;

import com.badlogic.gdx.utils.Array;
import forge.adventure.data.EnemyData;
import forge.adventure.data.WorldData;
import forge.adventure.util.CardUtil;
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
        LocalDate now = LocalDate.now();
        if (cached == null || !cached.date.equals(now))
            cached = new DelveDay(now);
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
        return candidates.get(rng.nextInt(candidates.size()));
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

    public Deck loadStarter(String guildName) {
        Deck d = CardUtil.getDeck("decks/starter/Adventure - Low " + guildName + ".dck",
                false, false, "", false, false);
        Deck copy = new Deck(guildName + " Starter");
        copy.getMain().addAll(d.getMain());
        return copy;
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
