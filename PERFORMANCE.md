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

## Linux and Wayland

The Linux launch default now uses `--use-angle=gl-egl`, required for shared-texture
OSR by [CEF #3953](https://github.com/chromiumembedded/cef/issues/3953). Explicit
caller switches are preserved. The CEF child still defaults to X11/XWayland because
of its GTK integration; this does not change the host application's Wayland window.

Imports use the EGL display that owns the current Minecraft context, without opening
or initializing an unrelated default display. The host must actually use EGL; a GLX
context under XWayland is not sufficient. Capability checks require either
`GL_EXT_EGL_image_storage` or `GL_OES_EGL_image`, and the importer supports both.
The OES texture is configured without mipmap requirements. Texture bindings and EGL
images are cleaned up on failed imports as well as successful imports.

DMA-BUF descriptors retain up to four planes and all 64 modifier bits. Invalid or
truncated metadata is rejected, and tiled/compressed modifiers are never silently
dropped on a driver lacking modifier support. EGL handles DRM pixel format conversion;
an additional BGRA shader swap is not needed. Repeated import failures log once until
an import succeeds, instead of flooding the log every frame.

**NVIDIA remains a native limitation, not a completed fix.** The pinned JCEF build
uses CEF `143.0.14+gdd46a37+chromium-143.0.7499.193`. Its shared-texture capture
allocates CPU-mappable linear GBM buffers, which can fail on NVIDIA before a usable
paint callback reaches Java. [CEF #4237](https://github.com/chromiumembedded/cef/issues/4237)
and [the pending CEF fix](https://github.com/chromiumembedded/cef/pull/4238) describe
the producer-side change. Changing this Java importer does not apply that native fix.
Do not remove a host's NVIDIA safeguard until patched native CEF/JCEF binaries are
built, distributed and verified on that driver.

Nine additional regression tests cover Linux launch switches and DMA-BUF descriptor
construction. All 22 tests and the Java/client builds pass on the Windows build host.
No Arch/Wayland/NVIDIA runtime validation has been performed.
