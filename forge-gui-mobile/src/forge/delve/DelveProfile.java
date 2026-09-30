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
    private final File statsFile;
    private final java.util.Properties stats = new java.util.Properties();

    public static DelveProfile get() {
        if (instance == null)
            instance = new DelveProfile();
        return instance;
    }

    private DelveProfile() {
        root = new File(ForgeProfileProperties.getUserDir(), "delve");
        lockedDir = new File(root, "locked");
        collectionFile = new File(root, "collection.dck");
        statsFile = new File(root, "profile.properties");
        lockedDir.mkdirs();
        if (statsFile.exists()) {
            try (java.io.FileInputStream in = new java.io.FileInputStream(statsFile)) {
                stats.load(in);
            } catch (java.io.IOException e) {
                e.printStackTrace();
            }
        }

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

    /** Town gold: everything brought out of the dungeon. */
    public int gold() {
        try {
            return Integer.parseInt(stats.getProperty("gold", "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public void addGold(int amount) {
        stats.setProperty("gold", String.valueOf(Math.max(0, gold() + amount)));
        saveStats();
    }

    // ---- today's shop purchases (limited stock) ----------------------------------

    private void rollShopDay() {
        String today = java.time.LocalDate.now().toString();
        if (!today.equals(stats.getProperty("shop.date"))) {
            stats.setProperty("shop.date", today);
            stats.setProperty("shop.bought", "");
            stats.setProperty("shop.packs.0", "0");
            stats.setProperty("shop.packs.1", "0");
        }
    }

    public boolean shopBought(int index) {
        rollShopDay();
        return ("," + stats.getProperty("shop.bought", "") + ",").contains("," + index + ",");
    }

    public void markShopBought(int index) {
        rollShopDay();
        String b = stats.getProperty("shop.bought", "");
        stats.setProperty("shop.bought", b.isEmpty() ? String.valueOf(index) : b + "," + index);
        saveStats();
    }

    public int packsBought(int type) {
        rollShopDay();
        return Integer.parseInt(stats.getProperty("shop.packs." + type, "0"));
    }

    public void markPackBought(int type) {
        stats.setProperty("shop.packs." + type, String.valueOf(packsBought(type) + 1));
        saveStats();
    }

    /** Spend town gold; returns false (and spends nothing) if there isn't enough. */
    public boolean spendGold(int amount) {
        if (gold() < amount) return false;
        addGold(-amount);
        return true;
    }

    // ---- character --------------------------------------------------------------

    public boolean hasCharacter() {
        return stats.containsKey("heroRace");
    }

    public int heroRace() {
        try {
            return Integer.parseInt(stats.getProperty("heroRace", "2")); // 2 = Human
        } catch (NumberFormatException e) {
            return 2;
        }
    }

    public boolean heroFemale() {
        return Boolean.parseBoolean(stats.getProperty("heroFemale", "false"));
    }

    public void setCharacter(int race, boolean female) {
        stats.setProperty("heroRace", String.valueOf(race));
        stats.setProperty("heroFemale", String.valueOf(female));
        saveStats();
    }

    /** Atlas of the player's walking sprite. */
    public String heroAtlas() {
        return forge.adventure.data.HeroListData.instance().getHero(heroRace(), heroFemale());
    }

    private void saveStats() {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(statsFile)) {
            stats.store(out, "Delve profile");
        } catch (java.io.IOException e) {
            e.printStackTrace();
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
