package forge.delve;

import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import forge.Forge;
import forge.delve.DelveRun.Node;
import forge.delve.DelveRun.NodeType;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The dungeon map for the current run: steps left to right, pick one room per
 * step. Handles fights (via DelveDuelScene), rests, card rewards and the end of
 * the run (keep picks or Locked Deck).
 */
public class DelveMapScene extends DelveScene {
    private static DelveMapScene object;

    /** which node was chosen on each completed step (index into that layer) */
    private final List<Integer> chosen = new ArrayList<>();
    private DelveRun shownRun;

    public static DelveMapScene instance() {
        if (object == null)
            object = new DelveMapScene();
        return object;
    }

    @Override
    public void enter() {
        DelveRun run = DelveRun.current();
        if (run != shownRun) {
            chosen.clear();
            shownRun = run;
        }
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        DelveRun run = DelveRun.current();
        if (run == null) {
            Forge.switchScene(DelveHubScene.instance());
            return;
        }
        title(run.day.themeName());
        label("Life [GOLD]" + run.life + "/" + DelveRun.MAX_LIFE + "[]     Deck " + run.deckSize()
                        + " cards     Fights won " + run.fightsWon,
                0, 30, W, 16, Align.center);

        int steps = run.layers.size();
        float colW = 88, gap = 6;
        float startX = (W - steps * colW - (steps - 1) * gap) / 2f;
        for (int s = 0; s < steps; s++) {
            List<Node> layer = run.layers.get(s);
            float x = startX + s * (colW + gap);
            label((s == steps - 1 ? "[%90]Final" : "[%90]Step " + (s + 1)), x, 58, colW, 12, Align.center);
            float nodeH = 44, nodeGap = 10;
            float totalH = layer.size() * nodeH + (layer.size() - 1) * nodeGap;
            float y = 76 + (140 - totalH) / 2f;
            for (int k = 0; k < layer.size(); k++) {
                Node node = layer.get(k);
                String text = nodeText(node);
                boolean done = s < run.step;
                boolean wasChosen = done && s < chosen.size() && chosen.get(s) == k;
                if (wasChosen) text = "[GREEN]" + text;
                final int step = s, idx = k;
                TextraButton b = button(text, x, y, colW, nodeH, () -> choose(step, idx));
                b.setDisabled(s != run.step || run.over);
                if (done && !wasChosen) b.getColor().a = 0.4f;
                y += nodeH + nodeGap;
            }
        }

        button("View deck", 110, 236, 110, 22, this::viewDeck);
        button("Abandon run", 260, 236, 110, 22, () ->
                confirm("Abandon run", "End this run now? You'll still get the reward for how far you got.",
                        () -> endRun(false)));
    }

    private static String nodeText(Node node) {
        switch (node.type) {
            case REST: return "[%90]Rest\n[%75]heal or trim";
            case BOSS: return "[%90][RED]Boss[]\n[%75]" + node.enemy.getName();
            case ELITE: return "[%90][ORANGE]Elite[]\n[%75]" + node.enemy.getName();
            default: return "[%90]Fight\n[%75]" + node.enemy.getName();
        }
    }

    private void choose(int step, int index) {
        DelveRun run = DelveRun.current();
        if (run == null || step != run.step) return;
        Node node = run.layers.get(step).get(index);
        if (node.type == NodeType.REST) {
            rest(run, index);
            return;
        }
        String what = node.type == NodeType.BOSS ? "the boss" : node.type == NodeType.ELITE ? "an elite" : "a fight";
        confirm(node.enemy.getName(), "Enter " + what + " against " + node.enemy.getName() + ".\n"
                        + "They start at " + node.enemyLife + " life. You have " + run.life + ".",
                () -> fight(run, node, index));
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
        completeStep(run, index);
        if (node.type == NodeType.BOSS) {
            info("Dungeon cleared!", "You defeated " + node.enemy.getName() + " and cleared today's dungeon.",
                    () -> endRun(true));
            return;
        }
        boolean elite = node.type == NodeType.ELITE;
        List<PaperCard> offer = run.day.rewardChoices(run.rng, run.deckColors(), elite);
        DelvePickScene.instance().show((elite ? "Elite reward" : "Victory") + ": add one card to your deck",
                offer, 1, 1, "Skip", picks -> {
                    for (PaperCard pc : picks) {
                        run.deck.getMain().add(pc);
                        run.picked.add(pc);
                    }
                    Forge.switchScene(this);
                });
    }

    private void completeStep(DelveRun run, int index) {
        while (chosen.size() < run.step) chosen.add(-1);
        chosen.add(index);
        run.step++;
        build();
    }

    // ---- rest -------------------------------------------------------------------

    private void rest(DelveRun run, int index) {
        showDialog(createGenericDialog("Rest",
                "Rest to heal " + DelveRun.REST_HEAL + " life, or train to remove one card from your deck.",
                "Heal", "Remove a card",
                () -> {
                    removeDialog();
                    run.life = Math.min(DelveRun.MAX_LIFE, run.life + DelveRun.REST_HEAL);
                    completeStep(run, index);
                },
                () -> {
                    removeDialog();
                    DelvePickScene.instance().show("Choose a card to remove from your deck", uniqueCards(run, true),
                            1, 1, "Cancel", picks -> {
                                if (!picks.isEmpty()) {
                                    run.deck.getMain().remove(picks.get(0));
                                    completeStep(run, index);
                                }
                                Forge.switchScene(this);
                            });
                }));
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
        if (cleared) return 5;
        if (run.fightsWon == 0) return 1;
        return run.fightsWon >= 3 ? 3 : 2;
    }

    private void endRun(boolean cleared) {
        DelveRun run = DelveRun.current();
        if (run == null) return;
        run.over = true;
        run.cleared = cleared;
        int picks = keepPicks(run, cleared);
        String summary = (cleared ? "You cleared the dungeon" : "Your run ended") + " after winning "
                + run.fightsWon + (run.fightsWon == 1 ? " fight." : " fights.")
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
