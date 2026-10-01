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
            int price = DelveEconomy.shopPrice(pc);
            TextraButton buy = button(sold ? "[GRAY]Sold" : "[%85]Buy " + price + "g", x, y + cardH + 1, cardW, 15,
                    () -> buySingle(index, pc, price));
            buy.setDisabled(sold || prof.gold() < price);
        }

        // packs on the right
        float px = 272, pw = 198;
        image("ui/delve/panel.png", px - 6, 30, pw + 12, 170);
        label("[%100]Booster packs", px, 36, pw, 14, Align.center);
        label("[%70]" + DelveEconomy.PACK_PRICE + " gold each, " + DelveEconomy.PACKS_PER_DAY + " of each per day",
                px, 50, pw, 12, Align.center);
        packButton(0, day.edition, px, 70, pw);
        packButton(1, day.recentPackSet(), px, 116, pw);
        label("[%70]Singles and packs go straight into your collection. The last single is always a legend that can lead a Commander deck.", px, 160, pw, 34, Align.center);
        button("[GOLD]Run tokens", px + 24, 208, pw - 48, 22, this::tokenCounter);

        button("Leave", 190, 244, 100, 20, () -> Forge.switchScene(DelveHubScene.instance()));
    }

    private void packButton(int type, CardEdition set, float x, float y, float w) {
        DelveProfile prof = DelveProfile.get();
        int left = DelveEconomy.PACKS_PER_DAY - prof.packsBought(type);
        TextraButton b = button("[%85]" + set.getName() + "\n[%70]" + (left > 0 ? left + " left" : "sold out"),
                x, y, w, 38, () -> buyPack(type, set));
        b.setDisabled(left <= 0 || prof.gold() < DelveEconomy.PACK_PRICE);
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
        if (prof.packsBought(type) >= DelveEconomy.PACKS_PER_DAY || !prof.spendGold(DelveEconomy.PACK_PRICE)) return;
        prof.markPackBought(type);
        DelveAudio.shuffle();
        List<PaperCard> cards = DelveDay.today().openPack(set, rng);
        prof.addToCollection(cards);
        DelvePickScene.instance().show("You open a " + set.getName() + " booster", cards, 0, 0, "Back",
                x -> Forge.switchScene(this));
    }

    @Override
    public boolean back() {
        Forge.switchScene(DelveHubScene.instance());
        return true;
    }
}
