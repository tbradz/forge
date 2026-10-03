package forge.delve;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import forge.Forge;
import forge.assets.FImage;
import forge.assets.FTextureRegionImage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Playmats: shown under your half of the battlefield in every Delve duel. Some are sold at
 * the Outfitter; title playmats come free with a Castle title (art from
 * delve-tools/make_interiors.py, 5:1). Your own images in {@link #customDir()} (png/jpg) are
 * playmats too, free in every save, cropped to fit (never squashed).
 */
public enum DelvePlaymat {
    FOREST("forest", "Deepwood", 90, null),
    MIDNIGHT("midnight", "Starfall", 120, null),
    EMBER("ember", "Emberglow", 120, null),
    KNIGHT("knight", "Knight's Crest", 0, DelveRenown.Title.KNIGHT),
    BARON("baron", "Baron's Tower", 0, DelveRenown.Title.BARON),
    DUKE("duke", "Duke's Regalia", 0, DelveRenown.Title.DUKE);

    /** Saved ids of your own playmats start with this, followed by the file name. */
    public static final String CUSTOM = "custom:";

    public final String id, title;
    /** Outfitter price; 0 = not for sale (comes with {@link #requires}) */
    public final int price;
    public final DelveRenown.Title requires;

    DelvePlaymat(String id, String title, int price, DelveRenown.Title requires) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.requires = requires;
    }

    /** Bought at the Outfitter, or earned with its title. */
    public boolean owned() {
        if (requires != null) return DelveRenown.title().atLeast(requires);
        return DelveProfile.get().ownedPlaymats().contains(id);
    }

    public static DelvePlaymat byId(String id) {
        for (DelvePlaymat p : values()) if (p.id.equals(id)) return p;
        return null;
    }

    // ---- your own playmats ---------------------------------------------------------------

    /** Where your own playmat images go (shared by every save): <Forge user dir>/delve/playmats. */
    public static File customDir() {
        File d = new File(DelveSaves.base(), "playmats");
        d.mkdirs();
        return d;
    }

    /** Your own playmat images, by file name. */
    public static List<File> customFiles() {
        List<File> out = new ArrayList<>();
        File[] files = customDir().listFiles();
        if (files == null) return out;
        for (File f : files) {
            String n = f.getName().toLowerCase();
            if (f.isFile() && (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg"))) out.add(f);
        }
        out.sort(java.util.Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** A custom playmat's display name: its file name without the extension. */
    public static String customTitle(File f) {
        String n = f.getName();
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    // ---- the equipped playmat ----------------------------------------------------------------

    /** The equipped playmat's id if you still have it ("" = none). */
    public static String currentId() {
        String id = DelveProfile.get().currentPlaymat();
        if (id.startsWith(CUSTOM)) return new File(customDir(), id.substring(CUSTOM.length())).isFile() ? id : "";
        DelvePlaymat p = byId(id);
        return p != null && p.owned() ? id : "";
    }

    /** The texture for a playmat id (built-in or custom), or null. */
    public static Texture texture(String id) {
        try {
            if (id.startsWith(CUSTOM))
                return Forge.getAssets().getTexture(Gdx.files.absolute(
                        new File(customDir(), id.substring(CUSTOM.length())).getAbsolutePath()), true, false);
            DelvePlaymat p = byId(id);
            return p == null ? null : Forge.getAssets().getTexture(
                    forge.adventure.util.Config.instance().getFile("ui/delve/playmat_" + p.id + ".png"), true, false);
        } catch (Exception e) {
            e.printStackTrace(); // a bad image just means no mat
            return null;
        }
    }

    /** The middle of a texture cropped to {@code aspect} (width / height), so any image fills the area without stretching. */
    public static TextureRegion cropped(Texture t, float aspect) {
        int w = t.getWidth(), h = t.getHeight();
        if (w / (float) h > aspect) {
            int cw = Math.round(h * aspect);
            return new TextureRegion(t, (w - cw) / 2, 0, cw, h);
        }
        int ch = Math.round(w / aspect);
        return new TextureRegion(t, 0, (h - ch) / 2, w, ch);
    }

    private static String cachedId;
    private static float cachedAspect;
    private static FImage cachedImage;

    /**
     * The equipped playmat for a w x h area (asked every frame by the duel screen), or null for none.
     * Built-in mats are painted at about the battlefield's shape and drawn whole; your own images
     * are cropped to the area so they're never squashed.
     */
    public static FImage currentImage(float w, float h) {
        String id = currentId();
        if (id.isEmpty() || w <= 0 || h <= 0) return null;
        float aspect = Math.round(w / h * 100) / 100f;
        if (!id.equals(cachedId) || aspect != cachedAspect) {
            cachedId = id;
            cachedAspect = aspect;
            Texture t = texture(id);
            cachedImage = t == null ? null : new FTextureRegionImage(id.startsWith(CUSTOM) ? cropped(t, aspect) : new TextureRegion(t));
        }
        return cachedImage;
    }
}
