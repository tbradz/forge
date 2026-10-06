package forge.delve;

import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;
import forge.card.CardRarity;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The dungeon map for the current run: steps left to right, pick one room per
 * step. Handles fights (via DelveDuelScene), events, the merchant, rests, card
 * rewards and the end of the run (keep picks or Locked Deck).
 */
public class DelveMapScene extends DelveScene {
    private static DelveMapScene object;

    /** which node was chosen on each completed step (index into that layer) */
    private List<Integer> chosen = new ArrayList<>();

    private DelveMapScene() {
        super("ui/delve_path.json");
    }

    public static DelveMapScene instance() {
        if (object == null)
            object = new DelveMapScene();
        return object;
    }

    @Override
    public void enter() {
        DelveRun run = DelveRun.current();
        if (run != null) chosen = run.chosen;
        DelveAudio.dungeon();
        build();
        super.enter();
    }

    // ---- relics -------------------------------------------------------------------

    /** Pick one relic from {@code options} (optionally skip), then run {@code then}. */
    private void relicChoice(DelveRun run, String title, String text, List<DelveRelic> options, boolean canSkip, Runnable then) {
        if (options.isEmpty()) {
            if (then != null) then.run();
            return;
        }
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        StringBuilder body = new StringBuilder("[%85]" + text + "\n");
        for (DelveRelic r : options) {
            body.append("\n[%85][GOLD]").append(r.title).append(r.rare ? " (rare)" : "").append("[WHITE]: ").append(r.description);
            labels.add("Take the " + r.title);
            actions.add(() -> {
                run.gainRelic(r);
                DelveRunSave.save(run);
                if (then != null) then.run();
            });
        }
        if (canSkip) {
            labels.add("Leave them");
            actions.add(() -> { if (then != null) then.run(); });
        }
        choose(title, body.toString(), labels, null, actions);
    }

    /** Add basic lands any time (free); the only deck edit allowed outside merchants, rests and events. */
    private void basicLands(DelveRun run) {
        String colors = "";
        forge.card.ColorSet cs = run.deckColors();
        for (char c : "WUBRG".toCharArray()) {
            byte mask = forge.card.MagicColor.fromName(String.valueOf(c));
            boolean inDeck = cs.hasAnyColor(mask) || run.deck.getMain().count(DelveDay.basic(c)) > 0;
            if (inDeck) colors += c;
        }
        if (colors.isEmpty()) colors = "WUBRG";
        List<String> labels = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (char c : colors.toCharArray()) {
            PaperCard basic = DelveDay.basic(c);
            int have = run.deck.getMain().count(basic);
            labels.add("+ " + basic.getName() + "  (" + have + ")");
            enabled.add(true);
            actions.add(() -> { run.deck.getMain().add(basic); afterLands(run); });
            // add only (Tyler): removing cards, lands included, takes a merchant, rest or event
        }
        labels.add("Done");
        enabled.add(true);
        actions.add(this::build);
        int lands = 0;
        for (java.util.Map.Entry<PaperCard, Integer> e : run.deck.getMain())
            if (e.getKey().getRules().getType().isBasicLand()) lands += e.getValue();
        choose("Basic lands", "[%80]Add basic lands for free, any time between rooms. Your deck: " + run.deckSize()
                + " cards, " + lands + " basic lands. (Removing cards takes a merchant, rest or event.)", labels, enabled, actions);
    }

    private void afterLands(DelveRun run) {
        DelveRunSave.save(run);
        basicLands(run);
    }

    private void showRelics(DelveRun run) {
        StringBuilder sb = new StringBuilder();
        for (DelveRelic r : run.relics)
            sb.append(sb.length() > 0 ? "\n" : "").append("[GOLD]").append(r.title).append("[WHITE]: ").append(r.description);
        info("Your relics", sb.length() == 0 ? "No relics yet. Elites, Treasure Rooms and merchants have them." : sb.toString(), null);
    }

    // ---- the trail map ----------------------------------------------------------------

    private static final float ENTRANCE_X = 26, TRAIL_RIGHT = 436, TRAIL_MID = 150, ROW_GAP = 56;
    /** Narrowest column spacing; longer floors run past the screen and the map scrolls. */
    private static final float COL_MIN = 52;
    private com.badlogic.gdx.scenes.scene2d.Group heroGroup;
    private CharacterSprite heroSprite;
    private boolean walking;
    /** the scrolling part of the map (trails, rooms, hero), and how far it's panned right */
    private com.badlogic.gdx.scenes.scene2d.Group mapLayer;
    private float scroll;
    private TextraButton panLeft, panRight;

    private static float colSpacing(DelveRun run) {
        return Math.max(COL_MIN, (TRAIL_RIGHT - ENTRANCE_X) / run.layers.size());
    }

    /** x of a map column (in map coordinates); column 0 is the entrance, column s+1 is step s. */
    private float colX(DelveRun run, int col) {
        return ENTRANCE_X + col * colSpacing(run);
    }

    private float maxScroll(DelveRun run) {
        return Math.max(0, colX(run, run.layers.size()) + 44 - W); // room for the boss and its label
    }

