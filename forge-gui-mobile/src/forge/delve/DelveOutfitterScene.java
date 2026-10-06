package forge.delve;

import com.badlogic.gdx.Gdx;
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
        DelveSleeves.register();
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
        all.removeIf(i -> i == 0 || DelveSleeves.isFoil(i)); // foil sleeves have their own tab
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

    /** 0 = sleeves, 1 = foil sleeves, 2 = playmats */
    int tab; // package: the screenshot tour (DelveShots) opens each tab
    private static final String[] TABS = {"Sleeves", "Foil sleeves", "Playmats"};

    private void build() {
        clearScreen();
        DelveProfile prof = DelveProfile.get();
        label("[%90][GOLD]Outfitter", 8, 5, 200, 16, Align.left);
        label("[%90][GOLD]Gold[] " + prof.gold(), 280, 5, 192, 16, Align.right);
        // the two other tabs, either side of Leave
        float[] xs = {20, 360};
        int slot = 0;
        for (int t = 0; t < TABS.length; t++) {
            if (t == tab) continue;
            final int to = t;
            button("[GOLD]" + TABS[t], xs[slot++], 244, 100, 20, () -> {
                tab = to;
                build();
            });
        }
        if (tab == 2) buildPlaymats();
        else if (tab == 1) buildFoils();
        else buildSleeves();
    }

    /** Foil sleeves: premium card backs with a painted holographic or metallic sheen. */
    private void buildFoils() {
        DelveProfile prof = DelveProfile.get();
        image("ui/delve/panel.png", 10, 30, 460, 208);
        label("[%85]Foil sleeves  -  premium card backs for duels and pack openings", 10, 33, 460, 12, Align.center);
        DelveSleeves[] all = DelveSleeves.values();
        float w = 62, h = w * 500f / 360f, gap = (440 - all.length * w) / (all.length - 1);
        for (int i = 0; i < all.length; i++) {
            DelveSleeves s = all[i];
            int idx = s.index();
            if (!FSkin.getSleeves().containsKey(idx)) continue;
            float x = 20 + i * (w + gap);
            boolean owned = prof.ownedSleeves().contains(idx), current = prof.currentSleeve() == idx;
            sleeve(idx, x, 54, w, h, false);
            label("[%70]" + s.title, x - 10, 54 + h + 4, w + 20, 12, Align.center);
            String text = current ? "[GOLD]In use" : owned ? "[%80]Use" : "[%80]Buy " + s.price + "g";
            button(text, x - 8, 54 + h + 18, w + 16, 16, () -> {
                if (owned) prof.setCurrentSleeve(idx);
                else buyFoil(s);
                build();
            }).setDisabled(current || (!owned && prof.gold() < s.price));
        }
        label("[%70]Your owned sleeves (foil or not) can be switched any time on the Sleeves tab.", 10, 214, 460, 12, Align.center);
        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private void buyFoil(DelveSleeves s) {
        DelveProfile prof = DelveProfile.get();
        if (prof.ownedSleeves().contains(s.index()) || !prof.spendGold(s.price)) return;
        prof.addSleeve(s.index());
        prof.setCurrentSleeve(s.index());
        DelveAudio.coins();
        info("Outfitter", "Foil sleeves! " + s.title + " is on your cards now.", null);
    }

    private static final int MATS_PER_PAGE = 6;
    private int matPage = 0;

    /**
     * Playmats: shown under your side of the battlefield in duels. The built-in mats (some for sale,
     * some with a Castle title) then your own images from the playmats folder, six to a page.
     */
    private void buildPlaymats() {
        DelveProfile prof = DelveProfile.get();
        image("ui/delve/panel.png", 10, 30, 460, 208);
        // every entry: id, title, built-in mat (null for your own)
        List<String> ids = new ArrayList<>(), titles = new ArrayList<>();
        List<DelvePlaymat> builtIn = new ArrayList<>();
        for (DelvePlaymat p : DelvePlaymat.values()) {
            ids.add(p.id);
            titles.add(p.title);
            builtIn.add(p);
        }
        for (java.io.File f : DelvePlaymat.customFiles()) {
            ids.add(DelvePlaymat.CUSTOM + f.getName());
            titles.add(DelvePlaymat.customTitle(f));
            builtIn.add(null);
        }
        int pages = (ids.size() + MATS_PER_PAGE - 1) / MATS_PER_PAGE;
        matPage = Math.min(matPage, pages - 1);
        label("[%85]Playmats  -  under your side of the battlefield in every duel"
                + (pages > 1 ? "  -  page " + (matPage + 1) + "/" + pages : ""), 10, 33, 460, 12, Align.center);
        String current = DelvePlaymat.currentId();
        float w = 138, h = w / 5f, colGap = (440 - 3 * w) / 2f; // mats are about 5:1, like the battlefield
        for (int n = 0; n < MATS_PER_PAGE; n++) {
            int i = matPage * MATS_PER_PAGE + n;
            if (i >= ids.size()) break;
            String id = ids.get(i);
            DelvePlaymat p = builtIn.get(i);
            float x = 20 + (n % 3) * (w + colGap), y = 48 + (n / 3) * 80;
            boolean owned = p == null || p.owned();
            com.badlogic.gdx.graphics.Texture t = DelvePlaymat.texture(id);
            if (t != null) {
                Image img = new Image(p == null ? DelvePlaymat.cropped(t, 5f) : new TextureRegion(t));
                img.setBounds(x, H - y - h, w, h);
                img.setTouchable(Touchable.disabled);
                if (!owned) img.getColor().a = 0.45f;
                track(img);
            }
            label("[%75]" + (p == null ? "[#c0e0ff]" : "") + shorten(titles.get(i)), x, y + h + 1, w, 12, Align.center);
            String text;
            Runnable action;
            boolean disabled = false;
            if (owned) {
                text = id.equals(current) ? "[GOLD]In use" : "[%80]Use";
                disabled = id.equals(current);
                action = () -> { prof.setCurrentPlaymat(id); build(); };
            } else if (p.requires != null) {
                text = "[GRAY]" + p.requires.title + " title";
                disabled = true;
                action = () -> { };
            } else {
                text = "[%80]Buy " + p.price + "g";
                disabled = prof.gold() < p.price;
                action = () -> buyPlaymat(p);
            }
            button(text, x + w / 2f - 50, y + h + 14, 100, 16, action).setDisabled(disabled);
        }
        if (pages > 1) {
            button("<", 14, 120, 16, 20, () -> { matPage--; build(); }).setDisabled(matPage == 0);
            button(">", 450, 120, 16, 20, () -> { matPage++; build(); }).setDisabled(matPage >= pages - 1);
        }
        button(current.isEmpty() ? "[GOLD]No playmat (in use)" : "[%80]No playmat", 20, 212, 130, 15, () -> {
            prof.setCurrentPlaymat("");
            build();
        }).setDisabled(current.isEmpty());
        label("[%75]Your own playmat: put a .png or .jpg image in the playmats folder.",
                156, 208, 230, 24, Align.left);
        button("[%75]Open folder", 390, 212, 70, 15, () -> {
            try {
                Gdx.net.openURI(DelvePlaymat.customDir().toURI().toString());
            } catch (Exception e) {
                info("Playmats folder", DelvePlaymat.customDir().getAbsolutePath(), null);
            }
        });
        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private static String shorten(String s) {
        return s.length() <= 24 ? s : s.substring(0, 23) + ".";
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
            button("<", 11, 150, 9, 30, () -> { page--; build(); }).setDisabled(page == 0); // clear of the sleeves and Use buttons
            button(">", 460, 150, 9, 30, () -> { page++; build(); }).setDisabled(page >= pages - 1);
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
