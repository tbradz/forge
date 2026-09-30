package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Castle: 8-player single-elimination 1v1 tournaments, best of 3, played with
 * a deck built in Your House or a Locked Deck. Pay an entry fee, win gold and a
 * booster. Other matches in the bracket are simulated.
 */
public class DelveCastleScene extends DelveScene {
    private static DelveCastleScene object;

    /** A tournament in progress (null when not entered). */
    private static class Tournament {
        final Deck deck;
        final List<EnemyData> field = new ArrayList<>(); // 7 opponents
        /** rounds.get(r) = names still in after round r (index 0 = the 8 entrants) */
        final List<List<String>> rounds = new ArrayList<>();
        final Random rng = new Random();
        int round = 0; // 0 = quarterfinal, 1 = semifinal, 2 = final
        boolean out = false;
        Tournament(Deck deck) { this.deck = deck; }
    }

    private static final String[] ROUND_NAMES = {"Quarterfinal", "Semifinal", "Final"};
    private Tournament t;

    private DelveCastleScene() {
        super("ui/delve_castle.json");
    }

    public static DelveCastleScene instance() {
        if (object == null)
            object = new DelveCastleScene();
        return object;
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private String me() {
        return "You";
    }

    private void build() {
        clearScreen();
        DelveProfile prof = DelveProfile.get();
        label("[%90][GOLD]Castle Tournament", 8, 5, 220, 16, Align.left);
        label("[%90][GOLD]Gold[] " + prof.gold() + "    Titles " + prof.castleTitles(), 240, 5, 232, 16, Align.right);
        if (t == null) buildLobby();
        else buildBracket();
    }

    // ---- lobby -----------------------------------------------------------------

    private void buildLobby() {
        image("ui/delve/panel.png", 60, 40, 360, 180);
        label("[%130]1v1 Tournament", 60, 50, 360, 22, Align.center);
        label("One tournament each evening. Eight duelists, single elimination, best of three.\n"
                        + "Bring a deck you built in Your House or a Locked Deck (40+ cards).\n\n"
                        + "Entry: [GOLD]" + DelveEconomy.CASTLE_ENTRY + " gold[]\n"
                        + "Champion: [GOLD]" + DelveEconomy.CASTLE_CHAMPION + " gold[] + a booster\n"
                        + "Finalist: [GOLD]" + DelveEconomy.CASTLE_FINALIST + " gold[]    Semifinalist: [GOLD]"
                        + DelveEconomy.CASTLE_SEMIFINAL + " gold[]",
                76, 78, 328, 110, Align.center);
        DelveProfile prof = DelveProfile.get();
        if (prof.castleToday()) {
            label("[%80][GOLD]You've competed tonight. Sleep at Your House for tomorrow's tournament.",
                    70, 194, 340, 20, Align.center);
        } else {
            button("[GOLD]Enter tonight's tournament", 140, 192, 200, 22, this::chooseDeck)
                    .setDisabled(prof.gold() < DelveEconomy.CASTLE_ENTRY);
        }
        button("Leave", 190, 240, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private List<Deck> eligibleDecks() {
        List<Deck> out = new ArrayList<>();
        for (Deck d : DelveDeckEditScene.deckList())
            if (d.getMain().countAll() >= 40) out.add(d);
        for (Deck d : DelveProfile.get().lockedDecks())
            if (d.getMain().countAll() >= 40) out.add(d);
        return out;
    }

    private void chooseDeck() {
        List<Deck> decks = eligibleDecks();
        if (decks.isEmpty()) {
            info("No eligible deck", "You need a deck of 40+ cards: build one in Your House, or lock a run deck "
                    + "after clearing a dungeon.", null);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Deck d : decks) {
            boolean locked = DelveProfile.get().lockedDecks().contains(d);
            labels.add(d.getName() + " (" + d.getMain().countAll() + (locked ? ", locked)" : ")"));
            actions.add(() -> start(d));
        }
        labels.add("Cancel");
        actions.add(() -> { });
        choose("Choose your deck", "Entry costs " + DelveEconomy.CASTLE_ENTRY + " gold.", labels, null, actions);
    }

    private void start(Deck deck) {
        if (!DelveProfile.get().spendGold(DelveEconomy.CASTLE_ENTRY)) return;
        DelveProfile.get().markCastle();
        t = new Tournament(deck);
        DelveDay day = DelveDay.today();
        List<EnemyData> pool = new ArrayList<>(day.eliteEnemies);
        pool.addAll(day.bossEnemies);
        if (pool.size() < 7) pool.addAll(day.weakEnemies);
        Collections.shuffle(pool, t.rng);
        List<String> entrants = new ArrayList<>();
        entrants.add(me());
        for (EnemyData e : pool) {
            if (t.field.size() >= 7) break;
            if (entrants.contains(e.getName())) continue;
            t.field.add(e);
            entrants.add(e.getName());
        }
        Collections.shuffle(entrants, t.rng);
        t.rounds.add(entrants);
        build();
    }

    // ---- bracket ---------------------------------------------------------------

    private void buildBracket() {
        image("ui/delve/panel.png", 8, 32, 464, 200);
        float[] colX = {20, 140, 260, 372};
        String[] heads = {"Quarterfinals", "Semifinals", "Final", "Champion"};
        for (int c = 0; c < 4; c++)
            label("[%75][GOLD]" + heads[c], colX[c], 38, 104, 12, Align.left);
        for (int r = 0; r < t.rounds.size(); r++) {
            List<String> names = t.rounds.get(r);
            float slotH = 176f / names.size();
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i);
                boolean mine = n.equals(me());
                float y = 54 + slotH * i + slotH / 2f - 7;
                label("[%70]" + (mine ? "[GOLD]" : "") + shorten(n), colX[r], y, 110, 14, Align.left);
            }
        }

        String opponent = currentOpponent();
        if (!t.out && opponent != null) {
            button("[GOLD]Play the " + ROUND_NAMES[t.round].toLowerCase() + " vs " + shorten(opponent), 110, 238, 200, 22,
                    this::playMatch);
            button("Forfeit", 320, 238, 80, 22, () -> confirm("Forfeit", "Leave the tournament? Your entry fee is lost.",
                    () -> finish(t.round)));
        }
    }

    private static String shorten(String s) {
        return s.length() <= 18 ? s : s.substring(0, 17) + ".";
    }

    /** The player's opponent this round, or null if the player is out/finished. */
    private String currentOpponent() {
        List<String> cur = t.rounds.get(t.rounds.size() - 1);
        int i = cur.indexOf(me());
        if (i < 0 || cur.size() < 2) return null;
        return cur.get(i % 2 == 0 ? i + 1 : i - 1);
    }

    private EnemyData enemyNamed(String name) {
        for (EnemyData e : t.field) if (e.getName().equals(name)) return e;
        return null;
    }

    private void playMatch() {
        EnemyData foe = enemyNamed(currentOpponent());
        if (foe == null) return;
        Deck foeDeck = DelveDay.today().enemyDeck(foe, DelveDay.Tier.CASTLE);
        DelveDuelScene.instance().setup(t.deck, 20, foe, foeDeck, 20, 3, t.round == 2,
                (won, life) -> afterMatch(won));
        Forge.switchScene(DelveDuelScene.instance());
    }

    private void afterMatch(boolean won) {
        Forge.switchScene(this);
        List<String> cur = t.rounds.get(t.rounds.size() - 1);
        List<String> next = new ArrayList<>();
        for (int i = 0; i + 1 < cur.size(); i += 2) {
            String a = cur.get(i), b = cur.get(i + 1);
            if (a.equals(me()) || b.equals(me())) {
                String other = a.equals(me()) ? b : a;
                next.add(won ? me() : other);
            } else {
                next.add(t.rng.nextBoolean() ? a : b); // simulated match
            }
        }
        t.rounds.add(next);
        if (!won) {
            finish(t.round);
            return;
        }
        if (next.size() == 1) {
            finish(3);
            return;
        }
        t.round++;
        build();
        info("Victory", "You advance to the " + ROUND_NAMES[t.round].toLowerCase() + ".", null);
    }

    /** @param reached 0 = out in quarterfinal, 1 = semifinal, 2 = final, 3 = champion */
    private void finish(int reached) {
        DelveProfile prof = DelveProfile.get();
        String msg;
        switch (reached) {
            case 3: {
                prof.addGold(DelveEconomy.CASTLE_CHAMPION);
                prof.addCastleTitle();
                List<PaperCard> pack = DelveDay.today().openPack(DelveDay.today().edition, t.rng);
                prof.addToCollection(pack);
                msg = "You are the Castle champion! +" + DelveEconomy.CASTLE_CHAMPION + " gold and a "
                        + DelveDay.today().themeName() + " booster (added to your collection).";
                break;
            }
            case 2:
                prof.addGold(DelveEconomy.CASTLE_FINALIST);
                msg = "You fell in the final. +" + DelveEconomy.CASTLE_FINALIST + " gold.";
                break;
            case 1:
                prof.addGold(DelveEconomy.CASTLE_SEMIFINAL);
                msg = "You fell in the semifinal. +" + DelveEconomy.CASTLE_SEMIFINAL + " gold.";
                break;
            default:
                msg = "You were knocked out in the quarterfinal. Better luck next time.";
        }
        t.out = true;
        build();
        info("Tournament over", msg, () -> {
            t = null;
            build();
        });
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
