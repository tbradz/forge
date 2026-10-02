package forge.delve;

import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.data.EnemyData;
import forge.deck.Deck;
import forge.item.PaperCard;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Tavern (evenings): practice games against the patrons, as many as you like.
 * No entry fee and no prizes - a place to test decks, or to pass the evening when
 * you can't afford the Castle. Patrons change every day and play decks from your
 * highest tier.
 */
public class DelveTavernScene extends DelveScene {
    private static DelveTavernScene object;

    private final List<EnemyData> patrons = new ArrayList<>();
    private int patronsDay = -1, patronsTier = -1;
    private int wins, losses;

    private DelveTavernScene() {
        super("ui/delve_tavern.json");
    }

    public static DelveTavernScene instance() {
        if (object == null)
            object = new DelveTavernScene();
        return object;
    }

    @Override
    public void enter() {
        DelveAudio.town();
        build();
        super.enter();
    }

    private void rollPatrons() {
        DelveDay day = DelveDay.today();
        if (patronsDay == day.dayNumber && patronsTier == day.tier && !patrons.isEmpty()) return;
        patronsDay = day.dayNumber;
        patronsTier = day.tier;
        wins = losses = 0;
        patrons.clear();
        List<EnemyData> pool = new ArrayList<>(day.weakEnemies);
        pool.addAll(day.eliteEnemies);
        Collections.shuffle(pool, new Random(day.seed ^ 0x7A7E4L));
        for (EnemyData e : pool) {
            if (patrons.size() >= 4) break;
            boolean dup = false;
            for (EnemyData p : patrons) dup |= p.getName().equals(e.getName());
            if (!dup) patrons.add(e);
        }
    }

