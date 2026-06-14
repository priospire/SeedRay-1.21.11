## Target

- Minecraft: verified jars for `1.21.11` and `1.21.10`
- Fabric Loader: `0.19.2`
- Fabric API: `0.141.4+1.21.11` for development and runtime minimum on the `1.21.11` jar
- Yarn mappings: `1.21.11+build.5`
- Java: `21`

Versioned build outputs use matching Fabric API/Yarn coordinates per Minecraft version. See `COMPATIBILITY.md` for the verified matrix and 26.x port status.

The active seed is configured in the in-game `F8` quick panel or `.minecraft/config/seed-xray.json`. Once saved, it persists across sessions until you explicitly replace it.

## Prediction Accuracy

The vanilla `1.21.11` adapter emits seed predictions for the requested Overworld and Nether ore families:

- Diamond, emerald, gold, iron, coal, copper, redstone, and lapis in the Overworld.
- Ancient debris, Nether gold, and Nether quartz in the Nether.

The adapter implements the vanilla placed/configured feature core:

- Uses vanilla chunk population seed and decorator seed math.
- Resolves biome placed-feature generation step and feature index from Minecraft's built-in vanilla registry data.
- Simulates vanilla `ore` and `scattered_ore` feature placement.
- Simulates vanilla count, rarity, in-square, uniform height, and trapezoid height placement.
- Interleaves simulated underground blocker features and target ore features by vanilla generation step/index, so gravel, dirt, tuff, blackstone, magma, soul sand, infested stone, and similar same-step features can block ores in the same order vanilla would apply them.
- Simulates all vanilla ore features in the dimension as generated blockers even when an ore filter disables rendering for that family, so disabled ores can still prevent later ore features from being falsely predicted in the same block.
- Applies vanilla-style biome placement filtering from a seed-derived `NoiseChunkGenerator` biome source.
- Uses Minecraft's `NoiseChunkGenerator` column sampler with the configured seed for local replacement checks; sampled air, fluid, bedrock, and other non-replaceable states are rejected when the vanilla sampler exposes them.
- Stores the predicted block state on every record, so deepslate variants such as deepslate emerald render with their deepslate ore texture when generated in deepslate-replaceable terrain.
- Uses the client's built-in vanilla `1.21.11` registry data for generator settings and noise parameters, so normal prediction does not depend on multiplayer server-sent ore/block states.
- A build-time self-test verifies that vanilla seed placement math produces nonzero Overworld, Nether, and ancient debris placement origins.
- Does not let loaded multiplayer client block states veto predictions, because PaperMC anti-xray may mask or spoof those states.

This means the overlay is based on vanilla seed feature placement plus local vanilla terrain sampling, not a scan of server-sent ore blocks. Full carver, cave-liquid, structure, and neighboring-origin mutation parity are still the main remaining accuracy limitations; server-sent block states are intentionally diagnostic only.

Prediction depends on the server using vanilla-compatible world generation for Minecraft `1.21.11`. Datapacks, custom ore rates, custom terrain generation, or plugins that modify generation can make predictions wrong.

The `1.21.10` jar uses a compatibility render adapter, but the worldgen adapter is still tuned against the `1.21.11` vanilla feature set. Use local-world spot checks before treating older-version predictions as a source of truth.

## PaperMC Anti-Xray Diagnostics

PaperMC anti-xray can replace hidden ores with normal-looking blocks or send fake ore states. This mod treats server-sent blocks as diagnostic evidence only.

Statuses:

- `PREDICTED_ONLY`: seed prediction exists, no client comparison yet.
- `PREDICTED_AND_CLIENT_MATCHES`: predicted ore matches the client-visible ore family.
- `PREDICTED_CLIENT_OBSTRUCTED`: seed prediction exists, but the loaded client chunk currently exposes a trusted non-ore obstruction at that block, such as air, fluid, or bedrock. These are hidden by default to reduce cave/liquid false positives.
- `PREDICTED_BUT_CLIENT_MASKED`: seed predicts ore, but the client currently sees another block.
- `CLIENT_ORE_NOT_PREDICTED`: loaded client chunk contains an ore not predicted by the current seed adapter.
- `UNKNOWN_UNLOADED`: client chunk is not loaded.

`PREDICTED_BUT_CLIENT_MASKED` is expected when anti-xray masks hidden ore. The predicted highlight remains rendered.

## Display Modes

- `Prediction`: renders only seed/worldgen predictions. This is the cleanest view when you want to ignore all client-visible ore states from PaperMC anti-xray.
- `Diagnostic`: renders diagnostic anomalies from loaded client chunks, such as client-visible ores that are not predicted by the seed adapter. This is for inspecting anti-xray behavior, not for deciding ground truth.
- `Combined`: renders seed predictions and diagnostics together. This is the default testing mode because predictions remain authoritative while masked, matching, and unpredicted client-visible states can still be counted and reviewed.

## Keybinds

