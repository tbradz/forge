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

/**
 * The Card Shop's Prerelease event (once a day): open six boosters of your highest
 * tier's set, build a sealed deck, then play three rounds against other players.
 * You keep the whole pool; prize packs depend on your record.
 */
public class DelvePrereleaseScene extends DelveScene {
    private static DelvePrereleaseScene object;

    private static final int ROUNDS = 3;
    private static final int[] PRIZE_PACKS = {0, 1, 2, 4}; // by wins

    private Deck deck;
    private DelveDay day;
    private final List<EnemyData> field = new ArrayList<>();   // 3 opponents
    private final int[] wins = new int[4], losses = new int[4]; // index 0 = you
    private int round;
    private boolean over;

    private DelvePrereleaseScene() {
        super("ui/delve_shop.json");
    }

    public static DelvePrereleaseScene instance() {
        if (object == null)
            object = new DelvePrereleaseScene();
        return object;
    }

    /** Pay, open packs, build, then come here for the rounds. */
    public void begin() {
        DelveProfile prof = DelveProfile.get();
        if (!prof.spendGold(DelveEconomy.PRERELEASE_ENTRY)) return;
        prof.markPrerelease();
        day = DelveDay.today();
        Random rng = new Random(day.seed ^ System.nanoTime());
        DelvePackOpenScene.instance().open(day, DelveGateScene.PRERELEASE_PACKS, rng, pool -> {
            prof.addToCollection(pool); // the pool is yours to keep
            DelveSealedScene.instance().open(day, pool, built -> start(built, rng));
        });
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
            button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveShopScene.instance()));
            return;
        }
        label("[%90][GOLD]" + day.edition.getName() + " prerelease", 8, 5, 300, 16, Align.left);
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
            int prize = PRIZE_PACKS[Math.min(3, wins[0])];
            label("[%85][GOLD]You went " + wins[0] + "-" + losses[0] + ": " + (prize == 0 ? "no prize packs" : prize + " prize pack" + (prize > 1 ? "s" : "")), 60, 202, 360, 12, Align.center);
            button("Back to the shop", 180, 234, 120, 22, () -> {
                deck = null;
                Forge.switchScene(DelveShopScene.instance());
            });
        }
    }

    private void play(int foe) {
        EnemyData e = field.get(foe - 1);
        Deck foeDeck = day.enemyDeck(e, DelveDay.Tier.LATE); // a decent sealed-strength deck from the same set
        DelveTalkScene.before(DelveTalkScene.SHOP, e, true, false, "Round " + (round + 1), () -> {
            DelveDuelScene.instance().setup(deck, 20, e, foeDeck, 20, 1, false,
                    (won, life) -> DelveTalkScene.after(DelveTalkScene.SHOP, e, won, true, false, () -> result(foe, won)));
            DelveDuelScene.instance().setReturnLabel("Back to the prerelease");
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

    @Override
    public boolean back() {
        return true; // finish the event first
    }
}
