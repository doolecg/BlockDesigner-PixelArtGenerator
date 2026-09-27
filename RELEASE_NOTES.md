# Pixel Art Generator 0.1.2

Kept up to date with BlockDesigner 0.4.23: built and tested against its plugin API. Nothing changes in how it works.

**Needs BlockDesigner 0.4.17 or later** (plugin API 5).

## Changed
- Built against the BlockDesigner 0.4.23 plugin API.

---

# Pixel Art Generator 0.1.1

Kept up to date with BlockDesigner 0.4.22: built and tested against its plugin API. Nothing changes in how it works.

**Needs BlockDesigner 0.4.17 or later** (plugin API 5).

## Changed
- Built against the BlockDesigner 0.4.22 plugin API.

---

# Pixel Art Generator 0.1.0

The first release: a picture turned into blocks as pixel art, a relief, an extruded shape or a rounded 3D guess, with slabs and stairs. It works offline, with no AI.

**Needs BlockDesigner 0.4.17 or later** (plugin API 5).

## New
- **Pixel Art Generator page** in the plugin's tab: open or drop a picture (PNG, JPEG, BMP, GIF). Picture, Regions and Blocks previews update as you change the settings, and **Build as new layer** adds the result as one undo step.
- **Shapes:** flat pixel art; **relief / heightmap** from brightness, colour regions (layers) or a depth image; **extruded silhouette** with a bevel; **3D inflate**, where a disc becomes a ball; and **3D revolve**, a lathe for vases and towers.
- **Slabs and stairs** give half-block detail on slopes and curves. The stairs face the way the game's own models do, and blocks without a slab or stair borrow the closest-looking building block's. **Hollow** keeps a one-block shell.
- **Place pixel art tool:** shows the result as ghosts where you point, turns with Shift+wheel or R, and places with a click as one undo step.
- **Colour regions:** the picture is split into colour groups and connected shapes, and specks are merged away.
- **Block sets:** concrete, wool and terracotta; building blocks; all solid blocks; or your own list. Block colours come from the loaded game's textures, side faces for walls and top faces for floors.
- **Matching** by region (clean shapes), by pixel, or by pixel with smooth or patterned dithering. Colours are compared in OKLab, and you can limit the number of kinds of block.
- **Background removal** from transparency or a plain border colour, with no halo left around the subject.
- **Walls** facing any direction or a **floor**; up to 512 blocks wide (256 for extruded and 3D shapes).
- **Copy block list** copies how many of each block the build needs.
- **File › Import** also takes pictures, with the same settings.

---
