package forge.delve;

import forge.Forge;
import forge.assets.FImage;
import forge.assets.FTextureImage;

/**
 * Playmats: shown under your half of the battlefield in every Delve duel. Some are sold at
 * the Outfitter; title playmats come free with a Castle title. Art from delve-tools/make_interiors.py.
 */
public enum DelvePlaymat {
    FOREST("forest", "Forest Felt", 90, null),
    MIDNIGHT("midnight", "Midnight Stars", 120, null),
    EMBER("ember", "Emberglow", 120, null),
    KNIGHT("knight", "Knight's Crest", 0, DelveRenown.Title.KNIGHT),
    BARON("baron", "Baron's Tower", 0, DelveRenown.Title.BARON),
    DUKE("duke", "Duke's Regalia", 0, DelveRenown.Title.DUKE);

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

    public String image() {
        return "ui/delve/playmat_" + id + ".png";
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

    /** The equipped playmat, if you still own it. */
    public static DelvePlaymat current() {
        DelvePlaymat p = byId(DelveProfile.get().currentPlaymat());
        return p != null && p.owned() ? p : null;
    }

    private static DelvePlaymat cachedFor;
    private static FImage cachedImage;

    /** The equipped playmat's image for the duel screen (asked every frame), or null for the plain battlefield. */
    public static FImage currentImage() {
        DelvePlaymat p = current();
        if (p == null) return null;
        if (p != cachedFor) {
            cachedFor = p;
            try {
                cachedImage = new FTextureImage(Forge.getAssets().getTexture(
                        forge.adventure.util.Config.instance().getFile(p.image()), true, false));
            } catch (Exception e) {
                e.printStackTrace(); // a missing mat just means a plain battlefield
                cachedImage = null;
            }
        }
        return cachedImage;
    }
}
