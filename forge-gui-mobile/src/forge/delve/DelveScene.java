package forge.delve;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.Forge;
import forge.adventure.scene.UIScene;
import forge.adventure.util.Controls;

import java.util.ArrayList;
import java.util.List;

/**
 * Base for Delve's menu-style screens: a backdrop from delve_panel.json plus
 * widgets placed in code. Coordinates are the 480x270 layout with y measured
 * from the top (like the JSON layouts), converted to libGDX's y-up here.
 */
abstract class DelveScene extends UIScene {
    protected static final float W = 480f, H = 270f;
    private final List<Actor> dynamic = new ArrayList<>();

    DelveScene() {
        this("ui/delve_panel.json");
    }

    DelveScene(String layout) {
        super(layout);
    }

    /**
     * An animated Adventure sprite (hero, enemy, item) drawn at {@code scale}, anchored at
     * its bottom-centre so it can be placed by the point it stands on.
     */
    protected static com.badlogic.gdx.scenes.scene2d.Group standing(
            forge.adventure.character.CharacterSprite sprite, float scale) {
        com.badlogic.gdx.scenes.scene2d.Group g = new com.badlogic.gdx.scenes.scene2d.Group() {
            @Override
            public void act(float delta) {
                super.act(delta);
                sprite.setPosition(-sprite.getWidth() / 2f, 0);
            }
        };
        g.setTransform(true);
        g.setScale(scale);
        g.addActor(sprite);
        g.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        return g;
    }

    /** Place a standing() group so its feet are at (x, yTop) in top-down layout coordinates. */
    protected static void standAt(com.badlogic.gdx.scenes.scene2d.Actor g, float x, float yTop) {
        g.setPosition(x, H - yTop);
    }

    protected com.badlogic.gdx.scenes.scene2d.ui.Image image(String path, float x, float yTop, float w, float h) {
        com.badlogic.gdx.graphics.Texture t = Forge.getAssets().getTexture(
                forge.adventure.util.Config.instance().getFile(path), true, false);
        com.badlogic.gdx.scenes.scene2d.ui.Image img = new com.badlogic.gdx.scenes.scene2d.ui.Image(t);
        img.setBounds(x, H - yTop - h, w, h);
        img.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
        return track(img);
    }

    protected TextraButton button(String text, float x, float yTop, float w, float h, Runnable onClick) {
        TextraButton[] self = new TextraButton[1];
        TextraButton b = Controls.newTextButton(text, () -> {
            if (!self[0].isDisabled()) onClick.run(); // libGDX still delivers clicks to disabled buttons
        });
        self[0] = b;
        DelveDialogs.dimWhenDisabled(b);
        b.setBounds(x, H - yTop - h, w, h);
        return track(b);
    }

    protected TextraLabel label(String text, float x, float yTop, float w, float h, int align) {
        TextraLabel l = Controls.newTextraLabel(text);
        l.setAlignment(align);
        l.setWrap(true);
        l.setBounds(x, H - yTop - h, w, h);
        return track(l);
    }

    protected TextraLabel title(String text) {
        return label("[%130]" + text, 0, 8, W, 22, Align.center);
    }

    protected <T extends Actor> T track(T a) {
        ui.addActor(a);
        dynamic.add(a);
        if (a instanceof TextraButton)
            addToSelectable(a);
        return a;
    }

    /** Remove everything placed in code (keeps the backdrop). */
    protected void clearScreen() {
        for (Actor a : dynamic)
            a.remove();
        dynamic.clear();
        clearSelectable();
    }

    protected void info(String title, String text, Runnable then) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make(title);
        DelveDialogs.body(d, text);
        DelveDialogs.rowButtons(d, new String[]{Forge.getLocalizer().getMessage("lblOK")}, new Runnable[]{() -> {
            removeDialog();
            if (then != null) then.run();
        }});
        showDialog(d);
    }

    /** A dialog with one button per choice; each button closes the dialog and runs its action. */
    protected void choose(String title, String text, List<String> labels, List<Boolean> enabled, List<Runnable> actions) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make(title);
        DelveDialogs.body(d, text);
        float h = labels.size() > 4 ? 17f : 20f;
        for (int i = 0; i < labels.size(); i++) {
            final Runnable action = actions.get(i);
            TextraButton b = DelveDialogs.listButton(d, labels.get(i), () -> {
                removeDialog();
                action.run();
            }, h);
            if (enabled != null && !enabled.get(i)) b.setDisabled(true);
        }
        showDialog(d);
    }

    protected void confirm(String title, String text, Runnable yes) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog d = DelveDialogs.make(title);
        DelveDialogs.body(d, text);
        DelveDialogs.rowButtons(d, new String[]{"Yes", "No"}, new Runnable[]{() -> {
            removeDialog();
            yes.run();
        }, this::removeDialog});
        showDialog(d);
    }
    /** A seat's standing portrait (null = the player's hero), feet at (x, yTop), scaled to ~34 tall. */
    protected void portrait(forge.adventure.data.EnemyData enemy, float x, float yTop) {
        try {
            forge.adventure.character.CharacterSprite who = enemy == null
                    ? new forge.adventure.character.CharacterSprite(DelveProfile.get().heroAtlas())
                    : new forge.adventure.character.EnemySprite(enemy);
            who.setAnimation(forge.adventure.character.CharacterSprite.AnimationTypes.Idle);
            float h = Math.max(who.getHeight(), who.getWidth());
            float scale = h > 0 ? Math.min(2f, 34f / h) : 2f;
            com.badlogic.gdx.scenes.scene2d.Group g = standing(who, scale);
            standAt(g, x, yTop);
            track(g);
        } catch (Exception e) {
            e.printStackTrace(); // portraits are cosmetic
        }
    }


    /**
     * Dungeon atmosphere: drifting fog plus torchlight that flickers at the given points
     * (top-down layout coordinates). Call right after clearScreen() so it sits under the UI.
     */
    protected void atmosphere(float[][] torches) {
        java.util.Random r = new java.util.Random();
        for (float[] t : torches) {
            com.badlogic.gdx.scenes.scene2d.ui.Image g = image("ui/delve/torch_glow.png", t[0] - 64, t[1] - 45, 128, 90);
            g.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
            float base = 0.75f + r.nextFloat() * 0.2f;
            g.getColor().a = base;
            g.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                            com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(base - 0.25f - r.nextFloat() * 0.15f, 0.12f + r.nextFloat() * 0.18f),
                            com.badlogic.gdx.scenes.scene2d.actions.Actions.alpha(base + r.nextFloat() * 0.1f, 0.10f + r.nextFloat() * 0.25f))));
        }
        for (int i = 0; i < 2; i++) { // two copies side by side, sliding left and wrapping
            com.badlogic.gdx.scenes.scene2d.ui.Image fog = image("ui/delve/fog.png", i * W, 0, W, H);
            fog.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
            fog.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                            com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(-W, 0, 80f),
                            com.badlogic.gdx.scenes.scene2d.actions.Actions.moveBy(W, 0))));
        }
    }
}
