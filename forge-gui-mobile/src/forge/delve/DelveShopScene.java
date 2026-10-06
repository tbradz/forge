package forge.delve;

import com.badlogic.gdx.utils.Align;
import com.github.tommyettinger.textra.TextraButton;
import forge.Forge;
import forge.adventure.scene.RewardScene;
import forge.adventure.util.Reward;
import forge.adventure.util.RewardActor;
import forge.card.CardEdition;
import forge.item.PaperCard;

import java.util.List;
import java.util.Random;

/**
 * The town Card Shop: today's small stock of singles plus two kinds of booster
 * packs, paid for with town gold. Stock is limited and resets each day.
 */
public class DelveShopScene extends DelveScene {
    private static DelveShopScene object;
    private final Random rng = new Random();

    private DelveShopScene() {
        super("ui/delve_shop.json");
    }

    public static DelveShopScene instance() {
        if (object == null)
            object = new DelveShopScene();
        return object;
    }

    @Override
    public void enter() {
        DelveAudio.town();
        build();
        super.enter();
    }

    private void build() {
        clearScreen();
        DelveDay day = DelveDay.today();
        DelveProfile prof = DelveProfile.get();
        label("[%90][GOLD]Card Shop", 8, 5, 200, 16, Align.left);
        label("[%90][GOLD]Gold[] " + prof.gold(), 280, 5, 192, 16, Align.right);

        // singles: two rows of four on the left
        List<PaperCard> stock = day.shopSingles();
        float cardH = 82, cardW = cardH * 0.716f, gap = 6, left = 10, top = 34;
        for (int i = 0; i < stock.size(); i++) {
            int r = i / 4, c = i % 4;
            float x = left + c * (cardW + gap), y = top + r * (cardH + 20);
            PaperCard pc = stock.get(i);
            boolean sold = prof.shopBought(i);
            RewardActor card = new RewardActor(new Reward(pc, true), false, RewardScene.Type.Loot, false);
            card.setBounds(x, H - y - cardH, cardW, cardH);
            track(card);
            if (sold) { // card art ignores alpha, so darken it with an overlay instead
                image("ui/delve/shade.png", x, y, cardW, cardH);
                label("[%110][GRAY]SOLD", x, y + cardH / 2f - 8, cardW, 16, Align.center);
            }
            final int index = i;
            int price = DelveRenown.shopPrice(DelveEconomy.shopPrice(pc));
            TextraButton buy = button(sold ? "[GRAY]Sold" : "[%85]Buy " + price + "g", x, y + cardH + 1, cardW, 15,
                    () -> buySingle(index, pc, price));
            buy.setDisabled(sold || prof.gold() < price);
        }

        // packs on the right
        float px = 272, pw = 198;
        image("ui/delve/panel.png", px - 6, 30, pw + 12, 170);
        label("[%100]Booster packs", px, 36, pw, 14, Align.center);
        label("[%70]" + DelveRenown.shopPrice(DelveEconomy.PACK_PRICE) + " gold each, " + DelveEconomy.PACKS_PER_DAY + " of each per day",
                px, 50, pw, 12, Align.center);
        packButton(0, day.edition, px, 70, pw);
        packButton(1, day.recentPackSet(), px, 116, pw);
        label("[%70]Singles and packs go straight into your collection. The last single is always a legend that can lead a Commander deck.", px, 160, pw, 34, Align.center);
        button("[GOLD]Run tokens", px, 202, pw / 2f - 3, 19, this::tokenCounter);
        boolean pre = prof.prereleaseToday();
        button(pre ? "[GRAY]Prerelease done" : "[GOLD]Prerelease " + DelveEconomy.PRERELEASE_ENTRY + "g",
                px + pw / 2f + 3, 202, pw / 2f - 3, 19, this::prerelease)
                .setDisabled(pre || prof.gold() < DelveEconomy.PRERELEASE_ENTRY);
        int pgLeft = DelveEconomy.PAI_GOW_PER_DAY - prof.paiGowToday();
        button(pgLeft > 0 ? "[GOLD]Pai Gow[WHITE]  (" + pgLeft + " left today)" : "[GRAY]Pai Gow: come back tomorrow",
                px, 224, pw, 18, this::paiGow).setDisabled(pgLeft <= 0 || prof.gold() < DelveEconomy.PACK_PRICE);

        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
        button("[GOLD]Sell rares", 300, 245, 110, 18, () -> sellRares(0));
        button("[GOLD]Sell bulk", 70, 245, 110, 18, this::sellBulk);
    }

