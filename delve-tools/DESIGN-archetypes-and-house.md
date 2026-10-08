# Delve design: archetypes, set gating, land-free mana, the house (draft, 2026-10-07)

Status: **proposal for Tyler to decide on.** Nothing here is built yet. Decisions get recorded at the bottom.

---

## 1. Archetypes instead of Jumpstart half-decks

### What the sets actually support
Counted across the 31 Modern tiers (cards at common to mythic whose text fits; a rough keyword count):

| Archetype | Support |
|---|---|
| Tokens / go-wide, Flyers, Graveyard, Lifegain | every set (about 10-50 cards each) |
| +1/+1 counters | every set except ONE and ECL (they use -1/-1 / other counters) |
| Landfall | only ZNR (24), FIN (13), EOE (12); under 10 everywhere else |
| Equipment | strong in AFR, NEO, ONE, FIN, KHM, HOB (12-26); thin elsewhere |
| Prowess / spells | SOS (29), FIN (17), BLB (13), FDN, MSH, TLA (10-11) |
| Artifacts | ONE, EOE, BRO, DFT (11-20) |
| Enchantments | WOE, DSK |
| Sacrifice / dies | SNC (27), DSK, TLA, MID, VOW |

### Option 1: fixed archetypes, playable in any set
A permanent list (say 8-10) that's always offered, each with a stable identity.
- Good: you learn and master each archetype; they can carry persistent perks, progression and unlocks
  ("builds" / characters); a natural home for the Landfall build that uses lands its own way.
- Problem: with **set-only** cards, most archetypes can't be built in most sets (Landfall in 28 of 31 sets,
  Equipment in about half). It only works if decks may borrow from other sets, which loses the set's flavour.

### Option 2: three archetypes per set, from what the set contains
At the Gate, the set offers the 3 archetypes it supports best (with a little randomness among its top 4-5 so
runs vary). Decks use only that set's cards.
- Good: every deck is real and coherent; each set plays differently; the set's signature themes show up
  (Landfall in Zendikar, Equipment in Neon Dynasty, Sacrifice in New Capenna).
- Problem: archetypes come and go by set, so there's less long-term identity or mastery.

### Recommendation: a hybrid (Option 2 with Option 1's identity)
- One **fixed catalogue** of archetypes with stable names, icons, a short description and (later) perks or
  progression: Tokens, Counters, Flyers, Graveyard, Lifegain, Equipment, Spells, Landfall, Artifacts,
  Enchantments, Sacrifice. These are the "builds".
- **Each set offers the 3** its cards support best (a minimum card count, e.g. 12+ fitting cards), built only
  from that set. Over a save you meet every archetype; each set flavours them.
- Optionally a 4th slot later: the **set's own mechanic** (Foretell, Party, Day/Night...) as a special archetype.

### How a deck would be built (either option)
1. Find the archetype's cards in the set; pick the two colours where they concentrate (e.g. ZNR Landfall is G/R).
2. Core: 6-8 archetype cards (payoffs plus enablers), best-ranked within a modest band.
3. Fill: creatures on a curve and 1-2 answers in those colours (the sturdier-deck rules we just added).
4. Compact format stays (20 cards); rewards during the run favour the archetype (battle spoils and merchants
   weight cards that fit it), which is where the Slay the Spire feel of a focused build comes from.

---

## 2. Cards and packs only from cleared sets

Proposal:
- **Card Shop** singles and boosters, **Pai Gow** pages, and **bulk/rare selling** use cleared sets only.
  A new save's shop is empty until the first clear: a clear goal ("clear Zendikar to open the shop").
- **Prerelease** is the one exception: it previews your *next* (uncleared) set. That's what a prerelease is,
  and it's a taste of what you're about to fight.
- **Castle**: yes, gated. Sealed and Draft nights use your newest cleared set; the 1v1 tournament and
  Commander pods already use your own decks (which can only hold cleared-set cards). Before the first clear,
  the Castle nights are closed ("the Castle receives delvers who've cleared a dungeon"): a progression hook.
- **Tavern**: yes, gated, but always open. Patrons play decks from your cleared sets (their strength already
  scales with progress); before any clear they use the first tier's set.
