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

    /** A Commander pod in progress: the player and three opponents, one free-for-all game. */
    private static class Pod {
        final Deck deck;
        final boolean loaner;
        final List<EnemyData> foes = new ArrayList<>();
        final List<Deck> decks = new ArrayList<>();
        boolean over = false;
        Pod(Deck deck, boolean loaner) { this.deck = deck; this.loaner = loaner; }
    }

    private Pod pod;

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
        label("[%90][GOLD]The Castle", 8, 5, 220, 16, Align.left);
        label("[%90][GOLD]Gold[] " + prof.gold() + "    Titles " + prof.castleTitles(), 240, 5, 232, 16, Align.right);
        if (pod != null) buildPod();
        else if (t == null) buildLobby();
        else buildBracket();
    }

    // ---- lobby -----------------------------------------------------------------

    /** Text markup size resets at line breaks, so apply it to every line. */
    private static String sized(String pct, String text) {
        return pct + text.replace("[]", "[WHITE]").replace("\n", "\n" + pct);
    }

    private void buildLobby() {
        DelveProfile prof = DelveProfile.get();
        boolean done = prof.castleToday();
        // left: 1v1 bracket
        image("ui/delve/panel.png", 12, 30, 224, 200);
        label("[%120]1v1 Tournament", 12, 38, 224, 20, Align.center);
        label(sized("[%80]", "Eight duelists, single elimination, best of three. "
                        + "Bring a deck from Your House or a Locked Deck (40+ cards).\n\n"
                        + "Entry [GOLD]" + DelveEconomy.CASTLE_ENTRY + "g[]\n"
                        + "Champion [GOLD]" + DelveEconomy.CASTLE_CHAMPION + "g[] + booster + token\n"
                        + "Finalist [GOLD]" + DelveEconomy.CASTLE_FINALIST + "g[]   Semifinal [GOLD]"
                        + DelveEconomy.CASTLE_SEMIFINAL + "g[]"),
                24, 62, 200, 130, Align.center);
        if (!done)
            button("[GOLD]Enter the tournament", 34, 200, 180, 22, this::chooseDeck)
                    .setDisabled(prof.gold() < DelveEconomy.CASTLE_ENTRY);
        // right: Commander pod
        image("ui/delve/panel.png", 244, 30, 224, 200);
        label("[%120]Commander Pod", 244, 38, 224, 20, Align.center);
        label(sized("[%80]", "Four players, one game, everyone for themselves. 40 life, commanders in the command zone. "
                        + "Bring a Commander deck from Your House, or borrow one of tonight's house decks.\n\n"
                        + "Entry [GOLD]" + DelveEconomy.POD_ENTRY + "g[]\n"
                        + "Last one standing [GOLD]" + DelveEconomy.POD_WIN + "g[] + booster + token"),
                256, 62, 200, 130, Align.center);
        if (!done)
            button("[GOLD]Join a pod", 266, 200, 180, 22, this::choosePodDeck)
                    .setDisabled(prof.gold() < DelveEconomy.POD_ENTRY);
        if (done)
            label("[%80][GOLD]You've competed tonight. Sleep at Your House for tomorrow's events.",
                    40, 236, 400, 14, Align.center);
        button("Leave", 190, 250, 100, 18, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    // ---- Commander pod ------------------------------------------------------------

    private void choosePodDeck() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Deck d : DelveDeckEditScene.legalCommanderDecks()) {
            labels.add(d.getName() + " (" + commanderName(d) + ")");
            actions.add(() -> startPod(d, false));
        }
        labels.add("[GOLD]Borrow a house deck");
        actions.add(this::chooseLoaner);
        labels.add("Cancel");
        actions.add(() -> { });
        String note = DelveDeckEditScene.commanderDecks().iterator().hasNext() && DelveDeckEditScene.legalCommanderDecks().isEmpty()
                ? "None of your Commander decks are legal yet (100 cards, singleton, a commander). " : "";
        choose("Choose your deck", note + "Entry costs " + DelveEconomy.POD_ENTRY + " gold.", labels, null, actions);
    }

    private static String commanderName(Deck d) {
        List<PaperCard> c = d.getCommanders();
        return c.isEmpty() ? "no commander" : c.get(0).getName();
    }

    private void chooseLoaner() {
        List<Deck> loaners = DelveDay.today().loanerCommanders();
        if (loaners.isEmpty()) {
            info("No house decks", "There are no legendary creatures in this era to lead a deck. Try another day.", null);
            return;
        }
        List<PaperCard> commanders = new ArrayList<>();
        for (Deck d : loaners) commanders.add(d.getCommanders().get(0));
        DelvePickScene.instance().show("Borrow a house deck: choose its commander", commanders, 0, 1, "Back",
                pc -> "Borrow", chosen -> {
            Forge.switchScene(this);
            if (chosen.isEmpty()) return;
            for (Deck d : loaners)
                if (d.getCommanders().get(0).equals(chosen.get(0))) startPod(d, true);
        });
    }

    private void startPod(Deck deck, boolean loaner) {
        if (!DelveProfile.get().spendGold(DelveEconomy.POD_ENTRY)) return;
        DelveProfile.get().markCastle();
        pod = new Pod(deck, loaner);
        DelveDay day = DelveDay.today();
        List<EnemyData> pool = new ArrayList<>(day.eliteEnemies);
        pool.addAll(day.bossEnemies);
        if (pool.size() < 3) pool.addAll(day.weakEnemies);
        Collections.shuffle(pool, new Random());
        for (EnemyData e : pool) {
            if (pod.foes.size() >= 3) break;
            boolean dup = false;
            for (EnemyData f : pod.foes) dup |= f.getName().equals(e.getName());
            if (dup) continue;
            Deck d = day.enemyCommanderDeck(e);
            if (d == null) continue;
            pod.foes.add(e);
            pod.decks.add(d);
        }
        build();
    }

    private void buildPod() {
        image("ui/delve/panel.png", 40, 32, 400, 196);
        label("[%110]Tonight's pod", 40, 40, 400, 18, Align.center);
        String[] names = new String[4], cmds = new String[4];
        names[0] = "You";
        cmds[0] = commanderName(pod.deck) + (pod.loaner ? " (borrowed)" : "");
        for (int i = 0; i < pod.foes.size(); i++) {
            names[i + 1] = pod.foes.get(i).getName();
            cmds[i + 1] = commanderName(pod.decks.get(i));
        }
        for (int i = 0; i < 4 && names[i] != null; i++) {
            float x = i % 2 == 0 ? 60 : 250, y = i < 2 ? 70 : 140;
            label("[%90]" + (i == 0 ? "[GOLD]" : "") + shorten(names[i]), x, y, 170, 16, Align.left);
            label("[%70]" + cmds[i], x, y + 18, 170, 30, Align.left);
        }
        if (!pod.over) {
            List<PaperCard> commanders = new ArrayList<>();
            for (Deck d : pod.decks) commanders.addAll(d.getCommanders());
            button("View their commanders", 60, 200, 150, 20, () -> DelvePickScene.instance().show(
                    "Your opponents' commanders", commanders, 0, 0, "Back", x -> Forge.switchScene(this)));
            button("[GOLD]Begin the game", 220, 200, 120, 20, this::playPod);
            button("Forfeit", 350, 200, 70, 20, () -> confirm("Forfeit", "Leave the pod? Your entry fee is lost.",
                    () -> finishPod(false)));
        }
    }

    private void playPod() {
        DelveDuelScene.instance().setupCommander(pod.deck, pod.foes, pod.decks, (won, life) -> {
            Forge.switchScene(this);
            finishPod(won);
        });
        Forge.switchScene(DelveDuelScene.instance());
    }

    private void finishPod(boolean won) {
        DelveProfile prof = DelveProfile.get();
        String msg;
        if (won) {
            prof.addGold(DelveEconomy.POD_WIN);
            prof.addCastleTitle();
            List<PaperCard> pack = DelveDay.today().openPack(DelveDay.today().edition, new Random());
            prof.addToCollection(pack);
            msg = "Last one standing! +" + DelveEconomy.POD_WIN + " gold, a " + DelveDay.today().themeName()
                    + " booster (added to your collection) and " + DelveTokens.grant(1, new Random()) + ".";
        } else {
            msg = "You were knocked out of the pod. Better luck next time.";
        }
        pod.over = true;
        build();
        info("Pod over", msg, () -> {
            pod = null;
            build();
        });
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
                msg = "You are the Castle champion! +" + DelveEconomy.CASTLE_CHAMPION + " gold, a "
                        + DelveDay.today().themeName() + " booster (added to your collection) and "
                        + DelveTokens.grant(1, t.rng) + ".";
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
