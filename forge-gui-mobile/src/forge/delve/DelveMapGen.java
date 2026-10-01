package forge.delve;

import forge.adventure.data.EnemyData;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One dungeon size with a random length: 6-9 steps, each offering 1-3 rooms.
 *
 *   first:        Fight
 *   middle:       mixes of Fight / Event / Merchant / Rest
 *   halfway:      Elite | Rest   (a second elite in 8-9 step dungeons)
 *   before boss:  two of Rest / Merchant / Event
 *   last:         Boss
 *
 * Rooms within a step are shuffled; everything comes from the run's seed, so a
 * saved run rebuilds the same map.
 */
final class DelveMapGen {
    private DelveMapGen() {}

    static void build(DelveRun run) {
        usedEvents.clear();
        DelveDay day = run.day;
        Picker weak = new Picker(day.weakEnemies, run);
        Picker elite = new Picker(day.eliteEnemies.isEmpty() ? day.weakEnemies : day.eliteEnemies, run);
        Picker boss = new Picker(day.bossEnemies.isEmpty() ? day.eliteEnemies : day.bossEnemies, run);

        int steps = 6 + run.rng.nextInt(4); // 6..9
        int eliteAt = steps / 2;              // 0-based layer index
        int secondEliteAt = steps >= 8 ? steps - 3 : -1;
        add(run, fight(weak, NodeType.FIGHT));
        for (int i = 1; i < steps - 2; i++) {
            if (i == eliteAt || i == secondEliteAt) {
                add(run, fight(elite, NodeType.ELITE), run.rng.nextBoolean() ? rest() : event(run));
                continue;
            }
            switch (run.rng.nextInt(3)) {
                case 0: add(run, fight(weak, NodeType.FIGHT), event(run)); break;
                case 1: add(run, fight(weak, NodeType.FIGHT), event(run), merchant()); break;
                default: add(run, fight(weak, NodeType.FIGHT), merchant(), rest());
            }
        }
        List<Node> late = new ArrayList<>(List.of(rest(), merchant(), event(run)));
        Collections.shuffle(late, run.rng);
        add(run, late.get(0), late.get(1));
        Node bossRoom = fight(boss, NodeType.BOSS);
        bossRoom.perk = DelvePerk.random(run.rng);
        add(run, bossRoom);
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
            case ELITE: life = 20; break;
            case BOSS: life = 25; break;
            default: life = 15;
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
