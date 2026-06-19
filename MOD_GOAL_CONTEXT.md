# Seed X-Ray Goal Context

Updated: 2026-06-19

## Active Objective

- Increase prediction accuracy by reducing false positives from generated blockers, other ores, gravel, liquids, bedrock, and other non-replaceable states that prevent vanilla ores from generating.
- Improve runtime performance so prediction, diagnostics, and rendering do not spike badly during normal movement.
- Maintain this file as the current source of truth for ongoing work.

## Current Verified Builds

- `dist/seed-xray-0.1.12+mc1.21.jar` builds and has `minecraft: 1.21`.
- `dist/seed-xray-0.1.12+mc1.21.11.jar` builds and has `minecraft: 1.21.11`.
- `dist/seed-xray-0.1.12+mc1.21.10.jar` builds and has `minecraft: 1.21.10`.
- Latest compile check in this iteration: `.\gradlew.bat compileJava` passed.
- Latest seed prediction self-test in this iteration: `.\gradlew.bat verifySeedPredictionSelfTest` passed.
- Latest clean build in this iteration: `.\gradlew.bat clean build` passed for `1.21.11`, including Gradle `check`, static QA, and seed prediction self-test.
- Latest compatibility build in this iteration: `.\gradlew.bat clean build "-Pminecraft_version=1.21.10" "-Pyarn_mappings=1.21.10+build.3" "-Pfabric_version=0.138.4+1.21.10"` passed after the compat renderer changes.
- Final `.\gradlew.bat check` passed after running outside the workspace sandbox so Gradle could write its native lock files under the user Gradle cache.
- `dist/seed-xray-0.1.12+mc1.21.10.jar` and `dist/seed-xray-0.1.12+mc1.21.11.jar` were refreshed from passing build outputs and their `fabric.mod.json` Minecraft/Fabric API metadata was verified on 2026-06-14.
- A direct headless self-test for `Blocks.AIR/LAVA/BEDROCK` obstruction classification was attempted and removed because accessing `Blocks` in that JavaExec test path triggers Minecraft's unbootstrapped registry initializer.

## Compatibility Work Done On 2026-06-19

- Added a Minecraft `1.21` release build target using Yarn `1.21+build.9` and Fabric API `0.102.0+1.21`.
- Added `src/compat/1.21/java` source overrides for the older Fabric world/HUD render APIs, older block render-layer APIs, older `BlockModelRenderer` render signature, and older screen key-input signature.
- Refactored the quick panel into shared `XrayConfigScreenBase` plus small version-specific `XrayConfigScreen` wrappers.
- Made keybinding creation version-neutral with reflection so the same manager supports both old string categories and newer `KeyBinding.Category` categories.
- Made seed terrain/diagnostic height checks version-neutral with `bottomY + height - 1`.
- Made built-in registry lookup version-neutral by reflecting `WrapperLookup.getOrThrow(...)` or `WrapperLookup.getWrapperOrThrow(...)`.
- Updated `tools/build-version-jars.ps1` to build `1.21`, `1.21.10`, and `1.21.11`, and to back up existing `dist` jars before overwriting them.
- Passed `.\gradlew.bat clean build "-Pminecraft_version=1.21" "-Pyarn_mappings=1.21+build.9" "-Pfabric_version=0.102.0+1.21"`; static QA and seed prediction self-test passed.
- Passed `.\gradlew.bat clean build "-Pminecraft_version=1.21.10" "-Pyarn_mappings=1.21.10+build.3" "-Pfabric_version=0.138.4+1.21.10"`; static QA and seed prediction self-test passed.
- Passed default `.\gradlew.bat clean build` for `1.21.11`; static QA and seed prediction self-test passed.
- Refreshed `dist/seed-xray-0.1.12+mc1.21.jar`, `dist/seed-xray-0.1.12+mc1.21.10.jar`, and `dist/seed-xray-0.1.12+mc1.21.11.jar` from passing build outputs, with previous dist jars backed up under `jar-backups/`.
- Verified each refreshed dist jar's `fabric.mod.json` metadata.
- Ran `.\tools\build-version-jars.ps1` after updating it; it built all three verified jars, backed up installed/dist jars, and completed successfully.

