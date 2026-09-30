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
    private DelveRun.Node node;
    private int startingLife;
    private BiConsumer<Boolean, Integer> onFinished; // (won, lifeRemaining)
    private boolean finished;
    private Runnable afterTransition;

    public static DelveDuelScene instance() {
        if (object == null)
            object = new DelveDuelScene();
        return object;
    }

    /** Prepare a duel. Call before Forge.switchScene(DelveDuelScene.instance()). */
    public void setup(DelveRun run, DelveRun.Node node, BiConsumer<Boolean, Integer> onFinished) {
        this.node = node;
        this.playerDeck = (Deck) run.deck.copyTo("Delve Run Deck");
        this.startingLife = run.life;
        this.onFinished = onFinished;
        this.enemyDeck = node.enemy.generateDeck(false, false);
        if (enemyDeck == null)
            enemyDeck = (Deck) run.deck.copyTo("Mirror"); // missing deck data: mirror match
        this.finished = false;
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
        LobbyPlayer aiLobby = GamePlayerUtil.createAiPlayer(node.enemy.getName(), "");
        try {
            TextureRegion avatar = new EnemySprite(node.enemy).getAvatar();
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
        ai.setStartingLife(node.enemyLife);

        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(ai);
        players.add(human);

        Map<RegisteredPlayer, IGuiGame> guiMap = new HashMap<>();
        guiMap.put(human, MatchController.instance);

        GameRules rules = new GameRules(GameType.Adventure);
        rules.setGamesPerMatch(1);
        rules.setPlayForAnte(false);
        rules.setManaBurn(false);
        rules.setWarnAboutAICards(false);

        match = MatchController.hostMatch();
        match.startMatch(rules, variants, players, guiMap,
                node.type == DelveRun.NodeType.BOSS ? MusicPlaylist.BOSS : MusicPlaylist.MATCH);
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
        return "Back to the Dungeon";
    }

    @Override
    public forge.assets.FSkinTexture matchBackground() {
        return node != null && node.type == DelveRun.NodeType.BOSS
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
