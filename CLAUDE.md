# Delve: a roguelike Magic mode on a fork of MTG Forge

Tyler steers the design; Claude builds, tests and installs. Read `delve-tools/DEV_NOTES.md` for
the full history, decisions and gotchas before changing anything.

## Where things are
- Branch: `delve` (fork tbradz/forge). Upstream Forge is Java 17 + Maven + libGDX.
- Delve code: `forge-gui-mobile/src/forge/delve/` (DelveHubScene = town, DelveMapScene = dungeon,
  DelveDay = sets/decks/rankings, DelveRun = run state, DelveProfile = save data, DelveEconomy = all gold numbers).
- Delve art/layouts: `forge-gui/res/adventure/common/ui/delve/` and `ui/delve_*.json`;
  perk/relic/Pai Gow card scripts: `forge-gui/res/adventure/common/custom_cards/delve_*.txt`.
- Art generators (Python + Pillow): `delve-tools/` (make_town_diorama.py = town, make_world_art.py --dungeon
  = dungeon backdrops/fog/torch glow, render_tmx.py renders Adventure maps).
- Small upstream edits are listed in DEV_NOTES (RewardActor batch colour, GameRules no-mulligan flag, etc.).

## Build
`mvn -B -q -DskipTests -Dcheckstyle.skip -pl forge-gui-mobile-dev -am package`
-> `forge-gui-mobile-dev/target/forge-gui-mobile-dev-*-jar-with-dependencies.jar`

## Install on Tyler's PC
Game install: `C:\Users\tyler\OneDrive\Desktop\MTG Forge` (launch with `Delve.cmd`).
- Copy the built jar there as `delve.jar` (fails if Delve is running; ask Tyler to close it).
- Copy any changed files under `forge-gui/res/adventure/common/ui/` and `.../custom_cards/` into the
  same paths under the install's `res\adventure\common\`.
- In game: Delve button on the title screen -> saves. "Dev save" (temporary) starts with 50,000 gold.

## Working agreements
- After finishing additions, give Tyler a breakdown of what was added and the remaining discussed backlog.
- Commit as Claude with the Co-Authored-By trailer; keep DEV_NOTES.md updated with decisions and status.
- Tyler prefers the side-view town diorama (not the top-down Adventure-map look).

## Where we left off (2026-10-04)
- Builds run locally on Tyler's PC (see DEV_NOTES "Building locally"); playtest zips per DEV_NOTES "Playtest package".
- Lots built since 2026-10-02 but not yet seen running: Forge Fork/PLAYTEST-CHECKLIST.md lists what to check.
- Commits from 2026-10-02 on may not be pushed: Tyler pushes from his own terminal (GitHub sign-in).
- Nothing waiting on Tyler except the GitHub push and the playtest checklist.

## Backlog (Tyler's list, 2026-10-02)
Next up
- [done] Visual pass 2 (make_interiors.py). [done] Card Shop buys spare rares/mythics (value less a 5g fee).
- [done] Town dungeon sprite is a crypt gate (buildings.png 192,304), replacing the colosseum look.
- [done] Bulk: the Card Shop buys spare commons/uncommons by the box (100 for 25g; keeps 4 of each + deck needs).
- Story by tier (later, after the rest of the game is ironed out): each set is a chapter with new town
  characters, events and quests, building to a final arc at the newest set.
- (Dropped: player storefront; the Card Shop covers selling.)

More ways to play
- [done] Castle Sealed night and Draft night (featured event rotates by day with the 1v1 tournament). Pauper / Standard-style nights: not chosen for now.
- Set-themed dungeons: [done] enemies and bosses fit each set (DelveDay.themeEnemies, map gen 4); [done] set-flavoured events (DelveEvents.themed, gen 5); [todo] set-flavoured backdrops (art, needs Tyler's approval).
- Optional run modes: see "Optional modes" below.
- Commander packs.

Town and world
- [done] Tavern NPCs and rumors (DelveRegulars). More regulars/rumor kinds can be added there.
- [done] Castle rankings and titles that unlock things (DelveRenown).
- Outfitter: sleeves and playmats only (Tyler). [done] playmats; [done] foil sleeves (DelveSleeves, make_sleeves.py; registered into FSkin.getSleeves() at 1000+; Outfitter Foil sleeves tab). Portraits/dice dropped.
- [done] Pai Gow: the pack menu pages through older sets.

Polish and release
- Balance pass from playtests: gold, prices, enemy decks, AI difficulty.
- Phone layout and a proper installer.
- Remove the temporary Dev save before any release.
- Untested: Treasure Map double reward.

## Optional modes (customization)
Tyler wants player-chosen options collected here so they can become a customization screen. Per-save
options are stored in the save's profile.properties (like `era`) and chosen when the save is made
(`DelveSavesScene.chooseSets` is the pattern); per-run options would go in run.properties.
- [built] All sets: climb every set since Eighth Edition (96 tiers) instead of Modern (Zendikar Rising
  onward, 31 tiers, the default). Per save, chosen at New save (`DelveProfile.allSets()`).
- [built] Difficulty (DelveDifficulty: Easy/Normal/Hard/Brutal): foe life %, fight deck strength step, fight gold %,
  your max life, careless-AI openers. Chosen at New save, changeable at Your House (`difficulty` in profile).
- [idea] More modifiers: permadeath-style rules, no-merchant runs, relic-less runs, mixed-set dungeons.
- [built] Dungeon length (DelveModes.Length: Short 7-9 / Standard 10-13 / Long 14-17 rooms; map gen 6, saved per run).
- [built] Run format: Compact 20-card decks (default) or Classic 40 (DelveModes.compactRuns). Plus variance
  smoothing in every dungeon fight (opening-hand land smoothing, a free basic in play). Testing how it plays.
- [built] Chaos decks (DelveModes.chaosDecks): the game picks your two half-decks, sight unseen.
  Both live in Your House > Options (with Difficulty); stored as `option.*` in the profile.
- [idea] Preconstructed decks instead of the Jumpstart half-decks. (Forge only has precons for ZNR-NEO,
  M21 and STX in the Modern range, so this would need a fallback for newer sets.)
- [idea] Per-set special rules (rejected as default rules, kept as an option).
- (Dropped: choose any starting set.)
