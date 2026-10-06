package forge.delve;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import forge.adventure.scene.RewardScene;
import forge.adventure.util.Reward;
import forge.adventure.util.RewardActor;
import forge.card.CardRarity;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Prerelease-style pack opening: boosters are opened one at a time. Click the
 * pack and it shakes, bursts, and its cards fly out face down and flip over one
 * by one (rares and mythics last, with a gold glow). "Open all" bursts every
 * remaining pack together and reveals the uncommons, rares and mythics. When every
 * pack is open, the whole pool is handed to {@code onDone}.
 */
public class DelvePackOpenScene extends DelveScene {
    private static DelvePackOpenScene object;

    private DelveDay day;
    private Random rng;
    private int packs, opened;
    private final List<PaperCard> pool = new ArrayList<>();
    private Consumer<List<PaperCard>> onDone;
    private final List<RewardActor> showing = new ArrayList<>();
    private boolean animating;
    private int spillId; // guards afterSpill so Flip all and the timer can't both finish a pack
    private com.github.tommyettinger.textra.TextraLabel hint, headerRight;
    private com.github.tommyettinger.textra.TextraButton openButton, openAllButton, flipAllButton;

    private DelvePackOpenScene() {
        super("ui/delve_opening.json");
    }

    public static DelvePackOpenScene instance() {
        if (object == null)
            object = new DelvePackOpenScene();
        return object;
    }

    /** header text, the set name printed on the pack, and packs opened ahead of time (null = open the day's set) */
    private String title, setName;
    private List<List<PaperCard>> preset;
    private int drawn;

    /** Open {@code packs} boosters of the day's tier set (the prerelease); {@code onDone} gets every card opened. */
    public void open(DelveDay day, int packs, Random rng, Consumer<List<PaperCard>> onDone) {
        this.day = day;
        this.rng = rng;
        start(day.edition.getName() + " prerelease", day.edition.getName(), null, packs, onDone);
    }

    /**
     * Show packs that were already opened (bought, won, rewarded), with the same animation.
     * The caller has already added the cards wherever they belong; {@code onDone} gets the non-basic cards.
     */
    public void openPacks(String title, String setName, List<List<PaperCard>> packs, Consumer<List<PaperCard>> onDone) {
        if (packs.isEmpty()) {
            if (onDone != null) onDone.accept(new ArrayList<>());
            return;
        }
        start(title, setName, new ArrayList<>(packs), packs.size(), onDone);
    }

    private void start(String title, String setName, List<List<PaperCard>> preset, int packs, Consumer<List<PaperCard>> onDone) {
        this.title = title;
        this.setName = setName;
        this.preset = preset;
        this.packs = packs;
        this.onDone = onDone;
        this.opened = 0;
        this.drawn = 0;
        pool.clear();
        forge.Forge.switchScene(this);
    }

    /** The next pack to open: a pre-opened one, or a fresh pack of the day's set. */
    private List<PaperCard> nextPack() {
        if (preset != null) return drawn < preset.size() ? preset.get(drawn++) : new ArrayList<>();
        drawn++;
        return day.openPack(day.edition, rng);
    }

    /** Show one already-opened pack. */
    public void openPack(String title, String setName, List<PaperCard> pack, Consumer<List<PaperCard>> onDone) {
        openPacks(title, setName, List.of(pack), onDone);
    }

    @Override
    public void enter() {
        DelveSleeves.register(); // so a foil sleeve shows on the card backs
        DelveAudio.dungeon();
        showPack();
        super.enter();
    }

    private void header() {
        label("[%90][GOLD]" + fit(title.replace(setName, DelveDay.shortName(setName)), 48), 8, 5, 230, 16, Align.left);
        headerRight = label("[%90]Pack " + Math.min(opened + 1, packs) + " of " + packs + "    Pool: " + pool.size() + " cards",
                240, 5, 232, 16, Align.right);
    }

