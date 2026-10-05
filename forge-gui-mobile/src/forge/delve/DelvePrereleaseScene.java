package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Limited events: three rounds against a field of three with standings, after building a 40-card
 * deck from a pool you keep.
 * - The Card Shop's Prerelease (once a day): open six boosters; prize packs by record.
 * - The Castle's Sealed night and Draft night (featured events, one Castle event a night): six
 *   boosters, or a booster draft (DelveDraft); Renown and gold by record, a booster for 3-0.
 */
public class DelvePrereleaseScene extends DelveScene {
    private static DelvePrereleaseScene object, castleObject;

    public enum Kind { PRERELEASE, SEALED_NIGHT, DRAFT_NIGHT }

    private static final int ROUNDS = 3;
    private static final int[] PRIZE_PACKS = {0, 1, 2, 4}; // by wins
    /** Castle nights: gold by wins (before title bonuses) and Renown by wins */
    private static final int[] NIGHT_GOLD = {0, 20, 50, 100};
    private static final int[] NIGHT_RENOWN = {0, DelveRenown.SEMIFINAL, DelveRenown.FINALIST, DelveRenown.CHAMPION};

    private Kind kind = Kind.PRERELEASE;
    private Deck deck;
    private DelveDay day;
    private final List<EnemyData> field = new ArrayList<>();   // 3 opponents
    private final int[] wins = new int[4], losses = new int[4]; // index 0 = you
    private int round;
    private boolean over;

    private DelvePrereleaseScene(String layout) {
        super(layout);
    }

    public static DelvePrereleaseScene instance() {
        if (object == null)
            object = new DelvePrereleaseScene("ui/delve_shop.json");
        return object;
    }

    /** The Castle's nights, on the Castle backdrop. */
    public static DelvePrereleaseScene castle() {
        if (castleObject == null)
            castleObject = new DelvePrereleaseScene("ui/delve_castle.json");
        return castleObject;
    }

    private boolean castleNight() {
        return kind != Kind.PRERELEASE;
    }

    private String eventName() {
        switch (kind) {
            case SEALED_NIGHT: return day.edition.getName() + " Sealed night";
            case DRAFT_NIGHT: return day.edition.getName() + " Draft night";
            default: return day.edition.getName() + " prerelease";
        }
    }

    /** Pay, open packs, build, then come here for the rounds. */
    public void begin() {
        DelveProfile prof = DelveProfile.get();
        if (!prof.spendGold(DelveEconomy.PRERELEASE_ENTRY)) return;
        prof.markPrerelease();
        kind = Kind.PRERELEASE;
        day = DelveDay.today();
        Random rng = new Random(day.seed ^ System.nanoTime());
        DelvePackOpenScene.instance().open(day, DelveGateScene.PRERELEASE_PACKS, rng, pool -> {
            prof.addToCollection(pool); // the pool is yours to keep
            DelveSealedScene.instance().open(day, pool, built -> start(built, rng));
        });
    }

    /** A Castle night (call on {@link #castle()}): pay, get a pool (six packs, or a draft), build, play. */
    public void beginNight(Kind night) {
        DelveProfile prof = DelveProfile.get();
        if (!prof.spendGold(DelveRenown.castleEntry(DelveEconomy.CASTLE_NIGHT_ENTRY))) return;
        prof.markCastle();
        kind = night;
        day = DelveDay.today();
        Random rng = new Random(day.seed ^ System.nanoTime());
        Consumer<List<PaperCard>> build = pool -> {
            prof.addToCollection(pool); // the pool is yours to keep
            DelveSealedScene.instance().open(day, pool, built -> start(built, rng));
        };
        if (night == Kind.DRAFT_NIGHT) DelveDraft.run(day, rng, build);
        else DelvePackOpenScene.instance().open(day, DelveGateScene.PRERELEASE_PACKS, rng, build);
    }

    private void start(Deck built, Random rng) {
        deck = built;
        round = 0;
        over = false;
        java.util.Arrays.fill(wins, 0);
        java.util.Arrays.fill(losses, 0);
        field.clear();
        List<EnemyData> pool = new ArrayList<>(day.weakEnemies);
        pool.addAll(day.eliteEnemies);
        Collections.shuffle(pool, rng);
        for (EnemyData e : pool) {
            if (field.size() >= 3) break;
            boolean dup = false;
            for (EnemyData f : field) dup |= f.getName().equals(e.getName());
            if (!dup) field.add(e);
        }
        Forge.switchScene(this);
    }

    @Override
    public void enter() {
        DelveAudio.town();
        build();
        super.enter();
    }

    private String name(int i) {
        return i == 0 ? "You" : DelvePersona.name(field.get(i - 1));
    }

