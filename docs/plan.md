# Pixel Art Generator: plan

> **Status (2026-09-27):** phases 0-3 are built and tested, and ship together as the first release, 0.1.0: the scaffold, flat pixel art, relief with slabs and stairs, the Place pixel art tool, extrude, inflate and revolve. The stair shapes are checked against Minecraft 26.3's own models. Phase 4 (host API 6) and phase 5 (local AI helpers) are still to do.


## Context

The user wants a BlockDesigner plugin that takes an image and builds shapes from it in blocks. It splits the image up by hue and turns each region into matching Minecraft blocks. The first version uses plain algorithms with no AI. A small local model can be added later for the jobs that algorithms do badly.

What the user chose:
- **Output modes:** all four, picked from one option: **Flat pixel art**, **Relief / heightmap**, **Extruded silhouette** and **3D guess** (inflate or revolve).
- **Voxels:** full blocks **plus slabs and stairs**, which smooth edges and slopes at half-block detail.
- **Local model, later on:** all three jobs: depth estimation, background removal and picking blocks by what they show. The algorithm version ships first, with clean hooks where the models plug in.

Per the memory rule, the plugin lives in **its own repo**: `F:/PROGRAMMING/REPOS/BlockDesigner-PixelArtGenerator` (GitHub `doolecg/BlockDesigner-PixelArtGenerator`), and never in main. The repo layout is copied from `BlockDesigner-ResourceTracker`:
- standalone Gradle Kotlin build
- `compileOnly` against the vendored `libs/blockdesigner-plugin-api-0.4.19.jar` and `libs/blockdesigner-core-0.4.19.jar`
- JavaFX `compileOnly`, toolchain 26 / release 25
- `blockdesigner-plugin.json` with `"api": 5`
- a README in the main repo's format
- `RELEASE_NOTES.md`, and a `docs/plan.md` that holds this plan

Nothing is committed until the user asks.

## What the existing API already gives us (reuse, don't rebuild)

| Need | Existing API |
|---|---|
| Block colour | `BlockCatalog.averageColor(BlockState)` (top face): `plugin-api/.../BlockCatalog.java:58` |
| Per-face colour and texture noise | `AssetAccess.quads(state, culled)` + `atlas()` (argb pixels + UVs): `AssetAccess.java` |
| Stairs and slabs of the same material | `BlockCatalog.family()`, `sameShape()`, `withId()`, `resolve("…[facing=east,half=top]")` |
| Write result, one undo step | `PluginContext.addLayer(name, Structure)`; or `ToolContext.beginStroke()` for a placing tool |
| Ghost preview | `ToolContext.preview().ghost(Map<BlockPos,BlockState>)` |
| Options UI | `Options.builder()`: `.file(...)`, `.choice`, `.integer`, `.decimal`, `.toggle`, `.blockList`, `.showWhen(...)` |
| Import menu entry | `PluginImporter.importFile(Path, OptionValues, Progress, BlockCatalog)` → `ImportedLayer` (runs on a background thread) |
| Custom panel with image previews | `PluginPanel.create(PanelContext)` → a JavaFX Node |
| Model/cache storage | `PluginContext.dataFolder()` |
| Prior art | `BlockDesigner-PaletteTools/.../PixelArtImporter.java`: ImageIO load, 48-colour palette, weighted RGB. Improve on it with OKLab and dithering; do not depend on it |

**One gap:** the API cannot list every block in the registry. v1 ships a curated candidate list: all full, opaque cube blocks in vanilla 1.21.1/26.x, grouped into sets, and filters it with `BlockCatalog.exists()`. A later host change proposes **API 6: `BlockCatalog.blockIds()`**, which lets an "All solid blocks (incl. mods)" set work. It goes to main with an app release.

## Plugin surface (UX)

