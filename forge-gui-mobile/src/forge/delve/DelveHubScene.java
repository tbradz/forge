package forge.delve;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.scene.StartScene;
import forge.adventure.scene.UIScene;
import forge.adventure.util.Controls;

/**
 * Delve: the town hub for the roguelike mode.
 *
 * The town is a painted scene; each building is its own picture and acts as the
 * button for that location (hover to highlight and show its name, click to enter).
 * Building art lives in res/adventure/common/ui/delve/ and can be swapped freely.
 */
public class DelveHubScene extends UIScene {
    private static DelveHubScene object;

    private static final float DIM = 0.78f;
    private static final float HOVER_SCALE = 1.06f;

    private final TextraLabel nameplate;

    public static DelveHubScene instance() {
        if (object == null)
            object = new DelveHubScene();
        return object;
    }

    private DelveHubScene() {
        super("ui/delve_hub.json");

        nameplate = Controls.newTextraLabel("");
        nameplate.setAlignment(Align.center);
        nameplate.setVisible(false);

        building("b_house", "Your House", this::openHouse);
        building("b_dungeon", "Dungeon Gate", this::openGate);
        building("b_shop", "Card Shop",
                "A small stock of singles and packs that restocks every day.", 2);
        building("b_tavern", "Tavern",
                "Talk to locals, pick up rumors, and play casual games to test decks.", 2);
        building("b_outfitter", "Outfitter",
                "Sleeves, playmats, dice, and other cosmetics.", 3);
        building("b_castle", "Castle",
                "1v1 and Commander tournaments for your saved and Locked Decks.", 3);

        ui.addActor(nameplate); // on top of the buildings
        ui.onButtonPress("leave", this::returnToStart);
    }

    private void building(String actorName, String title, String description, int phase) {
        building(actorName, title, () -> openBuilding(title, description, phase));
    }

    /** Wire a building picture as a button: dim at rest, lit and named on hover. */
    private void building(String actorName, String title, Runnable onClick) {
        Actor b = ui.findActor(actorName);
        if (b == null)
            return;
        b.setOrigin(Align.bottom);
        b.setColor(DIM, DIM, DIM, 1f);
        b.addListener(new ClickListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                super.enter(event, x, y, pointer, fromActor);
                if (pointer != -1) return; // mouse hover only
                b.setColor(1f, 1f, 1f, 1f);
                b.setScale(HOVER_SCALE);
                showNameplate(b, title);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                super.exit(event, x, y, pointer, toActor);
                if (pointer != -1) return;
                b.setColor(DIM, DIM, DIM, 1f);
                b.setScale(1f);
                nameplate.setVisible(false);
            }

            @Override
            public void clicked(InputEvent event, float x, float y) {
                onClick.run();
            }
        });
    }

    private void showNameplate(Actor b, String title) {
        nameplate.setText("[%120]" + title);
        nameplate.pack();
        nameplate.setPosition(b.getX() + b.getWidth() / 2f - nameplate.getWidth() / 2f,
                b.getY() + b.getHeight() * HOVER_SCALE + 2f);
        nameplate.setVisible(true);
    }

    /** Placeholder until each building has its own screen. */
    private void openBuilding(String title, String description, int phase) {
        showDialog(createGenericDialog(title,
                description + "\n\nComing in Phase " + phase + ".",
                Forge.getLocalizer().getMessage("lblOK"), null, this::removeDialog, null));
    }

    private void openGate() {
        if (!DelveProfile.get().hasCharacter()) { // first visit: make a character
            DelveCharacterScene.instance().open(() -> {
                Forge.switchScene(this);
                openGate();
            });
            return;
        }
        DelveRun run = DelveRun.current();
        if (run == null && DelveRunSave.exists())
            run = DelveRunSave.load(); // pick up a run saved before the game was closed
        if (run != null && !run.over)
            Forge.switchScene(DelveMapScene.instance()); // resume the run in progress
        else
            Forge.switchScene(DelveGateScene.instance());
    }

    private void openHouse() {
        DelveProfile p = DelveProfile.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Gold: ").append(p.gold()).append("\n");
        sb.append("Collection: ").append(p.collection().countAll()).append(" cards (")
                .append(p.collection().countDistinct()).append(" different)\n\n");
        if (p.lockedDecks().isEmpty()) {
            sb.append("Locked Decks: none yet. Clear a dungeon and choose to lock your deck.");
        } else {
            sb.append("Locked Decks:");
            for (forge.deck.Deck d : p.lockedDecks())
                sb.append("\n  ").append(d.getName()).append(" (").append(d.getMain().countAll()).append(" cards)");
        }
        showDialog(createGenericDialog("Your House", sb.toString(), "View collection", "Change character",
                () -> {
                    removeDialog();
                    java.util.List<forge.item.PaperCard> cards = new java.util.ArrayList<>(p.collection().toFlatList());
                    java.util.List<forge.item.PaperCard> unique = new java.util.ArrayList<>();
                    for (forge.item.PaperCard pc : cards) if (!unique.contains(pc)) unique.add(pc);
                    unique.sort(java.util.Comparator.comparing(forge.item.PaperCard::getName));
                    if (unique.size() > 40) unique = unique.subList(0, 40);
                    if (unique.isEmpty()) return;
                    DelvePickScene.instance().show("Your collection", unique, 0, 0, "Back",
                            x -> Forge.switchScene(this));
                }, () -> {
                    removeDialog();
                    DelveCharacterScene.instance().open(() -> Forge.switchScene(this));
                }, true, "Close"));
    }

    private String pendingTitle, pendingText;

    /** Show a message the next time the town is on screen (e.g. after a run). */
    public void notice(String title, String text) {
        pendingTitle = title;
        pendingText = text;
        if (Forge.getCurrentScene() == this)
            showPending();
    }

    private void showPending() {
        if (pendingText == null) return;
        String t = pendingTitle, m = pendingText;
        pendingTitle = pendingText = null;
        openInfo(t, m);
    }

    private void openInfo(String title, String text) {
        showDialog(createGenericDialog(title, text,
                Forge.getLocalizer().getMessage("lblOK"), null, this::removeDialog, null));
    }

    @Override
    public void enter() {
        super.enter();
        showPending();
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
