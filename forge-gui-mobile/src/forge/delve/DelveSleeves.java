package forge.delve;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import forge.Forge;
import forge.assets.FSkin;

/**
 * Delve's foil sleeves (premium, sold at the Outfitter; art from delve-tools/make_sleeves.py).
 * They're added to Forge's own sleeve table under high numbers, so everything that draws a
 * sleeve by number (duel card backs, pack openings, the Outfitter) shows them like Forge's sleeves.
 * Ownership and the equipped sleeve use the same profile settings as Forge's sleeves.
 */
public enum DelveSleeves {
    HOLO_PRISM("holo_prism", "Holo Prism", 250),
    GOLD_LEAF("gold_leaf", "Gold Leaf", 200),
    SILVER_ETCHED("silver_etched", "Silver Etched", 150),
    MANA_SWIRL("mana_swirl", "Mana Swirl", 250),
    STARFOIL("starfoil", "Starfoil", 200),
    DRAGONSCALE("dragonscale", "Dragonscale", 200);

    /** Foil sleeves use sleeve numbers from here up (Forge's own sleeves are far below). */
    public static final int BASE = 1000;

    public final String file, title;
    public final int price;

    DelveSleeves(String file, String title, int price) {
        this.file = file;
        this.title = title;
        this.price = price;
    }

    public int index() {
        return BASE + ordinal();
    }

    public static boolean isFoil(int index) {
        return index >= BASE;
    }

    /** Add the foil sleeves to Forge's sleeve table (safe to call any time; only loads once). */
    public static void register() {
        for (DelveSleeves s : values()) {
            if (FSkin.getSleeves().containsKey(s.index())) continue;
            try {
                Texture t = Forge.getAssets().getTexture(
                        forge.adventure.util.Config.instance().getFile("ui/delve/sleeves/" + s.file + ".png"), true, false);
                if (t != null) FSkin.getSleeves().put(s.index(), new TextureRegion(t));
            } catch (Exception e) {
                e.printStackTrace(); // a missing foil just isn't offered
            }
        }
    }
}
