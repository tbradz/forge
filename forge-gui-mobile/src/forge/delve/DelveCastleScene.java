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

    /** Forget any tournament or pod in progress (after switching saves). */
    static void reset() {
        if (object != null) {
            object.t = null;
            object.pod = null;
        }
    }

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
        if (t == null && pod == null) loadState();
        DelveAudio.castle();
        build();
        super.enter();
    }

    private String me() {
        return "You";
    }

    /** Bracket entries are keyed by enemy name; show the person's own name. */
    private String display(String key) {
        if (key == null || key.equals(me())) return key;
        EnemyData e = enemyNamed(key);
        return e == null ? key : DelvePersona.name(e);
    }

    private void build() {
        clearScreen();
        DelveProfile prof = DelveProfile.get();
        label("[%90][GOLD]The Castle", 8, 5, 220, 16, Align.left);
        label("[%90][GOLD]Gold[] " + prof.gold() + "    [GOLD]" + DelveRenown.title().title + "[]  Renown " + prof.renown(),
                240, 5, 232, 16, Align.right);
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
                        + "Entry [GOLD]" + DelveRenown.castleEntry(DelveEconomy.CASTLE_ENTRY) + "g[]\n"
                        + "Champion [GOLD]" + DelveRenown.castlePrize(DelveEconomy.CASTLE_CHAMPION) + "g[] + booster + token\n"
                        + "Finalist [GOLD]" + DelveRenown.castlePrize(DelveEconomy.CASTLE_FINALIST) + "g[]   Semifinal [GOLD]"
                        + DelveRenown.castlePrize(DelveEconomy.CASTLE_SEMIFINAL) + "g[]"),
                24, 62, 200, 130, Align.center);
        if (!done)
            button("[GOLD]Enter the tournament", 34, 200, 180, 22, this::chooseDeck)
                    .setDisabled(prof.gold() < DelveRenown.castleEntry(DelveEconomy.CASTLE_ENTRY));
        // right: Commander pod
        image("ui/delve/panel.png", 244, 30, 224, 200);
        label("[%120]Commander Pod", 244, 38, 224, 20, Align.center);
        label(sized("[%80]", "Four players, one game, everyone for themselves. 40 life, commanders in the command zone. "
                        + "Bring a Commander deck from Your House, or borrow one of tonight's house decks.\n\n"
                        + "Entry [GOLD]" + DelveRenown.castleEntry(DelveEconomy.POD_ENTRY) + "g[]\n"
                        + "Last one standing [GOLD]" + DelveRenown.castlePrize(DelveEconomy.POD_WIN) + "g[] + booster + token"),
                256, 62, 200, 130, Align.center);
        if (!done)
            button("[GOLD]Join a pod", 266, 200, 180, 22, this::choosePodDeck)
                    .setDisabled(prof.gold() < DelveRenown.castleEntry(DelveEconomy.POD_ENTRY));
        String note = done ? "You've competed tonight. Sleep at Your House for tomorrow's events."
                : prof.gold() < Math.min(DelveRenown.castleEntry(DelveEconomy.CASTLE_ENTRY), DelveRenown.castleEntry(DelveEconomy.POD_ENTRY))
                ? "Not enough gold for an entry tonight. The Tavern has free practice games." : null;
        if (note != null) {
            image("ui/delve/shade.png", 60, 233, 360, 15);
            label("[%80][GOLD]" + note, 60, 234, 360, 13, Align.center);
        }
        button("[GOLD]Champions board", 60, 250, 120, 18, this::championsBoard);
        button("Leave", 190, 250, 100, 18, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    /** The champions board: you and the rival duelists by Renown, and the title ladder with what each unlocks. */
    private void championsBoard() {
        StringBuilder sb = new StringBuilder("[%75]");
        List<DelveRenown.Standing> board = DelveRenown.board();
        for (int i = 0; i < board.size(); i++) {
            DelveRenown.Standing s = board.get(i);
            sb.append(s.you ? "[GOLD]" : "").append(i + 1).append(". ").append(s.you ? "You (" + DelveRenown.title().title + ")" : s.name)
                    .append("  -  ").append(s.renown).append(s.you ? "[WHITE]" : "").append("\n[%75]");
        }
        DelveRenown.Title now = DelveRenown.title(), next = DelveRenown.next(now);
        sb.append("\n[%75]Renown: semifinal +").append(DelveRenown.SEMIFINAL).append(", final +").append(DelveRenown.FINALIST)
                .append(", champion +").append(DelveRenown.CHAMPION).append(", pod win +").append(DelveRenown.POD_WIN).append(".\n[%75]");
        for (DelveRenown.Title t : DelveRenown.Title.values()) {
            if (t == DelveRenown.Title.COMMONER) continue;
            boolean have = now.atLeast(t);
            sb.append(have ? "[GOLD]" : "[GRAY]").append(t.title).append(" (").append(t.renown).append("): [WHITE]")
                    .append(have ? "" : "[GRAY]").append(t.unlocks).append("[WHITE]\n[%75]");
        }
        if (next != null)
            sb.append("[%75]").append(next.renown - DelveProfile.get().renown()).append(" more Renown to ").append(next.title).append(".");
        info("Champions of the Castle", sb.toString(), null);
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
        choose("Choose your deck", note + "Entry costs " + DelveRenown.castleEntry(DelveEconomy.POD_ENTRY) + " gold.", labels, null, actions);
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
        if (!DelveProfile.get().spendGold(DelveRenown.castleEntry(DelveEconomy.POD_ENTRY))) return;
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
        saveState();
        build();
    }

    private void buildPod() {
        image("ui/delve/panel.png", 40, 32, 400, 196);
        label("[%110]Tonight's pod", 40, 40, 400, 18, Align.center);
        String[] names = new String[4], cmds = new String[4];
        names[0] = "You";
        cmds[0] = commanderName(pod.deck) + (pod.loaner ? " (borrowed)" : "");
        for (int i = 0; i < pod.foes.size(); i++) {
            names[i + 1] = DelvePersona.name(pod.foes.get(i));
            cmds[i + 1] = commanderName(pod.decks.get(i));
        }
        for (int i = 0; i < 4 && names[i] != null; i++) {
            float x = i % 2 == 0 ? 56 : 246, y = i < 2 ? 66 : 132;
            portrait(i == 0 ? null : pod.foes.get(i - 1), x + 16, y + 40);
            label("[%90]" + (i == 0 ? "[GOLD]" : "") + shorten(names[i]), x + 36, y + 4, 140, 16, Align.left);
            label("[%70]" + cmds[i], x + 36, y + 22, 140, 30, Align.left);
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
        EnemyData host = pod.foes.isEmpty() ? null : pod.foes.get(0);
        DelveTalkScene.before(DelveTalkScene.CASTLE, host, true, false, "Commander pod", () -> {
            DelveDuelScene.instance().setupCommander(pod.deck, pod.foes, pod.decks, (won, life) ->
                    DelveTalkScene.after(DelveTalkScene.CASTLE, host, won, true, false, () -> {
                        Forge.switchScene(this);
                        finishPod(won);
                    }));
            Forge.switchScene(DelveDuelScene.instance());
        });
    }

    private void finishPod(boolean won) {
        DelveProfile prof = DelveProfile.get();
        String msg;
        List<PaperCard> prize = null;
        if (won) {
            prof.addGold(DelveRenown.castlePrize(DelveEconomy.POD_WIN));
            prof.addCastleTitle();
            List<PaperCard> pack = DelveDay.today().openPack(DelveDay.today().edition, new Random());
            prof.addToCollection(pack);
            prize = pack;
            msg = "Last one standing! +" + DelveRenown.castlePrize(DelveEconomy.POD_WIN) + " gold, " + article(DelveDay.today().themeName()) + " " + DelveDay.today().themeName()
                    + " booster (added to your collection) and " + DelveTokens.grant(1, new Random()) + "."
                    + DelveRenown.award(DelveRenown.POD_WIN);
        } else {
            msg = "You were knocked out of the pod. Better luck next time.";
        }
        pod.over = true;
        clearState();
        build();
        List<PaperCard> prizePack = prize;
        info("Pod over", msg, () -> {
            pod = null;
            build();
            openPrize(prizePack);
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
        choose("Choose your deck", "Entry costs " + DelveRenown.castleEntry(DelveEconomy.CASTLE_ENTRY) + " gold.", labels, null, actions);
    }

    private void start(Deck deck) {
        if (!DelveProfile.get().spendGold(DelveRenown.castleEntry(DelveEconomy.CASTLE_ENTRY))) return;
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
        saveState();
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
                label("[%70]" + (mine ? "[GOLD]" : "") + shorten(display(n)), colX[r], y, 110, 14, Align.left);
            }
        }

        String opponent = currentOpponent();
        if (!t.out && opponent != null) {
            button("[GOLD]Play the " + ROUND_NAMES[t.round].toLowerCase() + " vs " + shorten(display(opponent)), 110, 238, 200, 22,
                    this::playMatch);
            button("Forfeit", 320, 238, 80, 22, () -> confirm("Forfeit", "Leave the tournament? Your entry fee is lost.",
                    () -> finish(t.round)));
        }
    }

    private static String article(String word) {
        return "AEIOUaeiou".indexOf(word.isEmpty() ? 'x' : word.charAt(0)) >= 0 ? "an" : "a";
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
        DelveTalkScene.before(DelveTalkScene.CASTLE, foe, true, false, ROUND_NAMES[t.round], () -> {
            DelveDuelScene.instance().setup(t.deck, 20, foe, foeDeck, 20, 3, t.round == 2,
                    (won, life) -> DelveTalkScene.after(DelveTalkScene.CASTLE, foe, won, true, false, () -> afterMatch(won)));
            Forge.switchScene(DelveDuelScene.instance());
        });
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
        saveState();
        build();
        info("Victory", "You advance to the " + ROUND_NAMES[t.round].toLowerCase() + ".", null);
    }

    /** @param reached 0 = out in quarterfinal, 1 = semifinal, 2 = final, 3 = champion */
    private void finish(int reached) {
        DelveProfile prof = DelveProfile.get();
        String msg;
        List<PaperCard> prize = null;
        switch (reached) {
            case 3: {
                prof.addGold(DelveRenown.castlePrize(DelveEconomy.CASTLE_CHAMPION));
                prof.addCastleTitle();
                List<PaperCard> pack = DelveDay.today().openPack(DelveDay.today().edition, t.rng);
                prof.addToCollection(pack);
                prize = pack;
                msg = "You are the Castle champion! +" + DelveRenown.castlePrize(DelveEconomy.CASTLE_CHAMPION) + " gold, a "
                        + DelveDay.today().themeName() + " booster (added to your collection) and "
                        + DelveTokens.grant(1, t.rng) + ".";
                break;
            }
            case 2:
                prof.addGold(DelveRenown.castlePrize(DelveEconomy.CASTLE_FINALIST));
                msg = "You fell in the final. +" + DelveRenown.castlePrize(DelveEconomy.CASTLE_FINALIST) + " gold.";
                break;
            case 1:
                prof.addGold(DelveRenown.castlePrize(DelveEconomy.CASTLE_SEMIFINAL));
                msg = "You fell in the semifinal. +" + DelveRenown.castlePrize(DelveEconomy.CASTLE_SEMIFINAL) + " gold.";
                break;
            default:
                msg = "You were knocked out in the quarterfinal. Better luck next time.";
        }
        msg += DelveRenown.award(reached == 3 ? DelveRenown.CHAMPION : reached == 2 ? DelveRenown.FINALIST
                : reached == 1 ? DelveRenown.SEMIFINAL : 0);
        t.out = true;
        clearState();
        build();
        List<PaperCard> prizePack = prize;
        info("Tournament over", msg, () -> {
            t = null;
            build();
            openPrize(prizePack);
        });
    }

    /** A prize booster (already in your collection) gets the pack-opening show, then back to the Castle. */
    private void openPrize(List<PaperCard> pack) {
        if (pack == null || pack.isEmpty()) return;
        String set = DelveDay.today().edition.getName();
        DelvePackOpenScene.instance().openPack("Castle prize: " + set + " booster", set, pack,
                x -> Forge.switchScene(this));
    }

    // ---- saving an event in progress -----------------------------------------------

    private static java.io.File stateFile() { return new java.io.File(DelveSaves.dir(), "castle.properties"); }

    private static java.io.File deckFile() { return new java.io.File(DelveSaves.dir(), "castle.dck"); }

    /** Remember tonight's tournament or pod so closing the game doesn't lose it. */
    private void saveState() {
        try {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("day", String.valueOf(DelveProfile.get().day()));
            Deck deck;
            if (t != null) {
                deck = t.deck;
                p.setProperty("kind", "bracket");
                p.setProperty("round", String.valueOf(t.round));
                StringBuilder field = new StringBuilder();
                for (EnemyData e : t.field) field.append(field.length() > 0 ? "|" : "").append(e.getName());
                p.setProperty("field", field.toString());
                for (int r = 0; r < t.rounds.size(); r++)
                    p.setProperty("rounds." + r, String.join("|", t.rounds.get(r)));
            } else if (pod != null) {
                deck = pod.deck;
                p.setProperty("kind", "pod");
                p.setProperty("loaner", String.valueOf(pod.loaner));
                StringBuilder foes = new StringBuilder();
                for (EnemyData e : pod.foes) foes.append(foes.length() > 0 ? "|" : "").append(e.getName());
                p.setProperty("foes", foes.toString());
            } else {
                return;
            }
            p.setProperty("deckName", deck.getName());
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(stateFile())) {
                p.store(out, "Delve Castle event in progress");
            }
            forge.deck.io.DeckSerializer.writeDeck(deck, deckFile());
        } catch (Exception e) {
            e.printStackTrace(); // best-effort
        }
    }

    private static void clearState() {
        stateFile().delete();
        deckFile().delete();
    }

    /** Restore tonight's event, if one was saved today. */
    private void loadState() {
        if (!stateFile().exists() || !deckFile().exists()) return;
        try {
            java.util.Properties p = new java.util.Properties();
            try (java.io.FileInputStream in = new java.io.FileInputStream(stateFile())) {
                p.load(in);
            }
            if (Integer.parseInt(p.getProperty("day", "-1")) != DelveProfile.get().day()) {
                clearState(); // yesterday's event is over
                return;
            }
            Deck deck = forge.deck.io.DeckSerializer.fromFile(deckFile());
            if (deck == null) return;
            deck.setName(p.getProperty("deckName", deck.getName()));
            if ("bracket".equals(p.getProperty("kind"))) {
                Tournament restored = new Tournament(deck);
                for (String n : p.getProperty("field", "").split("\\|")) {
                    EnemyData e = enemyByName(n);
                    if (e != null) restored.field.add(e);
                }
                for (int r = 0; p.getProperty("rounds." + r) != null; r++)
                    restored.rounds.add(new ArrayList<>(List.of(p.getProperty("rounds." + r).split("\\|"))));
                restored.round = Integer.parseInt(p.getProperty("round", "0"));
                if (!restored.rounds.isEmpty()) t = restored;
            } else if ("pod".equals(p.getProperty("kind"))) {
                Pod restored = new Pod(deck, Boolean.parseBoolean(p.getProperty("loaner", "false")));
                for (String n : p.getProperty("foes", "").split("\\|")) {
                    EnemyData e = enemyByName(n);
                    Deck d = e == null ? null : DelveDay.today().enemyCommanderDeck(e);
                    if (d != null) {
                        restored.foes.add(e);
                        restored.decks.add(d);
                    }
                }
                if (!restored.foes.isEmpty()) pod = restored;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static EnemyData enemyByName(String name) {
        for (EnemyData e : forge.adventure.data.WorldData.getAllEnemies())
            if (e.getName().equals(name)) return e;
        return null;
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
