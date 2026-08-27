<!--suppress HtmlDeprecatedTag, XmlDeprecatedElement -->
<center>
<b>Roleplayer's Atlas</b><br/>
A hand-drawn clientside atlas for roleplay servers – territories, roads, quill inscriptions, dated marks, and maps you sign and hand to other players.<br/>
<b>Requires <a href="https://modrinth.com/mod/surveyor">Surveyor Map Framework</a>.</b><br/>
</center>

---

A fork of [Antique Atlas 4](https://modrinth.com/mod/antique-atlas-4) by [Sisby folk](https://github.com/sisby-folk), ported to 1.21.8 and rebuilt around one idea: on a roleplay server a map isn't a HUD, it's a document. You draw it, you sign it, you date it, and you give it to someone who then knows the place only by your word until they go and see it for themselves.

Everything here is **client-side**. The server needs no mod – which is the point, since most roleplay servers run plugin cores.

![the whole map exported as an image](docs/images/map-export.png)

**[M] opens the atlas in two stages.** Press it once and the book is drawn into your hands, held open in front of you: you keep walking, keep looking around, keep whatever is in your main hand, and the map is simply there, tilting with you. Press it again and the book is put away and the full page opens, where you drag to pan, scroll to zoom, and draw with the tools down the right edge. Press it a third time to close.

The first stage is the one you travel with. The second is the one you work on.

![the atlas held open in your hands](docs/images/handheld.png)

## What this fork adds

**Territories.** Paint an area chunk by chunk, give it a name, and it appears written across the land in the old cartographic style. Entering it can announce itself on screen.

![territories, roads and inscriptions on one page](docs/images/map-overview.png)

**Roads and routes.** Click out a path and it's drawn as a curving dashed track with its name written along the curve. Optionally reports its length on hover.

**Quill inscriptions.** Free text written anywhere on the map, with no icon – for the things a place is called rather than the things that are there.

**Marker layers.** Your own layers with names and colours, filtered by tabs down the left edge. Deaths get their own automatically.

**Dating.** Every mark records when it was drawn, and is dated in one of two reckonings, chosen in the settings.

The default is the reckoning kept on **Reign RP**: *cycles*, *unias* and *years*. A cycle is one setting and rising of the sun, two real hours; twelve of them make a unia, an elven day; thirty unias make a year, an elven month. A mark drawn on 15 September 2026 at 22:00 reads *Cycle 12 of Unia 15, Year 226*. It is worked out from the real moment the mark was drawn, on the Moscow clock the server counts by, so it is the same date for everyone who reads it and cannot drift.

The other is the world's own **day count**, which is what the atlas used before. The day comes from the world's *game* time, not its time of day, because a server can freeze the day cycle or hand each player their own – game time keeps ticking and is the same for everyone.

Either way the real date and time can follow it in ((out-of-character brackets)). Both halves have their own switch, and every mark can turn its own date off.

**Scrolls, signatures and hearsay.** Export any slice of your atlas – terrain, chosen markers, inscriptions, territories, routes – into an `.atlas` scroll and hand the file to another player. Signed scrolls carry your name and date. On the other side those marks arrive **faint**, filed into a layer of their own named after you, captioned *"As told by …"*. They stay faint until that player physically walks to the spot, at which point the mark firms up and records the day it was verified.

![hearsay from another player](docs/images/hearsay.png)

![a scroll read before it is taken in](docs/images/scroll-preview.png)

**Guide arrows.** Track any mark and an arrow points to it from your position with the distance. Arrives, drops itself, and chimes.

**The hearth.** Your respawn point, marked automatically, with its own button beside the player button. Read from the same packet the vanilla compass follows, so it works for beds, respawn anchors, `/spawnpoint` and plugins that set a real respawn point.

**Snapshots to a PNG.** The map comes out of the game as an image, written to the `screenshots` folder like any other. Two kinds: the page as you're looking at it, or the whole explored world stitched together at full tile resolution – the second is a poster rather than a screenshot, running to tens of thousands of pixels a side on a well-travelled world. Either can be framed like a hung item frame and signed with your name and the date.

**Notes and search.** Any mark can carry a note; the magnifier above the layer tabs searches both names and notes. The tab below it reorders the list: by kind, nearest first, newest first, or alphabetically.

![a mark with a note and the day it was drawn](docs/images/marker-lore.png)

**Biome corrections.** The atlas guesses at biomes it has no drawing for, and a datapack server gives it plenty to guess at. When a guess is wrong, overrule it: draw one biome as another everywhere, or correct named cells one at a time. Corrections off someone else's scroll are filed under their name, never overrule your own, and can be torn out whole.

![choosing what a biome is drawn as](docs/images/biome-picker.png)

**Hand-drawn towns.** The atlas draws villages the game tells it about; a town players built is invisible to it. So draw it yourself, from the same box of pieces the villages use: roads, crossroads, wells, houses, farms, market stalls, lamps. Nothing about the land underneath is changed.

![a town drawn by hand](docs/images/town.png)

![the box of town pieces](docs/images/town-pieces.png)

**Undo and redo.** Twenty steps deep, across every drawing tool. Ctrl+Z and Ctrl+Y.

**Place names on screen.** Come near a named mark and its name fades in over the world, the way vanilla announces a biome. How near is set per mark or once for all of them, with an optional chime. Territories announce by standing inside them, and the smallest one containing you wins – walk into a duchy inside a kingdom and it is the duchy you are told you are in. Marks on hidden layers stay quiet, and any mark can have it switched off on its own.

![a place name announcing itself](docs/images/place-name.png)

**Quick marks.** A key that marks a place without opening anything, either under your feet or wherever you are looking, so a peak on the horizon can be marked from the valley you are standing in.

Plus 53 marker icons and sounds for drawing, tracking and sealing.

## Configuration

`config/roleplayers-atlas.toml`, or in-game via [Mod Menu](https://modrinth.com/mod/modmenu). Death markers and grave styles, zone titles and their radius, guide arrow opacity, whether arrows drop on arrival, the hearth mark, fullscreen, remembered zoom and position, and how unexplored land is drawn.

With [Cloth Config](https://modrinth.com/mod/cloth-config) installed the settings screen is one long searchable list with the categories down the side and the world left visible behind it. Without it a similar built-in list stands in, grouped under the same headers. Both mods are optional; without them the config file still works.

Scrolls, layers and tracked marks live in `config/roleplayers-atlas/`.

Additional options are in the Surveyor config at `config/surveyor.toml`.

## Resource packs

Tiles, markers, biome detection and structure detection are fully data-driven through resource packs, unchanged from upstream. See the [resource pack tutorial](https://github.com/sisby-folk/antique-atlas/blob/1.20/RESPACKS.md).

Marker icons come in pairs – `name.png` and `name_accent.png`, where the accent layer is recoloured to whatever colour the player picked for that marker.

## Troubleshooting

Roleplayer's Atlas is a clientside frontend for [Surveyor Map Framework](https://modrinth.com/mod/surveyor). It renders Surveyor's save data as tiles and gives you the tools to draw on top.

Anything about the screen, tiles, markers, drawing tools or resource packs belongs in [this repository's issues](https://codeberg.org/Glam_Ardor/roleplayers-atlas/issues). Anything about explored area, structure discovery or save data belongs in [Surveyor's](https://github.com/sisby-folk/surveyor/issues).

**Crash reports need `logs/latest.log` via [mclo.gs](https://mclo.gs/), with both mods on their latest version.**

## Credit

This is a fork, and almost all of the foundation is someone else's work.

Antique Atlas was created by [Hunternif](https://github.com/Hunternif), continued by [Kenkron](https://github.com/Kenkron), [asie](https://github.com/asiekierka) and [tyra314](https://github.com/tyra314), and rewritten as Antique Atlas 4 by [Sisby folk](https://github.com/sisby-folk). Surveyor Map Framework, which this mod cannot run without, is also theirs.

The map art is by [Hunternif](https://www.deviantart.com/hunternif) and [lumiscosity](https://lumiscosity.neocities.org/) – see [CREDITS](CREDITS) for the file-by-file breakdown, which also marks the icons drawn for this fork.

This fork is maintained by Glam_Ardor.

## Licence

LGPL-3.0-or-later for code, CC BY-NC-SA for assets – inherited from upstream and unchanged. Both are copyleft: anything adapted from this mod has to carry the same terms.
