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
        building("b_shop", "Card Shop", () -> Forge.switchScene(DelveShopScene.instance()));
        building("b_tavern", "Tavern",
                "Talk to locals, pick up rumors, and play casual games to test decks.", 2);
        building("b_outfitter", "Outfitter",
                "Sleeves, playmats, dice, and other cosmetics.", 3);
        building("b_castle", "Castle", this::openCastle);

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
        DelveRun run = currentRun();
        if (run != null && !run.over) {
            Forge.switchScene(DelveMapScene.instance()); // resume the run in progress
            return;
        }
        DelveProfile p = DelveProfile.get();
        if (p.delvedToday() || p.isEvening()) {
            openInfo("Dungeon Gate", "The gate is sealed until morning. You get one delve per day.\n\n"
                    + (p.castleToday() ? "Sleep at Your House to start a new day."
                    : "The Castle tournament is open tonight, or sleep at Your House to start a new day."));
            return;
        }
        Forge.switchScene(DelveGateScene.instance());
    }

    /** The run in progress, loading a saved one if the game was closed mid-run. */
    private static DelveRun currentRun() {
        DelveRun run = DelveRun.current();
        if (run == null && DelveRunSave.exists())
            run = DelveRunSave.load();
        return run;
    }

    private void openCastle() {
        DelveProfile p = DelveProfile.get();
        DelveRun run = currentRun();
        if (run != null && !run.over) {
            openInfo("Castle", "You're in the middle of a dungeon run. Finish it (or abandon it) first; "
                    + "the tournament starts in the evening.");
            return;
        }
        if (!p.isEvening()) {
            showDialog(createGenericDialog("Castle", "The tournament begins at dusk. Skip today's dungeon run and "
                            + "head to the Castle now?", "Go to the Castle", "Not yet",
                    () -> {
                        removeDialog();
                        p.markDelved();
                        p.makeEvening();
                        refreshTime();
                        Forge.switchScene(DelveCastleScene.instance());
                    }, this::removeDialog));
            return;
        }
        Forge.switchScene(DelveCastleScene.instance());
    }

    private void sleep() {
        DelveRun run = currentRun();
        if (run != null && !run.over) {
            openInfo("Sleep", "You can't sleep with a run in progress. Finish or abandon it first.");
            return;
        }
        DelveProfile p = DelveProfile.get();
        String warn = !p.delvedToday() ? "You haven't delved today. " : !p.castleToday() ? "You skipped tonight's tournament. " : "";
        showDialog(createGenericDialog("Sleep", warn + "Sleep until tomorrow morning?", "Sleep", "Stay up",
                () -> {
                    removeDialog();
                    p.sleep();
                    refreshTime();
                    DelveDay d = DelveDay.today();
                    openInfo("Day " + d.dayNumber, "A new day. Today's dungeon draws from " + d.themeName()
                            + " (" + d.eraYear() + " era).");
                }, this::removeDialog));
    }

    private com.github.tommyettinger.textra.TextraLabel timeLabel;

    /** Header text and town lighting for the time of day. */
    private void refreshTime() {
        DelveProfile p = DelveProfile.get();
        String when = p.isEvening() ? "Evening" : "Morning";
        String hint = !p.isEvening() ? "the dungeon awaits"
                : !p.castleToday() ? "the Castle tournament is open" : "time to rest at Your House";
        if (timeLabel == null) {
            timeLabel = forge.adventure.util.Controls.newTextraLabel("");
            timeLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
            timeLabel.setBounds(90, 270 - 8 - 16, 300, 16);
            ui.addActor(timeLabel);
        }
        timeLabel.setText("[%90]Day " + p.day() + "  -  " + when + ":  [GOLD]" + hint);
        timeLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        com.badlogic.gdx.scenes.scene2d.Actor bg = ui.findActor("bg");
        if (bg instanceof com.badlogic.gdx.scenes.scene2d.ui.Image) { // daylight town in the morning, night in the evening
            com.badlogic.gdx.graphics.Texture tex = Forge.getAssets().getTexture(forge.adventure.util.Config.instance()
                    .getFile(p.isEvening() ? "ui/delve/town_bg.png" : "ui/delve/town_bg_day.png"), true, false);
            ((com.badlogic.gdx.scenes.scene2d.ui.Image) bg).setDrawable(
                    new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new com.badlogic.gdx.graphics.g2d.TextureRegion(tex)));
        }
    }

    private void openHouse() {
        DelveProfile p = DelveProfile.get();
        StringBuilder sb = new StringBuilder();
        sb.append("Day ").append(p.day()).append(p.isEvening() ? " (evening)" : " (morning)")
                .append("    Gold ").append(p.gold()).append("    Castle titles ").append(p.castleTitles()).append("\n");
        sb.append("Collection ").append(p.collection().countAll()).append(" cards (")
                .append(p.collection().countDistinct()).append(" different)    Locked Decks ")
                .append(p.lockedDecks().size());
        houseMenu(sb.toString());
    }

    private void houseMenu(String summary) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d =
                new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Your House", forge.adventure.util.Controls.getSkin());
        d.getContentTable().add(forge.adventure.util.Controls.newTextraLabel(summary));
        java.util.function.BiConsumer<String, Runnable> add = (text, action) -> {
            d.getButtonTable().row();
            d.getButtonTable().add(forge.adventure.util.Controls.newTextButton(text, () -> {
                removeDialog();
                action.run();
            })).width(220f).pad(2f);
        };
        add.accept("[GOLD]Sleep until tomorrow", this::sleep);
        add.accept("Build a deck", this::chooseDeckToEdit);
        add.accept("View collection", this::viewCollection);
        add.accept("Change character", () -> DelveCharacterScene.instance().open(() -> Forge.switchScene(this)));
        add.accept("Close", () -> { });
        showDialog(d);
    }

    private void chooseDeckToEdit() {
        if (DelveProfile.get().collection().isEmpty()) {
            openInfo("Build a deck", "Your collection is empty. Keep cards at the end of a dungeon run first.");
            return;
        }
        java.util.List<forge.deck.Deck> decks = DelveDeckEditScene.deckList();
        if (decks.isEmpty()) {
            DelveDeckEditScene.instance().open(null);
            return;
        }
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d =
                new com.badlogic.gdx.scenes.scene2d.ui.Dialog("Your decks", forge.adventure.util.Controls.getSkin());
        d.getContentTable().add(forge.adventure.util.Controls.newTextraLabel("Edit a deck or start a new one."));
        for (forge.deck.Deck deck : decks) {
            d.getButtonTable().row();
            d.getButtonTable().add(forge.adventure.util.Controls.newTextButton(deck.getName() + " (" + deck.getMain().countAll() + ")", () -> {
                removeDialog();
                DelveDeckEditScene.instance().open(deck);
            })).width(240f).pad(2f);
        }
        d.getButtonTable().row();
        d.getButtonTable().add(forge.adventure.util.Controls.newTextButton("[GOLD]New deck", () -> {
            removeDialog();
            DelveDeckEditScene.instance().open(null);
        })).width(240f).pad(2f);
        d.getButtonTable().row();
        d.getButtonTable().add(forge.adventure.util.Controls.newTextButton("Cancel", this::removeDialog)).width(240f).pad(2f);
        showDialog(d);
    }

    private void viewCollection() {
        DelveProfile p = DelveProfile.get();
        java.util.List<forge.item.PaperCard> unique = new java.util.ArrayList<>();
        for (forge.item.PaperCard pc : p.collection().toFlatList()) if (!unique.contains(pc)) unique.add(pc);
        unique.sort(java.util.Comparator.comparing(forge.item.PaperCard::getName));
        if (unique.size() > 40) unique = unique.subList(0, 40);
        if (unique.isEmpty()) {
            openInfo("Your collection", "Empty so far. Keep cards at the end of a dungeon run.");
            return;
        }
        DelvePickScene.instance().show("Your collection" + (p.collection().countDistinct() > 40 ? " (first 40)" : ""),
                unique, 0, 0, "Back", x -> Forge.switchScene(this));
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
        DelveDevExport.maybeExport();
        refreshTime();
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
