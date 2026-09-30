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
 * step. Handles fights (via DelveDuelScene), events, the merchant, rests, card
 * rewards and the end of the run (keep picks or Locked Deck).
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
        label("Life [GOLD]" + run.life + "/" + DelveRun.MAX_LIFE + "[]    Gold [GOLD]" + run.gold + "[]    Deck "
                        + run.deckSize() + " cards (min " + DelveRun.MIN_DECK + ")    Fights won " + run.fightsWon,
                0, 30, W, 16, Align.center);

        int steps = run.layers.size();
        float gap = 5, colW = (W - 16 - gap * (steps - 1)) / steps;
        float startX = 8;
        for (int s = 0; s < steps; s++) {
            List<Node> layer = run.layers.get(s);
            float x = startX + s * (colW + gap);
            label((s == steps - 1 ? "[%80]Final" : "[%80]Step " + (s + 1)), x, 54, colW, 12, Align.center);
            float nodeH = 40, nodeGap = 8;
            float totalH = layer.size() * nodeH + (layer.size() - 1) * nodeGap;
            float y = 70 + (152 - totalH) / 2f;
            for (int k = 0; k < layer.size(); k++) {
                Node node = layer.get(k);
                boolean done = s < run.step;
                boolean wasChosen = done && s < chosen.size() && chosen.get(s) == k;
                String text = nodeText(node, wasChosen);
                final int step = s, idx = k;
                TextraButton b = button(text, x, y, colW, nodeH, () -> choose(step, idx));
                b.setDisabled(s != run.step || run.over);
                if (done && !wasChosen) b.getColor().a = 0.35f;
                y += nodeH + nodeGap;
            }
        }

        button("View deck", 110, 236, 110, 22, this::viewDeck);
        button("Abandon run", 260, 236, 110, 22, () ->
                confirm("Abandon run", "End this run now? You'll still get the reward for how far you got.",
                        () -> endRun(false)));
    }

    private static String shortName(String s) {
        return s.length() <= 13 ? s : s.substring(0, 12) + ".";
    }

    private static String nodeText(Node node, boolean chosen) {
        String head, sub;
        switch (node.type) {
            case REST: head = "[SKY]Rest"; sub = "heal / trim"; break;
            case EVENT: head = "[#c080ff]?  Event"; sub = "unknown"; break;
            case MERCHANT: head = "[GOLD]Merchant"; sub = "buy / sell"; break;
            case BOSS: head = "[RED]Boss"; sub = shortName(node.enemy.getName()); break;
            case ELITE: head = "[ORANGE]Elite"; sub = shortName(node.enemy.getName()); break;
            default: head = "Fight"; sub = shortName(node.enemy.getName());
        }
        if (chosen) head = "[GREEN]" + head.replaceAll("\\[[^\\]]*\\]", "");
        return "[%75]" + head + "[]\n[%60]" + sub;
    }

    private void choose(int step, int index) {
        DelveRun run = DelveRun.current();
        if (run == null || step != run.step) return;
        Node node = run.layers.get(step).get(index);
        switch (node.type) {
            case REST: rest(run, index); return;
            case EVENT: event(run, node, index); return;
            case MERCHANT: merchant(run, node, index); return;
            default:
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
        boolean elite = node.type == NodeType.ELITE;
        int gold = DelveEconomy.fightGold(node.type, run.rng);
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
        if (cleared) return 5;
        if (run.fightsWon == 0) return 1;
        return run.fightsWon >= 3 ? 3 : 2;
    }

    private void endRun(boolean cleared) {
        DelveRun run = DelveRun.current();
        if (run == null) return;
        run.over = true;
        run.cleared = cleared;
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
