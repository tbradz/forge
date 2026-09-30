package forge.delve;

import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * State of one Delve run: the deck being built, life carried between fights,
 * and the dungeon map. Phase 1 keeps runs in memory; only the collection is saved.
 */
public class DelveRun {
    public enum NodeType {
        FIGHT("Fight"), ELITE("Elite"), REST("Rest"), BOSS("Boss");
        public final String label;
        NodeType(String label) { this.label = label; }
    }

    public static class Node {
        public final NodeType type;
        public final EnemyData enemy; // null for REST
        public final int enemyLife;
        Node(NodeType type, EnemyData enemy, int enemyLife) {
            this.type = type;
            this.enemy = enemy;
            this.enemyLife = enemyLife;
        }
        public String title() {
            return enemy == null ? type.label : type.label + ": " + enemy.getName();
        }
    }

    public static final int MAX_LIFE = 20;
    public static final int REST_HEAL = 7;

    private static DelveRun current;

    public final long seed;
    public final Random rng;
    public final DelveDay day;
    public final Deck deck;
    public int life = MAX_LIFE;
    /** layers.get(i) = the choices on step i of the map */
    public final List<List<Node>> layers = new ArrayList<>();
    /** index of the layer the player is about to choose from */
    public int step = 0;
    public Node currentNode;
    public int fightsWon = 0;
    public boolean over = false;
    public boolean cleared = false;
    public final List<PaperCard> picked = new ArrayList<>();

    private DelveRun(DelveDay day, Deck deck, long seed) {
        this.day = day;
        this.deck = deck;
        this.seed = seed;
        this.rng = new Random(seed);
        DelveMapGen.build(this);
    }

    public static DelveRun start(DelveDay day, Deck starter) {
        current = new DelveRun(day, starter, day.seed ^ System.nanoTime());
        return current;
    }

    public static DelveRun current() {
        return current;
    }

    public static void clear() {
        current = null;
    }

    public List<Node> nextChoices() {
        return step < layers.size() ? layers.get(step) : new ArrayList<>();
    }

    public boolean atEnd() {
        return step >= layers.size();
    }

    /** Colors of the non-land cards in the deck (what reward picks lean toward). */
    public forge.card.ColorSet deckColors() {
        int mask = 0;
        for (java.util.Map.Entry<PaperCard, Integer> e : deck.getMain()) {
            if (!e.getKey().getRules().getType().isLand())
                mask |= e.getKey().getRules().getColor().getColor();
        }
        return forge.card.ColorSet.fromMask(mask);
    }

    public int deckSize() {
        return deck.getMain().countAll();
    }
}
