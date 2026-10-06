package forge.delve;

import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import forge.Forge;
import forge.adventure.data.EnemyData;
import forge.adventure.scene.RewardScene;
import forge.adventure.util.Reward;
import forge.adventure.util.RewardActor;
import forge.card.CardEdition;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Pai Gow Magic (Booster Blitz rules) at the Card Shop.
 *
 * You and a regular each open a booster and split it into 4 face-down piles of 3.
 * The piles are shuffled, then you play up to 4 quick games: each pile is your whole
 * hand, there's no library, everyone starts at 5 life with 1,000 floating mana (kept between steps), and the
 * loser of each game goes first in the next. First to 3 wins (or the most wins
 * after 4) takes the 2 cards of their choice from the other player's pack.
 */
public class DelvePaiGowScene extends DelveScene {
    private static DelvePaiGowScene object;

    static final int PILES = 4, PILE_SIZE = 3, TO_WIN = 3;
    private static final int WON = 1, LOST = 2, DRAW = 3;

    private final Random rng = new Random();
    private CardEdition set;
    private EnemyData foe;
    private List<PaperCard> myPack, foePack;
    private final List<List<PaperCard>> myPiles = new ArrayList<>(), foePiles = new ArrayList<>();
    private final int[] results = new int[PILES];
    private int game;
    private boolean building, over;

    // pile builder
    private final List<PaperCard> pool = new ArrayList<>();
    private int[] placedIn; // pool index -> pile, or -1
    private int selected = -1;

    private DelvePaiGowScene() {
        super("ui/delve_shop.json");
    }

    public static DelvePaiGowScene instance() {
        if (object == null)
            object = new DelvePaiGowScene();
        return object;
    }

    /** Pay for your pack, open it, and start splitting it into piles. */
    public void begin(CardEdition set) {
        DelveProfile prof = DelveProfile.get();
        if (prof.paiGowToday() >= DelveEconomy.PAI_GOW_PER_DAY || !prof.spendGold(DelveEconomy.PACK_PRICE)) return;
        prof.markPaiGow();
        DelveAudio.shuffle();
        this.set = set;
        DelveDay day = DelveDay.today();
        myPack = day.openPack(set, rng);
        foePack = day.openPack(set, rng);
        prof.addToCollection(myPack); // yours from the start; a loss hands 2 over afterwards
        List<EnemyData> regulars = new ArrayList<>(day.weakEnemies);
        regulars.addAll(day.eliteEnemies);
        foe = regulars.isEmpty() ? null : regulars.get(rng.nextInt(regulars.size()));
        if (foe == null) return;

        myPiles.clear();
        foePiles.clear();
        foePiles.addAll(autoSplit(foePack));
        Collections.shuffle(foePiles, rng);
        java.util.Arrays.fill(results, 0);
        game = 0;
        over = false;

        pool.clear();
        for (PaperCard pc : myPack) if (!pc.getRules().getType().isBasicLand()) pool.add(pool.size(), pc);
        if (pool.size() < PILES * PILE_SIZE) { // tiny or odd packs: allow basics too
            pool.clear();
            pool.addAll(myPack);
        }
        pool.sort(Comparator.comparingDouble(DelvePaiGowScene::value).reversed());
        placedIn = new int[pool.size()];
        java.util.Arrays.fill(placedIn, -1);
        selected = -1;
        building = true;
        DelvePackOpenScene.instance().openPack("Pai Gow: your " + set.getName() + " booster", set.getName(), myPack,
                x -> Forge.switchScene(this));
    }

    // ---- splitting -------------------------------------------------------------------

    /** How good a card is in Pai Gow: its set ranking, with lands nearly worthless. */
    private static double value(PaperCard pc) {
        double v = DelveRank.score(pc);
        if (pc.getRules().getType().isLand()) v -= 1.0;
        return v;
    }

