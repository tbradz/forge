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

    /** Rolls added after release (special rooms, elite perks) use their own generator so a saved
     *  run's seed still rebuilds the same rooms and enemies it had before. */
    private static java.util.Random extra;

    static void build(DelveRun run) {
        usedEvents.clear();
        usedOriginal.clear();
        extra = new java.util.Random(run.seed * 31 + 7);
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
                Node eliteRoom = fight(elite, NodeType.ELITE);
                eliteRoom.perk = DelvePerk.random(extra, true);
                add(run, eliteRoom, run.rng.nextBoolean() ? rest() : event(run));
                continue;
            }
            switch (run.rng.nextInt(3)) {
                case 0: add(run, fight(weak, NodeType.FIGHT), eventOrSpecial(run)); break;
                case 1: add(run, fight(weak, NodeType.FIGHT), eventOrSpecial(run), merchant()); break;
                default: add(run, fight(weak, NodeType.FIGHT), merchant(), rest());
            }
        }
        List<Node> late = new ArrayList<>(List.of(rest(), merchant(), event(run)));
        Collections.shuffle(late, run.rng);
        add(run, late.get(0), late.get(1));
        Node bossRoom = fight(boss, NodeType.BOSS);
        bossRoom.perk = DelvePerk.random(run.rng);
        bossRoom.perk2 = DelvePerk.random(extra, true);
        add(run, bossRoom);
        scaleLife(run);
    }

    /**
     * Difficulty curve: fights near the entrance are softer and they toughen toward the
     * boss. Bosses also grow a little with the tier.
     */
    private static void scaleLife(DelveRun run) {
        int steps = run.layers.size();
        for (int s = 0; s < steps; s++)
            for (Node n : run.layers.get(s)) {
                switch (n.type) {
                    case FIGHT: n.enemyLife = s < steps / 3 ? 12 : s < 2 * steps / 3 ? 15 : 17; break;
                    case ELITE: n.enemyLife = 22; break;
                    case BOSS: n.enemyLife = Math.min(40, 30 + run.day.tier / 4); break;
                    default: break;
                }
            }
    }

    /** How deep a step is: 0 = first third, 1 = middle, 2 = last third. */
    static int depth(DelveRun run, int step) {
        int steps = run.layers.size();
        return step < steps / 3 ? 0 : step < 2 * steps / 3 ? 1 : 2;
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

    /** Usually an event; sometimes a rare special room (at most one of each per dungeon). */
    private static Node eventOrSpecial(DelveRun run) {
        Node ev = event(run); // always roll the event, so the main generator's sequence is unchanged
        int roll = extra.nextInt(100);
        if (roll < 12 && !hasRoom(run, NodeType.TREASURE)) return new Node(NodeType.TREASURE, null, 0, null);
        if (roll >= 88 && !hasRoom(run, NodeType.SHRINE)) return new Node(NodeType.SHRINE, null, 0, null);
        return ev;
    }

    private static boolean hasRoom(DelveRun run, NodeType type) {
        for (List<Node> layer : run.layers)
            for (Node n : layer) if (n.type == type) return true;
        return false;
    }

    private static final List<String> usedEvents = new ArrayList<>();

    /** A random event, avoiding repeats within one run. */
    /** How many events existed when save files started storing seeds (the first 10 in DelveEvents). */
    private static final int ORIGINAL_EVENTS = 10;
    private static final List<Integer> usedOriginal = new ArrayList<>();

    private static Node event(DelveRun run) {
        // replay the original pick on the main generator so its sequence (and every later room) is unchanged...
        int o = run.rng.nextInt(ORIGINAL_EVENTS);
        for (int i = 0; i < 20 && usedOriginal.contains(o); i++) o = run.rng.nextInt(ORIGINAL_EVENTS);
        usedOriginal.add(o);
        // ...then pick the actual event from the full list with the extra generator
        List<DelveEvents.Event> all = DelveEvents.all();
        DelveEvents.Event e = all.get(extra.nextInt(all.size()));
        for (int i = 0; i < 40 && usedEvents.contains(e.title); i++) e = all.get(extra.nextInt(all.size()));
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