- `F7`: Toggle X-Ray
- `B`: Cycle mode: Prediction / Diagnostic / Combined
- `N`: Cycle ore filter group
- `L`: Toggle labels
- `H`: Toggle HUD
- `]`: Increase prediction radius
- `[`: Decrease prediction radius
- `K`: Clear prediction and diagnostic cache
- `J`: Toggle terrain transparency mode. Default is real terrain transparency; fallback dimming is only used when this is switched off.
- `F8`: Open the Seed X-Ray quick panel

## Config

Config path: `.minecraft/config/seed-xray.json`

Main fields:

- `enabled`
- `displayMode`
- `worldSeed`
- `transparencyPercent` (default `96`)
- `useTerrainTransparency`
- `useFallbackDimmingOverlay`
- `predictionRadiusChunks`
- `diagnosticScanRadiusChunks`
- `maxPredictedChunksPerTick`
- `maxDiagnosticChunksScannedPerTick`
- `maxRenderedHighlights`
- `distanceLimitBlocks`
- `showHud`
- `showLabels`
- `showDiagnosticStatus`
- `showFilledBoxes`
- `showOutlines`
- `showHighlights`
- `showOreTextures`
- `showClientVisibleUnpredictedOres`
- `showMaskedPredictions`
- `showClientObstructedPredictions`
- `oreFilters`
- `oreHighlightEnabled`
- `oreTextureEnabled`
- `oreColors`
- `oreAlphas`
- `debugLogging`
- `clampExtremeRadius`
- `clearCacheOnDimensionChange`

Defaults prioritize performance:

- Prediction radius: `8` chunks
- Diagnostic radius: `4` chunks
- Max predicted chunks per tick: `1`
- Max diagnostic chunks scanned per tick: `1`
- Max rendered highlights: `1000`
- Distance limit: `192` blocks
- Full loaded-chunk anomaly scans are disabled by default; diagnostics still compare predicted positions against client-visible block states.

Runtime performance safeguards:

- Prediction caches are pruned around the active player radius instead of growing indefinitely while exploring.
- Local terrain column/top-Y caches are bounded per dimension.
- The renderer keeps the nearest capped records while scanning instead of sorting every in-range candidate every frame.
- Lightweight predicted-position obstruction scans run in Prediction, Combined, and Diagnostic modes so air/fluid/bedrock false positives can be hidden after their chunks load.
- Full client-visible anomaly scans remain limited to diagnostic display modes and skip chunks with no predicted records unless that anomaly scan is enabled.

The `F8` quick panel provides per-ore enabled/highlight/texture/color/alpha controls and writes the same config file. Status messages in the panel fade out after saving.

`worldSeed` is a normal persistent config value. The mod does not reset it during startup or migration; replacing it in the quick panel is what changes future predictions.

## Text And Key Display Safety

The mod-owned UI uses literal client-side text and a local GLFW key-name resolver. The quick panel does not use Minecraft translation components for its own labels or key names, so server-controlled text such as signs or anvils cannot affect the mod UI text path. Fabric keybinding IDs remain registered normally so Minecraft's standard Controls screen stays compatible.

## Build

On Windows:

```powershell
.\gradlew.bat build
```

Run a dev client:

```powershell
.\gradlew.bat runClient
```

The included wrapper script downloads Gradle `9.5.0` if no system Gradle is installed.

Build all verified versioned jars:

```powershell
.\tools\build-version-jars.ps1
```

The script backs up any installed `seed-xray-*.jar` files into `jar-backups/`, then writes version-specific jars to `dist/`:

- `dist/seed-xray-0.1.12+mc1.21.11.jar`
- `dist/seed-xray-0.1.12+mc1.21.10.jar`

As of the latest Fabric porting check for this project, `26.1.x` requires migrating away from Yarn to Mojang's official/unobfuscated mappings and Java 25.

## Installation

Build the jar, then place the remapped jar from `build/libs/` into the client `.minecraft/mods` directory with Fabric Loader and Fabric API installed.

Use the jar matching the Minecraft version. Keep older jars in `jar-backups/` before replacing anything in `.minecraft/mods`.

## Renderer Compatibility

The no-depth ore highlight and texture overlays use Minecraft/Fabric render abstractions and avoid direct packet or networking behavior. If a Vulkan renderer mod is detected, terrain alpha mixin hooks are disabled and the dim overlay fallback is used, because alternate chunk renderers may not honor vanilla block-layer transparency hooks consistently.

## Setting The Seed

Use the `F8` quick panel for normal testing. Enter the seed, press `Save Seed`, and the prediction cache will be cleared so nearby chunks regenerate against the saved value. The saved seed is stored in `.minecraft/config/seed-xray.json` and remains there until you replace it.

## QA Checks

The Gradle `check` task runs a small static QA pass that rejects:

- Mod UI use of Minecraft translation components for mod-owned text or key names.
- Custom Fabric networking/custom-payload references in mod source.
- Reintroduction of a source-level fixed seed default.
