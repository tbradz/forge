package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.scene.StartScene;

import java.util.List;

/**
 * The save menu, shown when you press Delve on the title screen: load, create
 * or delete saves. Each save has its own days, tiers, gold, collection and decks.
 */
public class DelveSavesScene extends DelveScene {
    private static DelveSavesScene object;
    private static final int PER_PAGE = 5;
    private int page = 0;

    public static DelveSavesScene instance() {
        if (object == null)
            object = new DelveSavesScene();
        return object;
    }

    @Override
    public void enter() {
        DelveAudio.menus();
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        title("Delve - Saves");
        String current = DelveSaves.currentName();
        List<String> saves = DelveSaves.list();
        int pages = Math.max(1, (saves.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.min(page, pages - 1);
        image("ui/delve/panel.png", 20, 34, W - 40, 196);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= saves.size()) break;
            String name = saves.get(idx);
            float y = 44 + i * 36;
            boolean cur = name.equals(current);
            label("[%95]" + (cur ? "[GOLD]" : "") + name + (cur ? "  (last played)" : ""), 34, y, 250, 14, Align.left);
            label("[%70]" + DelveSaves.summary(name), 34, y + 15, 290, 12, Align.left);
            button("[GOLD]Play", W - 170, y + 3, 70, 22, () -> play(name));
            button("Delete", W - 94, y + 3, 64, 22, () -> confirm("Delete " + name,
                    "Delete " + name + " for good? Its collection, decks and progress are lost.", () -> {
                        DelveSaves.delete(name);
                        build();
                    }));
        }
        if (pages > 1) {
            button("<", 30, 210, 30, 18, () -> { page--; build(); }).setDisabled(page == 0);
            label("[%75]Page " + (page + 1) + " of " + pages, 64, 212, 100, 14, Align.left);
            button(">", 160, 210, 30, 18, () -> { page++; build(); }).setDisabled(page >= pages - 1);
        }
        button("[GOLD]New save", W / 2f - 110, 238, 100, 22, this::newSave);
        button("Back", W / 2f + 10, 238, 100, 22, () -> Forge.switchScene(StartScene.instance()));
    }

    /** A new save: create your character (for good), meet Bram, then into town. */
    private void newSave() {
        DelveSaves.load(DelveSaves.create());
        DelveCharacterScene.instance().open(() ->
                DelveIntroScene.instance().play(true, () -> Forge.switchScene(DelveHubScene.instance())));
    }

    private void play(String name) {
        DelveSaves.load(name);
        Forge.switchScene(DelveHubScene.instance());
    }

    @Override
    public boolean back() {
        Forge.switchScene(StartScene.instance());
        return true;
    }
}
