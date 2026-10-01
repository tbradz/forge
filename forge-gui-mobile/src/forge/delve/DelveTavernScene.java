package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.data.EnemyData;
import forge.deck.Deck;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Tavern (evenings): practice games against the patrons, as many as you like.
 * No entry fee and no prizes - a place to test decks, or to pass the evening when
 * you can't afford the Castle. Patrons change every day and play decks from your
 * highest tier.
 */
public class DelveTavernScene extends DelveScene {
    private static DelveTavernScene object;

    private final List<EnemyData> patrons = new ArrayList<>();
    private int patronsDay = -1, patronsTier = -1;
    private int wins, losses;

    private DelveTavernScene() {
        super("ui/delve_tavern.json");
    }

    public static DelveTavernScene instance() {
        if (object == null)
            object = new DelveTavernScene();
        return object;
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private void rollPatrons() {
        DelveDay day = DelveDay.today();
        if (patronsDay == day.dayNumber && patronsTier == day.tier && !patrons.isEmpty()) return;
        patronsDay = day.dayNumber;
        patronsTier = day.tier;
        wins = losses = 0;
        patrons.clear();
        List<EnemyData> pool = new ArrayList<>(day.weakEnemies);
        pool.addAll(day.eliteEnemies);
        Collections.shuffle(pool, new Random(day.seed ^ 0x7A7E4L));
        for (EnemyData e : pool) {
            if (patrons.size() >= 4) break;
            boolean dup = false;
            for (EnemyData p : patrons) dup |= p.getName().equals(e.getName());
            if (!dup) patrons.add(e);
        }
    }

    private void build() {
        clearScreen();
        rollPatrons();
        label("[%90][GOLD]The Tavern", 8, 5, 200, 16, Align.left);
        label("[%90]Tonight: " + wins + " won, " + losses + " lost", 240, 5, 232, 16, Align.right);
        image("ui/delve/panel.png", 40, 32, 400, 204);
        label("[%110]Practice games", 40, 40, 400, 18, Align.center);
        label("[%75]No fee, no prizes. Test a deck against tonight's patrons as often as you like.\n[%75]"
                        + "They play " + DelveDay.today().edition.getName() + " decks. Sleep at Your House when you're ready for tomorrow.",
                56, 60, 368, 30, Align.center);
        for (int i = 0; i < patrons.size(); i++) {
            EnemyData e = patrons.get(i);
            float x = i % 2 == 0 ? 66 : 250, y = 100 + (i / 2) * 44;
            button("Play " + shorten(e.getName()), x, y, 164, 22, () -> chooseDeck(e));
            label("[%65]" + colorsOf(e), x, y + 24, 164, 12, Align.center);
        }
        button("[GOLD]Commander with the patrons", 140, 192, 200, 20, this::chooseCommanderDeck)
                .setDisabled(patrons.size() < 3);
        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private static String shorten(String s) {
        return s.length() <= 16 ? s : s.substring(0, 15) + ".";
    }

    private static String colorsOf(EnemyData e) {
        String c = e.colors == null ? "" : e.colors.replaceAll("[^WUBRG]", "");
        return c.isEmpty() ? "mystery colors" : "colors " + c;
    }

    // ---- 1v1 practice -------------------------------------------------------------

    private List<Deck> playableDecks() {
        List<Deck> out = new ArrayList<>();
        for (Deck d : DelveDeckEditScene.deckList())
            if (d.getMain().countAll() >= 40) out.add(d);
        for (Deck d : DelveProfile.get().lockedDecks())
            if (d.getMain().countAll() >= 40) out.add(d);
        return out;
    }

    private void chooseDeck(EnemyData foe) {
        List<Deck> decks = playableDecks();
        if (decks.isEmpty()) {
            info("No deck", "You need a deck of 40+ cards: build one in Your House, or lock a run deck after clearing a dungeon.", null);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Deck d : decks) {
            labels.add(d.getName() + " (" + d.getMain().countAll() + ")");
            actions.add(() -> play(d, foe));
        }
        labels.add("Cancel");
        actions.add(() -> { });
        choose("Play " + foe.getName(), "Choose your deck.", labels, null, actions);
    }

    private void play(Deck deck, EnemyData foe) {
        Deck foeDeck = DelveDay.today().enemyDeck(foe, DelveDay.Tier.ELITE);
        DelveDuelScene.instance().setup(deck, 20, foe, foeDeck, 20, 1, false, (won, life) -> result(won));
        DelveDuelScene.instance().setReturnLabel("Back to the Tavern");
        Forge.switchScene(DelveDuelScene.instance());
    }

    private void result(boolean won) {
        if (won) wins++;
        else losses++;
        Forge.switchScene(this);
    }

    // ---- Commander practice -----------------------------------------------------

    private void chooseCommanderDeck() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Deck d : DelveDeckEditScene.legalCommanderDecks()) {
            labels.add(d.getName());
            actions.add(() -> playCommander(d));
        }
        List<Deck> loaners = DelveDay.today().loanerCommanders();
        for (Deck d : loaners) {
            labels.add("House deck: " + d.getCommanders().get(0).getName());
            actions.add(() -> playCommander(d));
        }
        labels.add("Cancel");
        actions.add(() -> { });
        choose("Commander with the patrons", "Four players, 40 life. Choose your deck.", labels, null, actions);
    }

    private void playCommander(Deck deck) {
        List<EnemyData> foes = new ArrayList<>();
        List<Deck> decks = new ArrayList<>();
        for (EnemyData e : patrons) {
            if (foes.size() >= 3) break;
            Deck d = DelveDay.today().enemyCommanderDeck(e);
            if (d == null) continue;
            foes.add(e);
            decks.add(d);
        }
        if (foes.isEmpty()) return;
        DelveDuelScene.instance().setupCommander(deck, foes, decks, (won, life) -> result(won));
        DelveDuelScene.instance().setReturnLabel("Back to the Tavern");
        Forge.switchScene(DelveDuelScene.instance());
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
