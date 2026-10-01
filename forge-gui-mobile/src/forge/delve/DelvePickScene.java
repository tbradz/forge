package forge.delve;

import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.scene.RewardScene;
import forge.adventure.util.Reward;
import forge.adventure.util.RewardActor;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Choose cards" screen used for draft picks, post-fight rewards and end-of-run
 * keep picks. Shows the cards face up with a Take button under each.
 *
 * With exactly one pick required, pressing Take finishes immediately; otherwise
 * the player toggles cards and presses Confirm.
 */
public class DelvePickScene extends DelveScene {
    private static DelvePickScene object;

    private String header;
    private List<PaperCard> cards;
    private int minPick, maxPick;
    private String skipText;
    private Consumer<List<PaperCard>> onDone;
    private String extraLabel, pendingExtraLabel;
    private Runnable extraAction, pendingExtraAction;

    private boolean pendingPreselect;

    /** Start the next {@link #show} call with every card selected (the player deselects). */
    public DelvePickScene withAllSelected() {
        pendingPreselect = true;
        return this;
    }

    /** Add one extra button (e.g. "Reroll") to the next {@link #show} call only. */
    public DelvePickScene withExtra(String label, Runnable action) {
        pendingExtraLabel = label;
        pendingExtraAction = action;
        return this;
    }
    private java.util.function.Function<PaperCard, String> buttonLabel; // null = Take/Keep
    private final List<Integer> selected = new ArrayList<>();
    private final List<TextraButton> takeButtons = new ArrayList<>();
    private TextraButton confirm;
    private TextraLabel headerLabel;

    public static DelvePickScene instance() {
        if (object == null)
            object = new DelvePickScene();
        return object;
    }

    /**
     * @param skipText label for a skip button, or null for no skip
     * @param onDone   receives the chosen cards (empty list when skipped)
     */
    public void show(String header, List<PaperCard> cards, int minPick, int maxPick,
                     String skipText, Consumer<List<PaperCard>> onDone) {
        show(header, cards, minPick, maxPick, skipText, null, onDone);
    }

    /** @param buttonLabel per-card button text (e.g. "Buy 20g"); null for Take/Keep */
    public void show(String header, List<PaperCard> cards, int minPick, int maxPick, String skipText,
                     java.util.function.Function<PaperCard, String> buttonLabel, Consumer<List<PaperCard>> onDone) {
        this.buttonLabel = buttonLabel;
        this.extraLabel = pendingExtraLabel;
        this.extraAction = pendingExtraAction;
        pendingExtraLabel = null;
        pendingExtraAction = null;
        this.header = header;
        this.cards = new ArrayList<>(cards);
        this.minPick = Math.min(minPick, cards.size());
        this.maxPick = Math.min(maxPick, cards.size());
        this.skipText = skipText;
        this.onDone = onDone;
        selected.clear();
        if (pendingPreselect)
            for (int i = 0; i < this.cards.size() && i < this.maxPick; i++) selected.add(i);
        pendingPreselect = false;
        if (Forge.getCurrentScene() == this)
            build(); // e.g. the next pick of a draft
        else
            Forge.switchScene(this);
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        takeButtons.clear();
        headerLabel = label("", 0, 6, W, 20, Align.center);

        int n = cards.size();
        float areaX = 12, areaTop = 30, areaW = W - 24, areaH = 206;
        float buttonH = maxPick == 0 ? 0 : 14, gap = 4;
        // pick the grid (rows x cols) that gives the biggest cards
        int bestRows = 1;
        float bestH = 0;
        for (int rows = 1; rows <= 5; rows++) {
            int cols = (int) Math.ceil(n / (double) rows);
            float hByRows = (areaH - rows * (buttonH + gap)) / rows;
            float hByCols = ((areaW - cols * gap) / cols) / 0.716f;
            float h = Math.min(hByRows, hByCols);
            if (h > bestH) { bestH = h; bestRows = rows; }
        }
        int rows = bestRows, cols = (int) Math.ceil(n / (double) rows);
        float cardH = Math.min(bestH, 150), cardW = cardH * 0.716f;
        float cellW = cardW + gap, cellH = cardH + buttonH + gap;
        float gridTop = areaTop + (areaH - rows * cellH) / 2f;

        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            int inRow = Math.min(cols, n - r * cols);
            float rowX = areaX + (areaW - inRow * cellW) / 2f;
            float x = rowX + c * cellW, yTop = gridTop + r * cellH;

            RewardActor card = new RewardActor(new Reward(cards.get(i), true), false, RewardScene.Type.Loot, false);
            card.setBounds(x, H - yTop - cardH, cardW, cardH);
            track(card);

            if (maxPick == 0) continue; // view only
            final int index = i;
            TextraButton take = button(labelFor(i),
                    x, yTop + cardH + 1, cardW, buttonH, () -> toggle(index));
            takeButtons.add(take);
        }

        boolean single = (maxPick == 1 && minPick == 1) || maxPick == 0;
        float by = 244;
        if (!single) {
            confirm = button("Confirm", W / 2f - (skipText != null ? 110 : 50), by, 100, 20, this::finish);
        } else {
            confirm = null;
        }
        if (skipText != null) {
            button(skipText, single ? W / 2f - 50 : W / 2f + 10, by, 100, 20, () -> {
                selected.clear();
                finish();
            });
        }
        if (extraLabel != null) {
            Runnable action = extraAction;
            button(extraLabel, single ? W / 2f - 160 : W / 2f - 220, by, 100, 20, () -> {
                onDone = null; // the extra action replaces the normal outcome
                action.run();
            });
        }
        refresh();
    }

    private String labelFor(int i) {
        if (buttonLabel != null) return buttonLabel.apply(cards.get(i));
        return maxPick == 1 && minPick == 1 ? "Take" : "Keep";
    }

    private void toggle(int index) {
        if (maxPick == 1 && minPick == 1) {
            selected.clear();
            selected.add(index);
            finish();
            return;
        }
        if (selected.contains(index)) selected.remove(Integer.valueOf(index));
        else if (selected.size() < maxPick) selected.add(index);
        refresh();
    }

    private void refresh() {
        for (int i = 0; i < takeButtons.size(); i++)
            takeButtons.get(i).setText(selected.contains(i) ? "[GOLD]" + (buttonLabel != null ? "Selected" : "Kept") : labelFor(i));
        String count = maxPick > 1 ? "  (" + selected.size() + "/" + maxPick + ")" : "";
        // recreate rather than setText so the centred layout is recomputed
        if (headerLabel != null) headerLabel.remove();
        headerLabel = label("[%110]" + header + count, 0, 6, W, 20, Align.center);
        if (confirm != null)
            confirm.setDisabled(selected.size() < minPick);
    }

    private void finish() {
        List<PaperCard> out = new ArrayList<>();
        for (int i : selected) out.add(cards.get(i));
        Consumer<List<PaperCard>> cb = onDone;
        onDone = null;
        if (cb != null) cb.accept(out);
    }

    @Override
    public boolean back() {
        return true; // must choose (or skip) explicitly
    }
}
