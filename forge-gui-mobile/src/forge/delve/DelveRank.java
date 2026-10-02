package forge.delve;

import forge.item.PaperCard;
import forge.localinstance.properties.ForgeConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Card quality from Forge's draft pick rankings (res/draft/rankings/<set>.rnk:
 * "#rank|Name|Rarity|SET", best first). A card's score is 1.0 for the set's best pick
 * down to 0.0 for its worst. Sets without a ranking file fall back to rarity.
 *
 * Delve uses scores to keep power under control: modest starting decks, rewards
 * that are always upgrades and get better with depth, and enemies that ramp.
 */
public final class DelveRank {
    private DelveRank() {}

    private static final Map<String, Map<String, Double>> bySet = new HashMap<>();

    private static Map<String, Double> load(String setCode) {
        String key = setCode.toUpperCase();
        Map<String, Double> cached = bySet.get(key);
        if (cached != null) return cached;
        Map<String, Double> scores = new HashMap<>();
        File f = new File(ForgeConstants.DRAFT_RANKINGS_FOLDER, setCode.toLowerCase() + ".rnk");
        if (f.exists()) {
            Map<String, Integer> ranks = new HashMap<>();
            int max = 1;
            try (BufferedReader in = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = in.readLine()) != null) {
                    if (!line.startsWith("#")) continue;
                    String[] parts = line.substring(1).split("\\|");
                    if (parts.length < 2) continue;
                    try {
                        int rank = Integer.parseInt(parts[0].trim());
                        String name = parts[1].trim();
                        if (!ranks.containsKey(name)) ranks.put(name, rank);
                        max = Math.max(max, rank);
                    } catch (NumberFormatException ignored) { }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            for (Map.Entry<String, Integer> e : ranks.entrySet())
                scores.put(e.getKey(), max <= 1 ? 0.5 : 1.0 - (e.getValue() - 1) / (double) (max - 1));
        }
        bySet.put(key, scores);
        return scores;
    }

    /** 0..1, higher is better. Unranked cards get a rarity-based guess. */
    public static double score(PaperCard pc) {
        if (pc == null) return 0;
        Double s = load(pc.getEdition()).get(pc.getName());
        if (s == null) s = load(pc.getEdition()).get(pc.getName().split(" // ")[0]);
        if (s != null) return s;
        switch (pc.getRarity()) {
            case MythicRare: return 0.85;
            case Rare: return 0.7;
            case Uncommon: return 0.5;
            default: return 0.3;
        }
    }

    /** Whether the set has real rankings (otherwise scores are rarity guesses). */
    public static boolean ranked(String setCode) {
        return !load(setCode).isEmpty();
    }
}
