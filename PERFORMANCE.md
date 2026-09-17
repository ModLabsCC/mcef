# Rendering and resource lifetime changes

Based on CCBlueX/mcef commit `cd472e4488090dbbe802a76410e803ea4c31f522`.
The pinned native JCEF/Chromium binaries are unchanged; these fixes are in the Java
Minecraft integration, not a rebuilt Chromium engine.

- Closed renderers reject both delayed initialization and subsequent paint callbacks,
  preventing GPU resources from being recreated after their owner has gone away.
- Forced closure and native `onBeforeClose` release rendering resources on Minecraft's
  thread. Repeated cleanup is harmless. A cancellable close waits for native confirmation.
- Popup pixel storage is explicitly allocated/freed, reused at the same size, and released
  on hide/close. Closing also releases any retained drag payload.
- Software dirty-region packing reuses one scratch allocation per renderer instead of
  allocating/freeing native memory for every paint. It is released on resize and close.
- Damage is clipped to both source and destination bounds before native memory reads.
  Popup removal restores pixels from the popup's actual position in the view buffer.
- `setRenderingEnabled(false, fps)` reduces CEF's frame limit to 1 FPS and skips host
  texture uploads/imports. Re-enabling restores the requested FPS and requests a full
  repaint so skipped dirty regions cannot leave stale pixels. Call after native creation,
  on the Minecraft thread. This does not suspend page JavaScript, network traffic or audio.
- Stale software frames from before a resize are discarded before GPU allocation/upload.
- JCEF classes are exported in Gradle's main class variant for composite builds.

## Verification

`./gradlew test` includes five damage-clipping regression cases (view edges, negative
popup origins, source bounds, empty/offscreen damage, non-mutating sparse damage)
and eight existing downloader tests. These do not measure native Chromium memory,
GPU resources, frame time, or prove the absence of all browser leaks.

For runtime validation, compare the same world, resolution, FPS limit and acceleration
setting before/after. Warm up for two minutes, then repeatedly open/close ClickGUI and
browser/login screens, resize the window and reopen dropdowns for ten minutes. Track
Minecraft and all JCEF process private memory, GPU memory, and frame-time percentiles.
Memory should settle after warm-up rather than grow with each cycle. Hidden browsers
should stop uploading textures; restored screens and dropdowns must repaint correctly.
Repeat with acceleration on and off. Also test theme/resource reload and browser restart.