    // ---- selling rares to the shop -------------------------------------------------------

    /** Cards shown per page when selling (the pick screen shrinks cards to fit, so keep pages small). */
    private static final int SELL_PAGE = 12;

    /**
     * Rares and mythics you can sell: every copy beyond what your most demanding built deck
     * (House or Commander) uses, so selling never breaks a deck. Best payout first.
     */
    /** How many copies of each card your most demanding built deck (House or Commander) uses. */
    private static java.util.Map<PaperCard, Integer> deckNeeds() {
        java.util.Map<PaperCard, Integer> needed = new java.util.HashMap<>();
        List<forge.deck.Deck> decks = new java.util.ArrayList<>();
        for (forge.deck.Deck d : DelveDeckEditScene.decks()) decks.add(d);
        for (forge.deck.Deck d : DelveDeckEditScene.commanderDecks()) decks.add(d);
        for (forge.deck.Deck d : decks) {
            java.util.Map<PaperCard, Integer> inDeck = new java.util.HashMap<>();
            for (java.util.Map.Entry<forge.deck.DeckSection, forge.deck.CardPool> part : d)
                for (java.util.Map.Entry<PaperCard, Integer> e : part.getValue())
                    inDeck.merge(e.getKey(), e.getValue(), Integer::sum);
            inDeck.forEach((pc, n) -> needed.merge(pc, n, Math::max));
        }
        return needed;
    }