1. **"Pixel Art Generator" tab page** (`PluginPanel`). This is the main workspace:
   - Image picker. Drag-and-drop also works.
   - Three small previews side by side: **Original**, **Regions** (the quantised hue map) and **Blocks** (each cell painted with its block's average colour).
   - Mode choice and the options for that mode (`showWhen`).
   - Buttons: **Build as new layer**, **Place with tool**, **Export palette used** (a block count list).
2. **"Place pixel art" tool** (`PluginTool`). It ghosts the current result at the hovered face. Scroll rotates it, a modifier+scroll changes the scale, and a click commits through `beginStroke`.
3. **Importer** (`PluginImporter` for png/jpg/jpeg/bmp/gif). File › Import on an image runs the same pipeline with the same options, for quick use.

All processing runs off the FX thread and returns with `runOnUiThread`. Each stage's result is cached, so changing an option only reruns the stages after it.

## Pipeline

**Stage-cached pipeline:** `Load → Prepare → Segment → Palette match → Shape → Sub-block refine → Structure`

1. **Load.** `ImageIO` gives a `BufferedImage` (ARGB). Keep the alpha channel.
2. **Prepare.**
   - Crop.
   - Target size in blocks: width, with the height following the aspect ratio. Caps are 512 for flat and 256 for 3D modes.
   - Downscale with an area-average (box) filter in linear light, so thin details average out instead of aliasing.
   - Brightness, contrast and saturation sliders.
3. **Segment ("see the hues").**
   - Convert every pixel to **OKLab**.
   - **k-means** clustering in OKLab (k = 2…64, seeded with k-means++, deterministic seed) gives the colour regions.
   - Connected components per cluster; merge regions smaller than *min region size* into their most similar neighbour, which removes speckle.
   - **Background mask:** transparent pixels, or a flood fill from the image border within a colour tolerance (with a "Background: auto / transparent / none / pick colour" option).
   - The result is a label map, a mask, and per-region mean colour, area and edges. Relief, extrude and 3D all use it.
4. **Palette match.**
   - **Block sets:** Concrete, Wool, Terracotta, Concrete+Terracotta+Wool, Stone & wood (building blocks), All solid (curated), and Custom, which uses `.blockList`.
   - Per block, compute the average of the **top** face and of the **side** faces from the atlas pixels of its quads, stored in OKLab. Also compute a *texture noise* value (the variance of the texture) as an optional penalty, so flat-looking blocks win for flat colours.
   - Walls use side colours and floors use top colours, chosen by the build orientation.
   - **Matching:** nearest block in OKLab (ΔE_ok), cached per quantised colour. Choices:
     - **Per region** (each hue region is one block: clean, "shapes" look)
     - **Per pixel**
     - **Per pixel + dithering**, either Floyd–Steinberg or ordered Bayer 4×4, with the error diffused in OKLab
   - A *max distinct blocks* option reruns the matching restricted to the N most-used blocks.
5. **Shape (the mode).** Every mode produces an **occupancy field at 2× resolution** (2×2×2 sub-cells per block) plus a colour per cell:
   - **Flat pixel art:** a one-block-thick plane. Orientation is Wall N/S/E/W or Floor. No sub-block work, except an optional "slab floor" for a thin floor.
   - **Relief / heightmap:**
     - The height source is luminance, a height per region (a stepped "layer-cake" relief), or a separate greyscale depth image (`.file`).
     - Settings: max height, invert, blur radius, terrace steps, and Solid / Shell.
     - The half-block height resolution is what makes slabs and stairs appear on slopes.
   - **Extruded silhouette:**
     - The background mask removes the backdrop; the rest is extruded D blocks deep.
     - Options: bevel (in half-blocks), front colours on the front face, and side cells taking the colour of the nearest edge pixel.
     - Option: per-region depth offsets, so separate hue regions stand out as stacked layers.
   - **3D guess (no AI):**
     - **Inflate:** a distance transform of the mask gives a thickness that swells like a dome, t(d) = √(d·(2R−d)) with R from the local max. It is mirrored front and back, like inflating a balloon.
     - **Revolve (lathe):** each image row's half-width in the mask becomes a ring radius around a vertical axis. This suits vases, trees, towers and bottles.
     - **Inflate + depth map:** a depth map (a file now, the AI model later) is added on top of the inflation.
     - Colour is projected from the front. Back faces mirror it, or use a darker shade (a toggle).
6. **Sub-block refine (slabs and stairs).**
   - For each block, read its 8 octants from the 2× field:
     - 8 → full block
     - 0 → air
     - the bottom or top 4 → `slab[type=bottom|top]`
     - a 6-octant pattern → straight `stairs[facing, half]`
     - 5 → `shape=outer_left/right`
     - 7 → `shape=inner_left/right`
     - Other patterns → full if at least 4 octants are set, otherwise air.
   - A lookup table maps the 256 patterns to the shape and its properties. It is generated once in code and unit tested against the vanilla stair geometry.
   - Material: take the matched block's family and use `sameShape` to get its stairs or slab. If the family has none, re-match that cell against only the blocks whose family has the shape. Toggles: "Use slabs", "Use stairs".
7. **Structure.** Write the cells into a `Structure` and hand it to `addLayer("Pixel art: <file>", s)`, the ghost map, or `ImportedLayer`.

## Code layout (single Gradle module, pure logic split from UI)

```
BlockDesigner-PixelArtGenerator/
  build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew*, libs/
  src/main/resources/blockdesigner-plugin.json   (id "pixel-art-generator", api 5)
  src/main/java/io/blockdesigner/pixelart/
    PixelArtGeneratorPlugin.java        enable(): register panel, tool, importer
    pipeline/  Pipeline.java (stage cache), Prepare.java, OkLab.java, KMeans.java,
               Regions.java (components+merge+mask), Dither.java
    palette/   BlockSets.java (curated lists), BlockColors.java (per-face OKLab + noise via AssetAccess),
               Matcher.java
    shape/     ShapeMode.java, FlatShape, ReliefShape, ExtrudeShape, InflateShape, RevolveShape,
               DistanceTransform.java, SubBlock.java (octant table → slab/stair states)
    helpers/   DepthEstimator, Segmenter, MaterialTagger  (interfaces; v1 impls = algorithmic/none)
    ui/        PixelArtPanel.java, PlacePixelArtTool.java, ImageImporter.java, Previews.java
  src/test/java/...   OkLabTest, KMeansTest, RegionsTest, MatcherTest, SubBlockTest, ShapeModesTest
  docs/plan.md, README.md, RELEASE_NOTES.md, logo (same style as the other plugin logos)
```

`pipeline`, `palette` and `shape` do not depend on JavaFX. They take a colour source interface, so tests run without assets: a fake catalog supplies fixed colours.

## Local-model hooks (later phases, designed in now)

The `helpers` package defines three interfaces that the pipeline calls when a helper is available:
- `DepthEstimator: float[][] depth(BufferedImage)`. Its output feeds Relief and "Inflate + depth".
- `Segmenter: boolean[][] mask(BufferedImage)`. It replaces the flood-fill background mask.
- `MaterialTagger: Map<regionId, String tag>` (e.g. "brick", "leaves", "water", "glass"). A tag→block-family table then biases matching toward blocks that fit the meaning, and colour still picks the shade.

Planned implementations, all running locally and offline, with nothing bundled:
- **Depth and segmentation:** ONNX Runtime for Java, with **Depth-Anything-V2-Small** for depth and a small matting/segmentation model (U²-Net-p / MODNet class) for the background. The runtime and models are downloaded on demand into `dataFolder()/models` after the user opts in. This keeps the plugin jar small and shows model licences at download time.
- **Tagging:** use **Ollama** on localhost if it is installed, with a small vision model (moondream / a small Qwen-VL class), sending each region's crop. If Ollama is absent, this helper is simply off.
- The options show "Helpers: Off / Local AI" with a status line. Every mode works without it.

## Phased delivery

- **Phase 0: scaffold.**
  - New repo from the ResourceTracker template.
  - Vendor the 0.4.19 jars, write the manifest, an empty plugin that loads, and a logo.
  - Write the plan into `docs/plan.md`.
- **Phase 1: flat pixel art MVP (plugin 0.1.0).**
  - Load, prepare, OKLab, k-means regions, the background mask.
  - The curated block sets and per-face colours.
  - Matching: per region, per pixel, and both dithers.
  - The panel with the three previews and Build as layer; the importer.
  - Tests.
- **Phase 2: relief + slabs/stairs (0.2.0).**
  - The 2× occupancy field.
  - `SubBlock` octant table with the family shape lookup.
  - The relief height sources.
  - The Place pixel art tool with ghost preview.
- **Phase 3: extrude + 3D guess (0.3.0).** Distance transform, extrude with bevel, inflate, revolve, and colour projection.
- **Phase 4: host API 6 (main repo, with an app release).**
  - `BlockCatalog.blockIds()` for the "All solid blocks (incl. mods)" set.
  - Document it in `PLUGINS.md` / `docs/plugin-api-reference.md`.
  - Bump `PluginApi.VERSION`.
- **Phase 5: local AI helpers (0.5.0).** The ONNX depth and segmentation models with on-demand download, then the Ollama tagger.

## Verification

- **Unit tests** (`./gradlew test` in the plugin repo):
  - OKLab round-trip against reference values.
  - k-means stays deterministic with a fixed seed.
  - Region merging on synthetic images.
  - The matcher picks the expected block from a fake catalog.
  - Every octant pattern maps to the right slab or stair state.
  - Inflate, revolve and extrude give the expected cell counts and symmetry on a disc or square mask.
- **In the app:**
  1. `./gradlew jar`, then install the jar through Plugins › Manage plugins… › Install…, or copy it to `%APPDATA%\BlockDesigner\plugins`.
  2. Run the app (`./gradlew :app:run` in main).
  3. Load a logo PNG, a photo and a sprite.
  4. Check each mode's previews, the ghost placement, the single undo step for Build, and File › Import on a PNG.
- **Visual regression:** a test-only renderer writes the Blocks preview PNG for a few fixture images to `build/pixel-art-shots`, to check by eye (like `MobsRenderIT`).
- **Release:** `publish_plugin.py F:/PROGRAMMING/REPOS/BlockDesigner-PixelArtGenerator --create`, only when the user asks.
