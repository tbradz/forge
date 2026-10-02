package forge.delve;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import forge.Forge;
import forge.adventure.character.EnemySprite;
import forge.adventure.scene.DuelScene;
import forge.assets.FSkin;
import forge.deck.Deck;
import forge.game.GameRules;
import forge.game.GameType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.gamemodes.match.HostedMatch;
import forge.gui.FThreads;
import forge.gui.interfaces.IGuiGame;
import forge.localinstance.properties.ForgePreferences;
import forge.model.FModel;
import forge.player.GamePlayerUtil;
import forge.player.PlayerControllerHuman;
import forge.screens.LoadingOverlay;
import forge.screens.TransitionScreen;
import forge.screens.match.MatchController;
import forge.sound.MusicPlaylist;
import forge.sound.SoundSystem;
import forge.toolbox.FOverlay;
import forge.trackable.TrackableCollection;
import forge.util.ScreenUtil;
import forge.LobbyPlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Hosts one Forge match for a Delve run.
 *
 * The human starts at the run's current life total; when the match ends we read
 * back the result and the life the human finished on, then hand control to the
 * callback (the map scene). Registered as DuelScene's override while a Delve
 * duel is running so Forge's normal end-of-match hooks land here.
 */
public class DelveDuelScene extends DuelScene {
    private static DelveDuelScene object;
    private static final int ENEMY_AVATAR_KEY = 91001;
    private static final int PLAYER_AVATAR_KEY = 91000;

    private HostedMatch match;
    private RegisteredPlayer human;
    private Deck playerDeck;
    private final List<forge.adventure.data.EnemyData> foes = new ArrayList<>();
    private final List<Deck> foeDecks = new ArrayList<>();
    private boolean commander;
    private DelvePerk perk, perk2; // the first foe's perks, if any
    private DelveRun run;          // the dungeon run this duel belongs to (null in town)
    private int lifeCap = DelveRun.MAX_LIFE;
    private String aiProfile = "Default";
    private int enemyLife;
    private boolean boss;
    private int gamesPerMatch = 1;
    private String returnLabel = "Back to the Dungeon";
    private int startingLife;
    private BiConsumer<Boolean, Integer> onFinished; // (won, lifeRemaining)
    private boolean finished;
    private Runnable afterTransition;
    // Pai Gow: 3-card piles as opening hands, 5 life, unlimited mana, no library
    static final int PAI_GOW_LIFE = 5, PAI_GOW_WELLS = 12;
    private boolean paiGow;
    private int paiGowFirst; // 0 = coin flip, 1 = you, 2 = the opponent
    private boolean lastDraw;

    public static DelveDuelScene instance() {
        if (object == null)
            object = new DelveDuelScene();
        return object;
    }

    /** Prepare a dungeon duel. Call before Forge.switchScene(DelveDuelScene.instance()). */
    public void setup(DelveRun run, DelveRun.Node node, BiConsumer<Boolean, Integer> onFinished) {
        int depth = DelveMapGen.depth(run, run.step);
        DelveDay.Tier tier = node.type == DelveRun.NodeType.BOSS ? DelveDay.Tier.BOSS
                : node.type == DelveRun.NodeType.ELITE ? DelveDay.Tier.ELITE
                : depth == 0 ? DelveDay.Tier.EARLY : depth == 2 ? DelveDay.Tier.LATE : DelveDay.Tier.FIGHT;
        Deck enemyDeck = run.day.enemyDeck(node.enemy, tier); // era cards in the enemy's colors
        int foeLife = Math.max(1, node.enemyLife + run.nextFoeLife);
        run.nextFoeLife = 0; // blessings and curses last for one fight
        int life = run.life + (run.has(DelveRelic.IRON_BUCKLER) ? 4 : 0);
        setup(run.deck, life, node.enemy, enemyDeck, foeLife, 1,
                node.type == DelveRun.NodeType.BOSS, onFinished);
        this.perk = node.perk;
        this.perk2 = node.perk2;
        this.run = run;
        this.lifeCap = run.maxLife();
        // the opening fights get a careless opponent; everyone else plays Forge's best AI
        this.aiProfile = node.type == DelveRun.NodeType.FIGHT && depth == 0 ? "Reckless" : "Default";
    }