    private void build() {
        clearScreen();
        rollPatrons();
        label("[%90][GOLD]The Tavern", 8, 5, 200, 16, Align.left);
        label("[%90]Tonight: " + wins + " won, " + losses + " lost", 240, 5, 232, 16, Align.right);
        image("ui/delve/panel.png", 40, 32, 400, 204);
        label("[%110]Practice games", 40, 40, 400, 18, Align.center);
        button("[%80]Talk to Bram", 352, 38, 80, 16, () -> DelveTourScene.instance().play(false,
                () -> forge.Forge.switchScene(this)));
        label("[%75]No fee. Test a deck against tonight's patrons as often as you like, or play for gold or an ante.\n[%75]"
                        + "They play " + DelveDay.today().edition.getName() + " decks. Sleep at Your House when you're ready for tomorrow.",
                56, 60, 368, 30, Align.center);
        for (int i = 0; i < patrons.size(); i++) {
            EnemyData e = patrons.get(i);
            float x = i % 2 == 0 ? 66 : 250, y = 100 + (i / 2) * 44;
            button("Play " + DelvePersona.name(e), x, y, 164, 22, () -> chooseDeck(e));
            label("[%70]" + shorten(e.getName()) + "[WHITE]   " + colorsOf(e), x, y + 24, 164, 12, Align.center);
        }
        button("[GOLD]Commander with the patrons", 140, 192, 200, 20, this::chooseCommanderDeck)
                .setDisabled(patrons.size() < 3);
        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private static String shorten(String s) {
        return s.length() <= 16 ? s : s.substring(0, 15) + ".";
    }

    private static String colorsOf(EnemyData e) {
        String c = e.colors == null ? "" : e.colors.replaceAll("[^WUBRG]", "");
        if (c.isEmpty()) return "[GRAY]Plays mystery colors";
        java.util.Map<Character, String> names = java.util.Map.of('W', "[#f8f0c0]White", 'U', "[#7fb0ff]Blue",
                'B', "[#b090c0]Black", 'R', "[#ff8070]Red", 'G', "[#80d080]Green");
        StringBuilder sb = new StringBuilder("Plays ");
        for (int i = 0; i < c.length(); i++)
            sb.append(i > 0 ? "[WHITE] / " : "").append(names.get(c.charAt(i)));
        return sb.toString();
    }

    // ---- 1v1 practice -------------------------------------------------------------

    private List<Deck> playableDecks() {
        List<Deck> out = new ArrayList<>();
        for (Deck d : DelveDeckEditScene.deckList())
            if (d.getMain().countAll() >= 40) out.add(d);
        for (Deck d : DelveProfile.get().lockedDecks())
            if (d.getMain().countAll() >= 40) out.add(d);
        return out;
    }

    private void chooseDeck(EnemyData foe) {
        List<Deck> decks = playableDecks();
        if (decks.isEmpty()) {
            info("No deck", "You need a deck of 40+ cards: build one in Your House, or lock a run deck after clearing a dungeon.", null);
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Deck d : decks) {
            labels.add(d.getName() + " (" + d.getMain().countAll() + ")");
            actions.add(() -> chooseStakes(List.of(foe), List.of(DelveDay.today().enemyDeck(foe, DelveDay.Tier.ELITE)),
                    () -> play(d, foe)));
        }
        labels.add("Cancel");
        actions.add(() -> { });
        choose("Play " + DelvePersona.title(foe), "Choose your deck.", labels, null, actions);
    }

    private void play(Deck deck, EnemyData foe) {
        Deck foeDeck = DelveDay.today().enemyDeck(foe, DelveDay.Tier.ELITE);
        DelveTalkScene.before(DelveTalkScene.TAVERN, foe, true, false, null, () -> {
            DelveDuelScene.instance().setup(deck, 20, foe, foeDeck, 20, 1, false,
                    (won, life) -> DelveTalkScene.after(DelveTalkScene.TAVERN, foe, won, true, false, () -> result(won)));
            DelveDuelScene.instance().setReturnLabel("Back to the Tavern");
            Forge.switchScene(DelveDuelScene.instance());
        });
    }

    private void result(boolean won) {
        if (won) wins++;
        else losses++;
        Forge.switchScene(this);
        settleStakes(won);
    }

    // ---- stakes: a gold bet or an ante -----------------------------------------------

    private int betGold;                                  // per opponent
    private int betOpponents;
    private PaperCard myAnte;
    private final List<PaperCard> theirAntes = new ArrayList<>();

    private void clearStakes() {
        betGold = 0;
        betOpponents = 0;
        myAnte = null;
        theirAntes.clear();
    }

    /** Before a game: play for nothing, bet gold against each opponent, or ante a card each. */
    private void chooseStakes(List<EnemyData> foes, List<Deck> foeDecks, Runnable play) {
        clearStakes();
        DelveProfile prof = DelveProfile.get();
        int n = foes.size();
        String who = n == 1 ? DelvePersona.name(foes.get(0)) : "the patrons";
        List<String> labels = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Just for fun");
        enabled.add(true);
        actions.add(play);
        for (int bet : DelveEconomy.TAVERN_BETS) {
            int risk = bet * n;
            if (risk > DelveEconomy.TAVERN_BET_MAX_TOTAL) continue;
            labels.add("Bet " + bet + " gold" + (n > 1 ? " each (" + risk + " total)" : ""));
            enabled.add(prof.gold() >= risk);
            actions.add(() -> {
                betGold = bet;
                betOpponents = n;
                play.run();
            });
        }
        int antes = prof.antesLeft();
        labels.add("Ante a card (" + antes + " left this tier)");
        enabled.add(antes > 0 && !prof.collection().isEmpty());
        actions.add(() -> chooseAnte(foes, foeDecks, play));
        labels.add("Cancel");
        enabled.add(true);
        actions.add(() -> { });
        choose("Stakes", "Play " + who + " for something? Win a bet and each opponent pays you; lose and you pay "
                        + (n > 1 ? "each of them" : "them") + ".\nAnte: you each put up a card from your collection, and the winner takes them all ("
                        + DelveProfile.ANTES_PER_TIER + " antes per dungeon tier).",
                labels, enabled, actions);
    }

    private void chooseAnte(List<EnemyData> foes, List<Deck> foeDecks, Runnable play) {
        // each opponent antes a random card from its deck (not a basic land)
        java.util.Random rng = new java.util.Random();
        theirAntes.clear();
        for (Deck d : foeDecks) {
            List<PaperCard> cards = new ArrayList<>();
            for (PaperCard pc : d.getMain().toFlatList()) if (!pc.getRules().getType().isBasicLand()) cards.add(pc);
            if (d.has(forge.deck.DeckSection.Commander)) cards.removeAll(d.getCommanders());
            if (!cards.isEmpty()) theirAntes.add(cards.get(rng.nextInt(cards.size())));
        }
        // pick yours: cheapest first so it's easy to risk a common
        List<PaperCard> mine = new ArrayList<>();
        for (PaperCard pc : DelveProfile.get().collection().toFlatList())
            if (!mine.contains(pc) && !pc.getRules().getType().isBasicLand()) mine.add(pc);
        mine.sort(java.util.Comparator.comparingInt((PaperCard pc) -> pc.getRarity().ordinal())
                .thenComparing(PaperCard::getName));
        if (mine.size() > 40) mine = new ArrayList<>(mine.subList(0, 40));
        StringBuilder theirs = new StringBuilder();
        for (int i = 0; i < theirAntes.size(); i++)
            theirs.append(i > 0 ? ", " : "").append(theirAntes.get(i).getName());
        DelvePickScene.instance().show("Ante: choose your card  (they put up " + theirs + ")", mine, 0, 1, "Back",
                pc -> "Ante", picked -> {
                    Forge.switchScene(this);
                    if (picked.isEmpty()) {
                        clearStakes();
                        return;
                    }
                    myAnte = picked.get(0);
                    DelveProfile.get().useAnte();
                    play.run();
                });
    }

    private void settleStakes(boolean won) {
        DelveProfile prof = DelveProfile.get();
        String msg = null;
        if (betGold > 0) {
            int total = betGold * betOpponents;
            if (won) {
                prof.addGold(total);
                msg = "You win the bet: +" + total + " gold.";
            } else {
                prof.spendGold(Math.min(total, prof.gold()));
                msg = "You lose the bet: -" + total + " gold.";
            }
            DelveAudio.coins();
        } else if (myAnte != null) {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < theirAntes.size(); i++)
                names.append(i > 0 ? ", " : "").append(theirAntes.get(i).getName());
            if (won) {
                prof.addToCollection(new ArrayList<>(theirAntes));
                msg = "You win the ante! " + names + (theirAntes.size() == 1 ? " joins" : " join") + " your collection.";
            } else {
                prof.removeFromCollection(myAnte);
                msg = "You lose the ante. " + myAnte.getName() + " leaves your collection.";
            }
        }
        clearStakes();
        if (msg != null) info(won ? "Victory" : "Defeat", msg, null);
    }

