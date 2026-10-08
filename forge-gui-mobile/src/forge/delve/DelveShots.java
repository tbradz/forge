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
            String set = System.getProperty("delve.shots.set"); // e.g. KHM: test that set's tier instead
            for (int t = 0; set != null && t < tiers.size(); t++)
                if (tiers.get(t).getCode().equalsIgnoreCase(set)) longest = t;
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
        add("archetype_pick", 3f, () -> {
            List<DelveDay.ArchetypeDeck> offers = DelveDay.today().archetypeOffers(new Random(1), true);
            List<PaperCard> faces = new ArrayList<>();
            for (DelveDay.ArchetypeDeck o : offers) faces.add(o.face);
            DelvePickScene.instance().show("Choose your archetype", faces, 1, 1, null,
                    pc -> { for (DelveDay.ArchetypeDeck o : offers) if (o.face.equals(pc)) return o.deck.getName(); return ""; }, x -> { });
        });
        add("map", 3f, () -> {
            DelveDay day = DelveDay.today();
            DelveDay.ArchetypeDeck o = day.archetypeOffers(new Random(1), true).get(0);
            run = DelveRun.start(day, o.deck);
            run.archetype = o.archetype;
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
        // deck check (printed to the log): opening-fight starter decks and Compact starting decks
        add("decks", 1f, () -> {
            DelveDay day = DelveDay.today();
            for (String c : new String[]{"WU", "UB", "BR", "RG", "GW"}) {
                Deck d = day.starterDeck(forge.card.ColorSet.fromNames(c.toCharArray()));
                StringBuilder sb = new StringBuilder("DelveShots starter " + c + ":");
                for (java.util.Map.Entry<PaperCard, Integer> e : d.getMain())
                    if (!e.getKey().getRules().getType().isLand()) sb.append(" ").append(e.getValue()).append("x ").append(e.getKey().getName()).append(",");
                System.out.println(sb);
            }
            System.out.println("DelveShots archetypes " + day.edition.getCode() + ": " + day.viableArchetypes());
            for (DelveArchetype a : day.viableArchetypes()) {
                Deck d = day.archetypeDeck(a, true, new Random(7));
                if (d == null) { System.out.println("DelveShots deck " + a + ": none"); continue; }
                int creatures = 0, answers = 0, cheap = 0, fit = 0;
                StringBuilder sb = new StringBuilder();
                for (java.util.Map.Entry<PaperCard, Integer> e : d.getMain()) {
                    PaperCard pc = e.getKey();
                    if (pc.getRules().getType().isLand()) continue;
                    if (pc.getRules().getType().isCreature()) creatures += e.getValue();
                    if (DelveDay.interaction(pc)) answers += e.getValue();
                    if (pc.getRules().getManaCost().getCMC() <= 2) cheap += e.getValue();
                    if (a.fits(pc)) fit += e.getValue();
                    sb.append(" ").append(pc.getName()).append(",");
                }
                System.out.println("DelveShots deck " + d.getName() + ": " + fit + " fit, " + creatures + " creatures, "
                        + answers + " answers, " + cheap + " cheap |" + sb);
            }
        });
        // Channel mana test: a dungeon fight with no lands; channel a card, then cast a spell with auto-pay
        add("channel_duel", 130f, () -> {
            DelveModes.setChannelMana(true);
            DelveDay day = DelveDay.today();
            DelveDay.ArchetypeDeck o = day.archetypeOffers(new Random(3), true).get(0);
            run = DelveRun.start(day, o.deck);
            run.archetype = o.archetype;
            run.channel = true;
            run.compact = true;
            DelveRun.Node node = null;
            for (List<DelveRun.Node> layer : run.layers)
                for (DelveRun.Node n : layer) if (node == null && n.type == DelveRun.NodeType.FIGHT) node = n;
            System.out.println("DelveShots channel: deck " + o.deck.getName() + " " + o.deck.getMain().countAll() + " cards");
            DelveDuelScene.instance().setup(run, node, (won, life) -> { });
            Forge.switchScene(DelveDuelScene.instance());
            Thread t = new Thread(DelveShots::channelTest, "DelveShots-channel");
            t.setDaemon(true);
            t.start();
        });
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
                    new ArrayList<>(r.subList(3, 6)), 2, (won, life) -> { }); // the AI goes first: does it handle a big pool?
            Forge.switchScene(DelveDuelScene.instance());
        });
    }

    private static void log(String s) {
        System.out.println("DelveShots channel: " + s);
    }

    private static String names(Iterable<forge.game.card.Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (forge.game.card.Card c : cards) sb.append(c.getName()).append(c.isTapped() ? "(T)" : "").append(", ");
        return sb.toString();
    }

    /** Drives the Channel test duel through the human's controller (no clicks needed). */
    private static void channelTest() {
        try {
            forge.gamemodes.match.HostedMatch m = null;
            forge.game.Game g = null;
            for (int i = 0; i < 60 && (m == null || g == null); i++) {
                Thread.sleep(500);
                m = DelveDuelScene.instance().match();
                if (m != null) g = m.getGame();
            }
            if (g == null) { log("no game"); return; }
            forge.player.PlayerControllerHuman pc = m.getHumanControllers().get(0);
            forge.game.player.Player me = pc.getPlayer();
            Thread.sleep(4000);
            pc.selectButtonOk(); // keep the opening hand
            for (int i = 0; i < 60; i++) { // wait for my first main phase with priority
                Thread.sleep(500);
                if (g.getPhaseHandler().is(forge.game.phase.PhaseType.MAIN1) && me.equals(g.getPhaseHandler().getPriorityPlayer())) break;
            }
            Thread.sleep(1500);
            log("phase " + g.getPhaseHandler().getPhase() + ", hand: " + names(me.getCardsIn(forge.game.zone.ZoneType.Hand)));
            log("command zone: " + names(me.getCardsIn(forge.game.zone.ZoneType.Command)));
            log("foe command zone: " + names(me.getOpponents().get(0).getCardsIn(forge.game.zone.ZoneType.Command)));
            // channel the first card that has a Channel ability
            forge.game.spellability.SpellAbility channel = null;
            for (forge.game.card.Card c : me.getCardsIn(forge.game.zone.ZoneType.Hand))
                for (forge.game.spellability.SpellAbility sa : c.getAllPossibleAbilities(me, true))
                    if (channel == null && sa.getDescription() != null && sa.getDescription().contains("Channel")) channel = sa;
            if (channel == null) { log("no Channel ability found in hand"); return; }
            log("channeling " + channel.getHostCard().getName() + ": " + channel.getDescription());
            pc.getInputProxy().selectAbility(channel);
            Thread.sleep(2000);
            log("after channel, command zone: " + names(me.getCardsIn(forge.game.zone.ZoneType.Command)));
            log("graveyard: " + names(me.getCardsIn(forge.game.zone.ZoneType.Graveyard)));
            // a second channel this turn should not be allowed
            int before = me.getCardsIn(forge.game.zone.ZoneType.Command).size();
            forge.game.spellability.SpellAbility again = null;
            for (forge.game.card.Card c : me.getCardsIn(forge.game.zone.ZoneType.Hand))
                for (forge.game.spellability.SpellAbility sa : c.getAllPossibleAbilities(me, true))
                    if (again == null && sa.getDescription() != null && sa.getDescription().contains("Channel")) again = sa;
            if (again != null) {
                log("second channel offered on " + again.getHostCard().getName() + " (should not be); trying it");
                pc.getInputProxy().selectAbility(again);
                Thread.sleep(1500);
            } else log("no second channel offered (correct)");
            log("command zone count before/after second try: " + before + "/" + me.getCardsIn(forge.game.zone.ZoneType.Command).size());
            // cast the cheapest creature with auto-pay
            forge.game.spellability.SpellAbility cast = null;
            int best = 99;
            for (forge.game.card.Card c : me.getCardsIn(forge.game.zone.ZoneType.Hand))
                for (forge.game.spellability.SpellAbility sa : c.getAllPossibleAbilities(me, true))
                    if (sa.isSpell() && c.isCreature() && c.getCMC() < best) {
                        sa.setActivatingPlayer(me);
                        if (forge.ai.ComputerUtilMana.canPayManaCost(sa, me, 0, false)) { cast = sa; best = c.getCMC(); }
                    }
            if (cast == null) { log("no castable creature"); return; }
            log("casting " + cast.getHostCard().getName() + " (mana value " + best + ")");
            try {
                log("AI sees mana sources: " + names(forge.ai.ComputerUtilMana.getAvailableManaSources(me, true))
                        + " estimate " + forge.ai.ComputerUtilMana.getAvailableManaEstimate(me));
            } catch (Exception e) { log("source check failed: " + e); }
            pc.getInputProxy().selectAbility(cast);
            Thread.sleep(1500);
            forge.gamemodes.match.input.Input in = pc.getInputQueue().getInput();
            log("input while paying: " + (in == null ? "none" : in.getClass().getSimpleName()));
            pc.selectButtonOk(); // auto-pay
            Thread.sleep(2500);
            in = pc.getInputQueue().getInput();
            log("input after auto-pay: " + (in == null ? "none" : in.getClass().getSimpleName()));
            if (in != null && in.getClass().getSimpleName().contains("PayMana")) { // pay by hand: activate each source
                for (forge.game.card.Card c : me.getCardsIn(forge.game.zone.ZoneType.Command))
                    if (!c.getManaAbilities().isEmpty()) { pc.getInputProxy().selectCard(c.getView(), null, null); Thread.sleep(400); }
                Thread.sleep(1500);
                in = pc.getInputQueue().getInput();
                log("input after paying by hand: " + (in == null ? "none" : in.getClass().getSimpleName()));
            }
            log("stack: " + g.getStack().size() + ", pool: " + me.getManaPool().totalMana());
            in = pc.getInputQueue().getInput();
            if (in != null && in.getClass().getSimpleName().contains("PayMana")) { pc.selectButtonCancel(); Thread.sleep(800); }
            log("battlefield: " + names(me.getCardsIn(forge.game.zone.ZoneType.Battlefield)));
            log("stack size " + g.getStack().size() + ", hand: " + names(me.getCardsIn(forge.game.zone.ZoneType.Hand)));
            // let a few turns pass (pass priority, no blocks) and see whether the foe casts spells with its mana
            forge.game.player.Player foe = me.getOpponents().get(0);
            for (int i = 0; i < 160 && g.getPhaseHandler().getTurn() < 5 && !g.isGameOver(); i++) {
                forge.gamemodes.match.input.Input cur = pc.getInputQueue().getInput();
                if (cur != null) pc.selectButtonOk();
                Thread.sleep(400);
            }
            log("turn " + g.getPhaseHandler().getTurn() + ", foe battlefield: " + names(foe.getCardsIn(forge.game.zone.ZoneType.Battlefield))
                    + " | foe graveyard: " + names(foe.getCardsIn(forge.game.zone.ZoneType.Graveyard)));
            log("foe mana sources: " + foe.getCardsIn(forge.game.zone.ZoneType.Command).size() + ", foe life " + foe.getLife() + ", my life " + me.getLife()
                    + ", my battlefield: " + names(me.getCardsIn(forge.game.zone.ZoneType.Battlefield)));
        } catch (Exception e) {
            log("failed: " + e);
            e.printStackTrace();
        }
    }

    private static void outfitter(int tab) {
        DelveOutfitterScene.instance().tab = tab;
        Forge.switchScene(DelveOutfitterScene.instance());
    }
}