    /**
     * Prepare any duel (dungeon fight or Castle match).
     *
     * @param enemy        supplies the opponent's name and portrait
     * @param games        games per match (Castle matches are best of 3)
     * @param onFinished   (won, human life at the end)
     */
    public void setup(Deck playerDeck, int playerLife, forge.adventure.data.EnemyData enemy, Deck enemyDeck,
                      int enemyLife, int games, boolean boss, BiConsumer<Boolean, Integer> onFinished) {
        prepare(playerDeck, playerLife, List.of(enemy), List.of(enemyDeck), enemyLife, games, boss, false, onFinished);
        this.returnLabel = games > 1 ? "Back to the Castle" : "Back to the Dungeon";
    }

    /**
     * Prepare a Commander free-for-all: the player against every foe at once,
     * everyone on 40 life with their commander in the command zone.
     */
    public void setupCommander(Deck playerDeck, List<forge.adventure.data.EnemyData> enemies, List<Deck> decks,
                               BiConsumer<Boolean, Integer> onFinished) {
        prepare(playerDeck, 40, enemies, decks, 40, 1, true, true, onFinished);
        this.returnLabel = "Back to the Castle";
    }

    private void prepare(Deck playerDeck, int playerLife, List<forge.adventure.data.EnemyData> enemies, List<Deck> decks,
                         int enemyLife, int games, boolean boss, boolean commander, BiConsumer<Boolean, Integer> onFinished) {
        this.playerDeck = (Deck) playerDeck.copyTo("Delve Deck");
        this.startingLife = playerLife;
        this.foes.clear();
        this.foes.addAll(enemies);
        this.foeDecks.clear();
        this.foeDecks.addAll(decks);
        this.enemyLife = enemyLife;
        this.gamesPerMatch = games;
        this.boss = boss;
        this.commander = commander;
        this.perk = null;
        this.perk2 = null;
        this.run = null;
        this.lifeCap = DelveRun.MAX_LIFE;
        this.aiProfile = "Default";
        this.onFinished = onFinished;
        this.finished = false;
        this.paiGow = false;
        this.paiGowFirst = 0;
        this.lastDraw = false;
        DuelScene.setOverride(this);
    }

    /**
     * Prepare one Pai Gow game: each player's pile is their whole hand.
     *
     * @param first 0 = coin flip, 1 = you go first, 2 = the opponent goes first
     */
    public void setupPaiGow(List<forge.item.PaperCard> myPile, forge.adventure.data.EnemyData enemy,
                            List<forge.item.PaperCard> foePile, int first, BiConsumer<Boolean, Integer> onFinished) {
        Deck mine = new Deck("Your pile"), theirs = new Deck(enemy.getName() + "'s pile");
        mine.getMain().add(myPile);
        theirs.getMain().add(foePile);
        prepare(mine, PAI_GOW_LIFE, List.of(enemy), List.of(theirs), PAI_GOW_LIFE, 1, false, false, onFinished);
        this.paiGow = true;
        this.paiGowFirst = first;
        this.returnLabel = "Back to the table";
    }

    /** True if the last finished match was a draw (nobody won). */
    public boolean lastWasDraw() {
        return lastDraw;
    }

    private void applyPaiGow(RegisteredPlayer p, int pileSize, boolean goesFirst) {
        p.setStartingHand(pileSize);
        List<forge.item.IPaperCard> cmd = new ArrayList<>();
        forge.item.IPaperCard rules = FModel.getMagicDb().getCommonCards().getCard("Delve Pai Gow Rules");
        if (rules != null) cmd.add(rules);
        else System.err.println("Delve: Pai Gow rules card missing");
        if (goesFirst) {
            // a Conspiracy, so it lives in the variant card database
            forge.item.IPaperCard pp = FModel.getMagicDb().getVariantCards().getCard("Power Play");
            if (pp == null) pp = FModel.getMagicDb().getCommonCards().getCard("Power Play");
            if (pp != null) cmd.add(pp);
            else System.err.println("Delve: Power Play missing; starting player falls back to a coin flip");
        }
        if (!cmd.isEmpty()) p.addExtraCardsInCommandZone(cmd);
        forge.item.IPaperCard well = FModel.getMagicDb().getCommonCards().getCard("Pai Gow Wellspring");
        if (well != null) {
            List<forge.item.IPaperCard> bf = new ArrayList<>();
            for (int i = 0; i < PAI_GOW_WELLS; i++) bf.add(well);
            p.addExtraCardsOnBattlefield(bf);
        } else System.err.println("Delve: Pai Gow wellspring card missing");
    }