    /** A sealed pack waiting to be opened, bobbing gently. */
    private void showPack() {
        clearScreen();
        showing.clear();
        animating = false;
        header();
        float pw = 72, ph = 108, px = (W - pw) / 2f, py = 70;
        Image pack = image("ui/delve/booster_pack.png", px, py, pw, ph);
        pack.setOrigin(Align.center);
        pack.setTouchable(Touchable.enabled);
        pack.addAction(Actions.forever(Actions.sequence(
                Actions.moveBy(0, 3, 0.9f, Interpolation.sine),
                Actions.moveBy(0, -3, 0.9f, Interpolation.sine))));
        com.github.tommyettinger.textra.TextraLabel name =
                label("[%55]" + DelveDay.shortName(setName), px + 4, py + ph * 0.72f, pw - 8, ph * 0.13f, Align.center);
        name.setTouchable(Touchable.disabled);
        name.addAction(Actions.forever(Actions.sequence(
                Actions.moveBy(0, 3, 0.9f, Interpolation.sine),
                Actions.moveBy(0, -3, 0.9f, Interpolation.sine))));
        hint = label("[%85]Click the pack to open it", 0, 196, W, 14, Align.center);
        pack.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (!animating) burst(pack, name);
            }
        });
        int left = packs - opened;
        float bx = left > 1 ? W / 2f - 105 : W / 2f - 50;
        openButton = button("[GOLD]Open", bx, 236, 100, 22, () -> {
            if (!animating) burst(pack, name);
        });
        if (left > 1)
            openAllButton = button("Open all (" + left + ")", W / 2f + 5, 236, 100, 22, () -> {
                if (!animating) openAll();
            });
    }

    /** Open every remaining pack at once: the packs burst together, then the best cards are revealed. */
    private void openAll() {
        animating = true;
        DelveAudio.shuffle();
        clearScreen();
        showing.clear();
        header();
        int left = packs - opened;
        float pw = 48, ph = 72, gap = 10;
        float total = left * pw + (left - 1) * gap, x0 = (W - total) / 2f, py = 90;
        List<PaperCard> all = new ArrayList<>();
        for (int i = 0; i < left; i++)
            for (PaperCard pc : nextPack())
                if (!pc.getRules().getType().isBasicLand()) all.add(pc);
        pool.addAll(all);
        final int count = left;
        for (int i = 0; i < left; i++) {
            Image pack = image("ui/delve/booster_pack.png", x0 + i * (pw + gap), py, pw, ph);
            pack.setOrigin(Align.center);
            float cx = pack.getX() + pw / 2f, cy = pack.getY() + ph / 2f;
            Image flash = image("ui/delve/pack_flash.png", 0, 0, 140, 140);
            flash.setPosition(cx - 70, cy - 70);
            flash.setOrigin(Align.center);
            flash.setTouchable(Touchable.disabled);
            flash.getColor().a = 0;
            flash.setScale(0.3f);
            pack.addAction(Actions.sequence(
                    Actions.delay(i * 0.12f),
                    Actions.rotateBy(6, 0.05f), Actions.rotateBy(-12, 0.08f), Actions.rotateBy(6, 0.05f),
                    Actions.parallel(Actions.scaleTo(1.3f, 1.3f, 0.16f, Interpolation.pow2Out), Actions.fadeOut(0.16f)),
                    Actions.run(() -> flash.addAction(Actions.sequence(
                            Actions.parallel(Actions.fadeIn(0.06f), Actions.scaleTo(1.3f, 1.3f, 0.22f, Interpolation.pow2Out)),
                            Actions.fadeOut(0.4f), Actions.removeActor()))),
                    Actions.removeActor()));
        }
        float burstDone = (left - 1) * 0.12f + 0.4f;
        ui.addAction(Actions.sequence(Actions.delay(burstDone), Actions.run(() -> {
            // highlights: every rare/mythic, then uncommons, up to two rows
            List<PaperCard> best = new ArrayList<>(all);
            best.removeIf(pc -> rarityOrder(pc) == 0);
            best.sort((a, b) -> Integer.compare(rarityOrder(b), rarityOrder(a)));
            if (best.size() > 16) best = new ArrayList<>(best.subList(0, 16));
            best.sort(java.util.Comparator.comparingInt(DelvePackOpenScene::rarityOrder)); // flip uncommons first
            opened = packs - 1; // the reveal finishes the last pack
            layOut(best, W / 2f, H / 2f, "Opened " + count + " packs (" + all.size() + " cards). Highlights:");
        })));
    }

    /** Shake, flash, and spill the cards. */
    private void burst(Image pack, com.github.tommyettinger.textra.TextraLabel name) {
        animating = true;
        DelveAudio.shuffle();
        name.remove();
        if (hint != null) hint.remove();
        if (openButton != null) openButton.remove();
        if (openAllButton != null) openAllButton.remove();
        pack.clearActions();
        float cx = pack.getX() + pack.getWidth() / 2f, cy = pack.getY() + pack.getHeight() / 2f;
        Image flash = image("ui/delve/pack_flash.png", 0, 0, 220, 220);
        flash.setPosition(cx - 110, cy - 110);
        flash.setOrigin(Align.center);
        flash.setTouchable(Touchable.disabled);
        flash.getColor().a = 0;
        flash.setScale(0.3f);
        pack.addAction(Actions.sequence(
                Actions.rotateBy(6, 0.05f), Actions.rotateBy(-12, 0.08f), Actions.rotateBy(12, 0.08f),
                Actions.rotateBy(-12, 0.08f), Actions.rotateBy(6, 0.05f),
                Actions.parallel(Actions.scaleTo(1.35f, 1.35f, 0.18f, Interpolation.pow2Out),
                        Actions.fadeOut(0.18f)),
                Actions.run(() -> {
                    flash.addAction(Actions.sequence(
                            Actions.parallel(Actions.fadeIn(0.08f), Actions.scaleTo(1.4f, 1.4f, 0.25f, Interpolation.pow2Out)),
                            Actions.fadeOut(0.45f), Actions.removeActor()));
                    spill(cx, cy);
                }),
                Actions.removeActor()));
    }

    /** Lay the pack's cards out face down, then flip them in order, rares last. */
    private void spill(float cx, float cy) {
        List<PaperCard> cards = new ArrayList<>();
        for (PaperCard pc : nextPack())
            if (!pc.getRules().getType().isBasicLand()) cards.add(pc);
        // reveal commons first, then uncommons, then the rare/mythic
        cards.sort(java.util.Comparator.comparingInt(DelvePackOpenScene::rarityOrder));
        pool.addAll(cards);
        layOut(cards, cx, cy, null);
    }

    /** Fly cards from (cx, cy) to a grid face down, then flip them in order (rares last, with a glow). */
    private void layOut(List<PaperCard> cards, float cx, float cy, String caption) {
        if (caption != null) label("[%80]" + caption, 0, 30, W, 12, Align.center);
        int n = cards.size();
        if (n == 0) {
            final int id0 = ++spillId;
            ui.addAction(Actions.sequence(Actions.delay(0.3f), Actions.run(() -> afterSpill(id0))));
            return;
        }
        int perRow = Math.min(8, (n + 1) / 2);
        int rows = (n + perRow - 1) / perRow;
        float gap = 5;
        float cardH = Math.min(rows > 1 ? (caption != null ? 86 : 90) : 120, ((W - 16) / perRow - gap) / 0.716f), cardW = cardH * 0.716f;
        float top = caption != null ? 44 : 32;
        for (int i = 0; i < n; i++) {
            PaperCard pc = cards.get(i);
            int r = i / perRow, c = i % perRow;
            int inRow = Math.min(perRow, n - r * perRow);
            float rowX = (W - inRow * (cardW + gap) + gap) / 2f;
            float tx = rowX + c * (cardW + gap), ty = H - (top + r * (cardH + 8)) - cardH;

            boolean rare = rarityOrder(pc) >= 2;
            Image glow = null;
            if (rare) {
                glow = image("ui/delve/rare_glow.png", 0, 0, cardW * 1.5f, cardH * 1.4f);
                glow.setPosition(tx - cardW * 0.25f, ty - cardH * 0.2f);
                glow.getColor().a = 0;
                glow.setTouchable(Touchable.disabled);
            }
            // a faded actor drawn just before (the burst flash, a rare's glow) leaves its alpha on the
            // batch, and RewardActor doesn't reset it - so reset it here or that card draws invisible
            RewardActor card = new RewardActor(new Reward(pc, true), true, RewardScene.Type.Shop, false) {
                @Override
                public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
                    batch.setColor(com.badlogic.gdx.graphics.Color.WHITE);
                    super.draw(batch, parentAlpha);
                }
            };
            card.setBackTexture(forge.assets.FSkin.getSleeves().get(DelveProfile.get().currentSleeve()));
            card.ownedLabel = null; // that label counts Adventure's collection, not Delve's
            card.setBounds(cx - cardW / 2f, cy - cardH / 2f, cardW, cardH);
            track(card);
            showing.add(card);
            final Image fGlow = glow;
            float flyDelay = i * 0.04f;
            float flipDelay = 0.55f + n * 0.04f + i * (rare ? 0.32f : 0.13f) + (rare ? 0.35f : 0);
            card.addAction(Actions.sequence(
                    Actions.delay(flyDelay),
                    Actions.moveTo(tx, ty, 0.35f, Interpolation.pow3Out),
                    Actions.delay(Math.max(0, flipDelay - flyDelay - 0.35f)),
                    Actions.run(() -> {
                        card.flip();
                        if (fGlow != null) fGlow.addAction(Actions.fadeIn(0.4f));
                    })));
        }
        float allFlipped = 0.55f + n * 0.04f + n * 0.13f + 1.2f;
        final int id = ++spillId;
        ui.addAction(Actions.sequence(Actions.delay(allFlipped), Actions.run(() -> afterSpill(id))));
        flipAllButton = button("Flip all", W - 110, 244, 90, 18, this::flipAll);
    }

    private void flipAll() {
        for (RewardActor a : showing) a.flip();
        afterSpill(spillId);
    }

    private int finishedSpill = 0;

    private void afterSpill(int id) {
        if (id != spillId || finishedSpill == id) return;
        finishedSpill = id;
        opened++;
        animating = false;
        if (headerRight != null) headerRight.remove();
        if (flipAllButton != null) flipAllButton.remove();
        headerRight = label("[%90]Pack " + opened + " of " + packs + " opened    Pool: " + pool.size() + " cards",
                240, 5, 232, 16, Align.right);
        boolean last = opened >= packs;
        // rebuild the header with the new pool size, keep the cards on the table
        boolean prerelease = preset == null;
        label("[%80]" + (last ? (prerelease ? "That's every pack. Time to build your deck!" : "That's every pack. They're in your collection.")
                : "Hover a card to read it."), 0, 230, W, 12, Align.center);
        if (last) {
            button(prerelease ? "[GOLD]Build your deck" : "[GOLD]Done", W / 2f - 70, 244, 140, 20, () -> {
                Consumer<List<PaperCard>> cb = onDone;
                onDone = null;
                if (cb != null) cb.accept(new ArrayList<>(pool));
            });
        } else {
            button("[GOLD]Open the next pack (" + (opened + 1) + "/" + packs + ")", W / 2f - 185, 244, 180, 20,
                    this::showPack);
            button("Open all the rest (" + (packs - opened) + ")", W / 2f + 5, 244, 180, 20, this::openAll);
        }
    }

    private static int rarityOrder(PaperCard pc) {
        CardRarity r = pc.getRarity();
        if (r == CardRarity.MythicRare) return 3;
        if (r == CardRarity.Rare) return 2;
        if (r == CardRarity.Uncommon) return 1;
        return 0;
    }

    @Override
    public boolean back() {
        return true; // finish opening first
    }
}
