package forge.delve;

import forge.adventure.data.EnemyData;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Map for a Standard-size dungeon: seven steps, each offering 1-3 rooms.
 *
 *   1: Fight
 *   2: Fight | Event
 *   3: Fight | Event | Merchant
 *   4: Elite | Rest
 *   5: Fight | Event | Merchant
 *   6: two of Rest / Merchant / Event
 *   7: Boss
 *
 * Rooms within a step are shuffled. Later phases add dungeon sizes and a
 * seeded branching layout.
 */
final class DelveMapGen {
    private DelveMapGen() {}

    static void build(DelveRun run) {
        usedEvents.clear();
        DelveDay day = run.day;
        Picker weak = new Picker(day.weakEnemies, run);
        Picker elite = new Picker(day.eliteEnemies.isEmpty() ? day.weakEnemies : day.eliteEnemies, run);
        Picker boss = new Picker(day.bossEnemies.isEmpty() ? day.eliteEnemies : day.bossEnemies, run);

        add(run, fight(weak, NodeType.FIGHT));
        add(run, fight(weak, NodeType.FIGHT), event(run));
        add(run, fight(weak, NodeType.FIGHT), event(run), merchant());
        add(run, fight(elite, NodeType.ELITE), rest());
        add(run, fight(weak, NodeType.FIGHT), event(run), merchant());
        List<Node> late = new ArrayList<>(List.of(rest(), merchant(), event(run)));
        Collections.shuffle(late, run.rng);
        add(run, late.get(0), late.get(1));
        add(run, fight(boss, NodeType.BOSS));
    }

    private static void add(DelveRun run, Node... nodes) {
        List<Node> layer = new ArrayList<>(List.of(nodes));
        Collections.shuffle(layer, run.rng);
        run.layers.add(layer);
    }

    private static Node fight(Picker p, NodeType type) {
        EnemyData e = p.next();
        int life;
        switch (type) {
            case ELITE: life = clamp(e.life, 16, 20); break;
            case BOSS: life = 25; break;
            default: life = clamp(e.life, 8, 14);
        }
        return new Node(type, e, life, null);
    }

    private static Node rest() {
        return new Node(NodeType.REST, null, 0, null);
    }

    private static Node merchant() {
        return new Node(NodeType.MERCHANT, null, 0, null);
    }

    private static final List<String> usedEvents = new ArrayList<>();

    /** A random event, avoiding repeats within one run. */
    private static Node event(DelveRun run) {
        DelveEvents.Event e = DelveEvents.random(run.rng);
        for (int i = 0; i < 20 && usedEvents.contains(e.title); i++)
            e = DelveEvents.random(run.rng);
        usedEvents.add(e.title);
        return new Node(NodeType.EVENT, null, 0, e);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Draws enemies without repeats (cycles if the roster is short). */
    private static class Picker {
        private final List<EnemyData> pool;
        private int i;
        Picker(List<EnemyData> source, DelveRun run) {
            pool = new ArrayList<>(source);
            Collections.shuffle(pool, run.rng);
        }
        EnemyData next() {
            return pool.get(i++ % pool.size());
        }
    }
}