    /** The best 12 cards dealt snake-style so each pile gets a strong, a middling and a weak card, then evened out so each pile has a creature where possible. */
    static List<List<PaperCard>> autoSplit(List<PaperCard> pack) {
        List<PaperCard> cards = new ArrayList<>(pack);
        cards.sort(Comparator.comparingDouble(DelvePaiGowScene::value).reversed());
        List<List<PaperCard>> piles = new ArrayList<>();
        for (int i = 0; i < PILES; i++) piles.add(new ArrayList<>());
        int n = Math.min(cards.size(), PILES * PILE_SIZE);
        for (int k = 0; k < n; k++) {
            int round = k / PILES, pos = k % PILES;
            piles.get(round % 2 == 0 ? pos : PILES - 1 - pos).add(cards.get(k));
        }
        // give creature-less piles a creature from a pile with two or more (swap cards of the same slot)
        for (List<PaperCard> poor : piles) {
            if (creatures(poor) > 0) continue;
            outer:
            for (List<PaperCard> rich : piles) {
                if (creatures(rich) < 2) continue;
                for (int slot = 0; slot < Math.min(poor.size(), rich.size()); slot++) {
                    if (rich.get(slot).getRules().getType().isCreature() && !poor.get(slot).getRules().getType().isCreature()) {
                        PaperCard a = rich.get(slot);
                        rich.set(slot, poor.get(slot));
                        poor.set(slot, a);
                        break outer;
                    }
                }
            }
        }
        return piles;
    }

    private static int creatures(List<PaperCard> pile) {
        int n = 0;
        for (PaperCard pc : pile) if (pc.getRules().getType().isCreature()) n++;
        return n;
    }

    private int pileCount(int pile) {
        int n = 0;
        for (int p : placedIn) if (p == pile) n++;
        return n;
    }

    private boolean pilesFull() {
        for (int i = 0; i < PILES; i++) if (pileCount(i) < PILE_SIZE) return false;
        return true;
    }

    private void autoPlace() {
        java.util.Arrays.fill(placedIn, -1);
        List<List<PaperCard>> piles = autoSplit(pool);
        for (int p = 0; p < piles.size(); p++)
            for (PaperCard pc : piles.get(p))
                for (int i = 0; i < pool.size(); i++)
                    if (placedIn[i] < 0 && pool.get(i) == pc) { placedIn[i] = p; break; }
        selected = -1;
        build();
    }

    private void place(int pile) {
        if (selected < 0 || pileCount(pile) >= PILE_SIZE) return;
        placedIn[selected] = pile;
        selected = -1;
        build();
    }

    private void lockPiles() {
        myPiles.clear();
        for (int p = 0; p < PILES; p++) {
            List<PaperCard> pile = new ArrayList<>();
            for (int i = 0; i < pool.size(); i++) if (placedIn[i] == p) pile.add(pool.get(i));
            myPiles.add(pile);
        }
        Collections.shuffle(myPiles, rng); // your opponent shuffles your piles
        building = false;
        build();
        info("Pai Gow", DelvePersona.name(foe) + " shuffles your piles face down. You won't know which pile you'll get until each game starts.\n\n"
                + "Every game: your pile is your hand, no library, 5 life, 1,000 floating mana that never empties (no lands needed). A stalemate is a draw. The loser of a game goes first in the next. First to "
                + TO_WIN + " wins takes " + DelveEconomy.PAI_GOW_TAKE + " cards from the other's pack.", null);
    }

    // ---- screens ---------------------------------------------------------------------

