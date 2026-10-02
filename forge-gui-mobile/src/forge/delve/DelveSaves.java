package forge.delve;

import forge.localinstance.properties.ForgeProfileProperties;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;

/**
 * Save slots. Each save is a folder under <userDir>/delve/saves/ holding that
 * save's profile, collection, decks and run in progress. The slot in use is
 * remembered in <userDir>/delve/current.txt.
 *
 * A Delve folder from before save slots existed is moved into "Save 1".
 */
public final class DelveSaves {
    private DelveSaves() {}

    private static String current;

    private static File base() {
        return new File(ForgeProfileProperties.getUserDir(), "delve");
    }

    private static File savesDir() {
        File d = new File(base(), "saves");
        d.mkdirs();
        return d;
    }

    /** The folder of the save in use (created if needed). */
    public static File dir() {
        if (current == null) {
            migrateLegacy();
            current = readCurrent();
            if (current == null || !new File(savesDir(), current).isDirectory()) {
                List<String> all = list();
                current = all.isEmpty() ? null : all.get(0);
            }
            if (current == null) current = create();
        }
        File d = new File(savesDir(), current);
        d.mkdirs();
        return d;
    }

    public static String currentName() {
        dir();
        return current;
    }

    /** Save names, oldest first. */
    public static List<String> list() {
        migrateLegacy();
        File[] dirs = savesDir().listFiles(File::isDirectory);
        List<String> out = new ArrayList<>();
        if (dirs == null) return out;
        Arrays.sort(dirs, Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        Arrays.sort(dirs, Comparator.comparing(File::getName, DelveSaves::naturalOrder));
        for (File d : dirs) out.add(d.getName());
        return out;
    }

    private static int naturalOrder(String a, String b) {
        String na = a.replaceAll("\\D", ""), nb = b.replaceAll("\\D", "");
        if (!na.isEmpty() && !nb.isEmpty() && a.replaceAll("\\d", "").equals(b.replaceAll("\\d", "")))
            return Long.compare(Long.parseLong(na), Long.parseLong(nb));
        return a.compareToIgnoreCase(b);
    }

    /** Make a new empty save ("Save N") and return its name; does not switch to it. */
    public static String create() {
        int n = 1;
        while (new File(savesDir(), "Save " + n).exists()) n++;
        String name = "Save " + n;
        new File(savesDir(), name).mkdirs();
        return name;
    }

    /** Switch to a save: everything Delve has loaded is reloaded from it. */
    public static void load(String name) {
        current = name;
        writeCurrent(name);
        DelveRun.forget();
        DelveProfile.reload();
        DelveDeckEditScene.reload();
        DelveCastleScene.reset();
    }

    /** Delete a save. If it was the one in use, switch to another (or a fresh one). */
    public static void delete(String name) {
        deleteTree(new File(savesDir(), name));
        if (name.equals(current)) {
            List<String> all = list();
            load(all.isEmpty() ? create() : all.get(0));
        }
    }

    /** One-line summary for the save menu: "Day 6 - Tier 2: Mirrodin - 640 gold". */
    public static String summary(String name) {
        Properties p = new Properties();
        File f = new File(new File(savesDir(), name), "profile.properties");
        if (!f.exists()) return "New save";
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            return "?";
        }
        int tier = Integer.parseInt(p.getProperty("tier", "0"));
        List<forge.card.CardEdition> tiers = DelveDay.tiers(!"modern".equals(p.getProperty("era", "all")));
        String tierName = tier < tiers.size() ? tiers.get(tier).getName() : "?";
        boolean run = new File(new File(savesDir(), name), "run.properties").exists();
        return "Day " + p.getProperty("day", "1") + "  -  Tier " + (tier + 1) + ": " + tierName
                + "  -  " + p.getProperty("gold", "0") + " gold" + (run ? "  -  run in progress" : "");
    }

    private static String readCurrent() {
        File f = new File(base(), "current.txt");
        if (!f.exists()) return null;
        try {
            String s = new String(Files.readAllBytes(f.toPath())).trim();
            return s.isEmpty() ? null : s;
        } catch (Exception e) {
            return null;
        }
    }

    private static void writeCurrent(String name) {
        try {
            base().mkdirs();
            Files.write(new File(base(), "current.txt").toPath(), name.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Move a pre-slots Delve folder (profile at delve/profile.properties) into "Save 1". */
    private static void migrateLegacy() {
        File b = base();
        if (!new File(b, "profile.properties").exists() && !new File(b, "collection.dck").exists()) return;
        File target = new File(savesDir(), "Save 1");
        if (target.exists()) target = new File(savesDir(), create());
        target.mkdirs();
        for (String n : new String[]{"profile.properties", "collection.dck", "locked", "decks", "commander",
                "run.properties", "run.dck"}) {
            File from = new File(b, n);
            if (from.exists() && !from.renameTo(new File(target, n)))
                System.err.println("Delve: couldn't move " + from + " into " + target);
        }
        writeCurrent(target.getName());
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File k : kids) deleteTree(k);
        f.delete();
    }
}
