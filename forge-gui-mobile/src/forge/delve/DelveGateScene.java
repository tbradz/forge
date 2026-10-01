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
 * The Dungeon Gate: choose a tier (any you've unlocked), then draft a deck from
 * that tier's set — one card from each booster until you have 20 — and choose
 * which of them to play. Basic lands fill the deck to 40.
 */
public class DelveGateScene extends DelveScene {
    private static DelveGateScene object;

    static final int DRAFT_PICKS = 20;
    static final int DECK_SIZE = DelveRun.MIN_DECK;
    /** the fewest drafted cards you may play (the rest is basic lands) */
    static final int MIN_SPELLS = 12;

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
        label("[%80]Draft " + DRAFT_PICKS + " cards from " + DelveDay.tiers().get(selectedTier).getName()
                        + " boosters, one from each pack.\n[%80]Then choose what to play; basic lands fill your deck to "
                        + DECK_SIZE + ".\n[%80]Clear the dungeon to choose a reward: packs, gold, cards from your run deck, or lock the deck.",
                56, 118, W - 112, 60, Align.center);

        boolean canDelve = !prof.delvedToday() && !prof.isEvening();
        button(canDelve ? "[GOLD]Begin the draft" : "[GRAY]The gate is sealed until morning", W / 2f - 100, 180, 200, 22,
                this::startDraft).setDisabled(!canDelve);
        button("Back", W / 2f - 50, 236, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    // ---- draft ------------------------------------------------------------------

    private void startDraft() {
        DelveDay day = DelveDay.forTier(selectedTier);
        Random rng = new Random(day.seed ^ System.nanoTime());
        draftStep(day, rng, new ArrayList<>());
    }

    private void draftStep(DelveDay day, Random rng, List<PaperCard> picks) {
        if (picks.size() >= DRAFT_PICKS) {
            buildDeck(day, picks);
            return;
        }
        List<PaperCard> pack = day.draftPack(rng);
        int rerolls = DelveProfile.get().tokens(DelveTokens.REROLL);
        if (rerolls > 0)
            DelvePickScene.instance().withExtra("Reroll (" + rerolls + ")", () -> {
                DelveProfile.get().useToken(DelveTokens.REROLL);
                draftStep(day, rng, picks);
            });
        DelvePickScene.instance().show("Draft: pick " + (picks.size() + 1) + " of " + DRAFT_PICKS
                        + "  (" + day.edition.getName() + ")", pack, 1, 1, null, chosen -> {
                    picks.addAll(chosen);
                    draftStep(day, rng, picks);
                });
    }

    /** Choose which drafted cards to play; basics fill the rest. */
    private void buildDeck(DelveDay day, List<PaperCard> picks) {
        DelvePickScene.instance().withAllSelected().show("Build your deck: choose " + MIN_SPELLS + "-" + picks.size()
                        + " cards, basic lands fill to " + DECK_SIZE, picks, MIN_SPELLS, picks.size(), null,
                pc -> "Play", chosen -> {
                    Deck deck = buildDraftDeck(chosen, DECK_SIZE - chosen.size());
                    deck.setName(day.edition.getName() + " Draft");
                    begin(day, deck);
                });
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