## Repository Publish

- Synced the latest `1.21.11` source only into `C:\Users\user\Desktop\GammaRay-1.21.11`; no `src/compat` older-version source was copied.
- Refreshed `C:\Users\user\Desktop\GammaRay-1.21.11\dist\seed-xray-0.1.12+mc1.21.11.jar` from the GammaRay checkout's own passing build output.
- Verified the GammaRay checkout with `.\gradlew.bat clean build`; Gradle `check`, static QA, and the seed prediction self-test passed.
- Verified the GammaRay dist jar metadata: mod version `0.1.12`, Minecraft `1.21.11`, Fabric API `>=0.141.4`.
- Searched the GammaRay checkout for updater/update-checker/release-check references and found none.
- Pushed commit `2a2d86c` to `origin/main`; GitHub accepted the push and reported the repository has moved from `priospire/GammaRay-1.21.11` to `priospire/SeedRay-1.21.11`.

## Accuracy Work Done

- Prediction cache now indexes records by actual ore block chunk, not only by feature-origin chunk.
- Scheduler predicts a two-chunk origin margin so veins and generated blockers crossing chunk borders can render inside the user-facing radius.
- Diagnostics scan predicted records intersecting the loaded block chunk.
- Predicted block state is stored on each prediction record so deepslate variants, especially deepslate emerald, can be rendered and diagnosed as their actual predicted block.
- Vanilla underground obstruction simulation is interleaved with target ores by generation step/index, instead of running all blockers first. This avoids suppressing valid target ores with later vanilla features.
- All dimension-appropriate vanilla ore features are now simulated as generated blockers even when their ore filter is disabled, so hidden/disabled ore families can still prevent false positives for later target ores.
- Local terrain replacement checks reject sampled air, fluid, bedrock, and other non-replaceable states when the vanilla column sampler exposes them. Full cave carver and cave-liquid parity is still incomplete.
- Loaded client chunks now classify air, fluid, and bedrock at predicted positions as `PREDICTED_CLIENT_OBSTRUCTED`. These predictions are hidden by default through `showClientObstructedPredictions=false`, while normal maskable terrain states such as stone/deepslate/netherrack still remain `PREDICTED_BUT_CLIENT_MASKED`.

## Performance Work Done

- Default render cap lowered from `1500` to `1000`; config migration version `12` clamps older higher defaults down once.
- Prediction cache prunes chunks outside the active radius plus origin margin instead of growing indefinitely.
- Local seed terrain sampler column/top-Y caches are bounded to `16,384` entries per dimension.
- Highlight rendering now uses a bounded nearest-record heap before drawing, avoiding a full sort/allocation pass over every in-range candidate each frame.
- Diagnostic scheduler skips chunks with no predicted records unless full unpredicted client-visible ore scanning is enabled, and prunes old diagnostic scan state around the player.
- Lightweight predicted-position obstruction scans now run in Prediction mode as well as Combined/Diagnostic, so the hidden-obstructed behavior does not require full diagnostic rendering.

## Remaining Accuracy Limits

- Full carver/cave-liquid/structure parity is not yet exact.
- Cross-origin generated-state interactions are approximated by prediction-origin margin and actual block-chunk indexing, but not all neighboring origin mutations are simulated in one shared chunk-region pass.
- Client-visible blocks remain diagnostic only; stone/deepslate/netherrack still do not veto predictions because PaperMC anti-xray can mask ores as those blocks.

## Next Verification

- Runtime QA in a local/dev client is still needed to measure FPS impact and visually sample false positives in cave/liquid-heavy chunks.
- Future accuracy work should target full carver/cave-liquid/neighbor-origin chunk-region parity.

## Safety/Scope

- No packet manipulation, custom payloads, authentication bypass, staff evasion, or server-side hiding behavior.
- Client-visible blocks remain diagnostic only; seed/worldgen prediction remains authoritative.
