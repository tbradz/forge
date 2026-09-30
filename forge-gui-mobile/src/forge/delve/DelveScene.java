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
        TextraButton b = Controls.newTextButton(text, onClick);
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
        showDialog(createGenericDialog(title, text, Forge.getLocalizer().getMessage("lblOK"), null,
                () -> {
                    removeDialog();
                    if (then != null) then.run();
                }, null));
    }

    /** A dialog with one button per choice; each button closes the dialog and runs its action. */
    protected void choose(String title, String text, List<String> labels, List<Boolean> enabled, List<Runnable> actions) {
        com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                new com.badlogic.gdx.scenes.scene2d.ui.Dialog(title == null ? "" : title, Controls.getSkin());
        TextraLabel body = Controls.newTextraLabel(text);
        body.setWrap(true);
        body.setAlignment(Align.center);
        dialog.getContentTable().add(body).width(300f);
        for (int i = 0; i < labels.size(); i++) {
            final Runnable action = actions.get(i);
            TextraButton b = Controls.newTextButton(labels.get(i), () -> {
                removeDialog();
                action.run();
            });
            if (enabled != null && !enabled.get(i)) b.setDisabled(true);
            dialog.getButtonTable().row();
            dialog.getButtonTable().add(b).width(260f).pad(2f);
        }
        showDialog(dialog);
    }

    protected void confirm(String title, String text, Runnable yes) {
        showDialog(createGenericDialog(title, text, "Yes", "No",
                () -> {
                    removeDialog();
                    yes.run();
                }, this::removeDialog));
    }
}
