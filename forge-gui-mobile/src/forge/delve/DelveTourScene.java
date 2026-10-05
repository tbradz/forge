package forge.delve;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.utils.Align;
import forge.Forge;
import forge.adventure.character.CharacterSprite;

/**
 * The new-save intro, all on the town scene: you walk up the road into town, Bram the
 * innkeeper meets you on the plaza and walks you from building to building (the one
 * you're at lights up) explaining each, leaving his Tavern for last, where he heads
 * back inside. Replayable from the Tavern ("Talk to Bram").
 */
public class DelveTourScene extends DelveScene {
    private static DelveTourScene object;
    private static final String INNKEEPER = "sprites/enemy/humanoid/human/peasant/farmer.atlas";
    private static final float SPEED = 75f; // layout units per second
    private static final float SCALE = 2f;

    /** One stop: the building to light up (null = none), where you stand, and what Bram says there. */
    private static final class Stop {
        final String building, heading;
        final float x, y; // feet of the hero (Bram stands to the left)
        final String[] pages;

        Stop(String building, String heading, float x, float y, String... pages) {
            this.building = building;
            this.heading = heading;
            this.x = x;
            this.y = y;
            this.pages = pages;
        }
    }

    /** "{FIRST_SET}" in a page is replaced with this save's first tier's set. */
    private static final Stop[] STOPS = {
            new Stop(null, "Welcome to town", 250, 258,
                    "Well now, a new face! You'll be the delver the town council sent for. I'm Bram. I keep the inn here, "
                            + "and anyone who goes down into the ruins drinks at my place first.",
                    "Under the old ruins there's a dungeon, and it shifts every single day. Folk here are counting on someone to clear it. "
                            + "Come on, I'll walk you through town. Best you know where everything is."),
            new Stop("b_dungeon", "The Dungeon Gate", 250, 252,
                    "This is why you're here: the Dungeon Gate. The dungeon below shifts every single day, and you get one trip down each morning.",
                    "Before you go in, you pick two of three half-decks, each built around a color and a plan from the set the dungeon is steeped in. "
                            + "Shuffled together, that's your deck. {FIRST_SET}, to start.",
                    "Down below you choose your path room by room. Fights pay gold, and merchants down there sell cards that make your deck stronger. "
                            + "Elites guard relics, and the boss at the bottom has tricks of its own.",
                    "Beat the boss and you bring home every coin, plus a reward: packs, gold, or cards from your deck to keep. "
                            + "Clearing it also opens the next set's dungeon. Fall, and you keep only part of your gold."),
            new Stop("b_castle", "The Castle", 250, 140,
                    "Up on the hill, the Castle. Every evening there's a featured event: a tournament, a sealed night or a draft night, turn and turn about. Or a four-player Commander pod, if that's your game.",
                    "One event a night, and the entry isn't free. Win, and the prizes are worth it. The nobles keep a list of champions, too."),
            new Stop("b_house", "Your House", 70, 230,
                    "This one's yours. Build your decks here from the cards you collect, and sleep when you're ready for the next day."),
            new Stop("b_shop", "The Card Shop", 150, 216,
                    "The Card Shop. Singles and booster packs, new stock every day. The last single is always a legend that can lead a Commander deck.",
                    "The owner runs a prerelease once a day: open six packs, build a deck, play three rounds. And there's Pai Gow at the counter, if you fancy a gamble."),
            new Stop("b_outfitter", "The Outfitter", 414, 228,
                    "The Outfitter. Sleeves and the like. Doesn't win you games, but you'll look good losing them."),
            new Stop("b_tavern", "The Tavern", 330, 216,
                    "And last, my place: the Tavern. Evenings, the regulars will play you for free as long as you like. Good way to test a deck.",
                    "If you're feeling bold, you can play them for a little gold, or ante a card. Just don't come crying to me when you lose your best rare.",
                    "That's the whole town. The gate opens at dawn. I'd best get back behind the bar. Come find me tonight, delver."),
    };
    /** Bram's door: where he walks to before heading inside at the end (bottom-centre of b_tavern). */
    private static final float DOOR_X = 336, DOOR_Y = 207;

    private int stop, page;
    private boolean walking, firstVisit;
    private Runnable onDone;
    private Group bram, hero;
    private CharacterSprite bramSprite, heroSprite;
    private TextureRegion bramFace;

    private DelveTourScene() {
        super("ui/delve_hub.json");
    }

    public static DelveTourScene instance() {
        if (object == null)
            object = new DelveTourScene();
        return object;
    }

    public void play(boolean firstVisit, Runnable onDone) {
        this.firstVisit = firstVisit;
        this.onDone = onDone;
        this.stop = 0;
        this.page = 0;
        Forge.switchScene(this);
    }

    @Override
    public void enter() {
        DelveAudio.town();
        Actor leave = ui.findActor("leave");
        if (leave != null) leave.setVisible(false);
        Actor bg = ui.findActor("bg");
        if (bg instanceof Image) { // the tour happens by day
            com.badlogic.gdx.graphics.Texture tex = Forge.getAssets().getTexture(
                    forge.adventure.util.Config.instance().getFile("ui/delve/town_bg_day.png"), true, false);
            ((Image) bg).setDrawable(new com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable(new TextureRegion(tex)));
        }
        clearScreen();
        spawnWalkers();
        walkTo(STOPS[0], this::build);
        super.enter();
    }

