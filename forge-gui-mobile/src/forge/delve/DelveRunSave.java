package forge.delve;

import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.item.PaperCard;
import forge.localinstance.properties.ForgeProfileProperties;
import forge.model.FModel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Saves the run in progress so closing the game doesn't lose it.
 *
 * The map is rebuilt from the run's seed (same rooms, same enemies), so only the
 * player's state is stored: deck, life, gold, progress, and merchant stock.
 * Files: <userDir>/delve/run.properties and run.dck.
 *
 * Closing mid-duel means the fight hasn't been recorded, so you'll face it again
 * with the life you had going in.
 */
public final class DelveRunSave {
    private DelveRunSave() {}

    private static File dir() {
        return DelveSaves.dir();
    }

    private static File propsFile() { return new File(dir(), "run.properties"); }

    private static File deckFile() { return new File(dir(), "run.dck"); }

    public static boolean exists() {
        return propsFile().exists() && deckFile().exists();
    }

    public static void save(DelveRun run) {
        if (run == null) return;
        try {
            Properties p = new Properties();
            p.setProperty("day", Integer.toString(run.day.dayNumber));
            p.setProperty("tier", Integer.toString(run.day.tier));
            p.setProperty("seed", Long.toString(run.seed));
            p.setProperty("life", Integer.toString(run.life));
            p.setProperty("gold", Integer.toString(run.gold));
            p.setProperty("step", Integer.toString(run.step));
            p.setProperty("fightsWon", Integer.toString(run.fightsWon));
            p.setProperty("nextFoeLife", Integer.toString(run.nextFoeLife));
            StringBuilder rel = new StringBuilder();
            for (DelveRelic r : run.relics) rel.append(rel.length() > 0 ? "," : "").append(r.name());
            p.setProperty("relics", rel.toString());
            p.setProperty("startRelicChosen", String.valueOf(run.startRelicChosen));
            p.setProperty("deckName", run.deck.getName());
            StringBuilder ch = new StringBuilder();
            for (int c : run.chosen) {
                if (ch.length() > 0) ch.append(',');
                ch.append(c);
            }
            p.setProperty("chosen", ch.toString());
            for (int s = 0; s < run.layers.size(); s++) {
                List<DelveRun.Node> layer = run.layers.get(s);
                for (int k = 0; k < layer.size(); k++) {
                    List<PaperCard> stock = layer.get(k).stock;
                    if (stock != null) p.setProperty("stock." + s + "." + k, cards(stock));
                }
            }
            try (FileOutputStream out = new FileOutputStream(propsFile())) {
                p.store(out, "Delve run in progress");
            }
            DeckSerializer.writeDeck(run.deck, deckFile());
        } catch (Exception e) {
            e.printStackTrace(); // saving is best-effort; never break the run over it
        }
    }

    /** Restore the saved run as DelveRun.current(); returns null if there is none or it can't be read. */
    public static DelveRun load() {
        if (!exists()) return null;
        try {
            Properties p = new Properties();
            try (FileInputStream in = new FileInputStream(propsFile())) {
                p.load(in);
            }
            Deck deck = DeckSerializer.fromFile(deckFile());
            if (deck == null) return null;
            deck.setName(p.getProperty("deckName", deck.getName()));
            DelveDay day = DelveDay.forTier(Integer.parseInt(p.getProperty("day", "1")),
                    Integer.parseInt(p.getProperty("tier", "0")));
            DelveRun run = DelveRun.restore(day, deck, Long.parseLong(p.getProperty("seed")));
            run.life = Integer.parseInt(p.getProperty("life", "20"));
            run.gold = Integer.parseInt(p.getProperty("gold", "0"));
            run.step = Integer.parseInt(p.getProperty("step", "0"));
            run.fightsWon = Integer.parseInt(p.getProperty("fightsWon", "0"));
            run.nextFoeLife = Integer.parseInt(p.getProperty("nextFoeLife", "0"));
            // runs saved before relics existed skip the starting choice
            run.startRelicChosen = Boolean.parseBoolean(p.getProperty("startRelicChosen", p.getProperty("relics") == null ? "true" : "false"));
            for (String r : p.getProperty("relics", "").split(",")) {
                DelveRelic rel = DelveRelic.byName(r.trim());
                if (rel != null && !run.relics.contains(rel)) run.relics.add(rel);
            }
            String ch = p.getProperty("chosen", "");
            if (!ch.isEmpty())
                for (String c : ch.split(",")) run.chosen.add(Integer.parseInt(c.trim()));
            for (String key : p.stringPropertyNames()) {
                if (!key.startsWith("stock.")) continue;
                String[] parts = key.split("\\.");
                int s = Integer.parseInt(parts[1]), k = Integer.parseInt(parts[2]);
                if (s < run.layers.size() && k < run.layers.get(s).size())
                    run.layers.get(s).get(k).stock = parseCards(p.getProperty(key));
            }
            return run;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void delete() {
        propsFile().delete();
        deckFile().delete();
    }

    private static String cards(List<PaperCard> list) {
        StringBuilder sb = new StringBuilder();
        for (PaperCard pc : list) {
            if (sb.length() > 0) sb.append(';');
            sb.append(pc.getName()).append('|').append(pc.getEdition());
        }
        return sb.toString();
    }

    private static List<PaperCard> parseCards(String s) {
        List<PaperCard> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        for (String entry : s.split(";")) {
            String[] nameSet = entry.split("\\|");
            PaperCard pc = FModel.getMagicDb().getCommonCards().getCard(nameSet[0], nameSet.length > 1 ? nameSet[1] : null);
            if (pc != null) out.add(pc);
        }
        return out;
    }
}
