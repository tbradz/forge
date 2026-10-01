package forge.delve;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.character.CharacterSprite;

/**
 * The new-save intro: you arrive at the Tavern and Bram the innkeeper welcomes the
 * town's new delver, explaining how things work one page at a time. Replayable from
 * the Tavern ("Talk to Bram").
 */
public class DelveIntroScene extends DelveScene {
    private static DelveIntroScene object;
    private static final String INNKEEPER = "sprites/enemy/humanoid/human/peasant/farmer.atlas";

    private static final String[][] PAGES = {
            {"Well now, a new face! You'll be the delver the town council sent for. Come in out of the cold. "
                    + "I'm Bram, I keep the inn here, and anyone who goes down into the ruins drinks here first.", null},
            {"Under the old ruins east of town there's a dungeon, and it shifts every single day. "
                    + "Each morning you get one trip down. Folk here are counting on someone to clear it.", "The Dungeon Gate"},
            {"Before you go in, you'll crack open six booster packs, all from the set the dungeon is steeped in. "
                    + "Eighth Edition, to start. Build a 40-card deck from what you pull. Basic lands are free.", "Prerelease"},
            {"Down below, you pick your path room by room: fights, strange happenings, merchants and quiet places to rest. "
                    + "Elites guard relics that help you for the rest of the trip. The boss at the bottom has tricks of its own.",
                    "The dungeon"},
            {"Beat the boss and you bring home every coin you found, plus a reward: packs, gold, cards from your deck, "
                    + "or the whole deck locked just as it is. Clearing it also opens the next set's dungeon. "
                    + "Fall, and you keep only part of your gold.", "Clearing it"},
            {"Evenings are for the Castle: a tournament or a Commander pod, once a night. "
                    + "Short on coin? My tables are free, and the regulars will play you as long as you like. "
                    + "Sleep at your house to start a new day.", "Evenings"},
            {"The Card Shop sells singles and packs, the Outfitter sells sleeves, and your house is where you build decks "
                    + "from the cards you collect. That's everything. The gate opens at dawn. Good luck, delver.", "Around town"},
    };

    private int page;
    private Runnable onDone;
    private boolean firstVisit;

    private DelveIntroScene() {
        super("ui/delve_tavern.json");
    }

    public static DelveIntroScene instance() {
        if (object == null)
            object = new DelveIntroScene();
        return object;
    }

    /** Play the intro; {@code firstVisit} shows your hero walking in. */
    public void play(boolean firstVisit, Runnable onDone) {
        this.firstVisit = firstVisit;
        this.onDone = onDone;
        this.page = 0;
        Forge.switchScene(this);
    }

    @Override
    public void enter() {
        DelveAudio.town();
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        label("[%90][GOLD]The Tavern", 8, 5, 200, 16, Align.left);
        label("[%80]" + (page + 1) + " / " + PAGES.length, 240, 5, 232, 16, Align.right);

        // Bram on the left; on a first visit the player walks in from the right
        Sprite avatar = null;
        try {
            CharacterSprite bram = new CharacterSprite(INNKEEPER);
            bram.setAnimation(CharacterSprite.AnimationTypes.Idle);
            bram.setDirection(CharacterSprite.AnimationDirections.Right);
            com.badlogic.gdx.scenes.scene2d.Group g = standing(bram, 4f);
            standAt(g, 110, 136);
            track(g);
            avatar = bram.getAvatar();
        } catch (Exception e) {
            e.printStackTrace(); // the innkeeper is cosmetic
        }
        try {
            CharacterSprite hero = new CharacterSprite(DelveProfile.get().heroAtlas());
            hero.setAnimation(CharacterSprite.AnimationTypes.Idle);
            hero.setDirection(CharacterSprite.AnimationDirections.Left);
            com.badlogic.gdx.scenes.scene2d.Group g = standing(hero, 4f);
            if (firstVisit && page == 0) {
                hero.setAnimation(CharacterSprite.AnimationTypes.Walk);
                standAt(g, W + 30, 136);
                g.addAction(Actions.sequence(Actions.moveTo(370, H - 136, 1.6f, Interpolation.linear),
                        Actions.run(() -> hero.setAnimation(CharacterSprite.AnimationTypes.Idle))));
            } else {
                standAt(g, 370, 136);
            }
            track(g);
        } catch (Exception e) {
            e.printStackTrace();
        }

        // dialogue box along the bottom
        image("ui/delve/panel.png", 12, 142, 456, 104);
        if (avatar != null) {
            Image face = new Image(new com.badlogic.gdx.graphics.g2d.TextureRegion(avatar));
            face.setBounds(24, H - 154 - 52, 52, 52);
            face.setTouchable(Touchable.disabled);
            track(face);
        }
        label("[%95][GOLD]Bram, the innkeeper", 86, 150, 200, 13, Align.left);
        String heading = PAGES[page][1];
        if (heading != null) label("[%80][#c0a060]" + heading, 250, 150, 206, 13, Align.right);
        com.github.tommyettinger.textra.TextraLabel text = label("[%85]" + PAGES[page][0], 86, 166, 372, 52, Align.topLeft);
        text.setAlignment(Align.topLeft);

        boolean last = page == PAGES.length - 1;
        if (page > 0) button("[%85]Back", 86, 222, 70, 18, () -> { page--; build(); });
        button(last ? "[GOLD]Head into town" : "[GOLD]Next", 320, 222, 100, 18, () -> {
            if (last) finish();
            else { page++; build(); }
        });
        if (!last) button("[%75]Skip", 424, 223, 38, 16, this::finish);
    }

    private void finish() {
        DelveProfile.get().setIntroSeen();
        Runnable cb = onDone;
        onDone = null;
        if (cb != null) cb.run();
        else Forge.switchScene(DelveHubScene.instance());
    }

    @Override
    public boolean back() {
        finish();
        return true;
    }
}
