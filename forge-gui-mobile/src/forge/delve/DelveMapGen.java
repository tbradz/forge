package forge.delve;

import forge.adventure.data.EnemyData;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One dungeon size with a random length: 10-13 steps (generators 2+; generator 1 was 6-9),
 * each offering 1-3 rooms.
 *
 *   first:        Fight
 *   middle:       a Fight plus 1-2 of Event / Merchant / Rest (gen 3: so 2 or 3 rooms, and no kind
 *                 comes up two steps running; events never do, a merchant or rest only to fill the pre-boss step)
 *   1/3 and 2/3:  Elite | Rest or Event   (gen 1: one at halfway, a second in 8-9 step dungeons)
 *   before boss:  two of Rest / Merchant / Event
 *   last:         Boss
 *
 * How rooms connect is in {@link DelveRun#connected}. Rooms within a step are shuffled;
 * everything comes from the run's seed and generator version, so a saved run rebuilds the same map.
 */
final class DelveMapGen {
    private DelveMapGen() {}

    /**
     * Generator for new runs. 2 = longer floors (10-13 steps) with the gold per fight scaled down to match.
     * 3 = open paths (every room reaches 2-3 rooms ahead, no locked lanes) and better-spread room types.
     * 4 = set-themed enemies (DelveDay.themedWeak/Elite/Boss).
     * 5 = set-themed events too (DelveEvents.themed).
     * 6 = floor length from Options (DelveModes.Length: 7-9 / 10-13 / 14-17), saved with the run.
     * 7 = Forge rooms (reforge a card into a stronger one of its colour) among the side rooms.
     * 8 = the web (Tyler, 2026-10-12: "review how slay the spire does dungeon layout"): paths traced across a grid of
     *     lanes that branch and merge but never cross, room types placed by Slay the Spire's rules, a treasure step
     *     midway, campfires before the boss, one champion elite, unknown (?) rooms that may be fights; 15 steps.
     */
    static final int CURRENT_GEN = 8;

    /** Rolls added after release (special rooms, elite perks) use their own generator so a saved
     *  run's seed still rebuilds the same rooms and enemies it had before. */
    private static java.util.Random extra;

    static void build(DelveRun run) {
        usedEvents.clear();
        usedOriginal.clear();
        extra = new java.util.Random(run.seed * 31 + 7);
        DelveDay day = run.day;
        // gen 4+: the dungeon's enemies fit its set (DelveDay.themeEnemies); older runs keep the full rosters
        boolean themed = run.gen >= 4;
        List<EnemyData> weakPool = themed ? day.themedWeak() : day.weakEnemies;
        List<EnemyData> elitePool = themed ? day.themedElite() : day.eliteEnemies;
        List<EnemyData> bossPool = themed ? day.themedBoss() : day.bossEnemies;
        Picker weak = new Picker(weakPool, run);
        Picker elite = new Picker(elitePool.isEmpty() ? weakPool : elitePool, run);
        Picker boss = new Picker(bossPool.isEmpty() ? elitePool : bossPool, run);

        int steps, eliteAt, secondEliteAt;    // 0-based layer indexes
        if (run.gen >= 6) {                   // the run's chosen floor length (Options)
            steps = run.length.min + run.rng.nextInt(run.length.spread);
            eliteAt = steps / 3;
            secondEliteAt = 2 * steps / 3;
        } else if (run.gen >= 2) {
            steps = 10 + run.rng.nextInt(4);  // 10..13
            eliteAt = steps / 3;
            secondEliteAt = 2 * steps / 3;
        } else {
            steps = 6 + run.rng.nextInt(4);   // 6..9
            eliteAt = steps / 2;
            secondEliteAt = steps >= 8 ? steps - 3 : -1;
        }
        if (run.gen >= 8) {
            buildWeb(run, run.length.webSteps(), weak, elite, boss);
            return;
        }
        add(run, fight(weak, NodeType.FIGHT));
        if (run.gen >= 3) {
            spreadRooms(run, steps, eliteAt, secondEliteAt, weak, elite);
        } else {
            classicRooms(run, steps, eliteAt, secondEliteAt, weak, elite);
        }
        Node bossRoom = fight(boss, NodeType.BOSS);
        bossRoom.perk = DelvePerk.random(run.rng);
        bossRoom.perk2 = DelvePerk.random(extra, true);
        add(run, bossRoom);
        scaleLife(run);
    }

    // ---- generator 8: the web ------------------------------------------------------------------------------------

    static final int LANES = 5, PATHS = 5;

    /**
     * Slay the Spire's map, on its side: {@link #PATHS} paths start at the entrance and walk the steps, each move
     * staying in its lane or stepping one lane up or down, never crossing a path already laid. Where paths meet they
     * share a room, so the map branches and merges; lanes no path touched stay empty. Then each room gets a type:
     * fights first, a treasure step a little past halfway, campfires before the boss; elites and campfires not in the
     * first third; no elite, campfire or merchant twice in a row on a path; rooms forking from one room differ.
     */
    private static void buildWeb(DelveRun run, int steps, Picker weak, Picker elite, Picker boss) {
        java.util.Random r = run.rng;
        boolean[][] used = new boolean[steps][LANES];
        java.util.Set<Long> edges = new java.util.LinkedHashSet<>();
        int firstStart = -1;
        for (int p = 0; p < PATHS; p++) {
            int lane = r.nextInt(LANES);
            if (p == 1) while (lane == firstStart) lane = r.nextInt(LANES); // at least two ways in
            if (p == 0) firstStart = lane;
            used[0][lane] = true;
            for (int s = 0; s < steps - 1; s++) {
                List<Integer> opts = new ArrayList<>();
                for (int d = -1; d <= 1; d++) {
                    int b = lane + d;
                    if (b >= 0 && b < LANES && !crosses(edges, s, lane, b)) opts.add(b);
                }
                int next = opts.isEmpty() ? lane : opts.get(r.nextInt(opts.size())); // straight on never crosses
                edges.add(edge(s, lane, next));
                used[s + 1][next] = true;
                lane = next;
            }
        }
        // room types, step by step (lanes top to bottom), by the rules
        int treasureAt = Math.round(steps * 0.53f), firstThird = steps / 3;
        NodeType[][] type = new NodeType[steps][LANES];
        for (int s = 0; s < steps; s++)
            for (int lane = 0; lane < LANES; lane++) {
                if (!used[s][lane]) continue;
                if (s == 0) type[s][lane] = NodeType.FIGHT;
                else if (s == steps - 1) type[s][lane] = NodeType.REST;
                else if (s == treasureAt) type[s][lane] = NodeType.TREASURE;
                else type[s][lane] = pickType(r, type, edges, s, lane, steps, firstThird);
            }
        // make sure a floor has enough of the rarer rooms to plan around: a few elites, a couple of merchants
        ensure(r, type, edges, used, steps, NodeType.ELITE, Math.max(2, steps / 5), firstThird, treasureAt);
        ensure(r, type, edges, used, steps, NodeType.MERCHANT, 2, 1, treasureAt);
        // the rooms themselves, in lane order; links from lanes to indexes
        List<int[]> champions = new ArrayList<>();
        for (int s = 0; s < steps; s++) {
            List<Node> layer = new ArrayList<>();
            for (int lane = 0; lane < LANES; lane++) {
                if (!used[s][lane]) continue;
                Node n;
                switch (type[s][lane]) {
                    case ELITE:
                        n = fight(elite, NodeType.ELITE);
                        n.perk = DelvePerk.random(extra, true);
                        if (s >= steps / 2) champions.add(new int[]{s, layer.size()});
                        break;
                    case EVENT:
                        n = extra.nextInt(100) < 8 && !hasRoom(run, NodeType.SHRINE) ? new Node(NodeType.SHRINE, null, 0, null) : event(run);
                        if (n.type == NodeType.EVENT) n.ambush = weak.next();
                        break;
                    case MERCHANT: n = merchant(); break;
                    case REST: n = rest(); break;
                    case TREASURE: n = new Node(NodeType.TREASURE, null, 0, null); break;
                    default: n = fight(weak, NodeType.FIGHT);
                }
                n.lane = lane;
                layer.add(n);
            }
            run.layers.add(layer);
        }
        for (long e : edges) {
            int s = (int) (e / 100), a = (int) (e / 10 % 10), b = (int) (e % 10);
            Node from = byLane(run.layers.get(s), a);
            int to = run.layers.get(s + 1).indexOf(byLane(run.layers.get(s + 1), b));
            if (from != null && to >= 0 && !from.next.contains(to)) from.next.add(to);
        }
        for (Node n : run.layers.get(steps - 1)) n.next.add(0); // every campfire leads to the boss
        if (!champions.isEmpty()) { // the floor's champion: a late elite, tougher, guarding a second relic
            int[] c = champions.get(extra.nextInt(champions.size()));
            Node ch = run.layers.get(c[0]).get(c[1]);
            ch.champion = true;
            ch.perk2 = DelvePerk.random(extra, true);
        }
        Node bossRoom = fight(boss, NodeType.BOSS);
        bossRoom.perk = DelvePerk.random(run.rng);
        bossRoom.perk2 = DelvePerk.random(extra, true);
        bossRoom.lane = LANES / 2;
        run.layers.add(new ArrayList<>(List.of(bossRoom)));
        scaleLife(run);
    }

    private static long edge(int s, int a, int b) {
        return s * 100L + a * 10L + b;
    }

    /** Would a move from lane a to lane b after step s cross a path already laid there? */
    private static boolean crosses(java.util.Set<Long> edges, int s, int a, int b) {
        for (long e : edges) {
            if (e / 100 != s) continue;
            int c = (int) (e / 10 % 10), d = (int) (e % 10);
            if (a < c && b > d || a > c && b < d) return true;
        }
        return false;
    }

    private static Node byLane(List<Node> layer, int lane) {
        for (Node n : layer) if (n.lane == lane) return n;
        return null;
    }

    /** The rooms (lanes) leading into one, and the rooms out of one. */
    private static List<Integer> parents(java.util.Set<Long> edges, int s, int lane) {
        List<Integer> out = new ArrayList<>();
        for (long e : edges) if (e / 100 == s - 1 && e % 10 == lane) out.add((int) (e / 10 % 10));
        return out;
    }

    private static List<Integer> children(java.util.Set<Long> edges, int s, int lane) {
        List<Integer> out = new ArrayList<>();
        for (long e : edges) if (e / 100 == s && e / 10 % 10 == lane) out.add((int) (e % 10));
        return out;
    }

    /** May the room at (s, lane) be of type t? No elite, campfire or merchant right after another (either way along a
     *  path); a room forking from the same room as another isn't the same type (for the rarer types). */
    private static boolean allowed(NodeType[][] type, java.util.Set<Long> edges, int s, int lane, NodeType t, int steps, int firstThird) {
        if ((t == NodeType.ELITE || t == NodeType.REST) && s < firstThird) return false;
        if (t == NodeType.REST && s >= steps - 2) return false; // campfires wait for the step before the boss
        boolean rare = t == NodeType.ELITE || t == NodeType.REST || t == NodeType.MERCHANT;
        if (rare) {
            for (int p : parents(edges, s, lane)) {
                if (type[s - 1][p] == t) return false;
                for (int sib : children(edges, s - 1, p))
                    if (sib != lane && type[s][sib] == t) return false;
            }
            if (s + 1 < steps)
                for (int c : children(edges, s, lane)) if (type[s + 1][c] == t) return false;
        }
        return true;
    }

    private static final NodeType[] WEB_TYPES = {NodeType.FIGHT, NodeType.EVENT, NodeType.REST, NodeType.ELITE, NodeType.MERCHANT};
    private static final int[] WEB_WEIGHTS = {50, 22, 12, 9, 7};

    private static NodeType pickType(java.util.Random r, NodeType[][] type, java.util.Set<Long> edges, int s, int lane, int steps, int firstThird) {
        int total = 0;
        int[] w = new int[WEB_TYPES.length];
        for (int i = 0; i < WEB_TYPES.length; i++) {
            w[i] = allowed(type, edges, s, lane, WEB_TYPES[i], steps, firstThird) ? WEB_WEIGHTS[i] : 0;
            total += w[i];
        }
        int roll = r.nextInt(Math.max(1, total));
        for (int i = 0; i < WEB_TYPES.length; i++) {
            if (roll < w[i]) return WEB_TYPES[i];
            roll -= w[i];
        }
        return NodeType.FIGHT;
    }

    /** Turn fights into rooms of type t (where the rules allow) until there are at least {@code want}. */
    private static void ensure(java.util.Random r, NodeType[][] type, java.util.Set<Long> edges, boolean[][] used, int steps,
                               NodeType t, int want, int from, int skip) {
        int have = 0;
        List<int[]> spots = new ArrayList<>();
        for (int s = 1; s < steps - 1; s++)
            for (int lane = 0; lane < LANES; lane++) {
                if (!used[s][lane]) continue;
                if (type[s][lane] == t) have++;
                else if (type[s][lane] == NodeType.FIGHT && s >= from && s != skip) spots.add(new int[]{s, lane});
            }
        Collections.shuffle(spots, r);
        for (int[] at : spots) {
            if (have >= want) break;
            if (!allowed(type, edges, at[0], at[1], t, steps, steps / 3)) continue;
            type[at[0]][at[1]] = t;
            have++;
        }
    }

    /** A fight's starting life by how deep it is (the first step's are gentle). */
    static int fightLife(DelveRun run, int s) {
        int steps = run.layers.size();
        return s == 0 && run.gen >= 3 ? 10 : s < steps / 3 ? 12 : s < 2 * steps / 3 ? 15 : 17;
    }

    /** Generator 3's middle and pre-boss steps: varied widths, no event two steps running. */
    private static void spreadRooms(DelveRun run, int steps, int eliteAt, int secondEliteAt, Picker weak, Picker elite) {
        java.util.Set<NodeType> prev = java.util.EnumSet.noneOf(NodeType.class);
        for (int i = 1; i < steps - 2; i++) {
            List<Node> rooms = new ArrayList<>();
            if (i == eliteAt || i == secondEliteAt) {
                Node eliteRoom = fight(elite, NodeType.ELITE);
                eliteRoom.perk = DelvePerk.random(extra, true);
                rooms.add(eliteRoom);
                rooms.addAll(sideRooms(run, prev, 1, 1, false, NodeType.REST, NodeType.EVENT));
            } else {
                rooms.add(fight(weak, NodeType.FIGHT));
                int count = run.rng.nextInt(10) < 6 ? 2 : 1; // 3-room steps a bit more often than 2
                NodeType[] kinds = run.gen >= 7 // gen 7+: Forge rooms (reforge a card) join the mix
                        ? new NodeType[]{NodeType.EVENT, NodeType.MERCHANT, NodeType.REST, NodeType.FORGE}
                        : new NodeType[]{NodeType.EVENT, NodeType.MERCHANT, NodeType.REST};
                rooms.addAll(sideRooms(run, prev, count, 1, true, kinds));
            }
            prev = kinds(rooms);
            add(run, rooms.toArray(new Node[0]));
        }
        // the step before the boss always offers a choice
        add(run, sideRooms(run, prev, 2, 2, false, NodeType.REST, NodeType.MERCHANT, NodeType.EVENT).toArray(new Node[0]));
    }

    /**
     * Up to {@code count} different non-fight rooms of kinds that weren't in the previous step, so
     * no kind comes up two steps running. If fewer than {@code least} kinds are new, a merchant or
     * rest may repeat to make up the number; an event-like room (event, Treasure, Shrine) never does.
     * (Tuned with a simulation of 5000 maps: every step has 2-3 rooms, every room reaches 2-3 ahead.)
     */
    private static List<Node> sideRooms(DelveRun run, java.util.Set<NodeType> prev, int count, int least,
                                        boolean specials, NodeType... options) {
        List<NodeType> pool = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        for (NodeType t : options) {
            if (prev.contains(t)) continue;
            pool.add(t);
            weights.add(t == NodeType.EVENT ? 4 : t == NodeType.FORGE ? 2 : 3);
        }
        List<Node> out = new ArrayList<>();
        while (out.size() < count && !pool.isEmpty()) {
            int total = 0;
            for (int w : weights) total += w;
            int roll = run.rng.nextInt(total), at = 0;
            while (roll >= weights.get(at)) roll -= weights.get(at++);
            NodeType t = pool.remove(at);
            weights.remove(at);
            out.add(t == NodeType.EVENT ? (specials ? eventOrSpecial(run) : event(run))
                    : t == NodeType.MERCHANT ? merchant() : t == NodeType.FORGE ? new Node(NodeType.FORGE, null, 0, null) : rest());
        }
        while (out.size() < least) { // not enough new kinds: repeat a merchant or rest that isn't here yet
            List<NodeType> fill = new ArrayList<>();
            for (NodeType t : options)
                if ((t == NodeType.MERCHANT || t == NodeType.REST) && !kinds(out).contains(t)) fill.add(t);
            if (fill.isEmpty()) break;
            out.add(fill.get(run.rng.nextInt(fill.size())) == NodeType.MERCHANT ? merchant() : rest());
        }
        return out;
    }

    /** Room kinds in a step, with Treasure and Shrine counted as events (they're surprises too). */
    private static java.util.Set<NodeType> kinds(List<Node> rooms) {
        java.util.Set<NodeType> out = java.util.EnumSet.noneOf(NodeType.class);
        for (Node n : rooms)
            out.add(n.type == NodeType.TREASURE || n.type == NodeType.SHRINE ? NodeType.EVENT : n.type);
        return out;
    }

    /** Generators 1-2: the original middle and pre-boss steps (kept so saved runs rebuild the same). */
    private static void classicRooms(DelveRun run, int steps, int eliteAt, int secondEliteAt, Picker weak, Picker elite) {
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
                    // gen 3+: the very first fight is a gentle 10 (Tyler)
                    case FIGHT: n.enemyLife = fightLife(run, s); break;
                    case ELITE: n.enemyLife = n.champion ? 28 : 22; break;
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
        if (run.gen >= 5) { // gen 5+: the set's own events, listed twice so they turn up about a fifth of the time
            List<DelveEvents.Event> themed = DelveEvents.themed(run.day);
            all.addAll(themed);
            all.addAll(themed);
        }
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