- Run rewards (clear rewards, battle spoils) are unaffected: clearing is how a set opens.

---

## 3. Mana without lands, and what happens to Landfall

### Unifying idea: a "Wellspring" enters each turn
Instead of putting mana in a pool (Pai Gow's method), each player gets **one special land per turn**
automatically: "At the beginning of your precombat main phase, a Wellspring enters under your control"
(Wellspring: {T}: add one mana of any colour), up to a cap (e.g. 6). Decks contain no lands.
- Mana grows 1, 2, 3... each turn, no screw or flood, every draw is a spell: the planning-per-turn feel.
- Land counts and **Landfall still work naturally** (one land a turn, like real Magic), unlike Pai Gow's
  12-at-once lands that broke land-counting cards.
- Relics and perks can bend it: start with two, a higher cap, an extra Wellspring on turn 3, and so on.

### The Landfall build (a "refined lands" character)
Landfall wants *more* lands entering, so its build keeps real lands, but in a separate **land pile**
outside the deck (exiled at the start, invisible to draws):
- Each turn you may also put the top land of your land pile onto the battlefield (a second land a turn),
  so Landfall triggers twice a turn without lands ever clogging draws.
- Its rewards are landfall payoffs, extra-land effects, and better lands for the pile (dual lands, lands with
  abilities). That's the "uses lands, but in a refined way" build.
- Only Zendikar, Final Fantasy and Edge of Eternities support Landfall decks set-only, which fits Option 2:
  in those sets, Landfall is one of the three offers.

### Rollout
Ship it first as a **run format** in Options (like Compact) so it can be compared with lands-in-deck play.

---

## 4. The house: something to spend gold on

Today the house is a menu with unlimited deck slots. Proposal: a real **house interior** you can walk around
(like the Tavern room), which starts small and grows.

| Size | Deck slots | Room slots | Price (placeholder) |
|---|---|---|---|
| Cottage (start) | 3 | 0 | - |
| House | 6 | 1 | 300g |
| Manor | 10 | 3 | 900g |
| Estate | 16 | 5 | 2,000g |

(Existing saves keep all their decks; they just can't add past the limit until they upgrade.)

Rooms (each takes a room slot; mostly convenience or cosmetic, to avoid power creep):
- **Trophy hall**: banners for every cleared set, your Castle titles, best runs. Cosmetic, a progress display.
- **Library**: the archetype codex: each archetype, which sets offer it, your record with it; a scout's look
  at the next set's archetypes.
- **Workshop**: small run-start perk (e.g. once per run, swap one starting card for another of the archetype).
- **Vault**: +3 deck slots.
- **Garden / study / armoury**: cosmetic.

Decorating: furniture, rugs, wall hangings and set banners bought with gold (a new **Furnisher** in town, or a
tab at the Outfitter), placed in fixed spots in each room. All art goes through Tyler's approval
(make_interiors.py can produce the rooms in the existing style).

Interior as a screen: click the desk to build decks, the chest for the collection, the bed to sleep, the
trophy wall for titles, the door for Options, like the Tavern's clickable patrons.

---

## Suggested order
1. Archetypes (the core of the dungeon; decide Option 1 / 2 / hybrid first).
2. Set gating (small, and it sharpens progression right away).
3. Wellspring mana as a run format, then the Landfall build.
4. The house (needs art mockups and your approval).

## Decisions (fill in as Tyler decides)
- Archetype option (2026-10-07): per set, the archetypes its cards support; 3 offered at random when it supports more.
  Set-only decks. BUILT: DelveArchetype (11), DelveDay.viableArchetypes/archetypeOffers/archetypeDeck.
- Gating (2026-10-07): approved as written in section 2. BUILT: DelveDay.town()/townOrFirst(), DelveProfile.clearedTiers().
- Mana system (2026-10-08): Tyler's version: 3 mana per turn + Channel one card a turn into a permanent mana source of its
  colours; sources are not lands; Landfall gets its own land pile; max mana raised by relics/boons/cards. BUILT (base +
  Channel + foe mana). Next: mana relics/boons, temporary mana, the Landfall land pile.
  Tyler's "Delve_Design_Recommendations.docx" (2026-10-08) is the guiding doc for the dungeon from here on.
- House sizes / rooms / prices:
