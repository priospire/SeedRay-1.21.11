# Seed X-Ray Compatibility

## Verified Builds

The project currently produces separate verified jars for:

- Minecraft `1.21`
  - Yarn `1.21+build.9`
  - Fabric Loader `0.19.2`
  - Fabric API `0.102.0+1.21`
  - Output: `dist/seed-xray-0.1.12+mc1.21.jar`
- Minecraft `1.21.11`
  - Yarn `1.21.11+build.5`
  - Fabric Loader `0.19.2`
  - Fabric API `0.141.4+1.21.11`
  - Output: `dist/seed-xray-0.1.12+mc1.21.11.jar`
- Minecraft `1.21.10`
  - Yarn `1.21.10+build.3`
  - Fabric Loader `0.19.2`
  - Fabric API `0.138.4+1.21.10`
  - Output: `dist/seed-xray-0.1.12+mc1.21.10.jar`

Build both with:

```powershell
.\tools\build-version-jars.ps1
```

The script backs up installed `seed-xray-*.jar` files and existing `dist/` jars into `jar-backups/` before writing refreshed jars.

## 26.x Status

Fabric's 26.1 porting docs state that 26.1 is unobfuscated and that mods still using Yarn mappings must first migrate to Mojang's official mappings. The current mod source is Yarn-named and Java 21, so a 26.x jar cannot be honestly marked compatible by only changing Gradle coordinates.

Fabric Loader metadata exists for `26.1`, `26.1.1`, `26.1.2`, `26.2-pre-1`, and `26.2-snapshot-8` in the local metadata checks used during this pass. A verified stable `26.2` target was not available from those checks.

A real 26.x port needs one of these:

- a separate source-set port to Mojang/official mappings and Java 25, or
- a future published mapping/tooling path that can compile this source truthfully for the target 26.x version.

Until that port is completed and built, do not install the 1.21.x jars into 26.x.

## Vulkan Renderer

The overlay renderer avoids raw OpenGL calls and uses Minecraft/Fabric render abstractions. If a Vulkan renderer mod is detected, the mod disables vanilla terrain-alpha mixin hooks and enables the dim overlay fallback because alternate chunk renderers may bypass vanilla block render-layer hooks.

Ore highlight and texture overlays remain active under the fallback path, but actual terrain transparency may depend on the Vulkan renderer's own chunk pipeline.

The Minecraft `1.21` jar uses the older Fabric `WorldRenderEvents`/`HudRenderCallback` APIs and a legacy block-renderer mixin signature. It is compile-verified separately from the newer `1.21.10` and `1.21.11` render paths.

## Accuracy Notes

The predictor now:

- renders records by actual ore block chunk, not only by feature-origin chunk,
- predicts a two-chunk origin margin outside the visible radius so vanilla veins and nearby generated blockers crossing chunk borders are included,
- sorts render candidates by camera distance before applying `maxRenderedHighlights`,
- keeps diagnostics tied to the actual block chunk for cross-chunk veins,
- interleaves simulated blocker and target ore features by vanilla generation step/index,
- stores the predicted block state for deepslate ore texture/render parity, including deepslate emerald.

Remaining differences can still come from full carver/lava/surface parity, datapacks, custom worldgen, altered ore rates, or server plugins that modify generated terrain.
