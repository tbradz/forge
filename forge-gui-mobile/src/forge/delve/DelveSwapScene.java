package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.scene.RewardScene;
import forge.adventure.util.Reward;
import forge.adventure.util.RewardActor;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows a change to the run deck as cards: what's leaving on the left, what's joining on
 * the right. Used for event outcomes (gain / lose / transmute) and for the add-or-swap
 * choice when you take an upgrade, so you see both cards instead of just their names.
 */
public class DelveSwapScene extends DelveScene {
    private static DelveSwapScene object;

    private String title, text, leftHeader, rightHeader;
    private List<PaperCard> leaving = new ArrayList<>(), joining = new ArrayList<>();
    private String[] labels;
    private Runnable[] actions;

    public static DelveSwapScene instance() {
        if (object == null)
            object = new DelveSwapScene();
        return object;
    }

    /** An event's outcome: the cards that left and joined, the event's text, then Continue. */
    public void report(String title, String text, List<PaperCard> lost, List<PaperCard> gained, Runnable then) {
        open(title, text, "[#ff8080]Leaving your deck", "[GOLD]Joining your deck", lost, gained,
                new String[]{"[GOLD]Continue"}, new Runnable[]{then});
    }

    /** Taking an upgrade: swap your weakest card for it, or add it and grow the deck. */
    public void swapOrAdd(PaperCard weakest, PaperCard taken, int deckSizeIfAdded, Runnable swap, Runnable add) {
        open("Upgrade", "You take " + taken.getName() + ". Swap out your weakest card, or add it and grow the deck to "
                        + deckSizeIfAdded + "?",
                "[#ff8080]Your weakest card", "[GOLD]Your new card", List.of(weakest), List.of(taken),
                new String[]{"[GOLD]Swap out " + shorten(weakest.getName()), "Add to the deck (" + deckSizeIfAdded + ")"},
                new Runnable[]{swap, add});
    }

    private void open(String title, String text, String leftHeader, String rightHeader,
                      List<PaperCard> leaving, List<PaperCard> joining, String[] labels, Runnable[] actions) {
        this.title = title;
        this.text = text;
        this.leftHeader = leftHeader;
        this.rightHeader = rightHeader;
        this.leaving = withoutNulls(leaving);
        this.joining = withoutNulls(joining);
        this.labels = labels;
        this.actions = actions;
        Forge.switchScene(this);
    }

    private static List<PaperCard> withoutNulls(List<PaperCard> cards) {
        List<PaperCard> out = new ArrayList<>();
        for (PaperCard pc : cards) if (pc != null) out.add(pc);
        return out;
    }

    private static String shorten(String s) {
        return s.length() <= 18 ? s : s.substring(0, 17) + ".";
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        atmosphere(new float[][]{{60, 160}, {420, 160}});
        title("[GOLD]" + title);
        label("[%80]" + text, 30, 30, W - 60, 30, Align.center);
        column(leftHeader, leaving, 20);
        column(rightHeader, joining, 250);
        label("[%220][GOLD]>", 222, 132, 36, 30, Align.center);

        float bw = labels.length == 1 ? 120 : 170, gap = 10;
        float x = (W - (labels.length * bw + (labels.length - 1) * gap)) / 2f;
        for (int i = 0; i < labels.length; i++) {
            Runnable action = actions[i];
            button(labels[i], x + i * (bw + gap), 240, bw, 22, () -> {
                if (action != null) action.run();
            });
        }
    }

    /** One side: a header and its cards side by side, as large as fit in the column. */
    private void column(String header, List<PaperCard> cards, float left) {
        float colW = 210, top = 66, maxH = 148, gap = 8; // cards end above the buttons at y 240
        label("[%90]" + header, left, top - 4, colW, 14, Align.center);
        if (cards.isEmpty()) {
            label("[%80][GRAY]Nothing", left, top + 70, colW, 14, Align.center);
            return;
        }
        int n = cards.size();
        float cardW = Math.min(maxH * 0.716f, (colW - (n - 1) * gap) / n), cardH = cardW / 0.716f;
        float x = left + (colW - (n * cardW + (n - 1) * gap)) / 2f, y = top + 14 + (maxH - cardH) / 2f;
        for (PaperCard pc : cards) {
            RewardActor card = new RewardActor(new Reward(pc, true), false, RewardScene.Type.Loot, false);
            card.setBounds(x, H - y - cardH, cardW, cardH);
            track(card);
            x += cardW + gap;
        }
    }

    @Override
    public boolean back() {
        return true; // choose with the buttons
    }
}
