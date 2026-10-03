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
        DelveAudio.dungeon();
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        atmosphere(new float[][]{{60, 160}, {420, 160}}); // matches dungeon_bg.png's torches
        DelveProfile prof = DelveProfile.get();
        int top = prof.topTier();
        title("Dungeon Gate");
        label("[%85]Day " + prof.day() + "  -  choose a tier. Clear your highest tier to unlock the next set.",
                30, 36, W - 60, 14, Align.center);

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
        // three steps of a run, side by side
        String set = DelveDay.tiers().get(selectedTier).getName();
        String[][] steps = {
                {"1. Your deck", "Pick two of three " + set + " half-decks. Shuffled together they make your "
                        + DECK_SIZE + "-card starting deck."},
                {"2. The dungeon", "Fights pay gold: spend it with merchants on upgrades. Elites guard relics. The boss has a plan."},
                {"3. Clear it", "Keep all your gold and pick a reward: packs, gold, cards from your deck, or lock it."}};
        float colW = (W - 112) / 3f;
        for (int i = 0; i < 3; i++) {
            float x = 56 + i * colW;
            label("[%85][GOLD]" + steps[i][0], x + 4, 118, colW - 8, 12, Align.center);
            com.github.tommyettinger.textra.TextraLabel t = label("[%75]" + steps[i][1], x + 4, 132, colW - 8, 36, Align.top);
            t.setAlignment(Align.top);
        }

        // who lives down there: the set's most common creatures (the dungeon's enemies fit them)
        List<String> denizens = DelveDay.forTier(selectedTier).denizens(3);
        if (!denizens.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String t : denizens) sb.append(sb.length() > 0 ? ", " : "").append(DelveDay.plural(t));
            label("[%80][#c0a060]Below lurk: " + sb, 40, 212, W - 80, 14, Align.center);
        }

        boolean canDelve = !prof.delvedToday() && !prof.isEvening();
        button(canDelve ? "[GOLD]Choose your decks" : "[GRAY]The gate is sealed until morning", W / 2f - 100, 174, 200, 22,
                this::startDraft).setDisabled(!canDelve);
        button("Back", W / 2f - 50, 236, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    // ---- Jumpstart start ----------------------------------------------------------

    /** Pick two of three half-decks from the tier's set; together they're the run's 40-card deck. */
    private void startDraft() {
        DelveDay day = DelveDay.forTier(selectedTier);
        Random rng = new Random(day.seed ^ System.nanoTime());
        List<DelveDay.HalfDeck> halves = day.halfDecks(rng);
        if (halves.size() < 2) {
            info("Dungeon Gate", "This set doesn't have enough single-colour cards for half-decks.", null);
            return;
        }
        List<PaperCard> faces = new ArrayList<>();
        StringBuilder names = new StringBuilder();
        for (DelveDay.HalfDeck h : halves) {
            faces.add(h.face);
            names.append(names.length() > 0 ? "   |   " : "").append(h.name);
        }
        DelvePickScene.instance().show("Pick two half-decks:  " + names, faces, 2, 2, null,
                pc -> halfFor(halves, pc).name, picked -> {
                    DelveDay.HalfDeck a = halfFor(halves, picked.get(0)), b = halfFor(halves, picked.get(1));
                    Deck deck = DelveDay.combine(a, b);
                    List<PaperCard> view = new ArrayList<>();
                    for (PaperCard pc : deck.getMain().toFlatList())
                        if (!pc.getRules().getType().isBasicLand() && !view.contains(pc)) view.add(pc);
                    view.sort(java.util.Comparator.comparingInt(pc -> pc.getRules().getManaCost().getCMC()));
                    DelvePickScene.instance().show(deck.getName() + "  (24 spells + 16 basic lands)", view, 0, 0,
                            "Into the dungeon", x -> begin(day, deck));
                });
    }

    private static DelveDay.HalfDeck halfFor(List<DelveDay.HalfDeck> halves, PaperCard face) {
        for (DelveDay.HalfDeck h : halves) if (h.face.equals(face)) return h;
        return halves.get(0);
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
