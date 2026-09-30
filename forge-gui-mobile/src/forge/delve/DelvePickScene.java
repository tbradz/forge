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
        this.header = header;
        this.cards = new ArrayList<>(cards);
        this.minPick = Math.min(minPick, cards.size());
        this.maxPick = Math.min(maxPick, cards.size());
        this.skipText = skipText;
        this.onDone = onDone;
        selected.clear();
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
            TextraButton take = button(maxPick == 1 && minPick == 1 ? "Take" : "Keep",
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
        refresh();
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
            takeButtons.get(i).setText(selected.contains(i) ? "[GOLD]Kept" : (maxPick == 1 && minPick == 1 ? "Take" : "Keep"));
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
