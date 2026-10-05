package forge.delve;

import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * A booster draft at the Castle's Draft night: eight seats, three packs of the day's set each,
 * pick one card and pass (left, then right, then left). You're seat 0; the seven AI drafters
 * take the strongest card by the set's draft rankings, with a little noise so they vary.
 */
final class DelveDraft {
    static final int SEATS = 8, ROUNDS = 3;

    private final DelveDay day;
    private final Random rng;
    private final Consumer<List<PaperCard>> onDone;
    private final List<PaperCard> picks = new ArrayList<>();
    private List<List<PaperCard>> packs = new ArrayList<>();
    private int round, pick;

    private DelveDraft(DelveDay day, Random rng, Consumer<List<PaperCard>> onDone) {
        this.day = day;
        this.rng = rng;
        this.onDone = onDone;
    }

    /** Run the whole draft; {@code onDone} gets your picks. */
    static void run(DelveDay day, Random rng, Consumer<List<PaperCard>> onDone) {
        DelveDraft d = new DelveDraft(day, rng, onDone);
        d.openPacks();
        d.showPick();
    }

    private void openPacks() {
        packs = new ArrayList<>();
        for (int s = 0; s < SEATS; s++) {
            List<PaperCard> pack = new ArrayList<>(day.openPack(day.edition, rng));
            pack.removeIf(pc -> pc.getRules().getType().isBasicLand());
            packs.add(pack);
        }
        pick = 0;
    }

    private void showPick() {
        List<PaperCard> mine = packs.get(0);
        DelvePickScene.instance().show("Draft night: pack " + (round + 1) + ", pick " + (pick + 1)
                        + "   (passing " + (round % 2 == 0 ? "left" : "right") + ", " + picks.size() + " cards drafted)",
                mine, 1, 1, null, pc -> "Pick", chosen -> took(chosen.get(0)));
    }

    private void took(PaperCard card) {
        packs.get(0).remove(card);
        picks.add(card);
        for (int s = 1; s < SEATS; s++) { // the AI drafters take theirs
            List<PaperCard> p = packs.get(s);
            if (p.isEmpty()) continue;
            PaperCard best = null;
            double bestScore = -1;
            for (PaperCard pc : p) {
                double score = DelveRank.score(pc) + rng.nextDouble() * 0.08;
                if (score > bestScore) { bestScore = score; best = pc; }
            }
            p.remove(best);
        }
        Collections.rotate(packs, round % 2 == 0 ? 1 : -1); // pass the packs on
        pick++;
        if (packs.get(0).isEmpty()) {
            round++;
            if (round >= ROUNDS) {
                onDone.accept(picks);
                return;
            }
            openPacks();
        }
        showPick();
    }
}
