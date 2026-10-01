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
 * The Dungeon Gate: choose a tier (any you've unlocked), then open six boosters
 * of that tier's set prerelease-style and build a 40-card deck from them.
 */
public class DelveGateScene extends DelveScene {
    private static DelveGateScene object;

    /** Boosters opened for a prerelease run deck (like a real prerelease). */
    static final int PRERELEASE_PACKS = 6;
    static final int DECK_SIZE = DelveRun.MIN_DECK;

    private int selectedTier = -1;

    public static DelveGateScene instance() {
        if (object == null)
            object = new DelveGateScene();
        return object;
    }

    @Override
    public void enter() {
        int top = DelveProfile.get().topTier();
        if (selectedTier < 0 || selectedTier > top) selectedTier = top;
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        DelveProfile prof = DelveProfile.get();
        int top = prof.topTier();
        title("Dungeon Gate");
        label("Day " + prof.day() + "  -  choose a tier to delve. Clear your highest tier to unlock the next set.",
                30, 34, W - 60, 16, Align.center);

        image("ui/delve/panel.png", 40, 56, W - 80, 150);
        button("<", 56, 70, 30, 24, () -> { selectedTier--; build(); }).setDisabled(selectedTier <= 0);
        button(">", W - 86, 70, 30, 24, () -> { selectedTier++; build(); }).setDisabled(selectedTier >= top);
        boolean frontier = selectedTier == top;
        label("[%120]" + (frontier ? "[GOLD]" : "") + DelveDay.tierName(selectedTier), 90, 72, W - 180, 20, Align.center);
        label("[%80]" + (frontier
                        ? (top + 1 < DelveDay.tiers().size() ? "Your highest tier. Clear it to unlock " + DelveDay.tierName(top + 1) + "."
                        : "The newest set. There is no higher tier (yet).")
                        : "Already cleared. Replay it for its rewards."),
                56, 98, W - 112, 14, Align.center);
        label("[%80]Prerelease: open " + PRERELEASE_PACKS + " " + DelveDay.tiers().get(selectedTier).getName()
                        + " boosters and build a " + DECK_SIZE + "-card deck from what you open.\n[%80]Basic lands are free."
                        + "\n[%80]Clear the dungeon to choose a reward: packs, gold, cards from your run deck, or lock the deck.",
                56, 118, W - 112, 60, Align.center);

        boolean canDelve = !prof.delvedToday() && !prof.isEvening();
        button(canDelve ? "[GOLD]Open your packs" : "[GRAY]The gate is sealed until morning", W / 2f - 100, 180, 200, 22,
                this::startDraft).setDisabled(!canDelve);
        button("Back", W / 2f - 50, 236, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    // ---- prerelease -------------------------------------------------------------

    /** Open the boosters one at a time, then build a deck from everything opened. */
    private void startDraft() {
        DelveDay day = DelveDay.forTier(selectedTier);
        Random rng = new Random(day.seed ^ System.nanoTime());
        DelvePackOpenScene.instance().open(day, PRERELEASE_PACKS, rng,
                pool -> DelveSealedScene.instance().open(day, pool, deck -> begin(day, deck)));
    }

    /** The picks plus {@code lands} basic lands split by the colored mana symbols in the picks. */
    static Deck buildDraftDeck(List<PaperCard> picks, int lands) {
        Deck deck = new Deck("Drafted Deck");
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
            for (int i = 0; i < lands; i++) addBasic(deck, basics[i % 5]);
            return deck;
        }
        int added = 0;
        for (int i = 0; i < 5; i++) {
            int n = Math.round(lands * pips[i] / (float) total);
            for (int k = 0; k < n && added < lands; k++, added++) addBasic(deck, basics[i]);
        }
        int most = 0;
        for (int i = 1; i < 5; i++) if (pips[i] > pips[most]) most = i;
        while (added++ < lands) addBasic(deck, basics[most]);
        return deck;
    }

    /** 23 spells + 17 basics (generated enemy decks). */
    static Deck buildDraftDeck(List<PaperCard> picks) {
        return buildDraftDeck(picks, 17);
    }

    private static void addBasic(Deck deck, String name) {
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard(name));
    }

    private void begin(DelveDay day, Deck deck) {
        DelveRun.start(day, deck);
        Forge.switchScene(DelveMapScene.instance());
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
