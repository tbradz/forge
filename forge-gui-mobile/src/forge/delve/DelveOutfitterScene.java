package forge.delve;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.assets.FSkin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The Outfitter: card sleeves and playmats. Six sleeves are for sale each day; owned sleeves
 * can be equipped any time. The equipped sleeve is the back of your cards in
 * duels and when opening packs. Playmats ({@link DelvePlaymat}) lie under your side of the
 * battlefield; some are sold here, title playmats come with a Castle title.
 */
public class DelveOutfitterScene extends DelveScene {
    private static DelveOutfitterScene object;
    private static final int STOCK = 6, OWNED_PER_PAGE = 8;
    private int page = 0;

    private DelveOutfitterScene() {
        super("ui/delve_outfitter.json");
    }

    public static DelveOutfitterScene instance() {
        if (object == null)
            object = new DelveOutfitterScene();
        return object;
    }

    @Override
    public void enter() {
        DelveAudio.town();
        build();
        super.enter();
    }

    /** Price of a sleeve: steady per sleeve, 40-120 gold. */
    static int price(int index) {
        int[] tiers = {40, 60, 80, 100, 120};
        return tiers[Math.floorMod(index * 37 + 11, tiers.length)];
    }

    /** Today's sleeves for sale (ones you don't own), the same all day. */
    private List<Integer> stock() {
        DelveProfile prof = DelveProfile.get();
        List<Integer> all = new ArrayList<>(FSkin.getSleeves().keySet());
        all.removeIf(i -> i == 0);
        java.util.Collections.sort(all);
        java.util.Collections.shuffle(all, new Random(prof.day() * 7919L + 17));
        List<Integer> out = new ArrayList<>();
        for (int i : all) {
            if (out.size() >= STOCK) break;
            out.add(i);
        }
        return out;
    }

    private Image sleeve(int index, float x, float yTop, float w, float h, boolean dim) {
        TextureRegion r = FSkin.getSleeves().get(index);
        Image img = new Image(r);
        img.setBounds(x, H - yTop - h, w, h);
        img.setTouchable(Touchable.disabled);
        if (dim) img.getColor().a = 0.35f;
        return track(img);
    }

    /** false = sleeves, true = playmats */
    private boolean mats;

    private void build() {
        clearScreen();
        DelveProfile prof = DelveProfile.get();
        label("[%90][GOLD]Outfitter", 8, 5, 200, 16, Align.left);
        label("[%90][GOLD]Gold[] " + prof.gold(), 280, 5, 192, 16, Align.right);
        button(mats ? "Sleeves" : "[GOLD]Playmats", 330, 244, 100, 20, () -> {
            mats = !mats;
            build();
        });
        if (mats) buildPlaymats();
        else buildSleeves();
    }

