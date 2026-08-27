# Roleplayer's Atlas

**A hand-drawn atlas for roleplay servers.** Territories, roads, quill inscriptions, dated marks, and maps you sign and hand to another player.

A fork of [Antique Atlas 4](https://modrinth.com/mod/antique-atlas-4) by [Sisby folk](https://modrinth.com/user/sisby), rebuilt around one idea: on a roleplay server a map isn't a HUD, it's a document. You draw it, you sign it, you date it, and you give it to someone who then knows the place only by your word – until they go and see it for themselves.

**Client-side only.** The server needs no mod, which is the point: most roleplay servers run plugin cores you can't add mods to. [Surveyor Map Framework](https://modrinth.com/mod/surveyor) rides inside the jar, so there is nothing else to install.

> ![territories, roads and inscriptions on one page](https://cdn.modrinth.com/data/P0zBDYta/images/904d9da9769a2a14ac879571427b0d5fae137757.png)

---

## The book opens in two stages

Press **[M]** once and the book is drawn into your hands, held open in front of you. You keep walking, keep looking around, keep whatever is in your main hand, and the map is simply there, tilting as you turn. This is the stage you travel with.

Press it again and the book is put away and the full page opens, where you drag to pan, scroll to zoom, and draw with the tools down the right edge. This is the stage you work on.

A third press closes it.

> ![the atlas held open in your hands](https://cdn.modrinth.com/data/P0zBDYta/images/8cb9e8c17768f32843fc8d80637ac864cb38ebac.png)

---

## Drawing on the world

**Territories.** Paint an area cell by cell, name it, and the name appears written across the land in the old cartographic style. Walking in can announce it on screen. Redraw the bounds later without starting again.

**Roads.** Click out a path and it becomes a curving dashed track with its name written along the curve, and its length on hover. Pick an old road back up at its far end and carry it on.

**Quill inscriptions.** Free text anywhere on the map, no icon – for what a place is called rather than what stands there.

**Hand-drawn towns.** The atlas draws villages the game tells it about. A town players built is invisible to it, so draw it yourself, from the same box of pieces the villages use: roads, crossroads, wells, houses, farms, market stalls, lamps. Nothing about the land underneath is changed, and any of it lifts again.

> ![a town drawn by hand](https://cdn.modrinth.com/data/P0zBDYta/images/5213acdb856f40e845504dae47d3ed51cd61c8ea.png)

> ![the box of town pieces](https://cdn.modrinth.com/data/P0zBDYta/images/55c37b4eeef755aaafe896ebf66139e677603c60.png)

**Biome corrections.** The atlas guesses at biomes it has no drawing for, and a datapack server gives it plenty to guess at. When a guess is wrong, overrule it – draw one biome as another everywhere, or correct named cells one at a time.

> ![choosing what a biome is drawn as](https://cdn.modrinth.com/data/P0zBDYta/images/e4c44043b061b5bb7a970adf696511058fdcaa97.png)

**Undo and redo,** twenty steps deep, across every drawing tool.

---

## Marks that are worth something

**Dating.** Every mark records when it was drawn, and is dated in one of two reckonings, chosen in the settings.

The default is the reckoning kept on **Reign RP**: *cycles*, *unias* and *years*. A cycle is one setting and rising of the sun, two real hours; twelve make a unia, an elven day; thirty unias make a year, an elven month. A mark drawn on 15 September 2026 at 22:00 reads *Cycle 12 of Unia 15, Year 226*. It is worked out from the real moment the mark was drawn, on the Moscow clock the server counts by, so it reads the same for everyone and cannot drift.

The other is the world's own **day count**, which is what the atlas used before. The day comes from the world's *game* time rather than its time of day, because a server can freeze the day cycle or hand each player their own. Game time keeps ticking and reads the same for everyone.

Either way the real date and time can follow it in ((out-of-character brackets)). Both halves have their own switch, and every mark can turn its own date off.

**Layers.** Your own layers with names and colours, filtered by tabs down the left edge. Deaths get one automatically.

**Notes and search.** Any mark can carry a note, and the magnifier searches names and notes alike. The tab below reorders the list: by kind, nearest first, newest first, or alphabetically.

> ![a mark with a note and the day it was drawn](https://cdn.modrinth.com/data/P0zBDYta/images/91f33bfc55afaa1f91378f707a3ff12f3dd35096.png)

**Place names on screen.** Come near a named mark and its name fades in over the world, the way vanilla announces a biome. How near is set per mark or once for all of them, with an optional chime. Territories announce by standing inside them, and the smallest one containing you wins – walk into a duchy inside a kingdom and it is the duchy you are told you are in. Marks on hidden layers stay quiet, and any mark can have it switched off on its own.

> ![a place name announcing itself](https://cdn.modrinth.com/data/P0zBDYta/images/a16492c55d482bc4c44d627f1edee18be9a176ca.png)

**Quick marks.** One key marks a place without opening anything – under your feet, or wherever you are looking, so a peak on the horizon can be marked from the valley you stand in.

**Guide arrows.** Track a mark and an arrow points at it with the distance. It drops itself when you arrive.

**The hearth.** Your respawn point, marked automatically, read from the same packet the vanilla compass follows. Works with beds, respawn anchors, `/spawnpoint`, and plugins that set a real respawn point.

---

## Maps you hand over

Export a slice of your atlas – terrain, chosen marks, inscriptions, territories, roads, your biome corrections and your towns – into an `.atlas` scroll, and give the file to another player.

Signed scrolls carry your name and the date. On the other side those marks arrive **faint**, filed into a layer named after you, captioned *"As told by …"*. They stay faint until that player has physically walked to the spot, at which point the mark firms up and records the day it was seen.

> ![hearsay from another player](https://cdn.modrinth.com/data/P0zBDYta/images/0416495ff08cdc1c1130ebacba6306c6e94d3eb8.png)

Your corrections and towns land as a sheet under your name. That sheet never overrules anything the reader drew themselves, and it can be torn out whole. Before any of it is written, the reader sees exactly what the scroll holds and can decline.

> ![a scroll read before it is taken in](https://cdn.modrinth.com/data/P0zBDYta/images/049f65d8681db5f6b5d1b4d2b7059bc05598bbc8.png)

**Snapshots to a PNG.** The map also comes out of the game as an image, written to your `screenshots` folder like any other. Two kinds: the page as you are looking at it, or the whole explored world stitched together at full tile resolution – and that second one is a poster rather than a screenshot, running to tens of thousands of pixels on a side for a well-travelled world. Either can be framed like a hung item frame and signed with your name and the date. Post it, print it, hang it in a Discord.

> ![the whole map saved out as an image](https://cdn.modrinth.com/data/P0zBDYta/images/676f2b75d001793ac3d4355ce7440fc947bd1430.png)

---

---

## Settings

`config/roleplayers-atlas.toml`, or in-game through [Mod Menu](https://modrinth.com/mod/modmenu). With [Cloth Config](https://modrinth.com/mod/cloth-config) the screen is one long searchable list with the categories down the side and the world left visible behind it; without it, a similar built-in list stands in. Either way the file still works.

Elevation tiers, unexplored land, death markers and their wording, place names on screen, guide arrows, the hearth, the quick-mark key and its icon, fullscreen, remembered zoom and position.

Scrolls, layers, corrections and tracked marks live in `config/roleplayers-atlas/`.

---

## Credit

This is a fork, and almost all of the foundation is someone else's work.

Antique Atlas was created by [Hunternif](https://github.com/Hunternif), continued by [Kenkron](https://github.com/Kenkron), [asie](https://github.com/asiekierka) and [tyra314](https://github.com/tyra314), and rewritten as Antique Atlas 4 by [Sisby folk](https://modrinth.com/user/sisby). [Surveyor Map Framework](https://modrinth.com/mod/surveyor), which this cannot run without, is theirs as well.

Surveyor has no 1.21.11 build of its own – its 1.21 line ends at 1.21.8, after which it moved to 26.1. The 1.21.11 file here therefore carries **a modified copy of Surveyor**, ported by hand and versioned `1.2.1+1.21.11-ra.1` so it cannot be mistaken for an official build. It stays under LGPL-3.0-or-later, and its source is [on Codeberg](https://codeberg.org/Glam_Ardor/surveyor-1.21.11). Anything wrong with that build is this mod's fault, not Sisby folk's – report it here.

The map art is by Hunternif and [lumiscosity](https://lumiscosity.neocities.org/) – the [CREDITS](https://codeberg.org/Glam_Ardor/roleplayers-atlas/src/branch/main/CREDITS) file names every texture and who drew it.

LGPL-3.0-or-later for code, CC BY-NC-SA for assets, inherited from upstream and unchanged. Both are copyleft: anything adapted from this has to carry the same terms.

[Source and issues on Codeberg.](https://codeberg.org/Glam_Ardor/roleplayers-atlas)
