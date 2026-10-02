package forge.delve;

import forge.adventure.data.EnemyData;

import java.util.Random;

/**
 * Who an opponent is: a personal name for town folk (stable per enemy type, so the
 * same regulars come back), a personality, and lines for before and after a match.
 *
 * People get one of six personalities; monsters talk (or growl) by what they are.
 * Bosses use their Adventure intro and insult lines when the data has them.
 */
public final class DelvePersona {
    private DelvePersona() {}

    public enum Moment { GREET, THEY_LOST, THEY_WON }

    enum Kind {
        COCKY, FRIENDLY, GRUMPY, NERVOUS, SCHOLAR, VETERAN, // people
        GOBLIN, BEAST, UNDEAD, FIEND, PHYREXIAN, ELDRITCH, ELEMENTAL // monsters
    }

    private static final String[] NAMES = {
            "Mara", "Bren", "Tamsin", "Oswin", "Kell", "Ysolde", "Garrick", "Lio", "Petra", "Dunstan",
            "Wren", "Corin", "Isolde", "Hob", "Saffi", "Torvald", "Nim", "Ebba", "Rook", "Jessa",
            "Aldous", "Fenna", "Quill", "Brannoc", "Lark", "Odile", "Pell", "Rhosyn", "Talia", "Ulric",
            "Vesna", "Wick", "Yara", "Zeb", "Agnes", "Bastian", "Cress", "Dario", "Elke", "Finch",
            "Greta", "Hollis", "Ines", "Jory", "Kestrel", "Lucan", "Marisol", "Nils", "Orla", "Piet",
            "Runa", "Silas", "Thea", "Umber", "Vik", "Willa", "Xander", "Ysa", "Zora", "Ansel",
            "Birdie", "Caspian", "Della", "Emrys", "Flint", "Gwen", "Hugo", "Ivo", "Juno", "Kip",
            "Alaric", "Brynn", "Cedric", "Dagny", "Edda", "Fitz", "Gideon", "Hesper", "Idris", "Jasper",
            "Katla", "Leif", "Magnus", "Nell", "Oakley", "Perrin", "Rosalind", "Sorrel", "Tobias", "Una",
            "Vaughn", "Wynne", "Yusuf", "Zelda", "Arlo", "Bess", "Calla", "Desmond", "Esme", "Florin",
            "Gilda", "Harlan", "Ilse", "Jonas", "Kara", "Lorcan", "Maeve", "Niko", "Ottilie", "Pascal",
            "Quinn", "Reza", "Sable", "Teodor", "Ursa", "Vida", "Warrick", "Ximena", "Yves", "Zinnia",
            "Anouk", "Brannagh", "Clement", "Dorrit", "Ezra", "Freya", "Gus", "Hanne", "Ignatius", "Jolene",
            "Kasimir", "Lotte", "Merrick", "Nadia", "Osric", "Philippa", "Roderick", "Signe", "Thaddeus", "Valka",
            "Wendell", "Yelena", "Ambrose", "Beatrix", "Cyrus", "Delphine", "Emmett", "Fiora", "Gregor", "Hilde",
            "Inigo", "Jarvis", "Kenna", "Lysander", "Mirabel", "Nestor", "Oona", "Percival", "Rhea", "Stellan"};

    private static int hash(EnemyData e) {
        return e == null ? 0 : e.getName().hashCode() & 0x7fffffff;
    }

    // more names, built from syllables, so every enemy type can have its own
    private static final String[] STARTS = {"Al", "Bel", "Cor", "Dar", "El", "Fen", "Gar", "Hal", "Is", "Jor",
            "Kel", "Lor", "Mar", "Nor", "Os", "Per", "Quin", "Ros", "Sel", "Tam", "Ul", "Vel", "Wil", "Yor", "Zan"};
    private static final String[] ENDS = {"a", "en", "ic", "is", "o", "wyn", "ra", "eth", "an", "ia", "us", "el",
            "ard", "ina", "ott", "ix", "ley"};
    private static java.util.Map<String, String> assigned;

