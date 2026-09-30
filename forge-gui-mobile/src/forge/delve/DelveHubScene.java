package forge.delve;

import forge.Forge;
import forge.adventure.scene.StartScene;
import forge.adventure.scene.UIScene;

/**
 * Delve: the town hub for the roguelike mode.
 *
 * Phase 0 placeholder. The town is a click-through menu: each building is a
 * button that opens its own screen. For now every building shows what it will
 * do and which phase it arrives in.
 */
public class DelveHubScene extends UIScene {
    private static DelveHubScene object;

    public static DelveHubScene instance() {
        if (object == null)
            object = new DelveHubScene();
        return object;
    }

    private DelveHubScene() {
        super("ui/delve_hub.json");
        ui.onButtonPress("house", () -> building("Your House",
                "Your collection, deck editor, trophies, and cosmetic loadout.", 1));
        ui.onButtonPress("dungeon", () -> building("Dungeon Gate",
                "Today's dungeon: pick a size and a starter (fixed or drafted), then delve.", 1));
        ui.onButtonPress("shop", () -> building("Card Shop",
                "A small stock of singles and packs that restocks every day.", 2));
        ui.onButtonPress("tavern", () -> building("Tavern",
                "Talk to locals, pick up rumors, and play casual games to test decks.", 2));
        ui.onButtonPress("outfitter", () -> building("Outfitter",
                "Sleeves, playmats, dice, and other cosmetics.", 3));
        ui.onButtonPress("castle", () -> building("Castle",
                "1v1 and Commander tournaments for your saved and Locked Decks.", 3));
        ui.onButtonPress("leave", this::returnToStart);
    }

    private void building(String name, String description, int phase) {
        showDialog(createGenericDialog(name,
                description + "\n\nComing in Phase " + phase + ".",
                Forge.getLocalizer().getMessage("lblOK"), null, this::removeDialog, null));
    }

    private void returnToStart() {
        Forge.switchScene(StartScene.instance());
    }

    @Override
    public boolean back() {
        returnToStart();
        return true;
    }
}
