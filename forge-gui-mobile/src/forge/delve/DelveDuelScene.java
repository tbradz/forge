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
    private Deck enemyDeck;
    private forge.adventure.data.EnemyData enemy;
    private int enemyLife;
    private boolean boss;
    private int gamesPerMatch = 1;
    private String returnLabel = "Back to the Dungeon";
    private int startingLife;
    private BiConsumer<Boolean, Integer> onFinished; // (won, lifeRemaining)
    private boolean finished;
    private Runnable afterTransition;

    public static DelveDuelScene instance() {
        if (object == null)
            object = new DelveDuelScene();
        return object;
    }

    /** Prepare a dungeon duel. Call before Forge.switchScene(DelveDuelScene.instance()). */
    public void setup(DelveRun run, DelveRun.Node node, BiConsumer<Boolean, Integer> onFinished) {
        DelveDay.Tier tier = node.type == DelveRun.NodeType.BOSS ? DelveDay.Tier.BOSS
                : node.type == DelveRun.NodeType.ELITE ? DelveDay.Tier.ELITE : DelveDay.Tier.FIGHT;
        Deck enemyDeck = run.day.enemyDeck(node.enemy, tier); // era cards in the enemy's colors
        setup(run.deck, run.life, node.enemy, enemyDeck, node.enemyLife, 1,
                node.type == DelveRun.NodeType.BOSS, onFinished);
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
        this.playerDeck = (Deck) playerDeck.copyTo("Delve Deck");
        this.startingLife = playerLife;
        this.enemy = enemy;
        this.enemyDeck = enemyDeck;
        this.enemyLife = enemyLife;
        this.gamesPerMatch = games;
        this.boss = boss;
        this.onFinished = onFinished;
        this.finished = false;
        this.returnLabel = games > 1 ? "Back to the Castle" : "Back to the Dungeon";
        DuelScene.setOverride(this);
    }

    @Override
    public void enter() {
        forge.Adventure.getInstance().renderTransitionScreen = false;
        SoundSystem.instance.stopBackgroundMusic();
        EnumSet<GameType> variants = EnumSet.of(GameType.Adventure);

        human = RegisteredPlayer.forVariants(2, variants, playerDeck, null, false, null, null);
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
        human.setPlayer(me);
        human.setTeamNumber(0);
        human.setStartingLife(startingLife);

        RegisteredPlayer ai = RegisteredPlayer.forVariants(2, variants, enemyDeck, null, false, null, null);
        LobbyPlayer aiLobby = GamePlayerUtil.createAiPlayer(enemy.getName(), "");
        try {
            TextureRegion avatar = new EnemySprite(enemy).getAvatar();
            if (avatar != null) {
                avatar = new TextureRegion(avatar);
                avatar.flip(true, false);
                FSkin.getAvatars().put(ENEMY_AVATAR_KEY, avatar);
                aiLobby.setAvatarIndex(ENEMY_AVATAR_KEY);
            }
        } catch (Exception e) {
            // avatar is cosmetic; never block a duel on it
            e.printStackTrace();
        }
        ai.setPlayer(aiLobby);
        ai.setTeamNumber(1);
        ai.setStartingLife(enemyLife);

        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(ai);
        players.add(human);

        Map<RegisteredPlayer, IGuiGame> guiMap = new HashMap<>();
        guiMap.put(human, MatchController.instance);

        GameRules rules = new GameRules(GameType.Adventure);
        rules.setGamesPerMatch(gamesPerMatch);
        rules.setPlayForAnte(false);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);

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
            for (Player p : match.getGame().getPlayers()) {
                if (p.getController() instanceof PlayerControllerHuman)
                    life = p.getLife();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        final boolean fWon = won;
        final int fLife = Math.max(0, Math.min(life, DelveRun.MAX_LIFE));
        // keep the override until exitDuelScene() has been routed here too
        afterTransition = () -> Gdx.app.postRunnable(() -> {
            DuelScene.setOverride(null);
            Forge.clearTransitionScreen();
            Forge.clearScreenStack();
            if (onFinished != null)
                onFinished.accept(fWon, fLife);
        });
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