    /**
     * Every enemy type gets its own name, assigned in a fixed order so it never changes
     * between sessions: the curated list first, then syllable names.
     */
    private static synchronized java.util.Map<String, String> table() {
        if (assigned != null) return assigned;
        assigned = new java.util.HashMap<>();
        java.util.List<String> extra = new java.util.ArrayList<>();
        for (String a : STARTS) for (String b : ENDS) extra.add(a + b);
        // people first (they get the hand-picked names), then everything else
        java.util.List<String> people = new java.util.ArrayList<>(), others = new java.util.ArrayList<>();
        try {
            for (EnemyData e : forge.adventure.data.WorldData.getAllEnemies()) {
                if (e == null || e.getName() == null || people.contains(e.getName()) || others.contains(e.getName())) continue;
                boolean person = e.sprite != null && e.sprite.toLowerCase().contains("/humanoid");
                (person ? people : others).add(e.getName());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        java.util.Collections.sort(people);
        java.util.Collections.sort(others);
        java.util.List<String> enemies = new java.util.ArrayList<>(people);
        enemies.addAll(others);
        java.util.Set<String> used = new java.util.HashSet<>();
        for (String en : enemies) {
            int h = en.hashCode() & 0x7fffffff;
            String pick = null;
            // a scattered probe, so neighbouring picks don't sound alike
            for (int i = 0; i < NAMES.length && pick == null; i++) {
                String c = NAMES[(int) ((h + (long) i * 61) % NAMES.length)];
                if (!used.contains(c)) pick = c;
            }
            for (int i = 0; pick == null && i < extra.size(); i++) {
                String c = extra.get((int) ((h + (long) i * 97) % extra.size()));
                if (!used.contains(c) && !java.util.Arrays.asList(NAMES).contains(c)) pick = c;
            }
            if (pick == null) pick = NAMES[h % NAMES.length];
            used.add(pick);
            assigned.put(en, pick);
        }
        return assigned;
    }

    /** A short personal name for a town opponent (unique per enemy type, never changes). */
    public static String name(EnemyData e) {
        if (e == null) return "Someone";
        String n = table().get(e.getName());
        return n != null ? n : NAMES[hash(e) % NAMES.length];
    }

    /** "Mara the Challenger": the name plus what they are. */
    public static String title(EnemyData e) {
        if (e == null) return "Someone";
        return name(e) + " the " + e.getName();
    }

    static Kind kind(EnemyData e) {
        String s = e == null || e.sprite == null ? "" : e.sprite.toLowerCase();
        if (s.contains("/goblin/") || s.contains("/kobold/")) return Kind.GOBLIN;
        if (s.contains("/humanoid/")) return Kind.values()[hash(e) / NAMES.length % 6];
        if (s.contains("/beast/")) return Kind.BEAST;
        if (s.contains("/undead/")) return Kind.UNDEAD;
        if (s.contains("/fiend/")) return Kind.FIEND;
        if (s.contains("phyrexian")) return Kind.PHYREXIAN;
        if (s.contains("/aberration/")) return Kind.ELDRITCH;
        if (s.contains("/humanoid")) return Kind.FRIENDLY;
        return Kind.ELEMENTAL;
    }

    /** True for opponents who talk in words (people and goblins), not growls. */
    public static boolean talks(EnemyData e) {
        Kind k = kind(e);
        return k.ordinal() <= Kind.GOBLIN.ordinal() || k == Kind.FIEND || k == Kind.PHYREXIAN;
    }

    /** A line for this moment. Bosses use their own intro and insult when they have them. */
    public static String line(EnemyData e, Moment m, boolean boss, Random rng) {
        if (boss) {
            if (m == Moment.GREET && e != null && e.bossIntro != null && !e.bossIntro.isEmpty()) return clean(e.bossIntro);
            if (m == Moment.THEY_WON && e != null && e.bossInsult != null && !e.bossInsult.isEmpty()) return clean(e.bossInsult);
            String[] pool = m == Moment.GREET ? BOSS_GREET : m == Moment.THEY_WON ? BOSS_WON : BOSS_LOST;
            if (talks(e)) return pool[rng.nextInt(pool.length)];
        }
        String[][] set = LINES[kind(e).ordinal()];
        String[] pool = set[m.ordinal()];
        return clean(pool[rng.nextInt(pool.length)]);
    }

    /** The game font can't draw "/" and treats "[" as markup. */
    private static String clean(String s) {
        return s.replace("/", " or ").replace("[", "(").replace("]", ")").trim();
    }

    private static final String[] BOSS_GREET = {
            "So. The town finally sent someone worth the trouble.",
            "Every delver who reached this room said they'd be the one. Every one.",
            "You've come a long way to lose.",
            "My deck was built for this hour. Was yours?",
            "Kneel, or shuffle up. Those are your choices."};
    private static final String[] BOSS_WON = {
            "Another name for the walls of this place.",
            "Go home, delver. Come back when you're worth remembering.",
            "Did you truly think it would be that easy?",
            "The dungeon keeps what it takes."};
    private static final String[] BOSS_LOST = {
            "Impossible... this is not the end of me.",
            "Take your prize, then. The deep will remember you.",
            "Well played. Truly. I hate it.",
            "The dungeon will shift again by morning. So will I."};

    // [kind][moment][lines]  moments: GREET, THEY_LOST, THEY_WON
    private static final String[][][] LINES = {
            { // COCKY
                    {"Hope you brought sleeves you don't mind scuffing.",
                            "I've already won this in my head. Twice.",
                            "Shuffle up. This won't take long.",
                            "You can still walk away. No one would blame you.",
                            "Watch closely. You might learn something."},
                    {"Lucky draws. That's all that was.",
                            "Fine. Best of a hundred, then.",
                            "I was going easy on you. Obviously.",
                            "Enjoy it. It won't happen again.",
                            "Who taught you to play like that? Never mind, don't tell me."},
                    {"Like I said. Not long.",
                            "Don't feel bad. I'm just that good.",
                            "Come back when you've opened a few more packs.",
                            "That's what winning looks like. Take notes.",
                            "Was that your best deck? Really?"}},
            { // FRIENDLY
                    {"Oh good, a game! Good luck to you.",
                            "I've been hoping someone would sit down. Let's play!",
                            "May the best deck win. Mine, hopefully!",
                            "Friendly game? Friendly game.",
                            "Love your sleeves, by the way."},
                    {"Ha! Well played, really well played.",
                            "That last turn was brilliant. Mind if I steal it?",
                            "You earned that one. Good game!",
                            "Ah, so close! Rematch sometime?",
                            "Great game. Drinks on me next time."},
                    {"Good game! You had me worried there.",
                            "Phew. That could've gone either way.",
                            "Sorry about that last draw. Honestly.",
                            "You'll get me next time, I just know it.",
                            "That was fun! Same time tomorrow?"}},
            { // GRUMPY
                    {"Let's get this over with.",
                            "Sit down, don't talk, play your cards.",
                            "Another one. Wonderful.",
                            "I was having a perfectly quiet evening.",
                            "If you take forever on your turns, I'm leaving."},
                    {"Hmph. Don't expect a handshake.",
                            "Typical. Absolutely typical.",
                            "I'm going home.",
                            "My deck's cursed. That's the only explanation.",
                            "Fine. You win. Happy?"},
                    {"Now leave me alone.",
                            "Told you to get it over with.",
                            "Hmph. Next.",
                            "Don't sulk. I hate sulking.",
                            "That's the most fun I've had all week. Which is to say, none."}},
            { // NERVOUS
                    {"Oh! Um, hi. Is this seat taken? It's not? Okay.",
                            "I just built this deck, so, um, go easy?",
                            "Sorry in advance if I take a while.",
                            "I've never played anyone from the dungeon before...",
                            "Okay. Okay okay okay. Let's do this."},
                    {"I knew I should have mulliganed.",
                            "Sorry, sorry, I misplayed that whole thing.",
                            "That's okay. I'm learning. I think.",
                            "You're really good. Like, really good.",
                            "Can you, um, tell me what I did wrong?"},
                    {"Wait, did I win? I won!",
                            "Oh no, I'm so sorry, that was pure luck.",
                            "I can't believe that worked.",
                            "My hands are still shaking.",
                            "Good game! I mean it. Wow."}},
            { // SCHOLAR
                    {"Ah, an opportunity to test a hypothesis.",
                            "I've calculated a sixty-two percent chance of victory. Let's verify.",
                            "Do you know the history of this format? No? Let's just play.",
                            "Your colors suggest an aggressive plan. Interesting.",
                            "Shall we see whose theory holds?"},
                    {"Fascinating. My model failed to account for you.",
                            "I'll need to revise my notes.",
                            "A statistical outlier. I'll allow it.",
                            "Instructive. Truly instructive.",
                            "Hm. The sequencing on your fourth turn was optimal."},
                    {"Exactly as the numbers predicted.",
                            "Card advantage, my friend. It always wins in the end.",
                            "You played well. You simply played the wrong archetype.",
                            "I'll add this to my records.",
                            "Variance favors the prepared."}},
            { // VETERAN
                    {"I've played since before you could shuffle, kid.",
                            "Let's see what the new blood can do.",
                            "Seen a thousand decks like yours. Show me something new.",
                            "Sit. Cut. Let's go.",
                            "Respect the game and it'll respect you."},
                    {"Heh. Good. Real good.",
                            "Haven't lost like that in years. Thanks for the lesson.",
                            "You've got a future in this.",
                            "Remember that feeling. Chase it.",
                            "Well earned. Don't get cocky."},
                    {"Experience, kid. Can't buy it in a booster.",
                            "Close. Closer than most get.",
                            "You'll get there. Keep at it.",
                            "Not bad. Watch your mana next time.",
                            "That's the game. Shake on it."}},
            { // GOBLIN
                    {"Shiny cards! Gimme gimme!",
                            "Me play! Me play now! Me go first!",
                            "Hee hee. Got a fun one in this deck.",
                            "You look squishy. And rich.",
                            "Boss says no biting during the game. Okay. Probably."},
                    {"Not fair! You cheat! Probably!",
                            "Me was just warming up!",
                            "Waaah! Me cards!",
                            "Next time me bring bigger rocks.",
                            "Okay you win. Can me have a shiny anyway?"},
                    {"HA! Me win! Me smartest!",
                            "Goblin best! Goblin always best!",
                            "Me keep this, yes? Yes.",
                            "You lose to goblin! Tell everyone!",
                            "Again! Again! Me want to win again!"}},
            { // BEAST
                    {"*growls low and paws at the ground*",
                            "*sniffs your deck suspiciously*",
                            "*bares its teeth*",
                            "*circles you slowly*",
                            "*lets out a hungry howl*"},
                    {"*whimpers and slinks off*",
                            "*limps back into the dark*",
                            "*yelps and bolts*",
                            "*growls one last time, then retreats*"},
                    {"*roars in triumph*",
                            "*paces around you, satisfied*",
                            "*snorts and turns away*",
                            "*howls into the dark*"}},
            { // UNDEAD
                    {"...the living... come to play...",
                            "*bones rattle in a slow rhythm*",
                            "Join us. Join the game.",
                            "We have shuffled these cards for a thousand years.",
                            "*a cold wind passes through the room*"},
                    {"...rest... at last...",
                            "*crumbles into dust*",
                            "We will rise... again...",
                            "*the chill fades from the air*"},
                    {"Stay. Stay forever.",
                            "*a hollow laugh echoes*",
                            "Your warmth fades, delver.",
                            "One more for the crypt."}},
            { // FIEND
                    {"Your soul looks delicious. Let's wager it.",
                            "Ah, a mortal with ambition. My favorite kind.",
                            "Every card you play, I'll remember.",
                            "Shall we make this interesting?",
                            "I have waited centuries for a worthy game."},
                    {"Curse you... this is not over.",
                            "The pit will hear of this.",
                            "Enjoy your victory. Briefly.",
                            "You'll see me again, mortal. In your dreams."},
                    {"Delicious.",
                            "Mortals always overestimate themselves.",
                            "Your despair is the sweetest prize.",
                            "Come back when you've something left to lose."}},
            { // PHYREXIAN
                    {"Flesh is weak. Your deck is weak.",
                            "You will be compleated.",
                            "Resistance is an inefficiency.",
                            "All will be one. Starting with you.",
                            "Your strategy has been catalogued."},
                    {"An anomaly. It will be corrected.",
                            "This vessel has failed. Another will follow.",
                            "Recalculating.",
                            "The machine endures. We endure."},
                    {"Compleation progresses.",
                            "Your flesh was always going to fail.",
                            "Efficient.",
                            "Glory to the machine."}},
            { // ELDRITCH
                    {"*a sound like many voices whispering at once*",
                            "*the air bends around it*",
                            "*it regards you with too many eyes*",
                            "*reality warps at the edges of the table*"},
                    {"*it folds away into nothing*",
                            "*the whispering stops*",
                            "*it shudders and fades*",
                            "*the room returns to normal, mostly*"},
                    {"*a sound like laughter, from everywhere*",
                            "*your thoughts feel slightly less your own*",
                            "*it hums with satisfaction*",
                            "*something unseen pats your head*"}},
            { // ELEMENTAL
                    {"*the ground trembles as it approaches*",
                            "*it crackles with raw power*",
                            "*an ancient presence turns toward you*",
                            "*it waits, silent and patient*"},
                    {"*it crumbles back into the earth*",
                            "*its glow dims and goes out*",
                            "*it falls still*",
                            "*the power drains from the room*"},
                    {"*it rumbles, satisfied*",
                            "*it flares brightly*",
                            "*it settles, unmoved*",
                            "*the air hums with its triumph*"}},
    };
}