    /** Playmats: shown under your side of the battlefield in duels. Some for sale, some come with a Castle title. */
    private void buildPlaymats() {
        DelveProfile prof = DelveProfile.get();
        image("ui/delve/panel.png", 10, 30, 460, 208);
        label("[%85]Playmats  -  your mat lies under your side of the battlefield in every duel", 10, 33, 460, 12, Align.center);
        DelvePlaymat[] all = DelvePlaymat.values();
        DelvePlaymat current = DelvePlaymat.current();
        float w = 138, h = w / 3f, colGap = (440 - 3 * w) / 2f;
        for (int i = 0; i < all.length; i++) {
            DelvePlaymat p = all[i];
            float x = 20 + (i % 3) * (w + colGap), y = 50 + (i / 3) * 86;
            boolean owned = p.owned();
            Image img = image(p.image(), x, y, w, h);
            if (!owned) img.getColor().a = 0.45f;
            label("[%75]" + p.title, x, y + h + 2, w, 12, Align.center);
            String text;
            Runnable action;
            boolean disabled = false;
            if (owned) {
                text = p == current ? "[GOLD]In use" : "[%80]Use";
                disabled = p == current;
                action = () -> { prof.setCurrentPlaymat(p.id); build(); };
            } else if (p.requires != null) {
                text = "[GRAY]" + p.requires.title + " title";
                disabled = true;
                action = () -> { };
            } else {
                text = "[%80]Buy " + p.price + "g";
                disabled = prof.gold() < p.price;
                action = () -> buyPlaymat(p);
            }
            button(text, x + w / 2f - 50, y + h + 16, 100, 16, action).setDisabled(disabled);
        }
        button(current == null ? "[GOLD]No playmat (in use)" : "[%80]No playmat", 20, 222, 140, 14, () -> {
            prof.setCurrentPlaymat("");
            build();
        }).setDisabled(current == null);
        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private void buyPlaymat(DelvePlaymat p) {
        DelveProfile prof = DelveProfile.get();
        if (p.owned() || p.price <= 0 || !prof.spendGold(p.price)) return;
        prof.addPlaymat(p.id);
        prof.setCurrentPlaymat(p.id);
        DelveAudio.coins();
        build();
        info("Outfitter", "A new playmat! It's under your side of the battlefield from your next duel (change it any time here).", null);
    }

    private void buildSleeves() {
        DelveProfile prof = DelveProfile.get();
        Map<Integer, TextureRegion> sleeves = FSkin.getSleeves();

        image("ui/delve/panel.png", 10, 30, 460, 98);
        label("[%85]Sleeves for sale today", 10, 33, 460, 12, Align.center);
        List<Integer> stock = stock();
        float w = 44, h = w * 500f / 360f, gap = (440 - STOCK * w) / (STOCK - 1);
        for (int i = 0; i < stock.size(); i++) {
            int idx = stock.get(i);
            float x = 20 + i * (w + gap);
            boolean owned = prof.ownedSleeves().contains(idx);
            sleeve(idx, x, 46, w, h, owned);
            int cost = price(idx);
            button(owned ? "[GRAY]Owned" : "[%80]Buy " + cost + "g", x - 6, 108, w + 12, 16, () -> buy(idx))
                    .setDisabled(owned || prof.gold() < cost);
        }

        image("ui/delve/panel.png", 10, 132, 460, 106);
        List<Integer> owned = new ArrayList<>(prof.ownedSleeves());
        owned.removeIf(i -> !sleeves.containsKey(i));
        int pages = Math.max(1, (owned.size() + OWNED_PER_PAGE - 1) / OWNED_PER_PAGE);
        page = Math.min(page, pages - 1);
        label("[%85]Your sleeves (" + owned.size() + ")" + (pages > 1 ? "  -  page " + (page + 1) + "/" + pages : ""),
                10, 135, 460, 12, Align.center);
        float ow = 38, oh = ow * 500f / 360f, ogap = (440 - OWNED_PER_PAGE * ow) / (OWNED_PER_PAGE - 1);
        for (int i = 0; i < OWNED_PER_PAGE; i++) {
            int n = page * OWNED_PER_PAGE + i;
            if (n >= owned.size()) break;
            int idx = owned.get(n);
            float x = 20 + i * (ow + ogap);
            boolean current = idx == prof.currentSleeve();
            sleeve(idx, x, 148, ow, oh, false);
            button(current ? "[GOLD]In use" : "[%80]Use", x - 4, 202 + 14, ow + 8, 16, () -> {
                prof.setCurrentSleeve(idx);
                build();
            }).setDisabled(current);
        }
        if (pages > 1) {
            button("<", 14, 216, 20, 16, () -> { page--; build(); }).setDisabled(page == 0);
            button(">", 446, 216, 20, 16, () -> { page++; build(); }).setDisabled(page >= pages - 1);
        }
        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private void buy(int index) {
        DelveProfile prof = DelveProfile.get();
        if (prof.ownedSleeves().contains(index) || !prof.spendGold(price(index))) return;
        prof.addSleeve(index);
        DelveAudio.coins();
        prof.setCurrentSleeve(index);
        build();
        info("Outfitter", "New sleeves! They're on your cards now (change them any time below).", null);
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
