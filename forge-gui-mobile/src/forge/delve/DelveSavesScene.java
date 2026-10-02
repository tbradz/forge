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
        // TEMPORARY: testing shortcut, remove before release
        button("[%80][#c0a060]Dev save", 24, 238, 90, 22, this::devSave);
        button("Back", W / 2f + 10, 238, 100, 22, () -> Forge.switchScene(StartScene.instance()));
    }

    /** A new save: pick which sets to climb, create your character (for good), then Bram shows you the town. */
    private void newSave() {
        chooseSets(allSets -> {
            DelveSaves.load(DelveSaves.create());
            DelveProfile.get().setAllSets(allSets);
            meetTheTown();
        });
    }

    /** Optional mode, chosen per save: the Modern climb (default) or every set since Eighth Edition. */
    private void chooseSets(java.util.function.Consumer<Boolean> then) {
        List<String> labels = List.of(
                "[GOLD]Modern[]: " + DelveDay.tiers(false).get(0).getName() + " onward (" + DelveDay.tiers(false).size() + " sets)",
                "All sets: " + DelveDay.tiers(true).get(0).getName() + " onward (" + DelveDay.tiers(true).size() + " sets)",
                "Cancel");
        List<Runnable> actions = List.of(() -> then.accept(false), () -> then.accept(true), () -> { });
        choose("Where does your climb begin?", "[%80]Each set is a dungeon tier: clear one to unlock the next. "
                + "Modern is the standard game. All sets is a much longer climb through twenty years of Magic.", labels, null, actions);
    }

    private void meetTheTown() {
        DelveCharacterScene.instance().open(() ->
                DelveTourScene.instance().play(true, () -> Forge.switchScene(DelveHubScene.instance())));
    }

    /** TEMPORARY dev start: a new save with lots of gold, optionally every tier unlocked. */
    private void devSave() {
        List<String> labels = List.of("Gold only", "Gold + every tier unlocked", "Cancel");
        List<Runnable> actions = List.of(() -> chooseSets(all -> startDev(false, all)), () -> chooseSets(all -> startDev(true, all)), () -> { });
        choose("Dev save", "[%80]Testing shortcut: a new save that starts with " + DEV_GOLD
                + " gold. Optionally unlock every set tier too.", labels, null, actions);
    }

    static final int DEV_GOLD = 50000;

    private void startDev(boolean allTiers, boolean allSets) {
        DelveSaves.load(DelveSaves.create());
        DelveProfile prof = DelveProfile.get();
        prof.setAllSets(allSets);
        prof.addGold(DEV_GOLD);
        if (allTiers) prof.devUnlockAllTiers();
        meetTheTown();
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
