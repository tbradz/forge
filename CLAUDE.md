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

## Where we left off (2026-10-02)
- Side-view town (make_town_diorama.py) and Bram's walking tour (DelveTourScene) are built, installed and
  playtested by Tyler: all good. Builds now run locally on Tyler's PC (see DEV_NOTES "Building locally").
- Backlog: visual pass 2 (shop/tavern/castle/pack-opening backdrops, dungeon room bases), player storefront,
  story by tier, more event formats (Pauper, Standard-style, sealed/draft), set-themed dungeons, optional run
  modes, Tavern NPCs/rumors, Castle rankings/titles, Outfitter items, Commander packs, balance pass,
  phone layout/installer, remove the Dev save before release.
