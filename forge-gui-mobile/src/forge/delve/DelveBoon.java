package forge.delve;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Starting boons: at the Dungeon Gate you pick one of three small advantages for the run
 * (saved with the run). Kept modest: they smooth a run, they don't win it.
 */
public enum DelveBoon {
    VIGOR("Vigor", "+3 max life for this run."),
    PURSE("Fat Purse", "Start the run with 30 gold."),
    SECOND_CHANCE("Second Chance", "One free Reroll of a card offer this run."),
    SCOUT("Scout's Eye", "Merchants stock one extra card this run."),
    SHARP_START("Sharp Start", "Your first foe starts with 4 less life.");

    public final String title, description;

    DelveBoon(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** Three different boons to choose from. */
    public static List<DelveBoon> offer(Random rng) {
        List<DelveBoon> all = new ArrayList<>(List.of(values()));
        Collections.shuffle(all, rng);
        return all.subList(0, 3);
    }

    /** One-off effects when the run starts (lasting ones are checked where they apply). */
    void applyAtStart(DelveRun run) {
        switch (this) {
            case VIGOR: run.life = run.maxLife(); break;
            case PURSE: run.gainGold(30); break;
            case SECOND_CHANCE: run.freeReroll = true; break;
            case SHARP_START: run.nextFoeLife -= 4; break;
            default: break;
        }
    }

    static DelveBoon parse(String s) {
        try {
            return s == null || s.isEmpty() ? null : valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
