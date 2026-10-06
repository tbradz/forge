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

    // ---- the room: everyone in tonight, where they are ---------------------------------------

    /**
     * Where people can be (feet, layout units). The first six are behind the three tables (tavern_fg.png
     * is drawn over them, so they look seated); the rest are standing spots on the floor.
     */
    private static final float[][] SPOTS = { // x, feet y, how high the name tag sits (pairs at a table alternate)
            {52, 248, 58}, {92, 248, 48}, {220, 248, 58}, {260, 248, 48}, {388, 248, 58}, {428, 248, 48},
            {146, 202, 52}, {334, 206, 52}, {184, 174, 52}, {404, 186, 52}};
    /** Bram, by the hearth */
    private static final float[] BRAM_SPOT = {296, 170, 52};
    private static final String BRAM_ATLAS = "sprites/enemy/humanoid/human/peasant/farmer.atlas";

    private void build() {
        clearScreen();
        rollPatrons();
        int day = DelveProfile.get().day();
        label("[%90][GOLD]The Tavern", 8, 5, 120, 16, Align.left);
        label("[%80]Tonight: " + wins + " won, " + losses + " lost", 352, 5, 120, 16, Align.right);
        button("[GOLD]Commander with the patrons", 128, 4, 154, 17, this::chooseCommanderDeck)
                .setDisabled(patrons.size() < 3);
        button("Leave", 288, 4, 58, 17, () -> Forge.switchScene(DelveHubScene.instance()));
        label("[%75]Click someone to talk or play. No fee; play for fun, a bet or an ante. Patrons play "
                + fit(DelveDay.today().edition.getName(), 28) + " decks that grow stronger as you do.", 20, 22, W - 40, 22, Align.center);

        // tonight's crowd in shuffled spots: the four patrons who'll play you, and the rumor regulars
        List<float[]> spots = new ArrayList<>(java.util.Arrays.asList(SPOTS));
        Collections.shuffle(spots, new Random(day * 104729L + 3));
        List<Runnable> tags = new ArrayList<>(); // name tags and click areas go above the tables
        int s = 0;
        for (EnemyData e : patrons) {
            float[] at = spots.get(s++);
            com.badlogic.gdx.scenes.scene2d.Group g = person(new forge.adventure.character.EnemySprite(e), at);
            tags.add(() -> tag(g, at, "[GOLD]" + DelvePersona.name(e), () -> patronMenu(e)));
        }
        for (DelveRegulars r : DelveRegulars.tonight(day)) {
            float[] at = spots.get(s++);
            try {
                com.badlogic.gdx.scenes.scene2d.Group g = person(new forge.adventure.character.CharacterSprite(r.atlas), at);
                tags.add(() -> tag(g, at, "[#c0e0ff]" + r.name, () -> DelveTalkScene.speak(DelveTalkScene.TAVERN, r.atlas,
                        r.name, "[%85]" + r.role, r.rumor(day), "[GOLD]Thanks", () -> Forge.switchScene(this))));
            } catch (Exception ex) {
                ex.printStackTrace(); // cosmetic: a missing sprite just means one fewer regular
            }
        }
        try {
            com.badlogic.gdx.scenes.scene2d.Group bram = person(new forge.adventure.character.CharacterSprite(BRAM_ATLAS), BRAM_SPOT);
            tags.add(() -> tag(bram, BRAM_SPOT, "[GOLD]Bram", () -> DelveTourScene.instance().play(false, () -> Forge.switchScene(this))));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        image("ui/delve/tavern_fg.png", 0, 0, W, H); // the tables, in front of whoever sits at them
        for (Runnable t : tags) t.run();
    }

    /** A person standing at {@code at} (feet), facing the middle of the room, scaled to a common height. */
    private com.badlogic.gdx.scenes.scene2d.Group person(forge.adventure.character.CharacterSprite sprite, float[] at) {
        sprite.setAnimation(forge.adventure.character.CharacterSprite.AnimationTypes.Idle);
        sprite.setDirection(at[0] < W / 2 ? forge.adventure.character.CharacterSprite.AnimationDirections.Right
                : forge.adventure.character.CharacterSprite.AnimationDirections.Left);
        float h = Math.max(sprite.getHeight(), sprite.getWidth());
        float scale = h > 0 ? Math.min(2f, 40f / h) : 2f;
        com.badlogic.gdx.scenes.scene2d.Group g = standing(sprite, scale);
        standAt(g, at[0], at[1]);
        return track(g);
    }

    /** A name over someone's head and an invisible click area over them. */
    private void tag(com.badlogic.gdx.scenes.scene2d.Group g, float[] at, String name, Runnable onClick) {
        label("[%55]" + name, at[0] - 40, at[1] - at[2], 80, 10, Align.center);
        com.badlogic.gdx.scenes.scene2d.Actor hit = new com.badlogic.gdx.scenes.scene2d.Actor();
        hit.setBounds(at[0] - 14, H - at[1], 28, 46);
        hit.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y) {
                onClick.run();
            }
        });
        track(hit);
    }

    /** A patron: play them, or have a word first. Their deck strength grows with your progress. */
    private void patronMenu(EnemyData e) {
        List<String> labels = new ArrayList<>(List.of("[GOLD]Play a game", "Talk", "Back"));
        List<Runnable> actions = new ArrayList<>(List.of(
                () -> chooseDeck(e),
                () -> DelveTalkScene.say(DelveTalkScene.TAVERN, e, DelvePersona.Moment.GREET, true, false, null, "Back",
                        () -> Forge.switchScene(this)),
                () -> { }));
        choose(DelvePersona.title(e), "[%80]" + colorsOf(e) + "[WHITE]\n[%80]Their deck: [GOLD]" + strengthName(patronTier())
                + "[WHITE] (it grows as you clear dungeons)", labels, null, actions);
    }

    // ---- patron decks grow with you ------------------------------------------------------------

    /**
     * How strong the patrons' decks are: your progress (days played, plus three per set tier you've
     * unlocked) steps them from a gentle deck up to boss-quality, a little at a time.
     */
    static DelveDay.Tier patronTier() {
        DelveProfile p = DelveProfile.get();
        int progress = p.day() + 3 * p.topTier();
        return progress < 4 ? DelveDay.Tier.EARLY : progress < 8 ? DelveDay.Tier.FIGHT : progress < 14 ? DelveDay.Tier.LATE
                : progress < 22 ? DelveDay.Tier.ELITE : DelveDay.Tier.BOSS;
    }

    private static String strengthName(DelveDay.Tier t) {
        switch (t) {
            case EARLY: return "Green";
            case FIGHT: return "Capable";
            case LATE: return "Seasoned";
            case ELITE: return "Veteran";
            default: return "Fearsome";
        }
    }

    private static Deck patronDeck(EnemyData e) {
        return DelveDay.today().enemyDeck(e, patronTier());
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
            actions.add(() -> chooseStakes(List.of(foe), List.of(patronDeck(foe)),
                    () -> play(d, foe)));
        }
        labels.add("Cancel");
        actions.add(() -> { });
        choose("Play " + DelvePersona.title(foe), "Choose your deck.", labels, null, actions);
    }

    private void play(Deck deck, EnemyData foe) {
        Deck foeDeck = patronDeck(foe);
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
