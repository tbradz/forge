package forge.delve;

import com.badlogic.gdx.Gdx;
import forge.Adventure;
import forge.Forge;
import forge.adventure.scene.ForgeScene;
import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.deck.FDeckEditor;
import forge.game.GameType;
import forge.item.PaperCard;
import forge.itemmanager.ItemManagerConfig;
import forge.model.FModel;
import forge.screens.FScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Prerelease deck building: Forge's sealed deck editor with the opened pool in
 * the sideboard. Move cards into the main deck and add free basic lands; when
 * the editor is closed with 40+ cards the run begins. Fewer than 40 sends you
 * back to keep building.
 */
public class DelveSealedScene extends ForgeScene {
    private static DelveSealedScene object;

    private FScreen screen;
    private PoolController controller;
    private DelveDay day;
    private Consumer<Deck> onDone;
    private boolean finishing;

    public static DelveSealedScene instance() {
        if (object == null)
            object = new DelveSealedScene();
        return object;
    }

    /** In-memory deck: the pool lives in the sideboard, the deck in main. Nothing is saved to disk. */
    private static class PoolController implements FDeckEditor.IDeckController {
        private Deck deck;
        private FDeckEditor editor;

        PoolController(Deck deck) { this.deck = deck; }

        @Override public void setEditor(FDeckEditor editor) {
            this.editor = editor;
            if (editor != null) editor.notifyNewControllerModel();
        }
        @Override public void setDeck(Deck d) {
            deck = d;
            if (editor != null) editor.notifyNewControllerModel();
        }
        @Override public Deck getDeck() { return deck; }
        @Override public void newDeck() { }
        @Override public String getDeckDisplayName() { return deck.getName(); }
        @Override public void notifyModelChanged() { }
        @Override public void exitWithoutSaving() { }
    }

    /** Build a deck from {@code pool}; {@code onDone} receives the 40+ card deck. */
    public void open(DelveDay day, List<PaperCard> pool, Consumer<Deck> onDone) {
        this.day = day;
        this.onDone = onDone;
        Deck deck = new Deck(day.edition.getName() + " Prerelease");
        deck.getOrCreate(DeckSection.Sideboard).add(pool);
        controller = new PoolController(deck);
        FDeckEditor.DeckEditorConfig config = new FDeckEditor.GameTypeDeckEditorConfig(GameType.Sealed, controller)
                .setSideboardConfig(ItemManagerConfig.SEALED_POOL)
                .setBasicLandSetFunction(d -> basicLandSets());
        screen = new FDeckEditor(config, deck);
        finishing = false;
        Forge.switchScene(this);
    }

    private List<CardEdition> basicLandSets() {
        List<CardEdition> out = new ArrayList<>();
        if (day.edition.hasBasicLands()) out.add(day.edition);
        for (CardEdition e : day.eraSets)
            if (e.hasBasicLands() && !out.contains(e)) out.add(e);
        if (out.isEmpty()) out.add(FModel.getMagicDb().getEditions().get("M21"));
        return out;
    }

    @Override
    public void enter() {
        super.enter();
        if (!finishing)
            Gdx.app.postRunnable(() -> forge.toolbox.FOptionPane.showMessageDialog(
                    "Your opened cards are in the Sideboard tab. Move the ones you want to play into your Main deck, "
                            + "then add basic lands from the menu (they're free). You need at least 40 cards.\n\n"
                            + "Close the editor (back arrow) when you're done to start the dungeon.",
                    "Build your prerelease deck"));
    }

    /** Closing the editor: start the run with 40+ cards, otherwise come back. */
    @Override
    public boolean leave() {
        Adventure.getInstance().renderTransitionScreen = true;
        boolean ok = super.leave();
        Deck deck = controller == null ? null : controller.getDeck();
        int size = deck == null ? 0 : deck.getMain().countAll();
        Gdx.app.postRunnable(() -> {
            if (size >= DelveRun.MIN_DECK) {
                finishing = true;
                Deck playDeck = new Deck(deck.getName());
                playDeck.getMain().addAll(deck.getMain());
                Consumer<Deck> cb = onDone;
                onDone = null;
                if (cb != null) cb.accept(playDeck);
            } else {
                finishing = true; // skip the intro text when coming back
                Forge.switchScene(this);
                forge.toolbox.FOptionPane.showMessageDialog("Your deck has " + size + " cards. It needs at least "
                        + DelveRun.MIN_DECK + " (basic lands are free: use Add Basic Lands in the menu).", "Not yet");
            }
        });
        return ok;
    }

    @Override
    public FScreen getScreen() {
        return screen;
    }
}
