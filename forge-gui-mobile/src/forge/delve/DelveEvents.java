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
                                r -> r.heal(DelveRun.MAX_LIFE) + " " + r.loseRandomCard()),
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
