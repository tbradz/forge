package forge.delve;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.adventure.character.EnemySprite;
import forge.adventure.data.EnemyData;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * An opponent says something before or after a match: they stand on the left, your
 * hero on the right, and their line sits in a dialogue box (the same look as Bram's
 * intro). One instance per backdrop: the dungeon, the Tavern, the Card Shop, the Castle.
 */
public class DelveTalkScene extends DelveScene {
    public static final String DUNGEON = "ui/delve_panel.json", TAVERN = "ui/delve_tavern.json",
            SHOP = "ui/delve_shop.json", CASTLE = "ui/delve_castle.json";

    private static final Map<String, DelveTalkScene> scenes = new HashMap<>();
    private static final Random rng = new Random();

    private EnemyData who;
    private String speaker, line, buttonText, heading;
    private Runnable then;

    private final String layout;

    private DelveTalkScene(String layout) {
        super(layout);
        this.layout = layout;
    }

    private static DelveTalkScene at(String layout) {
        return scenes.computeIfAbsent(layout, DelveTalkScene::new);
    }

    /**
     * Show an opponent's line for {@code moment}, then run {@code then} when the button is pressed.
     *
     * @param town  town folk are introduced by their personal name ("Mara the Challenger")
     * @param boss  bosses use their own intro and insult lines
     */
    public static void say(String layout, EnemyData who, DelvePersona.Moment moment, boolean town, boolean boss,
                           String heading, String buttonText, Runnable then) {
        if (who == null) {
            if (then != null) then.run();
            return;
        }
        DelveTalkScene s = at(layout);
        s.who = who;
        s.speaker = town ? DelvePersona.title(who) : who.getName();
        s.line = DelvePersona.line(who, moment, boss, rng);
        s.heading = heading;
        s.buttonText = buttonText;
        s.then = then;
        Forge.switchScene(s);
    }

    /** Before a match: "Let's play". */
    public static void before(String layout, EnemyData who, boolean town, boolean boss, String heading, Runnable then) {
        say(layout, who, DelvePersona.Moment.GREET, town, boss, heading, "[GOLD]Let's play", then);
    }

    /** After a match: their reaction to winning or losing. */
    public static void after(String layout, EnemyData who, boolean playerWon, boolean town, boolean boss, Runnable then) {
        say(layout, who, playerWon ? DelvePersona.Moment.THEY_LOST : DelvePersona.Moment.THEY_WON, town, boss,
                playerWon ? "[GREEN]You won" : "[RED]You lost", "Continue", then);
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        if (who == null) return;
        if (DUNGEON.equals(layout)) atmosphere(new float[][]{{60, 160}, {420, 160}});
        TextureRegion face = null;
        try { // the opponent on the left, scaled to a common height (some sprites are huge)
            EnemySprite foe = new EnemySprite(who);
            foe.setAnimation(CharacterSprite.AnimationTypes.Idle);
            foe.setDirection(CharacterSprite.AnimationDirections.Right);
            float h = Math.max(foe.getHeight(), foe.getWidth());
            float scale = h > 0 ? Math.min(4f, 84f / h) : 4f;
            com.badlogic.gdx.scenes.scene2d.Group g = standing(foe, scale);
            standAt(g, 120, 136);
            track(g);
            face = foe.getAvatar();
        } catch (Exception e) {
            e.printStackTrace(); // cosmetic
        }
        try {
            CharacterSprite hero = new CharacterSprite(DelveProfile.get().heroAtlas());
            hero.setAnimation(CharacterSprite.AnimationTypes.Idle);
            hero.setDirection(CharacterSprite.AnimationDirections.Left);
            com.badlogic.gdx.scenes.scene2d.Group g = standing(hero, 4f);
            standAt(g, 370, 136);
            track(g);
        } catch (Exception e) {
            e.printStackTrace();
        }

        image("ui/delve/panel.png", 12, 142, 456, 104);
        if (face != null) {
            Image f = new Image(new TextureRegion(face));
            f.setBounds(24, H - 154 - 52, 52, 52);
            f.setTouchable(Touchable.disabled);
            track(f);
        }
        label("[%95][GOLD]" + speaker, 86, 150, 260, 13, Align.left);
        if (heading != null) label("[%85]" + heading, 300, 150, 156, 13, Align.right);
        boolean words = DelvePersona.talks(who) && !line.startsWith("*");
        com.github.tommyettinger.textra.TextraLabel text = label("[%100]" + (words ? "\"" + line + "\"" : "[#c0b090]" + line),
                86, 172, 372, 44, Align.topLeft);
        text.setAlignment(Align.topLeft);
        button(buttonText, 330, 222, 110, 18, this::done);
    }

    private void done() {
        Runnable cb = then;
        then = null;
        if (cb != null) cb.run();
    }

    @Override
    public boolean back() {
        done();
        return true;
    }
}
