package forge.delve;

import forge.Adventure;
import forge.adventure.scene.ForgeScene;
import forge.card.CardEdition;
import forge.deck.Deck;
import forge.deck.FDeckEditor;
import forge.deck.io.DeckStorage;
import forge.game.GameType;
import forge.itemmanager.ItemManagerConfig;
import forge.localinstance.properties.ForgeProfileProperties;
import forge.model.FModel;
import forge.screens.FScreen;
import forge.util.storage.IStorage;
import forge.util.storage.StorageImmediatelySerialized;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Deck building in Your House: Forge's full deck editor, limited to the cards in
 * your Delve collection (basic lands are free). Decks are saved as .dck files
 * under <userDir>/delve/decks/ and can be taken to the Castle.
 *
 * Rules follow Forge's Quest format: at least 40 cards, up to 4 copies of a card,
 * a sideboard of up to 15.
 */
public class DelveDeckEditScene extends ForgeScene {
    private static DelveDeckEditScene object;
    private static IStorage<Deck> storage, commanderStorage;
    private static FDeckEditor.DeckEditorConfig config, commanderConfig;

    private FScreen screen;

    public static DelveDeckEditScene instance() {
        if (object == null)
            object = new DelveDeckEditScene();
        return object;
    }

    /** The player's built decks (not Locked Decks). */
    public static IStorage<Deck> decks() {
        if (storage == null) {
            File dir = new File(DelveSaves.dir(), "decks");
            dir.mkdirs();
            storage = new StorageImmediatelySerialized<>("Delve decks",
                    new DeckStorage(dir, DelveSaves.dir().getPath()), true);
        }
        return storage;
    }

    /** The player's Commander decks: 100-card singleton, a legendary creature in the Commander section. */
    public static IStorage<Deck> commanderDecks() {
        if (commanderStorage == null) {
            File dir = new File(DelveSaves.dir(), "commander");
            dir.mkdirs();
            commanderStorage = new StorageImmediatelySerialized<>("Delve commander decks",
                    new DeckStorage(dir, DelveSaves.dir().getPath()), true);
        }
        return commanderStorage;
    }

    /** Commander decks that pass Forge's Commander deck rules. */
    public static List<Deck> legalCommanderDecks() {
        List<Deck> out = new ArrayList<>();
        for (Deck d : commanderDecks())
            if (forge.deck.DeckFormat.Commander.getDeckConformanceProblem(d) == null) out.add(d);
        return out;
    }

    /** Forget loaded decks and editors (after switching saves). */
    static void reload() {
        storage = null;
        commanderStorage = null;
        config = null;
        commanderConfig = null;
    }

    public static List<Deck> deckList() {
        List<Deck> out = new ArrayList<>();
        for (Deck d : decks()) out.add(d);
        return out;
    }

    private static FDeckEditor.DeckEditorConfig config() {
        if (config == null) {
            FDeckEditor.FileDeckController<Deck> controller = new FDeckEditor.FileDeckController<Deck>(decks(), Deck::new, null) { };
            config = new FDeckEditor.GameTypeDeckEditorConfig(GameType.Quest, controller)
                    .setCatalogConfig(ItemManagerConfig.SEALED_POOL)
                    .setMainSectionConfig(ItemManagerConfig.DECK_EDITOR)
                    .setSideboardConfig(ItemManagerConfig.DECK_EDITOR)
                    .setPlayerInventorySupplier(() -> DelveProfile.get().collection())
                    .setBasicLandSetFunction(d -> basicLandSets());
        }
        return config;
    }

    private static FDeckEditor.DeckEditorConfig commanderConfig() {
        if (commanderConfig == null) {
            FDeckEditor.FileDeckController<Deck> controller =
                    new FDeckEditor.FileDeckController<Deck>(commanderDecks(), Deck::new, null) { };
            commanderConfig = new FDeckEditor.GameTypeDeckEditorConfig(GameType.Commander, controller)
                    .setCatalogConfig(ItemManagerConfig.SEALED_POOL)
                    .setMainSectionConfig(ItemManagerConfig.DECK_EDITOR)
                    .setSideboardConfig(ItemManagerConfig.DECK_EDITOR)
                    .setPlayerInventorySupplier(() -> DelveProfile.get().collection())
                    .setBasicLandSetFunction(d -> basicLandSets());
        }
        return commanderConfig;
    }

    /** Open the Commander deck editor on an existing deck, or a new one if {@code deck} is null. */
    public void openCommander(Deck deck) {
        screen = deck == null ? new FDeckEditor(commanderConfig(), (Deck) null) : new FDeckEditor(commanderConfig(), deck);
        forge.Forge.switchScene(this);
    }

    /** Sets offered in the "add basic lands" dialog: today's set if it has basics, plus recent core/expansion sets. */
    private static List<CardEdition> basicLandSets() {
        List<CardEdition> out = new ArrayList<>();
        CardEdition today = DelveDay.today().edition;
        if (today.hasBasicLands()) out.add(today);
        for (CardEdition e : FModel.getMagicDb().getSortedEditions()) {
            if (out.size() >= 8) break;
            if (e.hasBasicLands() && DelveDay.isRecent(e) && !out.contains(e)) out.add(e);
        }
        if (out.isEmpty()) out.add(FModel.getMagicDb().getEditions().get("M21"));
        return out;
    }

    /** Open the editor on an existing deck, or a new one if {@code deck} is null. */
    public void open(Deck deck) {
        // getScreen() is called repeatedly (e.g. for touch handling), so build the editor once here
        screen = deck == null ? new FDeckEditor(config(), (Deck) null) : new FDeckEditor(config(), deck);
        forge.Forge.switchScene(this);
    }

    @Override
    public boolean leave() {
        Adventure.getInstance().renderTransitionScreen = true;
        return super.leave();
    }

    @Override
    public FScreen getScreen() {
        return screen;
    }
}
