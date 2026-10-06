package forge.delve;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.Timer;
import forge.Forge;
import forge.adventure.data.EnemyData;
import forge.adventure.scene.UIScene;
import forge.deck.Deck;
import forge.item.PaperCard;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

/**
 * Screenshot tour (a developer tool): start the game with {@code -Ddelve.shots=<folder>} and it makes a
 * test save, visits every Delve screen and dialog by itself and saves a PNG of each, then quits. Used to
 * check layouts (overlapping or overflowing text) without playing through by hand. Run it on a portable
 * copy (forge.profile.properties pointing at its own userdata) so real saves are never touched. The test
 * save sits at the set with the longest name, to stress the headers. Nothing happens without the flag.
 */
public final class DelveShots {
    private DelveShots() {}

    private static final String DIR = System.getProperty("delve.shots");
    private static boolean started;
    private static final List<String> names = new ArrayList<>();
    private static final List<Runnable> steps = new ArrayList<>();
    private static final List<Float> waits = new ArrayList<>();
    private static int next;
    private static volatile String pending;

    public static boolean enabled() {
        return DIR != null && !DIR.isEmpty();
    }

    /** Called when the title screen is shown: begin the tour once (only with the flag). */
    public static void startIfEnabled() {
        if (!enabled() || started) return;
        started = true;
        new File(DIR).mkdirs();
        plan();
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                step();
            }
        }, 3f);
    }

    /** Called by Forge.render at the end of every frame: take the screenshot a step asked for. */
    public static void afterFrame() {
        String name = pending;
        if (name == null) return;
        pending = null;
        try {
            Pixmap pm = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
            PixmapIO.writePNG(Gdx.files.absolute(new File(DIR, name + ".png").getAbsolutePath()), pm, Deflater.DEFAULT_COMPRESSION, true);
            pm.dispose();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                step();
            }
        }, 0.3f);
    }

    private static void step() {
        if (next >= steps.size()) {
            try {
                java.nio.file.Files.writeString(new File(DIR, "done.txt").toPath(), String.join("\n", names));
            } catch (Exception ignored) {
            }
            Gdx.app.exit();
            return;
        }
        int i = next++;
        String name = String.format("%02d_%s", i + 1, names.get(i));
        try {
            if (Forge.getCurrentScene() instanceof UIScene) ((UIScene) Forge.getCurrentScene()).removeDialog();
            steps.get(i).run();
        } catch (Exception e) {
            System.err.println("DelveShots: step " + name + " failed");
            e.printStackTrace();
        }
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                pending = name;
            }
        }, waits.get(i));
    }

    /** optional regex ({@code -Ddelve.shots.only=new_save|paigow}): run just the matching steps */
    private static final String ONLY = System.getProperty("delve.shots.only");

    private static void add(String name, float wait, Runnable action) {
        if (ONLY != null && !name.matches(".*(" + ONLY + ").*")) return;
        names.add(name);
        waits.add(wait);
        steps.add(action);
    }

    // ---- the tour ------------------------------------------------------------------------------

    private static DelveRun run;

    private static void plan() {
        add("saves", 2f, () -> Forge.switchScene(DelveSavesScene.instance()));
        add("new_save_character", 2.5f, () -> {
            DelveSaves.load(DelveSaves.create());
            DelveProfile prof = DelveProfile.get();
            prof.setAllSets(false);
            prof.addGold(50000);
            List<forge.card.CardEdition> tiers = DelveDay.tiers();
            int longest = 0; // the set with the longest name, to stress headers
            for (int t = 0; t < tiers.size(); t++)
                if (tiers.get(t).getName().length() > tiers.get(longest).getName().length()) longest = t;
            prof.devSetTopTier(longest);
            DelveCharacterScene.instance().open(() -> { });
        });
        add("tour_first_stop", 4f, () -> DelveTourScene.instance().play(true, () -> { }));
        add("hub_day", 2.5f, () -> Forge.switchScene(DelveHubScene.instance()));
        add("hub_house", 1.5f, () -> DelveHubScene.instance().openHouse());
        add("hub_options", 1.5f, () -> DelveHubScene.instance().options());
        add("hub_difficulty", 1.5f, () -> DelveHubScene.instance().chooseDifficulty());
        add("shop", 2.5f, () -> Forge.switchScene(DelveShopScene.instance()));
        add("shop_sell_bulk", 1.5f, () -> DelveShopScene.instance().sellBulk());
        add("outfitter_sleeves", 2f, () -> outfitter(0));
        add("outfitter_foil", 2f, () -> outfitter(1));
        add("outfitter_playmats", 2f, () -> outfitter(2));
        add("gate", 2.5f, () -> Forge.switchScene(DelveGateScene.instance()));
        add("half_deck_pick", 3f, () -> {
            DelveDay day = DelveDay.today();
            List<DelveDay.HalfDeck> halves = day.halfDecks(new Random(1));
            List<PaperCard> faces = new ArrayList<>();
            for (DelveDay.HalfDeck h : halves) faces.add(h.face);
            DelvePickScene.instance().show("Pick two half-decks:  " + halves.get(0).name + "   |   " + halves.get(1).name
                    + (halves.size() > 2 ? "   |   " + halves.get(2).name : ""), faces, 2, 2, null, pc -> "Half-deck", x -> { });
        });
        add("map", 3f, () -> {
            DelveDay day = DelveDay.today();
            List<DelveDay.HalfDeck> halves = day.halfDecks(new Random(1));
            Deck deck = DelveDay.combineCompact(halves.get(0), halves.get(1));
            run = DelveRun.start(day, deck);
            run.boon = DelveBoon.VIGOR;
            Forge.switchScene(DelveMapScene.instance());
        });
        add("map_lands", 1.5f, () -> DelveMapScene.instance().basicLands(run));
        add("battle_spoils", 3f, () -> {
            List<PaperCard> three = new ArrayList<>(DelveDay.today().rares.subList(0, Math.min(3, DelveDay.today().rares.size())));
            DelvePickScene.instance().show("Battle spoils: add a card to your deck", three, 1, 1, "Skip", pc -> "Take", x -> { });
        });
        add("swap_report", 3f, () -> {
            List<PaperCard> r = DelveDay.today().rares;
            DelveSwapScene.instance().report("Echoes of " + DelveDay.today().edition.getName(),
                    "A standing stone hums with visions. " + r.get(0).getName() + " becomes " + r.get(1).getName() + ".",
                    List.of(r.get(0)), List.of(r.get(1)), () -> { });
        });
        add("swap_or_add", 3f, () -> {
            List<PaperCard> r = DelveDay.today().rares;
            DelveSwapScene.instance().swapOrAdd(r.get(2), r.get(3), 21, () -> { }, () -> { });
        });
        add("talk_elite", 3f, () -> {
            List<EnemyData> elites = DelveDay.today().themedElite();
            DelveTalkScene.say(DelveTalkScene.DUNGEON, elites.get(0), DelvePersona.Moment.GREET, false, false,
                    "[GOLD]Elite", "[GOLD]Let's play", () -> { });
        });
        add("pack_opening", 3f, () -> {
            List<PaperCard> pack = DelveDay.today().openPack(DelveDay.today().edition, new Random(2));
            String set = DelveDay.today().edition.getName();
            DelvePackOpenScene.instance().openPack("Card Shop: " + set + " booster", set, pack, x -> { });
        });
        add("sell_rares_page", 3f, () -> {
            List<PaperCard> twelve = new ArrayList<>(DelveDay.today().rares.subList(0, Math.min(12, DelveDay.today().rares.size())));
            DelvePickScene.instance().show("Sell rares: the owner pays a card's value less a 5g fee   (page 1 of 3)",
                    twelve, 0, twelve.size(), "Done", pc -> "Sell 17g", x -> { });
        });
        add("hub_night", 2.5f, () -> {
            DelveProfile.get().makeEvening();
            Forge.switchScene(DelveHubScene.instance());
        });
        add("tavern", 3f, () -> Forge.switchScene(DelveTavernScene.instance()));
        add("talk_regular", 3f, () -> {
            DelveRegulars r = DelveRegulars.tonight(DelveProfile.get().day()).get(0);
            DelveTalkScene.speak(DelveTalkScene.TAVERN, r.atlas, r.name, "[%85]" + r.role, r.rumor(DelveProfile.get().day()),
                    "[GOLD]Thanks", () -> { });
        });
        for (int n = 0; n < 3; n++) { // the featured event rotates by day: show all three
            final int k = n;
            add("castle_" + (k + 1), 2.5f, () -> {
                if (k > 0) DelveProfile.get().sleep();
                DelveProfile.get().makeEvening();
                Forge.switchScene(DelveCastleScene.instance());
            });
        }
        add("castle_champions", 1.5f, () -> DelveCastleScene.instance().championsBoard());
        add("castle_night_standings", 3f, () -> DelvePrereleaseScene.castle()
                .debugStandings(DelvePrereleaseScene.Kind.DRAFT_NIGHT, run.deck));
        // Pai Gow rules: empty piles can never produce a winner, so the stalemate rule should call a draw at once
        add("paigow_stalemate", 12f, () -> {
            DelveDuelScene.instance().setupPaiGow(new ArrayList<>(), DelveDay.today().themedElite().get(0), new ArrayList<>(), 1,
                    (won, life) -> System.out.println("DelveShots: stalemate game ended, won=" + won
                            + " draw=" + DelveDuelScene.instance().lastWasDraw()));
            Forge.switchScene(DelveDuelScene.instance());
        });
        add("paigow_mana", 12f, () -> {
            List<PaperCard> r = DelveDay.today().rares;
            DelveDuelScene.instance().setupPaiGow(new ArrayList<>(r.subList(0, 3)), DelveDay.today().themedElite().get(0),
                    new ArrayList<>(r.subList(3, 6)), 1, (won, life) -> { });
            Forge.switchScene(DelveDuelScene.instance());
        });
    }

    private static void outfitter(int tab) {
        DelveOutfitterScene.instance().tab = tab;
        Forge.switchScene(DelveOutfitterScene.instance());
    }
}
