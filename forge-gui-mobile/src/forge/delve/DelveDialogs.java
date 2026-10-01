package forge.delve;

import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import com.github.tommyettinger.textra.TextraLabel;
import forge.adventure.util.Controls;

/**
 * Delve's pop-up boxes: a gold title inside the frame (Adventure's dialogs draw the
 * title on top of the border), padded body text, and evenly sized buttons.
 */
final class DelveDialogs {
    private DelveDialogs() {}

    static final float BODY_W = 300f;

    /** An empty dialog with a padded title row; add the body and buttons, then show it. */
    static Dialog make(String title) {
        Dialog d = new Dialog("", Controls.getSkin());
        d.getTitleTable().clear();
        d.getContentTable().pad(10, 16, 4, 16);
        d.getButtonTable().pad(2, 16, 10, 16);
        if (title != null && !title.isEmpty()) {
            TextraLabel t = Controls.newTextraLabel("[%110][GOLD]" + title);
            t.setAlignment(Align.center);
            d.getContentTable().add(t).width(BODY_W).padBottom(6);
            d.getContentTable().row();
        }
        return d;
    }

    /** Wrapped, centered body text. */
    static void body(Dialog d, String text) {
        if (text == null || text.isEmpty()) return;
        // a "[]" colour reset also resets the size, so reset to white instead
        TextraLabel body = Controls.newTextraLabel(text.replace("[]", "[WHITE]"));
        body.setWrap(true);
        body.setAlignment(Align.center);
        d.getContentTable().add(body).width(BODY_W);
        d.getContentTable().row();
    }

    /** Fade a button while it is disabled, so it reads as unavailable. */
    static <T extends TextraButton> T dimWhenDisabled(T b) {
        b.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> b.getColor().a = b.isDisabled() ? 0.4f : 1f)));
        return b;
    }

    /** A button in a vertical list (one per row). */
    static TextraButton listButton(Dialog d, String text, Runnable onClick, float height) {
        TextraButton[] self = new TextraButton[1];
        TextraButton b = Controls.newTextButton(text, () -> {
            if (!self[0].isDisabled()) onClick.run(); // libGDX still delivers clicks to disabled buttons
        });
        self[0] = b;
        dimWhenDisabled(b);
        d.getButtonTable().row();
        d.getButtonTable().add(b).width(260f).height(height).pad(1.5f);
        return b;
    }

    /** Buttons in a grid of {@code cols} columns (for long menus). */
    static void gridButtons(Dialog d, java.util.List<String> texts, java.util.List<Runnable> actions, int cols) {
        float w = cols == 1 ? 260f : 300f / cols - 4f;
        for (int i = 0; i < texts.size(); i++) {
            if (i % cols == 0) d.getButtonTable().row();
            TextraButton b = Controls.newTextButton(texts.get(i), actions.get(i));
            d.getButtonTable().add(b).width(w).height(20f).pad(2f);
        }
    }

    /** A full-width button under a {@link #gridButtons} grid. */
    static void wideButton(Dialog d, String text, Runnable onClick, int cols) {
        d.getButtonTable().row();
        d.getButtonTable().add(Controls.newTextButton(text, onClick)).colspan(cols).width(160f).height(20f).padTop(6f);
    }

    /** Buttons side by side on one row (OK, or Yes / No). */
    static void rowButtons(Dialog d, String[] texts, Runnable[] actions) {
        for (int i = 0; i < texts.length; i++) {
            final Runnable a = actions[i];
            TextraButton b = Controls.newTextButton(texts[i], a);
            d.getButtonTable().add(b).width(texts.length == 1 ? 100f : 110f).height(22f).pad(2f, 6f, 2f, 6f);
        }
    }
}
