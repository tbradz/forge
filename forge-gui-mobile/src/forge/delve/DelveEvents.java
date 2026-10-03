package forge.delve;

import forge.item.PaperCard;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Random "?" events. Each event is a short scene with two or three choices;
 * each choice changes the run (life, gold, cards) and returns a line of text
 * describing what happened.
 *
 * Losing a card never takes the deck below {@link DelveRun#MIN_DECK}: if it
 * would, the lost card is replaced with a random card from today's pool.
 */
public final class DelveEvents {
    private DelveEvents() {}

    public static final class Choice {
        public final String label;
        public final Predicate<DelveRun> available;
        public final Function<DelveRun, String> apply;
        Choice(String label, Predicate<DelveRun> available, Function<DelveRun, String> apply) {
            this.label = label;
            this.available = available;
            this.apply = apply;
        }
    }

    public static final class Event {
        public final String title;
        public final String text;
        public final List<Choice> choices;
        Event(String title, String text, List<Choice> choices) {
            this.title = title;
            this.text = text;
            this.choices = choices;
        }
    }

    private static Choice choice(String label, Function<DelveRun, String> apply) {
        return new Choice(label, r -> true, apply);
    }

    private static Choice choice(String label, Predicate<DelveRun> available, Function<DelveRun, String> apply) {
        return new Choice(label, available, apply);
    }

    /**
     * Events built from the run's set (map generator 5+): its most common creature kinds turn up
     * as the wounded stranger and the guarded hoard, and its card cycle shows in a standing stone.
     */
    static List<Event> themed(DelveDay day) {
        List<String> kinds = day.denizens(2);
        if (kinds.isEmpty()) return new ArrayList<>();
        String kind = kinds.get(0), hoarders = kinds.size() > 1 ? kinds.get(1) : kinds.get(0);
        String lower = kind.toLowerCase();
        List<Event> e = new ArrayList<>();
        e.add(new Event("Wounded " + kind,
                an(lower) + " " + lower + " lies hurt among the rubble, too weak to fight. It watches you warily.",
                List.of(
                        choice("Tend its wounds (lose 3 life, gain " + an(kind).toLowerCase() + " " + kind + " card)",
                                r -> r.damage(3) + " " + r.gainKindCard(kind, false)),
                        choice("Search its belongings (+20 gold)", r -> r.gainGold(20) + " It hisses as you leave."),
                        choice("Walk on", r -> "You leave it be. It doesn't follow."))));
        e.add(new Event(DelveDay.plural(hoarders) + "' Hoard",
                "Behind a broken door, " + DelveDay.plural(hoarders).toLowerCase() + " sleep around a pile of plunder. One of them stirs.",
                List.of(
                        choice("Sneak in (a rare " + hoarders + " card, or they catch you: lose 6 life)",
                                r -> r.rng.nextBoolean() ? "You slip out with your prize! " + r.gainKindCard(hoarders, true)
                                        : "They wake! " + r.damage(6)),
                        choice("Back away quietly", r -> "Some treasure isn't worth the teeth."))));
        e.add(new Event("Echoes of " + day.edition.getName(),
                "A standing stone hums with visions of " + day.edition.getName() + ": its creatures, its spells, its wars.",
                List.of(
                        choice("Touch the stone (transform a random card)", r -> r.transformRandomCard()),
                        choice("Meditate beside it (heal 4)", r -> r.heal(4)))));
        return e;
    }

    private static String an(String word) {
        return "AEIOUaeiou".indexOf(word.charAt(0)) >= 0 ? "An" : "A";
    }

    public static Event random(Random rng) {
        List<Event> all = all();
        return all.get(rng.nextInt(all.size()));
    }

    static List<Event> all() {
        List<Event> e = new ArrayList<>();

        e.add(new Event("Abandoned Shrine",
                "A cracked altar hums faintly. Coins glint in its offering bowl.",
                List.of(
                        choice("Pray (heal 6)", r -> r.heal(6)),
                        choice("Take the coins (+35 gold, lose 3 life)", r -> r.gainGold(35) + " " + r.damage(3)))));

        e.add(new Event("Wounded Traveler",
                "A traveler slumps against the wall, clutching a satchel of cards.",
                List.of(
                        choice("Tend their wounds (lose 3 life, gain an uncommon)",
                                r -> r.damage(3) + " " + r.gainRandomCard(RarityTier.UNCOMMON)),
                        choice("Walk on", r -> "You leave them to their fate."))));

        e.add(new Event("Collapsing Tunnel",
                "The ceiling groans. Rocks begin to fall.",
                List.of(
                        choice("Sprint through (lose 5 life)", r -> r.damage(5)),
                        choice("Pay the tunnel dwarves (25 gold)", r -> r.gold >= 25,
                                r -> r.spendGold(25) + " They guide you around the collapse."))));

        e.add(new Event("Mysterious Chest",
                "An iron-bound chest sits in an alcove. Something scratches inside.",
                List.of(
                        choice("Open it", r -> r.rng.nextBoolean()
                                ? "Treasure! " + r.gainRandomCard(RarityTier.RARE)
                                : "A mimic! " + r.damage(5)),
                        choice("Leave it alone", r -> "Probably wise."))));

        e.add(new Event("Thief in the Shadows",
                "A hooded figure brushes past you. Your deck box feels lighter.",
                List.of(
                        choice("Give chase (lose 2 life)", r -> r.damage(2) + " You get your card back."),
                        choice("Let them go", r -> r.loseRandomCard()))));

        e.add(new Event("Fountain of Life",
                "Clear water bubbles up from the stone. It tastes of magic, and of loss.",
                List.of(
                        choice("Drink deeply (heal to full, lose a random card)",
                                r -> r.heal(r.maxLife()) + " " + r.loseRandomCard()),
                        choice("Sip (heal 3)", r -> r.heal(3)))));

        e.add(new Event("The Gambler",
                "A grinning man shuffles a deck of marked cards. \"Double or nothing?\"",
                List.of(
                        choice("Wager 20 gold", r -> r.gold >= 20,
                                r -> r.rng.nextBoolean() ? r.gainGold(40) + " Lady Luck smiles." : r.spendGold(20) + " You lose."),
                        choice("Decline", r -> "He shrugs and vanishes into the dark."))));

        e.add(new Event("Forgotten Library",
                "Dusty shelves hold spell cards that crumble at a touch, all but a few.",
                List.of(
                        choice("Search the shelves (choose 1 of 3)", r -> DelveRun.PICK_CARD),
                        choice("Leave quietly", r -> "The dust settles behind you."))));

        e.add(new Event("Cursed Idol",
                "A golden idol of a many-armed god. It is worth a fortune, and it is watching you.",
                List.of(
                        choice("Take it (+60 gold, lose 6 life)", r -> r.gainGold(60) + " " + r.damage(6)),
                        choice("Leave it", r -> "The idol's eyes follow you out."))));

        e.add(new Event("Transmuter's Circle",
                "Chalk runes glow on the floor. Place a card in the circle and it becomes something else.",
                List.of(
                        choice("Transmute a random card", r -> r.transformRandomCard()),
                        choice("Step around it", r -> "Some magic is best left alone."))));

        e.add(new Event("Hedge Wizard",
                "A wizard with ink-stained fingers offers to copy one of your cards, for a price.",
                List.of(
                        choice("Pay 30 gold (copy a card in your deck)", r -> r.gold >= 30,
                                r -> { r.spendGold(30); return DelveRun.PICK_COPY; }),
                        choice("No thanks", r -> "He goes back to his scribbling."))));

        e.add(new Event("Ancient Forge",
                "A forge still glows with dwarven fire. Cards fed to it melt into gold.",
                List.of(
                        choice("Melt a card (remove one from your deck, +15 gold)", r -> r.removableCount() > 0,
                                r -> { r.gainGold(15); return DelveRun.PICK_REMOVE; }),
                        choice("Warm your hands (heal 3)", r -> r.heal(3)))));

        e.add(new Event("Scrying Pool",
                "Still water shows your next opponent, stumbling and afraid.",
                List.of(
                        choice("Study their weakness (next foe -5 life)", r -> r.nextFoe(-5)),
                        choice("Drink (heal 4)", r -> r.heal(4)))));

        e.add(new Event("Goblin Ambush",
                "Goblins pour out of the cracks, waving rusty knives and demanding your coin.",
                List.of(
                        choice("Fight them off (lose 4 life, loot +30 gold)", r -> r.damage(4) + " " + r.gainGold(30)),
                        choice("Toss them 20 gold", r -> r.gold >= 20, r -> r.spendGold(20) + " They scatter, squabbling."),
                        choice("Run (lose 2 life)", r -> r.damage(2)))));

        e.add(new Event("Healer's Tent",
                "A cleric tends the wounded by lantern light. Her services aren't free.",
                List.of(
                        choice("Pay 25 gold (heal to full)", r -> r.gold >= 25 && r.life < r.maxLife(),
                                r -> r.spendGold(25) + " " + r.heal(r.maxLife())),
                        choice("Ask for a bandage (heal 3)", r -> r.heal(3)))));

        e.add(new Event("Blood Pact",
                "A voice from the dark offers power. \"Only a little blood. Only a little.\"",
                List.of(
                        choice("Accept (lose 7 life, gain a rare)", r -> r.damage(7) + " " + r.gainRandomCard(RarityTier.RARE)),
                        choice("Refuse", r -> "The voice laughs and fades."))));

        e.add(new Event("Overturned Cart",
                "A merchant's cart lies on its side. Cards are scattered everywhere.",
                List.of(
                        choice("Grab what's on top (gain an uncommon)", r -> r.gainRandomCard(RarityTier.UNCOMMON)),
                        choice("Dig deeper", r -> r.rng.nextInt(3) > 0
                                ? "You find something special! " + r.gainRandomCard(RarityTier.RARE)
                                : "The cart's owner returns, furious. " + r.damage(4)))));

        e.add(new Event("War Drums",
                "Drums echo ahead. Someone is paying well to see you fail.",
                List.of(
                        choice("Take their bribe (+40 gold, next foe +4 life)", r -> r.gainGold(40) + " " + r.nextFoe(4)),
                        choice("Smash the drums (next foe -2 life)", r -> r.nextFoe(-2)))));

        e.add(new Event("Echoing Hall",
                "Every sound here comes back changed. So do your cards.",
                List.of(
                        choice("Shout (transform two random cards)", r -> r.transformRandomCard() + " " + r.transformRandomCard()),
                        choice("Tiptoe through", r -> "Silence follows you out."))));

        e.add(new Event("Sleeping Dragon",
                "A dragon sleeps on a bed of gold. One wing twitches.",
                List.of(
                        choice("Sneak a handful (+80 gold, or wake it)", r -> r.rng.nextBoolean()
                                ? r.gainGold(80) + " It snores on."
                                : "It wakes! " + r.damage(8)),
                        choice("Back away slowly", r -> "Discretion is the better part of valor."))));

        e.add(new Event("Card Collector",
                "A collector in a velvet coat eyes your deck. \"I'll make it worth your while.\"",
                List.of(
                        choice("Sell him a card (remove one, +30 gold)", r -> r.removableCount() > 0,
                                r -> { r.gainGold(30); return DelveRun.PICK_REMOVE; }),
                        choice("Trade (lose a random card, gain a rare)", r -> r.loseRandomCard() + " " + r.gainRandomCard(RarityTier.RARE)),
                        choice("Not interested", r -> "He sniffs and moves on."))));

        e.add(new Event("Wandering Monk",
                "A monk sits in perfect stillness. He gestures for you to join him.",
                List.of(
                        choice("Meditate (heal 4, next foe -3 life)", r -> r.heal(4) + " " + r.nextFoe(-3)),
                        choice("Ask for his blessing (choose 1 of 3 cards)", r -> DelveRun.PICK_CARD))));

        return e;
    }

    public enum RarityTier { COMMON, UNCOMMON, RARE }

    static List<PaperCard> tierPool(DelveDay day, RarityTier t) {
        switch (t) {
            case RARE: return day.rares.isEmpty() ? day.uncommons : day.rares;
            case UNCOMMON: return day.uncommons.isEmpty() ? day.commons : day.uncommons;
            default: return day.commons;
        }
    }
}