    // ---- Commander practice -----------------------------------------------------

    private void chooseCommanderDeck() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (Deck d : DelveDeckEditScene.legalCommanderDecks()) {
            labels.add(d.getName());
            actions.add(() -> commanderStakes(d));
        }
        List<Deck> loaners = DelveDay.today().loanerCommanders();
        for (Deck d : loaners) {
            labels.add("House deck: " + d.getCommanders().get(0).getName());
            actions.add(() -> commanderStakes(d));
        }
        labels.add("Cancel");
        actions.add(() -> { });
        choose("Commander with the patrons", "Four players, 40 life. Choose your deck.", labels, null, actions);
    }

    private void commanderFoes(List<EnemyData> foes, List<Deck> decks) {
        for (EnemyData e : patrons) {
            if (foes.size() >= 3) break;
            Deck d = DelveDay.today().enemyCommanderDeck(e);
            if (d == null) continue;
            foes.add(e);
            decks.add(d);
        }
    }

    private void commanderStakes(Deck deck) {
        List<EnemyData> foes = new ArrayList<>();
        List<Deck> decks = new ArrayList<>();
        commanderFoes(foes, decks);
        if (foes.isEmpty()) return;
        chooseStakes(foes, decks, () -> playCommander(deck));
    }

    private void playCommander(Deck deck) {
        List<EnemyData> foes = new ArrayList<>();
        List<Deck> decks = new ArrayList<>();
        commanderFoes(foes, decks);
        if (foes.isEmpty()) return;
        EnemyData host = foes.get(0);
        DelveTalkScene.before(DelveTalkScene.TAVERN, host, true, false, "Commander", () -> {
            DelveDuelScene.instance().setupCommander(deck, foes, decks,
                    (won, life) -> DelveTalkScene.after(DelveTalkScene.TAVERN, host, won, true, false, () -> result(won)));
            DelveDuelScene.instance().setReturnLabel("Back to the Tavern");
            Forge.switchScene(DelveDuelScene.instance());
        });
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
