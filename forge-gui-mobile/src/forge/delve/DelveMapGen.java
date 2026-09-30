package forge.delve;

import forge.adventure.data.EnemyData;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 1 map: a short fixed shape with branching choices.
 *
 *   step 1: Fight
 *   step 2: Fight | Fight
 *   step 3: Elite | Rest
 *   step 4: Fight | Rest
 *   step 5: Boss
 *
 * Later phases replace this with the seeded, size-dependent generator.
 */
final class DelveMapGen {
    private DelveMapGen() {}

    static void build(DelveRun run) {
        DelveDay day = run.day;
        Picker weak = new Picker(day.weakEnemies, run);
        Picker elite = new Picker(day.eliteEnemies.isEmpty() ? day.weakEnemies : day.eliteEnemies, run);
        Picker boss = new Picker(day.bossEnemies.isEmpty() ? day.eliteEnemies : day.bossEnemies, run);

        run.layers.add(List.of(fight(weak, NodeType.FIGHT)));
        run.layers.add(List.of(fight(weak, NodeType.FIGHT), fight(weak, NodeType.FIGHT)));
        run.layers.add(List.of(fight(elite, NodeType.ELITE), rest()));
        run.layers.add(List.of(fight(weak, NodeType.FIGHT), rest()));
        run.layers.add(List.of(fight(boss, NodeType.BOSS)));
    }

    private static Node fight(Picker p, NodeType type) {
        EnemyData e = p.next();
        int life;
        switch (type) {
            case ELITE: life = clamp(e.life, 16, 20); break;
            case BOSS: life = 25; break;
            default: life = clamp(e.life, 8, 14);
        }
        return new Node(type, e, life);
    }

    private static Node rest() {
        return new Node(NodeType.REST, null, 0);
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
            java.util.Collections.shuffle(pool, run.rng);
        }
        EnemyData next() {
            return pool.get(i++ % pool.size());
        }
    }
}