    /** Pan the map so {@code target} (map x) is at the left edge, over {@code seconds}. */
    private void panTo(float target, float seconds) {
        DelveRun run = DelveRun.current();
        if (run == null || mapLayer == null) return;
        scroll = Math.max(0, Math.min(maxScroll(run), target));
        mapLayer.clearActions();
        if (seconds <= 0) mapLayer.setX(-scroll);
        else mapLayer.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(-scroll, 0, seconds,
                com.badlogic.gdx.math.Interpolation.sine));
        if (panLeft != null) panLeft.setVisible(scroll > 0.5f);
        if (panRight != null) panRight.setVisible(scroll < maxScroll(run) - 0.5f);
    }

    /** Keep a map x in view, a little left of centre so you can see what's coming. */
    private void follow(float x, float seconds) {
        panTo(x - W * 0.3f, seconds);
    }

    /** y (top-down) of node k in a layer of n nodes: where its plate's centre sits. */
    private static float rowY(int k, int n) {
        return TRAIL_MID + (k - (n - 1) / 2f) * ROW_GAP;
    }

    private float nodeX(DelveRun run, int step) { return colX(run, step + 1); }

    private float nodeY(DelveRun run, int step, int k) { return rowY(k, run.layers.get(step).size()); }

    /** Where the hero currently stands: the entrance, or the room chosen on the last step. */
    private float[] heroSpot(DelveRun run) {
        if (run.step == 0 || chosen.isEmpty()) return new float[]{colX(run, 0), TRAIL_MID};
        int s = run.step - 1, k = chosen.get(s);
        return new float[]{nodeX(run, s), nodeY(run, s, k)};
    }

    private void build() {
        clearScreen();
        atmosphere(new float[][]{{30, 140}, {450, 120}, {240, 250}}); // matches path_bg.png's torches
        walking = false;
        DelveRun run = DelveRun.current();
        if (run == null) {
            Forge.switchScene(DelveHubScene.instance());
            return;
        }
        chosen = run.chosen;
        if (!run.over) DelveRunSave.save(run);
        // header bar
        label("[%90][GOLD]" + fit(run.day.themeName(), 18) + "[]  [%70]Tier " + (run.day.tier + 1), 8, 5, 180, 16, Align.left);
        label("[%90][RED]Life[] " + run.life + "/" + run.maxLife() + "    [GOLD]Gold[] " + run.gold
                        + "    Deck " + run.deckSize() + "/" + run.minDeck() + "    Wins " + run.fightsWon,
                150, 5, 322, 16, Align.right);

        // drag anywhere on the map to look ahead (rooms sit above this and still take clicks)
        com.badlogic.gdx.scenes.scene2d.Actor dragger = new com.badlogic.gdx.scenes.scene2d.Actor();
        dragger.setBounds(0, 0, W, H);
        dragger.addListener(new com.badlogic.gdx.scenes.scene2d.utils.DragListener() {
            @Override
            public void drag(com.badlogic.gdx.scenes.scene2d.InputEvent event, float x, float y, int pointer) {
                if (!walking) panTo(scroll - getDeltaX(), 0);
            }
        });
        track(dragger);
        mapLayer = track(new com.badlogic.gdx.scenes.scene2d.Group());
        trackInto(mapLayer); // trails, rooms and the hero scroll together

        int steps = run.layers.size();
        // trails: gold where you've walked, grey ahead, faint for roads not taken
        for (int s = 0; s < steps; s++) {
            List<Node> layer = run.layers.get(s);
            List<float[]> from = new ArrayList<>();
            List<Boolean> fromOnPath = new ArrayList<>();
            if (s == 0) {
                from.add(new float[]{colX(run, 0), TRAIL_MID});
                fromOnPath.add(true);
            } else {
                List<Node> prev = run.layers.get(s - 1);
                for (int j = 0; j < prev.size(); j++) {
                    from.add(new float[]{nodeX(run, s - 1), nodeY(run, s - 1, j)});
                    fromOnPath.add(s - 1 < chosen.size() && chosen.get(s - 1) == j);
                }
            }
            for (int f = 0; f < from.size(); f++) {
                for (int k = 0; k < layer.size(); k++) {
                    if (!run.connected(s, f, k)) continue;
                    boolean walked = fromOnPath.get(f) && s < run.step && s < chosen.size() && chosen.get(s) == k;
                    boolean open = fromOnPath.get(f) && s == run.step;
                    boolean ahead = s > run.step || (s == run.step && !fromOnPath.get(f));
                    if (!walked && !open && !ahead) continue; // roads not taken are cleared away
                    trail(from.get(f)[0], from.get(f)[1], nodeX(run, s), nodeY(run, s, k), walked || open, walked ? 1f : open ? 0.95f : 0.6f);
                }
            }
        }

        // entrance
        image("ui/delve/stairs.png", colX(run, 0) - 18, TRAIL_MID - 22, 36, 32);
        label("[%55]Entrance", colX(run, 0) - 30, TRAIL_MID + 12, 60, 10, Align.center);

        // rooms
        for (int s = 0; s < steps; s++) {
            List<Node> layer = run.layers.get(s);
            for (int k = 0; k < layer.size(); k++)
                room(run, s, k, layer.get(k));
        }

        // hero
        float[] spot = heroSpot(run);
        heroSprite = new CharacterSprite(DelveProfile.get().heroAtlas());
        heroSprite.setAnimation(CharacterSprite.AnimationTypes.Idle);
        heroSprite.setDirection(CharacterSprite.AnimationDirections.Right);
        heroGroup = standing(heroSprite, 2f);
        standAt(heroGroup, spot[0] + (run.step == 0 ? 16 : -20), spot[1] + 4);
        track(heroGroup);
        trackInto(null);

        // long floors run off the screen: arrows (and dragging) look along the map
        panLeft = button("[%90]<", 2, 136, 16, 26, () -> panTo(scroll - W * 0.6f, 0.35f));
        panRight = button("[%90]>", W - 18, 136, 16, 26, () -> panTo(scroll + W * 0.6f, 0.35f));
        follow(spot[0], 0);

        button("[%80]Lands", 140, 246, 64, 18, () -> basicLands(run));
        button("[%80]Relics (" + run.relics.size() + ")", 210, 246, 80, 18, () -> showRelics(run));
        button("[%80]View deck", 300, 246, 80, 18, this::viewDeck);
        button("[%80]Abandon run", 390, 246, 84, 18, () ->
                confirm("Abandon run", "End this run now? You'll bring home a share of the gold you found, by how far you got.",
                        () -> endRun(false, true)));
        label("[%70]Step " + Math.min(run.step + 1, steps) + " of " + steps, 8, 250, 128, 12, Align.left); // Lands button follows
    }

    /** A dotted trail between two points. */
    private void trail(float x1, float y1, float x2, float y2, boolean gold, float alpha) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = (int) (len / 6f);
        float size = gold ? 5 : 4;
        for (int i = 3; i < n - 2; i++) { // stop short of the plates so lines don't run under them
            float t = i / (float) n;
            com.badlogic.gdx.scenes.scene2d.ui.Image d = image(gold ? "ui/delve/dot_gold.png" : "ui/delve/dot.png",
                    x1 + dx * t - size / 2, y1 + dy * t - size / 2, size, size);
            d.getColor().a = alpha;
        }
    }

    /** One room: stone plate, whoever stands there, a label, and a click target. */
    private void room(DelveRun run, int s, int k, Node node) {
        float x = nodeX(run, s), y = nodeY(run, s, k);
        boolean done = s < run.step;
        boolean wasChosen = done && s < chosen.size() && chosen.get(s) == k;
        boolean open = s == run.step && !run.over && run.reachable(k);
        float dim = open || wasChosen ? 1f : done ? 0.3f : 0.6f;

        com.badlogic.gdx.scenes.scene2d.ui.Image plate = image("ui/delve/plate.png", x - 18, y - 6, 36, 19);
        com.badlogic.gdx.scenes.scene2d.ui.Image glow = image("ui/delve/plate_glow.png", x - 18, y - 6, 36, 19);
        glow.setVisible(open);
        glow.getColor().a = 0.55f;
        plate.getColor().a = dim;

        CharacterSprite who = roomSprite(node);
        if (who != null && !wasChosen) { // the room's occupant is gone once you've been there
            // a few Adventure sprites are drawn much larger than the usual 16-24px: shrink those to fit the plate
            float scale = node.type == NodeType.BOSS ? 2.2f : 2f;
            // rooms are 56 apart: keep pictures short enough to clear the label of the room above
            float target = node.type == NodeType.BOSS ? 48f : 30f, h = Math.max(who.getHeight(), who.getWidth());
            if (h > 0 && h * scale > target) scale = target / h;
            com.badlogic.gdx.scenes.scene2d.Group g = standing(who, scale);
            standAt(g, x, y + 4);
            g.getColor().a = dim;
            who.getColor().a = dim;
            track(g);
        }

        String type;
        switch (node.type) {
            case REST: type = "[SKY]Rest"; break;
            case EVENT: type = "[#c080ff]? ? ?"; break;
            case MERCHANT: type = "[GOLD]Merchant"; break;
            case TREASURE: type = "[GOLD]Treasure"; break;
            case SHRINE: type = "[#ff6060]Cursed Shrine"; break;
            case BOSS: type = "[RED]Boss" + (node.perk != null ? " - " + node.perk.title : "") + (node.perk2 != null ? " +1" : ""); break;
            case ELITE: type = "[ORANGE]Elite" + (node.perk != null ? " - " + node.perk.title : ""); break;
            default: type = "Fight";
        }
        float spacing = colSpacing(run);
        boolean roomy = spacing >= 50;
        String name = node.enemy != null && roomy ? "\n[%50]" + shortName(node.enemy.getName()) : "";
        float lw = Math.min(64, spacing + 4);
        com.github.tommyettinger.textra.TextraLabel l = label("[%" + (roomy ? 60 : 50) + "]" + type + "[]" + name,
                x - lw / 2f, y + 12, lw, 16, Align.center);
        l.getColor().a = dim;

        if (!open) return;
        com.badlogic.gdx.scenes.scene2d.Actor hit = new com.badlogic.gdx.scenes.scene2d.Actor();
        hit.setBounds(x - 22, H - (y + 16), 44, 58);
        hit.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
            @Override
            public void enter(com.badlogic.gdx.scenes.scene2d.InputEvent event, float ex, float ey, int pointer,
                              com.badlogic.gdx.scenes.scene2d.Actor fromActor) {
                super.enter(event, ex, ey, pointer, fromActor);
                glow.getColor().a = 1f;
            }

            @Override
            public void exit(com.badlogic.gdx.scenes.scene2d.InputEvent event, float ex, float ey, int pointer,
                             com.badlogic.gdx.scenes.scene2d.Actor toActor) {
                super.exit(event, ex, ey, pointer, toActor);
                glow.getColor().a = 0.55f;
            }

            @Override
            public void clicked(com.badlogic.gdx.scenes.scene2d.InputEvent event, float ex, float ey) {
                choose(s, k);
            }
        });
        track(hit);
    }

    private static CharacterSprite roomSprite(Node node) {
        try {
            switch (node.type) {
                case REST: return idle(new CharacterSprite("sprites/3life.atlas"));
                case EVENT: return idle(new CharacterSprite("sprites/scroll.atlas"));
                case MERCHANT: return facing(new CharacterSprite("sprites/enemy/humanoid/human/peasant/inn_hermit.atlas"));
                case TREASURE: return idle(new CharacterSprite("sprites/treasure.atlas"));
                case SHRINE: return idle(new CharacterSprite("sprites/enemy/undead/unholyskull.atlas"));
                default: return facing(new forge.adventure.character.EnemySprite(node.enemy));
            }
        } catch (Exception e) {
            e.printStackTrace(); // a missing sprite shouldn't break the map
            return null;
        }
    }

    private static CharacterSprite idle(CharacterSprite c) {
        c.setAnimation(CharacterSprite.AnimationTypes.Idle);
        return c;
    }

    /** Occupants face the entrance (left), toward the approaching hero. */
    private static CharacterSprite facing(CharacterSprite c) {
        c.setAnimation(CharacterSprite.AnimationTypes.Idle);
        c.setDirection(CharacterSprite.AnimationDirections.Left);
        return c;
    }

    /** Walk the hero to a room, then run {@code arrived}. */
    private void walkTo(DelveRun run, int step, int k, Runnable arrived) {
        walking = true;
        float[] from = heroSpot(run);
        float tx = nodeX(run, step) - 20, ty = nodeY(run, step, k) + 4;
        float dx = tx - (heroGroup.getX()), dy = ty - (H - heroGroup.getY());
        CharacterSprite.AnimationDirections dir = Math.abs(dy) < Math.abs(dx) * 0.4f
                ? CharacterSprite.AnimationDirections.Right
                : dy > 0 ? CharacterSprite.AnimationDirections.RightDown : CharacterSprite.AnimationDirections.RightUp;
        heroSprite.setAnimation(CharacterSprite.AnimationTypes.Walk);
        heroSprite.setDirection(dir);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        follow(tx, dist / 70f); // the map pans along with you
        heroGroup.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.sequence(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.moveTo(tx, H - ty, dist / 70f),
                com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> {
                    heroSprite.setAnimation(CharacterSprite.AnimationTypes.Idle);
                    walking = false;
                    arrived.run();
                })));
    }

    private static String shortName(String s) {
        return s.length() <= 16 ? s : s.substring(0, 15) + ".";
    }

    private void choose(int step, int index) {
        DelveRun run = DelveRun.current();
        if (run == null || step != run.step || walking || !run.reachable(index)) return;
        Node node = run.layers.get(step).get(index);
        switch (node.type) {
            case REST: walkTo(run, step, index, () -> rest(run, index)); return;
            case EVENT: walkTo(run, step, index, () -> event(run, node, index)); return;
            case MERCHANT: walkTo(run, step, index, () -> merchant(run, node, index)); return;
            case TREASURE: walkTo(run, step, index, () -> treasure(run, index)); return;
            case SHRINE: walkTo(run, step, index, () -> shrine(run, index)); return;
            default:
        }
        String what = node.type == NodeType.BOSS ? "the boss" : node.type == NodeType.ELITE ? "an elite" : "a fight";
        confirm(node.enemy.getName(), "Enter " + what + " against " + node.enemy.getName() + ".\n"
                        + "They start at " + run.foeLife(node) + " life"
                        + (run.nextFoeLife < 0 ? " (blessed: " + run.nextFoeLife + ")" : run.nextFoeLife > 0 ? " (cursed: +" + run.nextFoeLife + ")" : "")
                        + ". You have " + run.life + (run.has(DelveRelic.IRON_BUCKLER) ? " (+4 from your Iron Buckler)" : "") + "."
                        + (node.perk != null ? "\n[GOLD]" + (node.perk.elite ? "Elite" : "Boss") + " perk - " + node.perk.title + ":[] " + node.perk.description : "")
                        + (node.perk2 != null ? "\n[GOLD]Second perk - " + node.perk2.title + ":[] " + node.perk2.description : "")
                        + (node.type == NodeType.BOSS ? "\n[GOLD]Its deck:[] " + run.day.bossTheme(node.enemy).name : "")
                        + (node.type == NodeType.FIGHT ? "\n\n" + quote(node.enemy, DelvePersona.Moment.GREET) : ""),
                () -> walkTo(run, step, index, () -> fight(run, node, index)));
    }

    // ---- fights ---------------------------------------------------------------

    private void fight(DelveRun run, Node node, int index) {
        run.currentNode = node;
        Runnable go = () -> {
            DelveDuelScene.instance().setup(run, node, (won, life) -> afterFight(run, node, index, won, life));
            Forge.switchScene(DelveDuelScene.instance());
        };
        if (node.type == NodeType.FIGHT) go.run();
        else DelveTalkScene.before(DelveTalkScene.DUNGEON, node.enemy, false, node.type == NodeType.BOSS,
                node.type == NodeType.BOSS ? "[RED]Boss" : "[GOLD]Elite", go);
    }

    /** A quoted line (or a described action, for things that don't talk) for dialogs. */
    private static String quote(forge.adventure.data.EnemyData e, DelvePersona.Moment m) {
        String line = DelvePersona.line(e, m, false, new java.util.Random());
        return line.startsWith("*") || !DelvePersona.talks(e) ? "[#c0b090]" + line + "[WHITE]" : "[#c0b090]\"" + line + "\"[WHITE]";
    }

    /** Elites, bosses and any defeat get a word from the opponent first. */
    private void afterFight(DelveRun run, Node node, int index, boolean won, int life) {
        if (!won || node.type != NodeType.FIGHT)
            DelveTalkScene.after(DelveTalkScene.DUNGEON, node.enemy, won, false, node.type == NodeType.BOSS,
                    () -> settleFight(run, node, index, won, life));
        else settleFight(run, node, index, won, life);
    }

    private void settleFight(DelveRun run, Node node, int index, boolean won, int life) {
        Forge.switchScene(this);
        if (!won) {
            run.life = 0;
            info("Defeated", node.enemy.getName() + " has beaten you. Your run is over.", () -> endRun(false, false));
            return;
        }
        run.life = Math.max(1, life);
        run.fightsWon++;
        boolean elite = node.type == NodeType.ELITE;
        int gold = DelveDifficulty.current().fightGold(DelveEconomy.fightGold(node.type, run.rng, run.gen));
        if (run.has(DelveRelic.GOLD_IDOL)) gold = gold * 3 / 2;
        run.gainGold(gold);
        DelveAudio.coins();
        completeStep(run, index);
        if (node.type == NodeType.BOSS) {
            bossLine = "You defeated " + node.enemy.getName() + " (+" + gold + " gold).\n";
            endRun(true, false);
            return;
        }
        // every won battle ends with a card for the deck (pick 1 of 3, or skip), Slay-the-Spire style (Tyler)
        Runnable spoils = () -> offerCard(run, "Battle spoils: add a card to your deck", elite, true);
        if (elite) // elites guard a relic as well as gold
            relicChoice(run, "The elite's hoard", "You take " + gold + " gold, and among its belongings you find relics. Take one.",
                    DelveRelic.offer(run.rng, run.relics, 3, 0.5), true, spoils);
        else
            info("Victory", quote(node.enemy, DelvePersona.Moment.THEY_LOST) + "\n\n" + node.enemy.getName() + " is beaten. You find [GOLD]" + gold + " gold[WHITE] (" + run.gold
                    + " this run), and a card for your deck.", spoils);
    }

    /** Pick 1 of 3 upgrades for the run deck (Reroll while you have tokens), then add it or swap out your weakest card. */
    private void offerCard(DelveRun run, String header, boolean strong) {
        offerCard(run, header, strong, false);
    }

    /**
     * Pick 1 of 3 cards for the run deck. {@code addOnly}: battle spoils just join the deck (removing
     * cards takes a merchant, rest or event); otherwise you may swap out your weakest card instead.
     */
    private void offerCard(DelveRun run, String header, boolean strong, boolean addOnly) {
        int depth = DelveMapGen.depth(run, Math.max(0, Math.min(run.layers.size() - 1, run.step - 1))) + (strong ? 1 : 0);
        List<PaperCard> offer = run.day.upgradeChoices(run.rng, run.deck, run.deckColors(), depth);
        int rerolls = DelveProfile.get().tokens(DelveTokens.REROLL);
        if (run.freeReroll) // a Count's (or better) free Reroll comes first
            DelvePickScene.instance().withExtra("[GOLD]Reroll (free)", () -> {
                run.freeReroll = false;
                DelveRunSave.save(run);
                offerCard(run, header, strong, addOnly);
            });
        else if (rerolls > 0)
            DelvePickScene.instance().withExtra("Reroll (" + rerolls + ")", () -> {
                DelveProfile.get().useToken(DelveTokens.REROLL);
                offerCard(run, header, strong, addOnly);
            });
        DelvePickScene.instance().show(header, offer, 1, 1, "Skip", picks -> {
            Forge.switchScene(this);
            if (picks.isEmpty()) return;
            if (addOnly) {
                addPicks(run, picks);
                DelveRunSave.save(run);
                build();
            } else {
                addOrSwap(run, picks.get(0));
            }
        });
    }

    /** Add a new card, or replace the deck's weakest card with it (keeps the deck lean). */
    private void addOrSwap(DelveRun run, PaperCard pc) {
        addOrSwap(run, pc, null);
    }

    private void addOrSwap(DelveRun run, PaperCard pc, Runnable then) {
        PaperCard weak = DelveDay.weakest(run.deck);
        if (weak == null || pc.getRules().getType().isLand()) {
            addPicks(run, List.of(pc));
            build();
            if (then != null) then.run();
            return;
        }
        DelveSwapScene.instance().swapOrAdd(weak, pc, run.deckSize() + 1, () -> {
            run.deck.getMain().remove(weak);
            addPicks(run, List.of(pc));
            Forge.switchScene(this);
            if (then != null) then.run();
        }, () -> {
            addPicks(run, List.of(pc));
            Forge.switchScene(this);
            if (then != null) then.run();
        });
    }

    private static void addPicks(DelveRun run, List<PaperCard> picks) {
        for (PaperCard pc : picks) {
            run.deck.getMain().add(pc);
            run.picked.add(pc);
        }
    }

    private void completeStep(DelveRun run, int index) {
        while (chosen.size() < run.step) chosen.add(-1);
        chosen.add(index);
        run.step++;
        build();
    }

    // ---- events -----------------------------------------------------------------

    private void event(DelveRun run, Node node, int index) {
        DelveEvents.Event e = node.event;
        List<String> labels = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (DelveEvents.Choice c : e.choices) {
            labels.add(c.label);
            enabled.add(c.available.test(run));
            actions.add(() -> {
                int lifeBefore = run.life, goldBefore = run.gold;
                run.beginChanges();
                String result = c.apply.apply(run);
                if (run.life < lifeBefore) DelveAudio.hurt();
                else if (run.life > lifeBefore) DelveAudio.heal();
                else if (run.gold > goldBefore) DelveAudio.coins();
                completeStep(run, index);
                if (DelveRun.PICK_CARD.equals(result)) {
                    offerCard(run, e.title + ": choose a card", true);
                } else if (DelveRun.PICK_COPY.equals(result)) {
                    DelvePickScene.instance().show(e.title + ": choose a card to copy", uniqueCards(run, false),
                            1, 1, null, pc -> "Copy", picks -> {
                                addPicks(run, picks);
                                Forge.switchScene(this);
                            });
                } else if (DelveRun.PICK_REMOVE.equals(result)) {
                    DelvePickScene.instance().show(e.title + ": choose a card to remove", uniqueCards(run, true),
                            1, 1, null, pc -> "Remove", picks -> {
                                if (!picks.isEmpty()) run.deck.getMain().remove(picks.get(0));
                                Forge.switchScene(this);
                            });
                } else if (!run.lostCards.isEmpty() || !run.gainedCards.isEmpty()) {
                    // the deck changed: show the cards going and coming, not just their names
                    DelveSwapScene.instance().report(e.title, result, new ArrayList<>(run.lostCards),
                            new ArrayList<>(run.gainedCards), () -> Forge.switchScene(this));
                } else {
                    info(e.title, result, null);
                }
            });
        }
        choose(e.title, e.text, labels, enabled, actions);
    }

    // ---- special rooms -------------------------------------------------------------

    /** Treasure Room: a little gold and your pick of three rares from the tier's set. */
    private void treasure(DelveRun run, int index) {
        run.gainGold(15);
        completeStep(run, index);
        List<PaperCard> rares = new ArrayList<>();
        for (int i = 0; i < 40 && rares.size() < 3; i++) {
            PaperCard pc = run.randomCard(DelveEvents.RarityTier.RARE, rares.size() < 2);
            if (pc != null && !rares.contains(pc)) rares.add(pc);
        }
        relicChoice(run, "Treasure Room", "A relic glints among the coins (+15 gold). Take one, then look over the rares.",
                DelveRelic.offer(run.rng, run.relics, 2, 0.6), true,
                () -> DelvePickScene.instance().show("Treasure Room: take one rare", rares, 1, 1, "Leave it", picks -> {
                    addPicks(run, picks);
                    Forge.switchScene(this);
                }));
    }

    /** Cursed Shrine: power at a price. */
    private void shrine(DelveRun run, int index) {
        choose("Cursed Shrine", "A black altar pulses with stolen power. Whatever you take, the dungeon will want back.",
                List.of("Take its power (choose 1 of 3 rares, next foe +6 life)",
                        "Feed it blood (lose 5 life, +35 gold)",
                        "Walk away"),
                List.of(true, true, true),
                List.of(() -> {
                            completeStep(run, index);
                            run.nextFoe(6);
                            List<PaperCard> rares = new ArrayList<>();
                            for (int i = 0; i < 40 && rares.size() < 3; i++) {
                                PaperCard pc = run.randomCard(DelveEvents.RarityTier.RARE, rares.size() < 2);
                                if (pc != null && !rares.contains(pc)) rares.add(pc);
                            }
                            DelvePickScene.instance().show("Cursed Shrine: take one (your next foe starts with 6 more life)",
                                    rares, 1, 1, null, picks -> {
                                        addPicks(run, picks);
                                        Forge.switchScene(this);
                                    });
                        },
                        () -> {
                            String r = run.damage(5) + " " + run.gainGold(35);
                            completeStep(run, index);
                            info("Cursed Shrine", r, null);
                        },
                        () -> completeStep(run, index)));
    }

    // ---- merchant -----------------------------------------------------------------

    private void merchant(DelveRun run, Node node, int index) {
        if (node.stock == null) {
            node.stock = new ArrayList<>();
            int depth = DelveMapGen.depth(run, Math.min(run.layers.size() - 1, run.step));
            // two upgrades you can afford right now, then two stronger ones to save up for
            int budget = Math.max(run.gold, DelveEconomy.buyPrice(CardRarity.Common));
            List<PaperCard> a = run.day.upgradeChoices(run.rng, run.deck, run.deckColors(), depth, budget);
            if (a.size() < 2) // the set is thin at this price: fall back to commons
                a = run.day.upgradeChoices(run.rng, run.deck, run.deckColors(), Math.max(0, depth - 1),
                        DelveEconomy.buyPrice(CardRarity.Common));
            List<PaperCard> b = run.day.upgradeChoices(run.rng, run.deck, run.deckColors(), depth + 1);
            node.stock.addAll(a.subList(0, Math.min(2, a.size())));
            int stockSize = run.boon == DelveBoon.SCOUT ? 5 : 4; // Scout's Eye: one more card
            for (PaperCard pc : b) {
                if (node.stock.size() >= stockSize) break;
                if (!node.stock.contains(pc)) node.stock.add(pc);
            }
            node.stock.sort(java.util.Comparator.comparingInt(DelveRun::buyPrice));
        }
        int removable = run.removableCount();
        String text = "\"Cards bought, cards sold. Coin is coin.\"\n\nYou have " + run.gold + " gold. Your deck has "
                + run.deckSize() + " cards" + (removable > 0 ? " (you can sell up to " + removable + ")." : " (at the minimum, nothing to sell).");
        String key = run.step + "." + index;
        if (!run.merchantRelics.containsKey(key)) {
            List<DelveRelic> r = DelveRelic.offer(run.rng, run.relics, 1, 0.35);
            run.merchantRelics.put(key, r.isEmpty() ? null : r.get(0));
        }
        DelveRelic relic = run.merchantRelics.get(key);
        boolean relicForSale = relic != null && !run.has(relic);
        int relicPrice = relic != null && relic.rare ? DelveEconomy.RELIC_PRICE_RARE : DelveEconomy.RELIC_PRICE;
        if (relicForSale) text += "\n\n[GOLD]" + relic.title + "[WHITE]: " + relic.description;
        choose("Merchant", text,
                List.of("Buy a card (" + node.stock.size() + " for sale)",
                        relicForSale ? "Buy the " + relic.title + " (" + relicPrice + "g)" : "No relics left",
                        "Sell cards from your deck", "Leave"),
                List.of(!node.stock.isEmpty(), relicForSale && run.gold >= relicPrice, removable > 0, true),
                List.of(() -> merchantBuy(run, node, index),
                        () -> {
                            run.spendGold(relicPrice);
                            info("Merchant", run.gainRelic(relic), () -> merchant(run, node, index));
                        },
                        () -> merchantSell(run, node, index),
                        () -> completeStep(run, index)));
    }

    private void merchantBuy(DelveRun run, Node node, int index) {
        DelvePickScene.instance().show("Merchant: buy a card   (you have " + run.gold + " gold)", node.stock, 1, 1, "Back",
                pc -> "Buy " + DelveRun.buyPrice(pc) + "g", picks -> {
                    Forge.switchScene(this);
                    if (picks.isEmpty()) {
                        merchant(run, node, index);
                        return;
                    }
                    PaperCard pc = picks.get(0);
                    int price = DelveRun.buyPrice(pc);
                    if (run.gold < price) {
                        info("Merchant", "\"That's " + price + " gold, friend. Come back richer.\"", () -> merchant(run, node, index));
                        return;
                    }
                    run.spendGold(price);
                    node.stock.remove(pc);
                    DelveAudio.coins();
                    addOrSwap(run, pc, () -> merchant(run, node, index));
                });
    }

    private void merchantSell(DelveRun run, Node node, int index) {
        int removable = run.removableCount();
        DelvePickScene.instance().show("Merchant: sell up to " + removable + (removable == 1 ? " card" : " cards")
                        + " (your deck can't go below " + run.minDeck() + ")",
                uniqueCards(run, true), 0, removable, "Back", pc -> "Sell " + DelveRun.sellPrice(pc) + "g", sold -> {
                    Forge.switchScene(this);
                    int total = 0;
                    for (PaperCard pc : sold) {
                        if (run.removableCount() <= 0) break;
                        run.deck.getMain().remove(pc);
                        total += DelveRun.sellPrice(pc);
                    }
                    if (total > 0) {
                        run.gainGold(total);
                        build();
                        info("Merchant", "You sell " + sold.size() + (sold.size() == 1 ? " card" : " cards") + " for " + total + " gold.",
                                () -> merchant(run, node, index));
                    } else {
                        merchant(run, node, index);
                    }
                });
    }

    // ---- forge ------------------------------------------------------------------

    /** Forge room: pick a card in your run deck, then reforge it into one of three stronger cards of its colour. */
    private void forgeRoom(DelveRun run, int index) {
        completeStep(run, index);
        List<PaperCard> candidates = new ArrayList<>();
        for (PaperCard pc : uniqueCards(run, false))
            if (!pc.getRules().getType().isLand() && !run.reforgeOptions(pc, 1).isEmpty()) candidates.add(pc);
        if (candidates.isEmpty()) {
            info("Forge", "\"Nothing here I can improve on, friend. Fine deck.\" The smith waves you on.", null);
            return;
        }
        DelvePickScene.instance().show("Forge: choose a card to reforge", candidates, 0, 1, "Leave",
                pc -> "Reforge", picks -> {
                    if (picks.isEmpty()) {
                        Forge.switchScene(this);
                        return;
                    }
                    PaperCard old = picks.get(0);
                    List<PaperCard> options = run.reforgeOptions(old, 3);
                    DelvePickScene.instance().show("Reforge " + old.getName() + " into:", options, 1, 1, null,
                            pc -> "Take", chosen -> {
                                PaperCard neu = chosen.get(0);
                                run.deck.getMain().remove(old);
                                run.deck.getMain().add(neu);
                                run.picked.add(neu);
                                DelveRunSave.save(run);
                                DelveSwapScene.instance().report("Forge", "The smith hammers " + old.getName()
                                        + " into " + neu.getName() + ".", List.of(old), List.of(neu), () -> Forge.switchScene(this));
                            });
                });
    }

    // ---- rest -------------------------------------------------------------------

    private void rest(DelveRun run, int index) {
        boolean canTrim = run.removableCount() > 0;
        choose("Rest", "A quiet corner to catch your breath."
                        + (canTrim ? "" : "\n\n(Your deck is at the " + run.minDeck() + "-card minimum, so you can't remove a card.)"),
                List.of("Heal " + DelveRun.REST_HEAL + " life", "Remove a card from your deck"),
                List.of(true, canTrim),
                List.of(() -> {
                            run.heal(DelveRun.REST_HEAL);
                            DelveAudio.heal();
                            completeStep(run, index);
                        },
                        () -> DelvePickScene.instance().show("Choose a card to remove from your deck", uniqueCards(run, true),
                                1, 1, "Cancel", pc -> "Remove", picks -> {
                                    if (!picks.isEmpty()) {
                                        run.deck.getMain().remove(picks.get(0));
                                        completeStep(run, index);
                                    }
                                    Forge.switchScene(this);
                                })));
    }

    // ---- deck -------------------------------------------------------------------

    private static List<PaperCard> uniqueCards(DelveRun run, boolean includeBasics) {
        List<PaperCard> out = new ArrayList<>();
        for (Map.Entry<PaperCard, Integer> e : run.deck.getMain()) {
            if (!includeBasics && e.getKey().getRules().getType().isBasicLand()) continue;
            out.add(e.getKey());
        }
        out.sort((a, b) -> {
            int c = Integer.compare(a.getRules().getManaCost().getCMC(), b.getRules().getManaCost().getCMC());
            return c != 0 ? c : a.getName().compareTo(b.getName());
        });
        return out;
    }

    private void viewDeck() {
        DelveRun run = DelveRun.current();
        DelvePickScene.instance().show("Your deck (" + run.deckSize() + " cards)", uniqueCards(run, false),
                0, 0, "Back", picks -> Forge.switchScene(this));
    }

    // ---- end of run ---------------------------------------------------------------

    /** Keep picks by result: 1 if you fell at the first fight, more the further you got. */
    private String bossLine = "";

    private void endRun(boolean cleared, boolean abandoned) {
        DelveRun run = DelveRun.current();
        if (run == null) return;
        run.over = true;
        run.cleared = cleared;
        DelveProfile prof = DelveProfile.get();
        prof.makeEvening(); // the run is done: the Castle and Tavern open tonight
        int found = run.gold;
        run.gold = 0;
        if (cleared) {
            prof.addGold(found);
            boolean unlocked = prof.clearTier(run.day.tier);
            String text = bossLine + "You cleared " + DelveDay.tierName(run.day.tier) + " after winning " + run.fightsWon
                    + " fights.\nYou bring home all " + found + " gold you found, and " + DelveTokens.grant(1, run.rng) + "."
                    + (unlocked ? "\n[GOLD]New tier unlocked: " + DelveDay.tierName(run.day.tier + 1) + "[]" : "");
            int rewards = 1;
            if (prof.tokens(DelveTokens.TREASURE_MAP) > 0) {
                choose("Dungeon cleared", text + "\n\nYou have " + prof.tokens(DelveTokens.TREASURE_MAP)
                                + " Treasure Map. Use one to take two rewards?",
                        List.of("Use a Treasure Map (2 rewards)", "Take one reward"), null, List.of(() -> {
                            prof.useToken(DelveTokens.TREASURE_MAP);
                            rewardMenu(run, 2, new ArrayList<>(), "");
                        }, () -> rewardMenu(run, 1, new ArrayList<>(), "")));
            } else {
                info("Dungeon cleared", text, () -> rewardMenu(run, rewards, new ArrayList<>(), ""));
            }
            return;
        }
        // defeat: a share of the gold, by how far you got
        int pct = (int) Math.round(run.completion() * 100);
        int banked = (int) Math.round(found * run.completion());
        String text = "Your run ended " + pct + "% of the way through the dungeon, after winning " + run.fightsWon
                + (run.fightsWon == 1 ? " fight." : " fights.");
        if (!abandoned && prof.tokens(DelveTokens.INSURANCE) > 0) { // insurance covers defeats, not walking out
            choose("Run over", text + "\n\nYou found " + found + " gold. Use Insurance (you have "
                            + prof.tokens(DelveTokens.INSURANCE) + ") to bring it all home and choose a clear reward?",
                    List.of("Use Insurance", "Take " + banked + " gold (" + pct + "%)"), null, List.of(() -> {
                        prof.useToken(DelveTokens.INSURANCE);
                        prof.addGold(found);
                        rewardMenu(run, 1, new ArrayList<>(), "Insurance paid out " + found + " gold. ");
                    }, () -> {
                        prof.addGold(banked);
                        finishRun("You bring home " + banked + " of the " + found + " gold you found (" + pct + "%).");
                    }));
            return;
        }
        prof.addGold(banked);
        info("Run over", text + "\nYou bring home " + pct + "% of the " + found + " gold you found: " + banked + " gold.",
                () -> finishRun("You bring home " + banked + " gold."));
    }

    /** Clear rewards: choose {@code left} of packs / gold / cards from the run deck / lock the deck. */
    private void rewardMenu(DelveRun run, int left, List<String> taken, String log) {
        if (left <= 0) {
            finishRun(log.trim());
            return;
        }
        String set = run.day.edition.getName();
        List<String> labels = new ArrayList<>();
        List<Boolean> enabled = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add(DelveEconomy.CLEAR_PACKS + " " + set + " boosters");
        enabled.add(!taken.contains("packs"));
        actions.add(() -> {
            taken.add("packs");
            List<PaperCard> all = new ArrayList<>();
            List<List<PaperCard>> packs = new ArrayList<>();
            for (int i = 0; i < DelveEconomy.CLEAR_PACKS; i++) {
                List<PaperCard> pack = new ArrayList<>();
                for (PaperCard pc : run.day.openPack(run.day.edition, run.rng))
                    if (!pc.getRules().getType().isBasicLand()) pack.add(pc); // basics are free anyway
                packs.add(pack);
                all.addAll(pack);
            }
            DelveProfile.get().addToCollection(all);
            DelvePackOpenScene.instance().openPacks("Your reward: " + DelveEconomy.CLEAR_PACKS + " " + set + " boosters",
                    set, packs, x -> {
                        Forge.switchScene(this);
                        rewardMenu(run, left - 1, taken, log + DelveEconomy.CLEAR_PACKS + " boosters opened. ");
                    });
        });
        labels.add(DelveEconomy.CLEAR_GOLD + " gold");
        enabled.add(!taken.contains("gold"));
        actions.add(() -> {
            taken.add("gold");
            DelveProfile.get().addGold(DelveEconomy.CLEAR_GOLD);
            rewardMenu(run, left - 1, taken, log + "+" + DelveEconomy.CLEAR_GOLD + " gold. ");
        });
        labels.add("Pick " + DelveRun.CLEAR_KEEPS + " cards from your run deck");
        enabled.add(!taken.contains("cards"));
        actions.add(() -> {
            taken.add("cards");
            List<PaperCard> options = uniqueCards(run, false);
            int n = Math.min(DelveRun.CLEAR_KEEPS, options.size());
            DelvePickScene.instance().show("Keep " + n + " cards for your collection", options, n, n, null, kept -> {
                DelveProfile.get().addToCollection(kept);
                Forge.switchScene(this);
                rewardMenu(run, left - 1, taken, log + kept.size() + " cards added to your collection. ");
            });
        });
        labels.add("Pick a rare (1 of 3 from " + set + ")");
        enabled.add(!taken.contains("rare"));
        actions.add(() -> {
            taken.add("rare");
            List<PaperCard> rares = new ArrayList<>();
            for (int i = 0; i < 60 && rares.size() < 3; i++) {
                PaperCard pc = run.randomCard(DelveEvents.RarityTier.RARE, false);
                if (pc != null && !rares.contains(pc)) rares.add(pc);
            }
            DelvePickScene.instance().show("Pick a rare for your collection", rares, 1, 1, null, pc -> "Take", picked -> {
                DelveProfile.get().addToCollection(picked);
                Forge.switchScene(this);
                rewardMenu(run, left - 1, taken, log + picked.get(0).getName() + " added to your collection. ");
            });
        });
        // (the "Lock the deck" reward was removed 2026-10-05, Tyler: Compact run decks aren't town decks)
        choose(left > 1 ? "Choose a reward (" + left + " left)" : "Choose your reward",
                "Rewards come from " + set + ". Cards and packs go to your collection.", labels, enabled, actions);
    }

    private void finishRun(String message) {
        DelveRun.clear();
        Forge.switchScene(DelveHubScene.instance());
        DelveHubScene.instance().notice("Run complete", message);
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
