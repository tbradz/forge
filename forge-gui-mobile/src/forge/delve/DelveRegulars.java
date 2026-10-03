package forge.delve;

import forge.adventure.data.EnemyData;
import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The Tavern's regulars: townsfolk who drink there in the evenings (three of five on any night).
 * Each one trades in a different kind of rumor, built from the real game state, so their talk
 * is worth hearing: what lurks in your next dungeon, tomorrow's Card Shop, the Castle board,
 * the next set tier, and tips.
 */
public enum DelveRegulars {
    WENNA("Old Wenna", "retired delver", "sprites/enemy/humanoid/human/peasant/peasant2.atlas"),
    PIP("Pip", "light-fingered", "sprites/enemy/humanoid/human/rogue/rogue.atlas"),
    ILSE("Sister Ilse", "Castle chaplain", "sprites/enemy/humanoid/human/cleric/priest.atlas"),
    CORIN("Corin", "archivist", "sprites/enemy/humanoid/human/wizard/archivist.atlas"),
    HOB("Hob", "travelling bard", "sprites/enemy/humanoid/human/bard/humanbard.atlas");

    public final String name, role, atlas;

    DelveRegulars(String name, String role, String atlas) {
        this.name = name;
        this.role = role;
        this.atlas = atlas;
    }

    /** Who's in tonight: three of the five, the same all evening. */
    public static List<DelveRegulars> tonight(int day) {
        List<DelveRegulars> all = new ArrayList<>(List.of(values()));
        Collections.shuffle(all, new Random(day * 4099L + 31));
        return all.subList(0, 3);
    }

    /** What they have to say tonight (the same rumor all evening). */
    public String rumor(int day) {
        Random r = new Random(day * 131L + ordinal() * 7919L);
        try {
            switch (this) {
                case WENNA: return dungeonRumor(r);
                case PIP: return shopRumor(day, r);
                case ILSE: return castleRumor(r);
                case CORIN: return nextTierRumor(day);
                default: return TIPS[r.nextInt(TIPS.length)];
            }
        } catch (Exception e) {
            e.printStackTrace(); // a rumor is never worth a crash
            return TIPS[r.nextInt(TIPS.length)];
        }
    }

    // ---- rumors --------------------------------------------------------------------------

    private static String list(List<String> words) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.size(); i++)
            sb.append(i == 0 ? "" : i == words.size() - 1 ? " and " : ", ").append(words.get(i));
        return sb.toString();
    }

    /** Old Wenna: what lives in the dungeon at your highest tier, and who might wait at the bottom. */
    private static String dungeonRumor(Random r) {
        DelveDay d = DelveDay.today();
        List<String> kinds = new ArrayList<>();
        for (String t : d.denizens(3)) kinds.add(DelveDay.plural(t));
        List<EnemyData> bosses = d.themedBoss();
        String at = "Down in the " + d.edition.getName() + " dungeon";
        String who = kinds.isEmpty() ? at + " it's all sorts these days." : at + " it's " + list(kinds) + ", thick as fleas.";
        if (bosses.size() >= 2) {
            List<EnemyData> b = new ArrayList<>(bosses);
            Collections.shuffle(b, r);
            who += " And at the bottom? Last I heard, " + article(b.get(0).getName()) + " " + b.get(0).getName()
                    + ", or maybe " + article(b.get(1).getName()) + " " + b.get(1).getName() + ". Bring answers for both.";
        }
        return who;
    }

    /** Pip: something good arriving at the Card Shop tomorrow (stock is fixed by day and tier). */
    private static String shopRumor(int day, Random r) {
        DelveProfile prof = DelveProfile.get();
        List<PaperCard> stock = DelveDay.forTier(day + 1, prof.topTier()).shopSingles();
        DelveDay.today(); // put today's card world back in the cache
        if (stock.isEmpty()) return "The shop's restocking tomorrow. Couldn't get a look at the crates, though.";
        PaperCard legend = stock.get(stock.size() - 1);
        PaperCard rare = stock.size() > 4 ? stock.get(4) : legend;
        return r.nextBoolean()
                ? "Saw the crates going into the Card Shop. There's " + article(legend.getName()) + " " + legend.getName()
                        + " in tomorrow's lot. You didn't hear it from me."
                : "Tomorrow the shop's putting out " + article(rare.getName()) + " " + rare.getName()
                        + ". Go early. Or don't, and I'll... buy it myself. Honestly.";
    }

    /** Sister Ilse: the champions board, and how far you are from your next title. */
    private static String castleRumor(Random r) {
        List<DelveRenown.Standing> board = DelveRenown.board();
        DelveRenown.Standing top = board.get(0);
        int rank = DelveRenown.yourRank();
        DelveRenown.Title now = DelveRenown.title(), next = DelveRenown.next(now);
        String lead = top.you ? "Your name tops the Castle board. The others pray for your downfall. Quietly, I hope."
                : top.name + " leads the champions board with " + top.renown + " Renown. You stand " + ordinal(rank) + ".";
        String climb = next == null ? " There is no higher title than yours. Try humility."
                : " " + (next.renown - DelveProfile.get().renown()) + " more Renown and they'll call you " + next.title + ". "
                        + next.unlocks;
        return lead + climb;
    }

    /** Corin: the set you'll unlock next, and what lives in its dungeon. */
    private static String nextTierRumor(int day) {
        DelveProfile prof = DelveProfile.get();
        int next = prof.topTier() + 1;
        if (next >= DelveDay.tiers().size())
            return "You've reached the newest pages of my histories. Whatever comes after " + DelveDay.today().edition.getName()
                    + ", nobody has written it yet.";
        DelveDay later = DelveDay.forTier(day, next);
        List<String> kinds = new ArrayList<>();
        for (String t : later.denizens(2)) kinds.add(DelveDay.plural(t));
        DelveDay.today();
        return "Clear the gate and the dungeon turns to " + later.edition.getName() + ". My books say "
                + (kinds.isEmpty() ? "strange things" : list(kinds)) + " crawl through it. I'd read up, if I were you. Well. I'd read anyway.";
    }

    private static final String[] TIPS = {
            "Elites guard relics. Beat one and you'll walk away with a charm worth more than the gold.",
            "A rest isn't just a nap. You can take a card out of your deck there. Thin decks draw their best cards.",
            "The Card Shop buys rares and mythics, if you've copies your decks don't need. Mind the fee.",
            "Prerelease at the Card Shop: six packs, a deck, three rounds. You keep every card you open.",
            "Insurance tokens from the shop: if you fall, the run pays like you cleared it. Cheap at the price, says me.",
            "The Outfitter sells playmats now. Doesn't help you win. Helps you lose in style.",
            "Castle titles aren't just for show. A Knight gets a discount at the Card Shop, and a Baron takes more life into the dungeon.",
            "Pai Gow at the Card Shop: three cards a hand, five life. Fast, mean and a bit silly. My kind of game.",
            "The merchants below sell cheaper than the shop up here. Spend your dungeon gold down there.",
    };

    private static String article(String name) {
        return "AEIOU".indexOf(Character.toUpperCase(name.charAt(0))) >= 0 ? "an" : "a";
    }

    private static String ordinal(int n) {
        int m = n % 100;
        String s = m >= 11 && m <= 13 ? "th" : n % 10 == 1 ? "st" : n % 10 == 2 ? "nd" : n % 10 == 3 ? "rd" : "th";
        return n + s;
    }
}
