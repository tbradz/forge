package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.model.FModel;

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
    private DelveRun.Size size = DelveRun.Size.STANDARD;

    private static String sizeText(DelveRun.Size s) {
        switch (s) {
            case SHALLOW: return "Shallow: 4 steps, a quick run. Clear it to keep 3 cards.";
            case DEEP: return "Deep: 10 steps, three elites and a tougher boss. Clear it to keep 8 cards.";
            default: return "Standard: 7 steps with an elite and a boss. Clear it to keep 5 cards.";
        }
    }
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
        label("Day " + day.dayNumber + " (" + day.eraYear() + " era): [GOLD]"
                + day.themeName() + "[]\nCards found today come from this set. Choose how to begin.",
                40, 34, W - 80, 36, Align.center);

        // dungeon size
        float sx = 60;
        for (DelveRun.Size s : DelveRun.Size.values()) {
            boolean sel = s == size;
            button((sel ? "[GOLD]" : "") + s.label, sx, 72, 116, 20, () -> { size = s; build(); });
            sx += 122;
        }
        label("[%70]" + sizeText(size), 40, 94, W - 80, 12, Align.center);

        label("[%110]Today's starters", 40, 112, 190, 16, Align.center);
        float y = 130;
        for (String guild : day.starterNames) {
            button(guild + " starter", 60, y, 150, 24, () -> startFixed(guild));
            y += 29;
        }

        label("[%110]Draft your starter", 250, 112, 190, 16, Align.center);
        label("Pick " + DRAFT_PICKS + " cards, one from each pack of three. Basic lands are added for you.",
                260, 130, 170, 44, Align.center);
        button("Draft a starter", 270, 188, 150, 24, this::startDraft);

        button("Back", 190, 240, 100, 22, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private void startFixed(String guild) {
        DelveDay day = DelveDay.today();
        Deck deck = day.loadStarter(guild);
        choose(guild + " starter", "Today's " + guild + " starter: " + deck.getMain().countAll()
                        + " cards, mostly commons from recent sets. A new one is built every day.",
                java.util.List.of("Start with this deck", "View the cards", "Back"),
                null,
                java.util.List.of(() -> begin(deck), () -> viewStarter(guild, deck), () -> { }));
    }

    private void viewStarter(String guild, Deck deck) {
        java.util.List<PaperCard> cards = new ArrayList<>();
        for (java.util.Map.Entry<PaperCard, Integer> e : deck.getMain())
            if (!e.getKey().getRules().getType().isBasicLand()) cards.add(e.getKey());
        cards.sort(java.util.Comparator.comparingInt(pc -> pc.getRules().getManaCost().getCMC()));
        DelvePickScene.instance().show(guild + " starter (plus basic lands)", cards, 0, 0, "Back", x -> {
            Forge.switchScene(this);
            startFixed(guild);
        });
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
        DelveRun.start(DelveDay.today(), deck, size);
        Forge.switchScene(DelveMapScene.instance());
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