    private void build() {
        clearScreen();
        if (deck == null) {
            button("Leave", 190, 244, 100, 20, this::goBack);
            return;
        }
        label("[%90][GOLD]" + fit(eventName(), 30), 8, 5, 230, 16, Align.left);
        label("[%90]" + (over ? "Final standings" : "Round " + (round + 1) + " of " + ROUNDS), 240, 5, 232, 16, Align.right);
        image("ui/delve/panel.png", 60, 34, 360, 194);
        label("[%100]Standings", 60, 42, 360, 16, Align.center);
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        order.sort((a, b) -> wins[b] != wins[a] ? Integer.compare(wins[b], wins[a]) : Integer.compare(a, b));
        for (int r = 0; r < 4; r++) {
            int i = order.get(r);
            float y = 64 + r * 34;
            portrait(i == 0 ? null : field.get(i - 1), 100, y + 30);
            label("[%90]" + (i == 0 ? "[GOLD]" : "") + (r + 1) + ".  " + name(i), 124, y + 8, 200, 14, Align.left);
            label("[%90]" + wins[i] + " - " + losses[i], 320, y + 8, 80, 14, Align.right);
        }
        if (!over) {
            int foe = round + 1; // round r: you play opponent r+1; the other two play each other
            label("[%80]Next: you vs " + name(foe), 60, 202, 360, 12, Align.center);
            button("[GOLD]Play round " + (round + 1), 180, 234, 120, 22, () -> play(foe));
        } else {
            int w = Math.min(3, wins[0]);
            String prizes;
            if (castleNight()) {
                prizes = (NIGHT_GOLD[w] == 0 ? "no prize" : DelveRenown.castlePrize(NIGHT_GOLD[w]) + " gold, +" + NIGHT_RENOWN[w] + " Renown")
                        + (w == 3 ? " and a booster" : "");
            } else {
                int prize = PRIZE_PACKS[w];
                prizes = prize == 0 ? "no prize packs" : prize + " prize pack" + (prize > 1 ? "s" : "");
            }
            label("[%85][GOLD]You went " + wins[0] + "-" + losses[0] + ": " + prizes, 60, 202, 360, 12, Align.center);
            button(castleNight() ? "Back to the Castle" : "Back to the shop", 180, 234, 120, 22, () -> {
                deck = null;
                goBack();
            });
        }
    }

    private void goBack() {
        Forge.switchScene(castleNight() ? DelveCastleScene.instance() : DelveShopScene.instance());
    }

    private void play(int foe) {
        EnemyData e = field.get(foe - 1);
        Deck foeDeck = day.enemyDeck(e, DelveDay.Tier.LATE); // a decent sealed-strength deck from the same set
        String talk = castleNight() ? DelveTalkScene.CASTLE : DelveTalkScene.SHOP;
        DelveTalkScene.before(talk, e, true, false, "Round " + (round + 1), () -> {
            DelveDuelScene.instance().setup(deck, 20, e, foeDeck, 20, 1, false,
                    (won, life) -> DelveTalkScene.after(talk, e, won, true, false, () -> result(foe, won)));
            DelveDuelScene.instance().setReturnLabel(castleNight() ? "Back to the Castle" : "Back to the prerelease");
            Forge.switchScene(DelveDuelScene.instance());
        });
    }

    private void result(int foe, boolean won) {
        Forge.switchScene(this);
        if (won) { wins[0]++; losses[foe]++; }
        else { losses[0]++; wins[foe]++; }
        // the other two opponents play each other
        List<Integer> others = new ArrayList<>(List.of(1, 2, 3));
        others.remove(Integer.valueOf(foe));
        int a = others.get(0), b = others.get(1);
        if (new Random().nextBoolean()) { wins[a]++; losses[b]++; } else { wins[b]++; losses[a]++; }
        round++;
        if (round >= ROUNDS) finish();
        build();
    }

    private void finish() {
        over = true;
        if (castleNight()) {
            finishNight();
            return;
        }
        int prize = PRIZE_PACKS[Math.min(3, wins[0])];
        List<PaperCard> cards = new ArrayList<>();
        List<List<PaperCard>> packs = new ArrayList<>();
        Random rng = new Random();
        for (int i = 0; i < prize; i++) {
            List<PaperCard> pack = new ArrayList<>(day.openPack(day.edition, rng));
            pack.removeIf(pc -> pc.getRules().getType().isBasicLand());
            packs.add(pack);
            cards.addAll(pack);
        }
        if (!cards.isEmpty()) {
            DelveProfile.get().addToCollection(cards);
            DelvePackOpenScene.instance().openPacks("Prerelease prize: " + prize + " " + day.edition.getName() + " booster"
                    + (prize > 1 ? "s" : ""), day.edition.getName(), packs, x -> Forge.switchScene(this));
        }
    }

    /** Castle night prizes: gold and Renown by record (title bonuses apply), and a booster for going 3-0. */
    private void finishNight() {
        DelveProfile prof = DelveProfile.get();
        int w = Math.min(3, wins[0]);
        int gold = NIGHT_GOLD[w] == 0 ? 0 : DelveRenown.castlePrize(NIGHT_GOLD[w]);
        if (gold > 0) prof.addGold(gold);
        if (w == 3) prof.addCastleTitle();
        String msg = "You went " + wins[0] + "-" + losses[0] + " at " + eventName() + "."
                + (gold > 0 ? " +" + gold + " gold." : "") + DelveRenown.award(NIGHT_RENOWN[w]);
        List<PaperCard> booster = null;
        if (w == 3) {
            booster = new ArrayList<>(day.openPack(day.edition, new Random()));
            prof.addToCollection(booster);
            msg += "\nUndefeated! A " + day.edition.getName() + " booster is yours too.";
        }
        List<PaperCard> prize = booster;
        info("Night over", msg, () -> {
            if (prize != null)
                DelvePackOpenScene.instance().openPack("Castle prize: " + day.edition.getName() + " booster",
                        day.edition.getName(), prize, x -> Forge.switchScene(this));
        });
    }

    @Override
    public boolean back() {
        return true; // finish the event first
    }
}
