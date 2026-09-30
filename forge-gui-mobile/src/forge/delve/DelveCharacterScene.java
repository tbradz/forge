package forge.delve;

import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.adventure.data.HeroListData;

/**
 * Character creation: pick one of Adventure's hero races and male/female. The
 * chosen hero walks the dungeon map and is your portrait in duels.
 */
public class DelveCharacterScene extends DelveScene {
    private static DelveCharacterScene object;

    private int race;
    private boolean female;
    private Runnable onDone;

    public static DelveCharacterScene instance() {
        if (object == null)
            object = new DelveCharacterScene();
        return object;
    }

    /** Open the creator; {@code onDone} runs after the player confirms. */
    public void open(Runnable onDone) {
        DelveProfile p = DelveProfile.get();
        this.race = p.heroRace();
        this.female = p.heroFemale();
        this.onDone = onDone;
        Forge.switchScene(this);
    }

    @Override
    public void enter() {
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        int races = HeroListData.instance().getRaces().size;
        race = ((race % races) + races) % races;
        String raceName = HeroListData.instance().getRaces().get(race);

        title("Create your character");
        label("This is who walks into the dungeon.", 0, 30, W, 14, Align.center);

        image("ui/delve/plate_glow.png", W / 2f - 52, 164, 104, 56);
        CharacterSprite hero = new CharacterSprite(HeroListData.instance().getHero(race, female));
        hero.setAnimation(CharacterSprite.AnimationTypes.Walk);
        hero.setDirection(CharacterSprite.AnimationDirections.Down);
        Group g = standing(hero, 7f);
        standAt(g, W / 2f, 186);
        track(g);

        label("[%130]" + raceName + (female ? " (female)" : " (male)"), 0, 218, W, 20, Align.center);

        button("<", 120, 110, 40, 40, () -> { race--; build(); });
        button(">", 320, 110, 40, 40, () -> { race++; build(); });
        button(female ? "Switch to male" : "Switch to female", 60, 242, 130, 22, () -> { female = !female; build(); });
        button("[GOLD]Done", 290, 242, 130, 22, this::done);
    }

    private void done() {
        DelveProfile.get().setCharacter(race, female);
        Runnable cb = onDone;
        onDone = null;
        if (cb != null) cb.run();
        else Forge.switchScene(DelveHubScene.instance());
    }

    @Override
    public boolean back() {
        done();
        return true;
    }
}
