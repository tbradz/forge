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
        super("ui/delve_panel.json");
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

    protected void confirm(String title, String text, Runnable yes) {
        showDialog(createGenericDialog(title, text, "Yes", "No",
                () -> {
                    removeDialog();
                    yes.run();
                }, this::removeDialog));
    }
}
