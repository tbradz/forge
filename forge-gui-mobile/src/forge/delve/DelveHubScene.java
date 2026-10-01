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
        building("b_tavern", "Tavern", this::openTavern);
        building("b_outfitter", "Outfitter", () -> Forge.switchScene(DelveOutfitterScene.instance()));
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
        openInfo(title, description + "\n\nComing in Phase " + phase + ".");
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
                    : "The Castle and the Tavern are open tonight, or sleep at Your House to start a new day."));
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
            ask("Castle", "The Castle opens at dusk. Skip today's dungeon run and head to the Castle now?",
                    "Go now", "Not yet", () -> {
                        p.markDelved();
                        p.makeEvening();
                        refreshTime();
                        Forge.switchScene(DelveCastleScene.instance());
                    });
            return;
        }
        Forge.switchScene(DelveCastleScene.instance());
    }

    private void openTavern() {
        DelveRun run = currentRun();
        if (run != null && !run.over) {
            openInfo("Tavern", "You're in the middle of a dungeon run. The Tavern can wait until evening.");
            return;
        }
        if (!DelveProfile.get().isEvening()) {
            openInfo("Tavern", "The Tavern opens in the evening, after your dungeon run. "
                    + "Practice games against the patrons, as many as you like.");
            return;
        }
        Forge.switchScene(DelveTavernScene.instance());
    }

    private void sleep() {
        DelveRun run = currentRun();
        if (run != null && !run.over) {
            openInfo("Sleep", "You can't sleep with a run in progress. Finish or abandon it first.");
            return;
        }
        DelveProfile p = DelveProfile.get();
        String warn = !p.delvedToday() ? "You haven't delved today. " : !p.castleToday() ? "You skipped tonight's tournament. " : "";
        ask("Sleep", warn + "Sleep until tomorrow morning?", "Sleep", "Stay up",
                () -> {
                    p.sleep();
                    refreshTime();
                    DelveDay d = DelveDay.today();
                    openInfo("Day " + d.dayNumber, "A new day. The shop has new stock, and the dungeon gate is open."
                            + "\nYour highest tier: " + DelveDay.tierName(d.tier) + ".");
                });
    }

    private com.github.tommyettinger.textra.TextraLabel timeLabel;

    /** Header text and town lighting for the time of day. */
    private void refreshTime() {
        DelveProfile p = DelveProfile.get();
        String when = p.isEvening() ? "Evening" : "Morning";
        String hint = !p.isEvening() ? "the dungeon awaits"
                : !p.castleToday() ? "the Castle and Tavern are open" : "the Tavern is open, or rest at Your House";
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
                .append("  -  [GOLD]").append(p.gold()).append(" gold[]  -  ").append(DelveDay.tierName(p.topTier())).append("\n");
        sb.append(p.collection().countAll()).append(" cards collected  -  ").append(p.lockedDecks().size())
                .append(" Locked Decks  -  ").append(p.castleTitles()).append(" Castle titles\n");
        sb.append("Tokens: ").append(p.tokenSummary());
        houseMenu(sb.toString());
    }

    private void houseMenu(String summary) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make("Your House");
        DelveDialogs.body(d, "[%85]" + summary.replace("\n", "\n[%85]"));
        java.util.List<String> labels = new java.util.ArrayList<>();
        java.util.List<Runnable> actions = new java.util.ArrayList<>();
        java.util.function.BiConsumer<String, Runnable> add = (text, action) -> {
            labels.add(text);
            actions.add(() -> {
                removeDialog();
                action.run();
            });
        };
        add.accept("[GOLD]Sleep until tomorrow", this::sleep);
        add.accept("View collection", this::viewCollection);
        add.accept("Build a deck", () -> chooseDeckToEdit(false));
        add.accept("Build a Commander deck", () -> chooseDeckToEdit(true));
        add.accept("Saves (" + DelveSaves.currentName() + ")", () -> Forge.switchScene(DelveSavesScene.instance()));
        DelveDialogs.gridButtons(d, labels, actions, 2);
        DelveDialogs.wideButton(d, "Close", this::removeDialog, 2);
        showDialog(d);
    }

    private void chooseDeckToEdit(boolean commander) {
        if (DelveProfile.get().collection().isEmpty()) {
            openInfo("Build a deck", "Your collection is empty. Keep cards at the end of a dungeon run first.");
            return;
        }
        java.util.List<forge.deck.Deck> decks = new java.util.ArrayList<>();
        if (commander) for (forge.deck.Deck x : DelveDeckEditScene.commanderDecks()) decks.add(x);
        else decks.addAll(DelveDeckEditScene.deckList());
        java.util.function.Consumer<forge.deck.Deck> edit = x -> {
            if (commander) DelveDeckEditScene.instance().openCommander(x);
            else DelveDeckEditScene.instance().open(x);
        };
        if (decks.isEmpty()) {
            edit.accept(null);
            return;
        }
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make(commander ? "Your Commander decks" : "Your decks");
        DelveDialogs.body(d, commander
                ? "100 cards, one copy of each (except basics), led by a legendary creature. Pick your commander in the Commander section."
                : "Edit a deck or start a new one.");
        for (forge.deck.Deck deck : decks)
            DelveDialogs.listButton(d, deck.getName() + " (" + (deck.getMain().countAll() + (commander ? deck.getCommanders().size() : 0)) + ")", () -> {
                removeDialog();
                edit.accept(deck);
            }, 19f);
        DelveDialogs.listButton(d, "[GOLD]New deck", () -> {
            removeDialog();
            edit.accept(null);
        }, 19f);
        DelveDialogs.listButton(d, "Cancel", this::removeDialog, 19f);
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
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make(title);
        DelveDialogs.body(d, text);
        DelveDialogs.rowButtons(d, new String[]{Forge.getLocalizer().getMessage("lblOK")}, new Runnable[]{this::removeDialog});
        showDialog(d);
    }

    /** Two-button question in Delve's dialog style. */
    private void ask(String title, String text, String yes, String no, Runnable onYes) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make(title);
        DelveDialogs.body(d, text);
        DelveDialogs.rowButtons(d, new String[]{yes, no}, new Runnable[]{() -> {
            removeDialog();
            onYes.run();
        }, this::removeDialog});
        showDialog(d);
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
