package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The Dungeon Gate: shows today's dungeon and lets the player start a run with a
 * fixed starter deck or draft their own starter.
 */
public class DelveGateScene extends DelveScene {
    private static DelveGateScene object;

    static final int DRAFT_PICKS = 23;
    static final int DRAFT_LANDS = 17; // 23 + 17 = the 40-card minimum

    public static DelveGateScene instance() {
        if (object == null)
            object = new DelveGateScene();
        return object;
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        DelveDay day = DelveDay.today();
        title("Dungeon Gate");
        label("Today's dungeon (" + day.date.format(DateTimeFormatter.ofPattern("MMM d")) + "): [GOLD]"
                + day.themeName() + "[]\nCards found today come from this set. Choose how to begin.",
                40, 34, W - 80, 36, Align.center);

        label("[%110]Fixed starter", 40, 80, 190, 16, Align.center);
        float y = 100;
        for (String guild : day.starterNames) {
            button(guild + " starter", 60, y, 150, 26, () -> startFixed(guild));
            y += 32;
        }

        label("[%110]Draft your starter", 250, 80, 190, 16, Align.center);
        label("Pick " + DRAFT_PICKS + " cards, one from each pack of three. Basic lands are added for you.",
                260, 100, 170, 50, Align.center);
        button("Draft a starter", 270, 164, 150, 26, this::startDraft);

        button("Back", 190, 232, 100, 24, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private void startFixed(String guild) {
        DelveDay day = DelveDay.today();
        Deck deck = day.loadStarter(guild);
        confirm(guild + " starter", deck.getMain().countAll() + " cards. Start today's dungeon with this deck?",
                () -> begin(deck));
    }

    private void startDraft() {
        DelveDay day = DelveDay.today();
        Random rng = new Random(System.nanoTime());
        List<PaperCard> picks = new ArrayList<>();
        draftStep(day, rng, picks);
    }

    private void draftStep(DelveDay day, Random rng, List<PaperCard> picks) {
        if (picks.size() >= DRAFT_PICKS) {
            begin(buildDraftDeck(picks));
            return;
        }
        DelvePickScene.instance().show("Draft your starter: pick " + (picks.size() + 1) + " of " + DRAFT_PICKS,
                day.draftChoices(rng), 1, 1, null, chosen -> {
                    picks.addAll(chosen);
                    draftStep(day, rng, picks);
                });
    }

    /** Adds basic lands split by the colored mana symbols in the picks. */
    static Deck buildDraftDeck(List<PaperCard> picks) {
        Deck deck = new Deck("Drafted Starter");
        deck.getMain().add(picks);
        int[] pips = new int[5];
        int total = 0;
        for (PaperCard pc : picks) {
            if (pc.getRules().getType().isLand()) continue;
            int[] counts = pc.getRules().getManaCost().getColorShardCounts(); // WUBRGC
            for (int i = 0; i < 5; i++) {
                pips[i] += counts[i];
                total += counts[i];
            }
        }
        String[] basics = {"Plains", "Island", "Swamp", "Mountain", "Forest"};
        if (total == 0) { // colorless picks only: split evenly
            for (int i = 0; i < DRAFT_LANDS; i++) addBasic(deck, basics[i % 5]);
            return deck;
        }
        int added = 0;
        for (int i = 0; i < 5; i++) {
            int n = Math.round(DRAFT_LANDS * pips[i] / (float) total);
            for (int k = 0; k < n && added < DRAFT_LANDS; k++, added++) addBasic(deck, basics[i]);
        }
        int most = 0;
        for (int i = 1; i < 5; i++) if (pips[i] > pips[most]) most = i;
        while (added++ < DRAFT_LANDS) addBasic(deck, basics[most]);
        return deck;
    }

    private static void addBasic(Deck deck, String name) {
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard(name));
    }

    private void begin(Deck deck) {
        DelveRun.start(DelveDay.today(), deck);
        Forge.switchScene(DelveMapScene.instance());
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