    private static List<PaperCard> sellableRares() {
        java.util.Map<PaperCard, Integer> needed = deckNeeds();
        List<PaperCard> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<PaperCard, Integer> e : DelveProfile.get().collection()) {
            PaperCard pc = e.getKey();
            if (!DelveEconomy.shopBuys(pc)) continue;
            for (int i = needed.getOrDefault(pc, 0); i < e.getValue(); i++) out.add(pc);
        }
        out.sort(java.util.Comparator.comparingInt((PaperCard pc) -> -DelveEconomy.shopSellPayout(pc))
                .thenComparing(PaperCard::getName));
        return out;
    }

    // ---- selling bulk (commons and uncommons) in lots ------------------------------------------

    /**
     * Commons and uncommons you can spare: copies beyond {@link DelveEconomy#BULK_KEEP} of each card and
     * beyond what your built decks use; basic lands aren't bulk. The set's weakest cards come first.
     */
    private static List<PaperCard> spareBulk() {
        java.util.Map<PaperCard, Integer> needed = deckNeeds();
        List<PaperCard> out = new java.util.ArrayList<>();
        for (java.util.Map.Entry<PaperCard, Integer> e : DelveProfile.get().collection()) {
            PaperCard pc = e.getKey();
            if (!DelveEconomy.isBulk(pc)) continue;
            int keep = Math.max(DelveEconomy.BULK_KEEP, needed.getOrDefault(pc, 0));
            for (int i = keep; i < e.getValue(); i++) out.add(pc);
        }
        out.sort(java.util.Comparator.comparingDouble(DelveRank::score).thenComparing(PaperCard::getName));
        return out;
    }

    /** The owner buys bulk 100 cards at a time for a few coins. */
    void sellBulk() {
        List<PaperCard> spare = spareBulk();
        int lots = spare.size() / DelveEconomy.BULK_LOT;
        String text = "[%80]\"Commons and uncommons? I'll take them by the box: " + DelveEconomy.BULK_LOT + " cards for "
                + DelveEconomy.BULK_LOT_PRICE + " gold.\"\n\n[%80]You have " + spare.size() + " spare (copies beyond "
                + DelveEconomy.BULK_KEEP + " of each card and beyond what your decks use). The weakest go first.";
        List<String> labels = new java.util.ArrayList<>();
        List<Boolean> enabled = new java.util.ArrayList<>();
        List<Runnable> actions = new java.util.ArrayList<>();
        labels.add("Sell one box (" + DelveEconomy.BULK_LOT + " cards, " + DelveEconomy.BULK_LOT_PRICE + "g)");
        enabled.add(lots >= 1);
        actions.add(() -> sellBulkLots(spare, 1));
        labels.add("Sell every full box (" + lots + " box" + (lots == 1 ? "" : "es") + ", " + lots * DelveEconomy.BULK_LOT_PRICE + "g)");
        enabled.add(lots >= 2);
        actions.add(() -> sellBulkLots(spare, lots));
        labels.add("Not now");
        enabled.add(true);
        actions.add(() -> { });
        choose("Sell bulk", text, labels, enabled, actions);
    }

    private void sellBulkLots(List<PaperCard> spare, int lots) {
        DelveProfile prof = DelveProfile.get();
        int sold = 0;
        for (int i = 0; i < lots * DelveEconomy.BULK_LOT && i < spare.size(); i++)
            if (prof.removeFromCollection(spare.get(i))) sold++;
        int gold = sold / DelveEconomy.BULK_LOT * DelveEconomy.BULK_LOT_PRICE;
        prof.addGold(gold);
        DelveAudio.coins();
        build();
        info("Card Shop", "You sell " + sold + " bulk cards for " + gold + " gold.", null);
    }

    /** Sell rares and mythics to the owner, a page at a time; each page's sale happens on Confirm. */
    private void sellRares(int page) {
        List<PaperCard> all = sellableRares();
        if (all.isEmpty()) {
            Forge.switchScene(this);
            info("Sell rares", "The owner only buys rares and mythics, and you have none to spare. "
                    + "Copies your built decks use stay in your collection. Commons and uncommons are bulk: the shop doesn't take them.", null);
            return;
        }
        int pages = (all.size() + SELL_PAGE - 1) / SELL_PAGE;
        int p = page % pages;
        List<PaperCard> shown = new java.util.ArrayList<>(all.subList(p * SELL_PAGE, Math.min(all.size(), (p + 1) * SELL_PAGE)));
        if (pages > 1)
            DelvePickScene.instance().withExtra("Page " + ((p + 1) % pages + 1) + " of " + pages, () -> sellRares(p + 1));
        DelvePickScene.instance().show("Sell rares: the owner pays a card's value less a " + DelveEconomy.SHOP_SELL_FEE
                        + "g fee" + (pages > 1 ? "   (page " + (p + 1) + " of " + pages + ")" : ""),
                shown, 0, shown.size(), "Done", pc -> "Sell " + DelveEconomy.shopSellPayout(pc) + "g", sold -> {
                    Forge.switchScene(this);
                    if (sold.isEmpty()) return;
                    DelveProfile prof = DelveProfile.get();
                    int total = 0, count = 0;
                    for (PaperCard pc : sold)
                        if (prof.removeFromCollection(pc)) {
                            total += DelveEconomy.shopSellPayout(pc);
                            count++;
                        }
                    prof.addGold(total);
                    DelveAudio.coins();
                    build();
                    info("Card Shop", "You sell " + count + (count == 1 ? " card" : " cards") + " for " + total + " gold.", null);
                });
    }

    private void packButton(int type, CardEdition set, float x, float y, float w) {
        DelveProfile prof = DelveProfile.get();
        int left = DelveEconomy.PACKS_PER_DAY - prof.packsBought(type);
        TextraButton b = button("[%85]" + set.getName() + "\n[%70]" + (left > 0 ? left + " left" : "sold out"),
                x, y, w, 38, () -> buyPack(type, set));
        b.setDisabled(left <= 0 || prof.gold() < DelveRenown.shopPrice(DelveEconomy.PACK_PRICE));
    }

    private void prerelease() {
        DelveDay day = DelveDay.today();
        confirm("Prerelease", "Today's prerelease: open " + DelveGateScene.PRERELEASE_PACKS + " " + day.edition.getName()
                        + " boosters, build a sealed deck, and play three rounds.\nYou keep every card you open. Prize packs by record: "
                        + "3-0 four, 2-1 two, 1-2 one.\n\nEntry " + DelveEconomy.PRERELEASE_ENTRY + " gold, once a day. Sign up?",
                () -> DelvePrereleaseScene.instance().begin());
    }

    /** Sets per page in the Pai Gow pack menu (newest first). */
    private static final int PAI_GOW_PAGE = 4; // + Older / Newer / Not now keeps the dialog to 7 buttons

    private void paiGow() {
        paiGow(0);
    }

    /** Pai Gow: pick which set's pack to buy (any unlocked tier, a page at a time), then play. */
    private void paiGow(int page) {
        DelveProfile prof = DelveProfile.get();
        List<String> labels = new java.util.ArrayList<>();
        List<Boolean> enabled = new java.util.ArrayList<>();
        List<Runnable> actions = new java.util.ArrayList<>();
        int newest = prof.topTier() - page * PAI_GOW_PAGE;
        for (int t = newest; t >= 0 && t > newest - PAI_GOW_PAGE; t--) {
            CardEdition set = DelveDay.tiers().get(t);
            labels.add(set.getName() + "  " + DelveEconomy.PACK_PRICE + "g");
            enabled.add(prof.gold() >= DelveEconomy.PACK_PRICE);
            actions.add(() -> DelvePaiGowScene.instance().begin(set));
        }
        if (newest - PAI_GOW_PAGE >= 0) {
            labels.add("[%85]Older sets...");
            enabled.add(true);
            actions.add(() -> paiGow(page + 1));
        }
        if (page > 0) {
            labels.add("[%85]Newer sets...");
            enabled.add(true);
            actions.add(() -> paiGow(page - 1));
        }
        labels.add("Not now");
        enabled.add(true);
        actions.add(() -> { });
        choose("Pai Gow", "[%75]Booster Blitz with a regular. You each buy a pack and split it into 4 piles of 3. "
                + "Each game your pile is your whole hand: no library, 5 life, unlimited mana. First to 3 wins takes "
                + "2 cards of their choice from the other's pack. Your pack is yours either way, minus 2 if you lose.\n"
                + "Which pack do you buy?", labels, enabled, actions);
    }

    /** The token counter: buy run tokens with town gold (no daily limit). */
    private void tokenCounter() {
        DelveProfile prof = DelveProfile.get();
        List<String> labels = new java.util.ArrayList<>();
        List<Boolean> enabled = new java.util.ArrayList<>();
        List<Runnable> actions = new java.util.ArrayList<>();
        StringBuilder text = new StringBuilder("[%70]Tokens are offered when they apply; you're always asked first.");
        for (DelveTokens t : DelveTokens.values()) {
            text.append("\n[%65][GOLD]").append(t.title).append("[WHITE] (have ").append(prof.tokens(t)).append("): ")
                    .append(t.description);
            labels.add("Buy " + t.title + "  " + t.price + "g");
            enabled.add(prof.gold() >= t.price);
            actions.add(() -> {
                if (!prof.spendGold(t.price)) return;
                prof.addToken(t, 1);
                build();
                tokenCounter();
            });
        }
        labels.add("Done");
        enabled.add(true);
        actions.add(this::build);
        choose("Run tokens   (gold " + prof.gold() + ")", text.toString(), labels, enabled, actions);
    }

    private void buySingle(int index, PaperCard pc, int price) {
        DelveProfile prof = DelveProfile.get();
        if (prof.shopBought(index) || !prof.spendGold(price)) return;
        prof.markShopBought(index);
        DelveAudio.coins();
        prof.addToCollection(List.of(pc));
        build();
        info("Card Shop", "You buy " + pc.getName() + " for " + price + " gold.", null);
    }

    private void buyPack(int type, CardEdition set) {
        DelveProfile prof = DelveProfile.get();
        if (prof.packsBought(type) >= DelveEconomy.PACKS_PER_DAY || !prof.spendGold(DelveRenown.shopPrice(DelveEconomy.PACK_PRICE))) return;
        prof.markPackBought(type);
        List<PaperCard> cards = DelveDay.today().openPack(set, rng);
        prof.addToCollection(cards);
        DelvePackOpenScene.instance().openPack("Card Shop: " + set.getName() + " booster", set.getName(), cards,
                x -> Forge.switchScene(this));
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