    private void spawnWalkers() {
        try {
            bramSprite = new CharacterSprite(INNKEEPER);
            bram = standing(bramSprite, SCALE);
            standAt(bram, STOPS[0].x - 26, STOPS[0].y + 2); // waiting on the plaza; you walk in up the road
            bramSprite.setDirection(CharacterSprite.AnimationDirections.Right);
            track(bram);
            bramFace = bramSprite.getAvatar();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            heroSprite = new CharacterSprite(DelveProfile.get().heroAtlas());
            hero = standing(heroSprite, SCALE);
            standAt(hero, 250, 306);
            track(hero);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Walk both of you to a stop (Bram a step to your left), then run {@code then}. */
    private void walkTo(Stop s, Runnable then) {
        walking = true;
        clearPanel();
        highlight(null);
        float time = 0.4f;
        if (hero != null) time = Math.max(time, move(hero, heroSprite, s.x, s.y));
        if (bram != null) time = Math.max(time, move(bram, bramSprite, s.x - 26, s.y + 2));
        ui.addAction(Actions.sequence(Actions.delay(time + 0.05f), Actions.run(() -> {
            walking = false;
            face(heroSprite, s.x - 26 < s.x ? CharacterSprite.AnimationDirections.Left : CharacterSprite.AnimationDirections.Right);
            face(bramSprite, CharacterSprite.AnimationDirections.Right);
            then.run();
        })));
    }

    private float move(Group g, CharacterSprite sprite, float x, float yFeet) {
        float dx = x - g.getX(), dy = (H - yFeet) - g.getY();
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float t = dist / SPEED;
        if (t < 0.05f) return 0;
        sprite.setAnimation(CharacterSprite.AnimationTypes.Walk);
        sprite.setDirection(dx < 0 ? CharacterSprite.AnimationDirections.Left : CharacterSprite.AnimationDirections.Right);
        g.addAction(Actions.sequence(Actions.moveTo(x, H - yFeet, t),
                Actions.run(() -> sprite.setAnimation(CharacterSprite.AnimationTypes.Idle))));
        return t;
    }

    private static void face(CharacterSprite s, CharacterSprite.AnimationDirections d) {
        if (s != null) s.setDirection(d);
    }

    /** Light up the building you're at; dim the rest a little. */
    private void highlight(String name) {
        for (String b : new String[]{"b_castle", "b_house", "b_shop", "b_tavern", "b_outfitter", "b_dungeon"}) {
            Actor a = ui.findActor(b);
            if (a == null) continue;
            float c = name == null ? 0.9f : b.equals(name) ? 1f : 0.6f;
            a.setColor(c, c, c, 1f);
            a.setOrigin(Align.bottom);
            a.setScale(b.equals(name) ? 1.06f : 1f);
        }
    }

    private final java.util.List<Actor> panel = new java.util.ArrayList<>();

    private void clearPanel() {
        for (Actor a : panel) a.remove();
        panel.clear();
    }

    private <T extends Actor> T p(T a) {
        panel.add(a);
        return a;
    }

    private void build() {
        clearPanel();
        Stop s = STOPS[stop];
        highlight(s.building);
        // the box goes where it won't cover the building you're looking at
        boolean top = s.y > 150;
        float py = top ? 30 : 160;
        p(image("ui/delve/panel.png", 12, py, 456, 82));
        if (bramFace != null) {
            Image f = new Image(new TextureRegion(bramFace));
            f.setBounds(22, H - (py + 10) - 44, 44, 44);
            f.setTouchable(Touchable.disabled);
            p(track(f));
        }
        p(label("[%90][GOLD]Bram", 74, py + 6, 120, 12, Align.left));
        p(label("[%80][#c0a060]" + s.heading, 250, py + 6, 208, 12, Align.right));
        String words = s.pages[page].replace("{FIRST_SET}", DelveDay.tiers().get(0).getName());
        com.github.tommyettinger.textra.TextraLabel text = p(label("[%80]" + words, 74, py + 20, 386, 40, Align.topLeft));
        text.setAlignment(Align.topLeft);
        boolean last = stop == STOPS.length - 1 && page == s.pages.length - 1;
        if (stop > 0 || page > 0) p(button("[%80]Back", 74, py + 62, 60, 15, this::back1));
        p(button(last ? "[GOLD]Head into town" : "[GOLD]Next", 330, py + 61, 100, 17, this::next));
        if (!last) p(button("[%70]Skip", 432, py + 62, 30, 15, this::skip));
    }

    private void next() {
        if (walking) return;
        Stop s = STOPS[stop];
        if (page < s.pages.length - 1) {
            page++;
            build();
        } else if (stop < STOPS.length - 1) {
            stop++;
            page = 0;
            walkTo(STOPS[stop], this::build);
        } else {
            bramGoesInside();
        }
    }

    /** The end of the tour: Bram walks to the Tavern door and goes in, and the town is yours. */
    private void bramGoesInside() {
        if (bram == null) {
            finish();
            return;
        }
        walking = true;
        clearPanel();
        highlight("b_tavern");
        float t = Math.max(0.2f, move(bram, bramSprite, DOOR_X, DOOR_Y));
        face(heroSprite, CharacterSprite.AnimationDirections.Right);
        bram.addAction(Actions.sequence(Actions.delay(t), Actions.fadeOut(0.35f), Actions.delay(0.25f),
                Actions.run(() -> {
                    walking = false;
                    finish();
                })));
    }

    private void back1() {
        if (walking) return;
        if (page > 0) {
            page--;
            build();
        } else if (stop > 0) {
            stop--;
            page = STOPS[stop].pages.length - 1;
            walkTo(STOPS[stop], this::build);
        }
    }

    /** Skip the rest of the tour straight into town. */
    private void skip() {
        if (!walking) finish();
    }

    private void finish() {
        DelveProfile.get().setIntroSeen();
        highlight(null);
        Runnable cb = onDone;
        onDone = null;
        if (cb != null) cb.run();
        else Forge.switchScene(DelveHubScene.instance());
    }

    @Override
    public boolean back() {
        if (!walking) finish();
        return true;
    }
}