    @Override
    public void enter() {
        forge.Adventure.getInstance().renderTransitionScreen = false;
        SoundSystem.instance.stopBackgroundMusic();
        EnumSet<GameType> variants = commander ? EnumSet.of(GameType.Commander) : EnumSet.of(GameType.Adventure);
        int nPlayers = foes.size() + 1;

        human = RegisteredPlayer.forVariants(nPlayers, variants, playerDeck, null, false, null, null);
        LobbyPlayer me = GamePlayerUtil.getGuiPlayer();
        if (me.getName() == null || me.getName().trim().isEmpty())
            me.setName("You");
        try { // the Delve hero is your portrait
            DelveProfile prof = DelveProfile.get();
            TextureRegion heroAvatar = forge.adventure.data.HeroListData.instance()
                    .getAvatar(prof.heroRace(), prof.heroFemale(), 0);
            if (heroAvatar != null) {
                FSkin.getAvatars().put(PLAYER_AVATAR_KEY, heroAvatar);
                me.setAvatarIndex(PLAYER_AVATAR_KEY);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (forge.assets.FSkin.getSleeves().containsKey(DelveProfile.get().currentSleeve()))
            me.setSleeveIndex(DelveProfile.get().currentSleeve()); // bought at the Outfitter
        human.setPlayer(me);
        human.setTeamNumber(0);
        human.setStartingLife(startingLife);
        if (run != null) applyRelics(human);
        if (paiGow) applyPaiGow(human, playerDeck.getMain().countAll(), paiGowFirst == 1);

        List<RegisteredPlayer> players = new ArrayList<>();
        for (int i = 0; i < foes.size(); i++) {
            forge.adventure.data.EnemyData enemy = foes.get(i);
            RegisteredPlayer ai = RegisteredPlayer.forVariants(nPlayers, variants, foeDecks.get(i), null, false, null, null);
            LobbyPlayer aiLobby = GamePlayerUtil.createAiPlayer(enemy.getName(), aiProfile);
            try {
                TextureRegion avatar = new EnemySprite(enemy).getAvatar();
                if (avatar != null) {
                    avatar = new TextureRegion(avatar);
                    avatar.flip(true, false);
                    FSkin.getAvatars().put(ENEMY_AVATAR_KEY + i, avatar);
                    aiLobby.setAvatarIndex(ENEMY_AVATAR_KEY + i);
                }
            } catch (Exception e) {
                // avatar is cosmetic; never block a duel on it
                e.printStackTrace();
            }
            ai.setPlayer(aiLobby);
            ai.setTeamNumber(i + 1); // free-for-all: everyone on their own team
            ai.setStartingLife(enemyLife);
            if (i == 0 && perk != null) applyPerk(ai, foeDecks.get(i));
            if (paiGow) applyPaiGow(ai, foeDecks.get(i).getMain().countAll(), paiGowFirst == 2);
            players.add(ai);
        }
        players.add(human);

        Map<RegisteredPlayer, IGuiGame> guiMap = new HashMap<>();
        guiMap.put(human, MatchController.instance);

        GameRules rules = new GameRules(commander ? GameType.Commander : GameType.Adventure);
        rules.setAppliedVariants(variants);
        rules.setGamesPerMatch(gamesPerMatch);
        rules.setPlayForAnte(false);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);
        if (paiGow) rules.setMulligans(false);

        match = MatchController.hostMatch();
        match.startMatch(rules, variants, players, guiMap,
                boss ? MusicPlaylist.BOSS : MusicPlaylist.MATCH);
        MatchController.instance.setGameView(match.getGameView());
        for (Player p : match.getGame().getPlayers()) {
            if (p.getController() instanceof PlayerControllerHuman) {
                PlayerControllerHuman hc = (PlayerControllerHuman) p.getController();
                hc.setGui(MatchController.instance);
                MatchController.instance.setOriginalGameController(p.getView(), hc);
                MatchController.instance.openView(new TrackableCollection<>(p.getView()));
            }
        }

        // ForgeScene.enter(): show the match screen
        FOverlay.hideAll();
        if (getScreen() != null) {
            getScreen().setSize(Forge.getScreenWidth(), Forge.getScreenHeight());
            Forge.openScreen(getScreen());
        }
        Gdx.input.setInputProcessor(Forge.getInputProcessor());
        new LoadingOverlay(null).show();
    }

    /** Called by Forge's match controller / win-lose screen when the match is over. */
    @Override
    public void GameEnd() {
        if (finished) return;
        finished = true;
        boolean won = false;
        int life = 0;
        try {
            won = human == match.getGame().getMatch().getWinner();
            lastDraw = match.getGame().getOutcome() != null && match.getGame().getOutcome().isDraw();
            for (Player p : match.getGame().getPlayers()) {
                if (p.getController() instanceof PlayerControllerHuman)
                    life = p.getLife();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        final boolean fWon = won;
        final int fLife = Math.max(0, Math.min(life, lifeCap));
        // keep the override until exitDuelScene() has been routed here too
        afterTransition = () -> Gdx.app.postRunnable(() -> {
            DuelScene.setOverride(null);
            Forge.clearTransitionScreen();
            Forge.clearScreenStack();
            if (onFinished != null)
                onFinished.accept(fWon, fLife);
        });
    }

    /** Override the win/lose screen's return button text (call after setup). */
    public void setReturnLabel(String label) {
        this.returnLabel = label;
    }

    /** Relic cards start in your command zone; Lucky Coin adds a card to your opening hand. */
    private void applyRelics(RegisteredPlayer human) {
        List<forge.item.IPaperCard> cmd = new ArrayList<>();
        for (DelveRelic r : run.relics) {
            forge.item.IPaperCard c = r.card();
            if (c != null) cmd.add(c);
            else if (r.cardName != null) System.err.println("Delve: relic card missing: " + r.cardName);
        }
        if (!cmd.isEmpty()) human.addExtraCardsInCommandZone(cmd);
        if (run.has(DelveRelic.LUCKY_COIN)) human.setStartingHand(human.getStartingHand() + 1);
    }

    /** The perk cards start in the boss's command zone; Rampant also starts with a land in play. */
    private void applyPerk(RegisteredPlayer ai, Deck deck) {
        if (perk2 != null && perk2.card() != null) {
            List<forge.item.IPaperCard> extra = new ArrayList<>();
            extra.add(perk2.card());
            ai.addExtraCardsInCommandZone(extra);
        }
        forge.item.IPaperCard card = perk.card();
        if (card == null) {
            System.err.println("Delve: perk card missing: " + perk.cardName);
            return;
        }
        List<forge.item.IPaperCard> cmd = new ArrayList<>();
        cmd.add(card);
        ai.addExtraCardsInCommandZone(cmd);
        if (perk == DelvePerk.RAMPANT) {
            forge.item.PaperCard land = null;
            for (java.util.Map.Entry<forge.item.PaperCard, Integer> e : deck.getMain())
                if (e.getKey().getRules().getType().isBasicLand()) { land = e.getKey(); break; }
            if (land != null) {
                List<forge.item.IPaperCard> bf = new ArrayList<>();
                bf.add(land);
                ai.addExtraCardsOnBattlefield(bf);
            }
        }
    }

    @Override
    public String returnButtonLabel() {
        return returnLabel;
    }

    @Override
    public forge.assets.FSkinTexture matchBackground() {
        return boss
                ? forge.assets.FSkinTexture.ADV_BG_CASTLE : forge.assets.FSkinTexture.ADV_BG_DUNGEON;
    }

    @Override
    public boolean hasCallbackExit() {
        return false;
    }

    @Override
    public void exitDuelScene() {
        TransitionScreen t = new TransitionScreen(afterTransition,
                ScreenUtil.getInstance().getLastScreenTexture(), false, false);
        t.afterMatch = true;
        Forge.setTransitionScreen(t);
    }
}