    @Override
    public void enter() {
        DelveAudio.town();
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        if (foe == null || myPack == null) {
            button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveShopScene.instance()));
            return;
        }
        if (building) buildPiles();
        else buildTable();
    }

    private RewardActor card(PaperCard pc, float x, float yTop, float w, float h) {
        RewardActor a = new RewardActor(new Reward(pc, true), false, RewardScene.Type.Loot, false);
        a.setBounds(x, H - yTop - h, w, h);
        track(a);
        return a;
    }

    private void buildPiles() {
        label("[%95][GOLD]Pai Gow[WHITE]  Split your pack into " + PILES + " piles of " + PILE_SIZE, 8, 5, 340, 16, Align.left);
        label("[%80]vs " + DelvePersona.name(foe), 300, 5, 172, 16, Align.right);

        // your pack on the left (unused cards just stay in your collection)
        int n = pool.size();
        int cols = Math.max(5, (int) Math.ceil(n / 3.0));
        float cellW = Math.min(40f, 205f / cols), cardW = cellW - 4, cardH = cardW / 0.716f;
        float btnH = 12, cellH = cardH + btnH + 4, left = 10, top = 28;
        for (int i = 0; i < n; i++) {
            int r = i / cols, c = i % cols;
            float x = left + c * cellW, y = top + r * cellH;
            card(pool.get(i), x, y, cardW, cardH);
            final int idx = i;
            String text;
            if (placedIn[i] >= 0) {
                image("ui/delve/shade.png", x, y, cardW, cardH);
                label("[%80][GRAY]Pile " + (placedIn[i] + 1), x, y + cardH / 2f - 6, cardW, 12, Align.center);
                text = "[%65]Return";
            } else {
                text = selected == i ? "[%65][GOLD]Picked" : "[%65]Pick";
            }
            button(text, x, y + cardH + 1, cardW, btnH, () -> {
                if (placedIn[idx] >= 0) placedIn[idx] = -1;
                else selected = selected == idx ? -1 : idx;
                build();
            });
        }

        // the four piles on the right
        float pileX = 226, colW = 62, pCardW = 33, pCardH = pCardW / 0.716f;
        for (int p = 0; p < PILES; p++) {
            float x = pileX + p * colW;
            int count = pileCount(p);
            final int pile = p;
            boolean canPlace = selected >= 0 && count < PILE_SIZE;
            TextraButton head = button(canPlace ? "[%70][GOLD]Place here" : "[%70]Pile " + (p + 1) + "  " + count + "/" + PILE_SIZE,
                    x, 26, colW - 6, 15, () -> place(pile));
            head.setDisabled(!canPlace && selected >= 0);
            int slot = 0;
            for (int i = 0; i < n; i++) {
                if (placedIn[i] != p) continue;
                float y = 46 + slot * (pCardH + 14);
                float cx = x + (colW - 6 - pCardW) / 2f;
                card(pool.get(i), cx, y, pCardW, pCardH);
                final int idx = i;
                button("[%60]Remove", cx - 4, y + pCardH + 1, pCardW + 8, 11, () -> { placedIn[idx] = -1; build(); });
                slot++;
            }
            for (; slot < PILE_SIZE; slot++) {
                float y = 46 + slot * (pCardH + 14);
                image("ui/delve/panel.png", x + (colW - 6 - pCardW) / 2f, y, pCardW, pCardH);
            }
        }

        button("Auto-split", 60, 244, 90, 20, this::autoPlace);
        button("Clear", 156, 244, 60, 20, () -> { java.util.Arrays.fill(placedIn, -1); selected = -1; build(); });
        button(pilesFull() ? "[GOLD]Ready" : "[GRAY]Ready", 300, 244, 110, 20, this::lockPiles).setDisabled(!pilesFull());
    }

    private int wins(int who) {
        int n = 0;
        for (int r : results) if (r == who) n++;
        return n;
    }

    private void buildTable() {
        label("[%95][GOLD]Pai Gow[WHITE]  " + set.getName(), 8, 5, 300, 16, Align.left);
        label("[%90]You " + wins(WON) + " - " + wins(LOST) + " " + DelvePersona.name(foe), 200, 5, 272, 16, Align.right);

        image("ui/delve/panel.png", 20, 28, W - 40, 192);
        portrait(null, 60, 74);
        label("[%85][GOLD]You", 30, 78, 60, 12, Align.center);
        portrait(foe, W - 60, 74);
        label("[%85]" + DelvePersona.name(foe), W - 110, 78, 100, 12, Align.center);

        // one column per game: result and, once played, the pile you had
        float colW = 74, x0 = W / 2f - 2 * colW;
        float cw = 22, ch = cw / 0.716f;
        for (int g = 0; g < PILES; g++) {
            float x = x0 + g * colW;
            String res = results[g] == WON ? "[GREEN]Won" : results[g] == LOST ? "[RED]Lost" : results[g] == DRAW ? "[GRAY]Draw"
                    : !over && g == game ? "[GOLD]Next" : "[GRAY]-";
            label("[%80]Game " + (g + 1), x, 36, colW, 12, Align.center);
            label("[%80]" + res, x, 50, colW, 12, Align.center);
            boolean shown = results[g] != 0;
            for (int k = 0; k < PILE_SIZE; k++) {
                float cx = x + (colW - 3 * (cw + 2)) / 2f + k * (cw + 2);
                if (shown && k < myPiles.get(g).size()) card(myPiles.get(g).get(k), cx, 66, cw, ch);
                else image("ui/delve/panel.png", cx, 66, cw, ch);
            }
        }
        label("[%70]Your piles, face down until each game starts. Hold a card to read it.", 30, 112, W - 60, 12, Align.center);
        label("[%75]5 life each, 1,000 floating mana that never empties, no library. Stalemates are draws. Loser goes first next game.",
                30, 128, W - 60, 24, Align.center);

        if (!over) {
            button("[GOLD]Play game " + (game + 1), W / 2f - 70, 170, 140, 22, this::play);
        } else {
            int me = wins(WON), them = wins(LOST);
            String verdict = me > them ? "[GOLD]You win the match!" : me < them ? "[RED]" + DelvePersona.name(foe) + " wins the match." : "A tie: everyone keeps their own pack.";
            label("[%100]" + verdict, 30, 160, W - 60, 16, Align.center);
            button("Back to the shop", W / 2f - 70, 184, 140, 22, () -> {
                myPack = null;
                Forge.switchScene(DelveShopScene.instance());
            });
        }
    }

    // ---- games -------------------------------------------------------------------------

    private void play() {
        int first = 0; // game 1: coin flip
        if (game > 0) first = results[game - 1] == LOST ? 1 : results[game - 1] == WON ? 2 : 0;
        final int f = first;
        Runnable go = () -> {
            DelveDuelScene.instance().setupPaiGow(myPiles.get(game), foe, foePiles.get(game), f, (won, life) -> result(won));
            Forge.switchScene(DelveDuelScene.instance());
        };
        if (game == 0) DelveTalkScene.before(DelveTalkScene.SHOP, foe, true, false, "Pai Gow", go);
        else go.run();
    }

    private void result(boolean won) {
        boolean draw = !won && DelveDuelScene.instance().lastWasDraw();
        results[game] = won ? WON : draw ? DRAW : LOST;
        game++;
        if (wins(WON) >= TO_WIN || wins(LOST) >= TO_WIN || game >= PILES) {
            // the match is over: a word from your opponent, then the cards change hands
            DelveTalkScene.after(DelveTalkScene.SHOP, foe, wins(WON) >= wins(LOST), true, false, () -> {
                Forge.switchScene(this);
                finish();
                build();
            });
            return;
        }
        Forge.switchScene(this);
        build();
    }

    private void finish() {
        over = true;
        DelveProfile prof = DelveProfile.get();
        int me = wins(WON), them = wins(LOST);
        if (me > them) {
            // pick any 2 of their pack; the 2 best-ranked are preselected
            List<PaperCard> theirs = new ArrayList<>(foePack);
            theirs.sort(Comparator.comparingDouble(DelvePaiGowScene::value).reversed());
            DelvePickScene.instance().withAllSelected().show("You win! Take " + DelveEconomy.PAI_GOW_TAKE + " cards from "
                            + DelvePersona.name(foe) + "'s pack", theirs, DelveEconomy.PAI_GOW_TAKE, DelveEconomy.PAI_GOW_TAKE, null,
                    picked -> {
                        prof.addToCollection(picked);
                        DelveAudio.coins();
                        Forge.switchScene(this);
                    });
        } else if (me < them) {
            List<PaperCard> mine = new ArrayList<>(myPack);
            mine.sort(Comparator.comparingDouble(DelvePaiGowScene::value).reversed());
            List<PaperCard> lost = mine.subList(0, Math.min(DelveEconomy.PAI_GOW_TAKE, mine.size()));
            StringBuilder names = new StringBuilder();
            for (PaperCard pc : lost) {
                prof.removeFromCollection(pc);
                names.append(names.length() > 0 ? " and " : "").append(pc.getName());
            }
            info("Pai Gow", DelvePersona.name(foe) + " takes " + names + " from your pack. The rest of the pack stays in your collection.", null);
        }
    }

    @Override
    public boolean back() {
        return true; // finish the match first
    }
}
