package forge.delve;

import forge.deck.CardPool;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeProfileProperties;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's permanent Delve progress: the card collection and Locked Decks.
 * Stored as plain Forge .dck files under <userDir>/delve/ so they're easy to
 * inspect and can't collide with Adventure saves.
 */
public class DelveProfile {
    private static DelveProfile instance;

    private final File root;
    private final File collectionFile;
    private final File lockedDir;
    private final Deck collection;
    private final List<Deck> lockedDecks = new ArrayList<>();

    public static DelveProfile get() {
        if (instance == null)
            instance = new DelveProfile();
        return instance;
    }

    private DelveProfile() {
        root = new File(ForgeProfileProperties.getUserDir(), "delve");
        lockedDir = new File(root, "locked");
        collectionFile = new File(root, "collection.dck");
        lockedDir.mkdirs();

        Deck loaded = collectionFile.exists() ? DeckSerializer.fromFile(collectionFile) : null;
        collection = loaded != null ? loaded : new Deck("Delve Collection");

        File[] files = lockedDir.listFiles((d, n) -> n.endsWith(".dck"));
        if (files != null) {
            for (File f : files) {
                Deck d = DeckSerializer.fromFile(f);
                if (d != null) lockedDecks.add(d);
            }
        }
    }

    public CardPool collection() {
        return collection.getMain();
    }

    public List<Deck> lockedDecks() {
        return lockedDecks;
    }

    public void addToCollection(List<PaperCard> cards) {
        collection.getMain().add(cards);
        save();
    }

    public void addLockedDeck(Deck deck, String name) {
        Deck locked = new Deck(uniqueName(name));
        locked.getMain().addAll(deck.getMain());
        lockedDecks.add(locked);
        DeckSerializer.writeDeck(locked, new File(lockedDir, safeFileName(locked.getName()) + ".dck"));
    }

    private void save() {
        DeckSerializer.writeDeck(collection, collectionFile);
    }

    private String uniqueName(String base) {
        String name = base;
        int n = 2;
        while (true) {
            final String candidate = name;
            if (lockedDecks.stream().noneMatch(d -> d.getName().equals(candidate)))
                return name;
            name = base + " " + n++;
        }
    }

    private static String safeFileName(String s) {
        return s.replaceAll("[^A-Za-z0-9 _-]", "").trim();
    }
}
