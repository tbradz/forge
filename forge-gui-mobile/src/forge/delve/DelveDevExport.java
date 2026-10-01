package forge.delve;

import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.deck.io.DeckSerializer;
import forge.localinstance.properties.ForgePreferences;
import forge.localinstance.properties.ForgeProfileProperties;
import forge.model.FModel;

import java.io.File;
import java.util.List;

/**
 * Developer tool (only with Forge's dev mode on): writes sample starter and enemy
 * decks for several days to <userDir>/delve/export/ so balance can be measured with
 * Forge's AI-vs-AI simulator. Runs once per app launch.
 */
final class DelveDevExport {
    private static boolean done;

    private DelveDevExport() {}

    static void maybeExport() {
        if (done || !FModel.getPreferences().getPrefBoolean(ForgePreferences.FPref.DEV_MODE_ENABLED)) return;
        done = true;
        File root = new File(ForgeProfileProperties.getUserDir(), "delve" + File.separator + "export");
        for (int tier : new int[]{0, 10, 20, 40}) {
            DelveDay day = DelveDay.forTier(1, tier);
            File dir = new File(root, "tier" + (tier + 1));
            dir.mkdirs();
            dump(day, dir, day.weakEnemies, DelveDay.Tier.FIGHT, 4);
            dump(day, dir, day.eliteEnemies, DelveDay.Tier.ELITE, 3);
            dump(day, dir, day.bossEnemies, DelveDay.Tier.BOSS, 3);
        }
        DelveDay.today(); // restore the cached current day
    }

    private static void dump(DelveDay day, File dir, List<EnemyData> enemies, DelveDay.Tier tier, int n) {
        for (int i = 0; i < n && i < enemies.size(); i++) {
            EnemyData e = enemies.get(i);
            String suffix = tier == DelveDay.Tier.BOSS ? "_" + day.bossTheme(e).name.replaceAll("[^A-Za-z]", "") : "";
            write(day.enemyDeck(e, tier), new File(dir, tier.name().toLowerCase() + "_" + i + suffix + ".dck"));
        }
    }

    private static void write(Deck d, File f) {
        try {
            DeckSerializer.writeDeck(d, f);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
