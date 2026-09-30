package forge.delve;

import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import forge.Forge;
import forge.adventure.character.CharacterSprite;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;
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
        build();
        super.enter();
    }

    // ---- the trail map ----------------------------------------------------------------

    private static final float ENTRANCE_X = 26, TRAIL_RIGHT = 436, TRAIL_MID = 150, ROW_GAP = 56;
    private com.badlogic.gdx.scenes.scene2d.Group heroGroup;
    private CharacterSprite heroSprite;
    private boolean walking;

    /** x of a map column; column 0 is the entrance, column s+1 is step s. */
    private float colX(DelveRun run, int col) {
        return ENTRANCE_X + col * (TRAIL_RIGHT - ENTRANCE_X) / run.layers.size();
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
        walking = false;
        DelveRun run = DelveRun.current();
        if (run == null) {
            Forge.switchScene(DelveHubScene.instance());
            return;
        }
        chosen = run.chosen;
        if (!run.over) DelveRunSave.save(run);
        // header bar
        label("[%90][GOLD]" + run.day.themeName() + "[]  [%70]" + run.size.label, 8, 5, 180, 16, Align.left);
        label("[%90][RED]Life[] " + run.life + "/" + DelveRun.MAX_LIFE + "    [GOLD]Gold[] " + run.gold
                        + "    Deck " + run.deckSize() + "/" + DelveRun.MIN_DECK + "    Wins " + run.fightsWon,
                150, 5, 322, 16, Align.right);

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
                    boolean walked = fromOnPath.get(f) && s < run.step && s < chosen.size() && chosen.get(s) == k;
                    boolean open = fromOnPath.get(f) && s == run.step;
                    boolean ahead = s > run.step;
                    float alpha = walked || open ? 1f : ahead ? 0.45f : 0.15f;
                    trail(from.get(f)[0], from.get(f)[1], nodeX(run, s), nodeY(run, s, k), walked, alpha);
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

        button("[%80]View deck", 300, 246, 80, 18, this::viewDeck);
        button("[%80]Abandon run", 390, 246, 84, 18, () ->
                confirm("Abandon run", "End this run now? You'll still get the reward for how far you got.",
                        () -> endRun(false)));
        label("[%70]Step " + Math.min(run.step + 1, steps) + " of " + steps
                + (run.step < steps ? "  -  choose a lit room" : ""), 8, 250, 280, 12, Align.left);
    }

    /** A dotted trail between two points. */
    private void trail(float x1, float y1, float x2, float y2, boolean gold, float alpha) {
        float dx = x2 - x1, dy = y2 - y1, len = (float) Math.sqrt(dx * dx + dy * dy);
        int n = (int) (len / 7f);
        for (int i = 2; i < n - 1; i++) {
            float t = i / (float) n;
            com.badlogic.gdx.scenes.scene2d.ui.Image d = image(gold ? "ui/delve/dot_gold.png" : "ui/delve/dot.png",
                    x1 + dx * t - 2, y1 + dy * t - 2, 4, 4);
            d.getColor().a = alpha;
        }
    }

    /** One room: stone plate, whoever stands there, a label, and a click target. */
    private void room(DelveRun run, int s, int k, Node node) {
        float x = nodeX(run, s), y = nodeY(run, s, k);
        boolean done = s < run.step;
        boolean wasChosen = done && s < chosen.size() && chosen.get(s) == k;
        boolean open = s == run.step && !run.over;
        float dim = open || wasChosen ? 1f : done ? 0.3f : 0.6f;

        com.badlogic.gdx.scenes.scene2d.ui.Image plate = image("ui/delve/plate.png", x - 18, y - 6, 36, 19);
        com.badlogic.gdx.scenes.scene2d.ui.Image glow = image("ui/delve/plate_glow.png", x - 18, y - 6, 36, 19);
        glow.setVisible(open);
        glow.getColor().a = 0.55f;
        plate.getColor().a = dim;

        CharacterSprite who = roomSprite(node);
        if (who != null && !wasChosen) { // the room's occupant is gone once you've been there
            float scale = node.type == NodeType.BOSS ? 2.2f : 2f;
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
            case BOSS: type = "[RED]Boss"; break;
            case ELITE: type = "[ORANGE]Elite"; break;
            default: type = "Fight";
        }
        float spacing = (TRAIL_RIGHT - ENTRANCE_X) / run.layers.size();
        boolean roomy = spacing >= 50;
        String name = node.enemy != null && roomy ? "\n[%50]" + shortName(node.enemy.getName()) : "";
        float lw = Math.min(64, spacing + 4);
        com.github.tommyettinger.textra.TextraLabel l = label("[%" + (roomy ? 60 : 50) + "]" + type + "[]" + name,
                x - lw / 2f, y + 14, lw, 20, Align.center);
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
        if (run == null || step != run.step || walking) return;
        Node node = run.layers.get(step).get(index);
        switch (node.type) {
            case REST: walkTo(run, step, index, () -> rest(run, index)); return;
            case EVENT: walkTo(run, step, index, () -> event(run, node, index)); return;
            case MERCHANT: walkTo(run, step, index, () -> merchant(run, node, index)); return;
            default:
        }
        String what = node.type == NodeType.BOSS ? "the boss" : node.type == NodeType.ELITE ? "an elite" : "a fight";
        confirm(node.enemy.getName(), "Enter " + what + " against " + node.enemy.getName() + ".\n"
                        + "They start at " + node.enemyLife + " life. You have " + run.life + ".",
                () -> walkTo(run, step, index, () -> fight(run, node, index)));
    }

    // ---- fights ---------------------------------------------------------------

    private void fight(DelveRun run, Node node, int index) {
        run.currentNode = node;
        DelveDuelScene.instance().setup(run, node, (won, life) -> afterFight(run, node, index, won, life));
        Forge.switchScene(DelveDuelScene.instance());
    }

    private void afterFight(DelveRun run, Node node, int index, boolean won, int life) {
        Forge.switchScene(this);
        if (!won) {
            run.life = 0;
            info("Defeated", node.enemy.getName() + " has beaten you. Your run is over.", () -> endRun(false));
            return;
        }
        run.life = Math.max(1, life);
        run.fightsWon++;
        boolean elite = node.type == NodeType.ELITE;
        int gold = DelveEconomy.fightGold(node.type, run.rng, run.size);
        run.gainGold(gold);
        completeStep(run, index);
        if (node.type == NodeType.BOSS) {
            info("Dungeon cleared!", "You defeated " + node.enemy.getName() + " and cleared today's dungeon. (+"
                    + gold + " gold)", () -> endRun(true));
            return;
        }
        List<PaperCard> offer = run.day.rewardChoices(run.rng, run.deckColors(), elite);
        DelvePickScene.instance().show((elite ? "Elite reward" : "Victory") + " (+" + gold + " gold): add one card to your deck",
                offer, 1, 1, "Skip", picks -> {
                    addPicks(run, picks);
                    Forge.switchScene(this);
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
                String result = c.apply.apply(run);
                completeStep(run, index);
                if (DelveRun.PICK_CARD.equals(result)) {
                    DelvePickScene.instance().show(e.title + ": choose a card", run.day.rewardChoices(run.rng, run.deckColors(), true),
                            1, 1, "Skip", picks -> {
                                addPicks(run, picks);
                                Forge.switchScene(this);
                            });
                } else {
                    info(e.title, result, null);
                }
            });
        }
        choose(e.title, e.text, labels, enabled, actions);
    }

    // ---- merchant -----------------------------------------------------------------

    private void merchant(DelveRun run, Node node, int index) {
        if (node.stock == null) {
            node.stock = new ArrayList<>();
            List<PaperCard> a = run.day.rewardChoices(run.rng, run.deckColors(), false);
            List<PaperCard> b = run.day.rewardChoices(run.rng, run.deckColors(), true);
            node.stock.addAll(a.subList(0, Math.min(2, a.size())));
            if (!b.isEmpty() && !node.stock.contains(b.get(0))) node.stock.add(b.get(0));
        }
        int removable = run.removableCount();
        String text = "\"Cards bought, cards sold. Coin is coin.\"\n\nYou have " + run.gold + " gold. Your deck has "
                + run.deckSize() + " cards" + (removable > 0 ? " (you can sell up to " + removable + ")." : " (at the minimum, nothing to sell).");
        choose("Merchant", text,
                List.of("Buy a card (" + node.stock.size() + " for sale)", "Sell cards from your deck", "Leave"),
                List.of(!node.stock.isEmpty(), removable > 0, true),
                List.of(() -> merchantBuy(run, node, index),
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
                    addPicks(run, List.of(pc));
                    build();
                    info("Merchant", "You buy " + pc.getName() + " for " + price + " gold.", () -> merchant(run, node, index));
                });
    }

    private void merchantSell(DelveRun run, Node node, int index) {
        int removable = run.removableCount();
        DelvePickScene.instance().show("Merchant: sell up to " + removable + (removable == 1 ? " card" : " cards")
                        + " (your deck can't go below " + DelveRun.MIN_DECK + ")",
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

    // ---- rest -------------------------------------------------------------------

    private void rest(DelveRun run, int index) {
        boolean canTrim = run.removableCount() > 0;
        choose("Rest", "A quiet corner to catch your breath."
                        + (canTrim ? "" : "\n\n(Your deck is at the " + DelveRun.MIN_DECK + "-card minimum, so you can't remove a card.)"),
                List.of("Heal " + DelveRun.REST_HEAL + " life", "Remove a card from your deck"),
                List.of(true, canTrim),
                List.of(() -> {
                            run.heal(DelveRun.REST_HEAL);
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
    static int keepPicks(DelveRun run, boolean cleared) {
        if (cleared) return run.size.clearKeeps;
        if (run.fightsWon == 0) return 1;
        return run.fightsWon >= 3 ? 3 : 2;
    }

    private void endRun(boolean cleared) {
        DelveRun run = DelveRun.current();
        if (run == null) return;
        run.over = true;
        run.cleared = cleared;
        DelveProfile.get().makeEvening(); // the run is done: the Castle opens tonight
        // all gold found in the dungeon comes home, win or lose
        int banked = run.gold;
        DelveProfile.get().addGold(banked);
        run.gold = 0;
        int picks = keepPicks(run, cleared);
        String summary = (cleared ? "You cleared the dungeon" : "Your run ended") + " after winning "
                + run.fightsWon + (run.fightsWon == 1 ? " fight." : " fights.")
                + "\nYou bring " + banked + " gold home (town gold: " + DelveProfile.get().gold() + ")."
                + "\n\nKeep " + picks + (picks == 1 ? " card" : " cards") + " from your run deck for your collection"
                + (cleared ? ", or lock the whole deck exactly as it is (it can never be changed)." : ".");
        if (cleared) {
            showDialog(createGenericDialog("Choose your reward", summary, "Keep " + picks + " cards", "Lock the deck",
                    () -> {
                        removeDialog();
                        chooseKeeps(run, picks);
                    },
                    () -> {
                        removeDialog();
                        lockDeck(run);
                    }));
        } else {
            info("Run over", summary, () -> chooseKeeps(run, picks));
        }
    }

    private void chooseKeeps(DelveRun run, int picks) {
        List<PaperCard> options = uniqueCards(run, false);
        if (options.isEmpty()) {
            finishRun("Nothing to keep this time.");
            return;
        }
        DelvePickScene.instance().show("Keep " + picks + (picks == 1 ? " card" : " cards") + " for your collection",
                options, Math.min(picks, options.size()), picks, null, kept -> {
                    DelveProfile.get().addToCollection(kept);
                    finishRun(kept.size() + (kept.size() == 1 ? " card" : " cards") + " added to your collection.");
                });
    }

    private void lockDeck(DelveRun run) {
        String name = run.day.themeName() + " " + run.deck.getName().replace(" Starter", "");
        DelveProfile.get().addLockedDeck(run.deck, name + " (Locked)");
        finishRun("Your run deck was saved as a Locked Deck. Find it in Your House.");
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
