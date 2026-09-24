<p align="center">
  <img src="https://github.com/CinemaMod/mcef/assets/30220598/938896d7-2589-49df-8f82-29266c64dfb7" alt="MCEF Logo" style="width:66px;height:66px;">
</p>

# ModLabs MCEF

ModLabs' fork of [CCBlueX/mcef](https://github.com/CCBlueX/mcef), with browser
resource lifecycle fixes and rendering performance improvements.
See [PERFORMANCE.md](PERFORMANCE.md) for the changes and runtime verification steps.

Build with JDK 25: `git submodule update --init --recursive`, then `./gradlew build`.
The artifact version is `3.4.0-26.3-modlabs.1`.

Linux EGL import improvements and an offscreen acceleration probe are included.
Hosts can choose software rendering for a failing session and retry acceleration
on their next launch. See [automatic detection](PERFORMANCE.md#automatic-acceleration-detection)
and [native platform limitations](PERFORMANCE.md#linux-and-wayland).

## Upstream background

A lightweight fork of MCEF designed specifically for integration with LiquidBounce. This barebone library provides essential Chromium web browser functionality for Minecraft.

MCEF is based on java-cef (Java Chromium Embedded Framework), which is based on CEF (Chromium Embedded Framework), which is based on Chromium. Originally created by montoyo and rewritten by the CinemaMod Group, this version has been streamlined for LiquidBounce integration.

The library downloads its pinned java-cef and CEF binaries from this repository's
GitHub release `jcef-<java-cef commit>`. That public release must contain
`<platform>.tar.gz` and `<platform>.tar.gz.sha256` for each supported platform.
The checksum file contains only the lowercase SHA-256 hex digest and a newline.
Include `java-cef/LICENSE.txt` with the release. For local tests, an extracted
platform directory can be supplied through `PROVIDED_JCEF_PATH`.

The native JCEF revision is pinned by the `java-cef` submodule and recorded in `jcef.commit`.

## Supported Platforms
- Windows 10/11 (x86_64, arm64)*
- macOS 11 or greater (Intel, Apple Silicon)
- GNU Linux glibc 2.31 or greater (x86_64, arm64)**

*Note: Some antivirus software may prevent MCEF from initializing. You may need to disable your antivirus or whitelist the mod files for proper functionality.

**This library will not work on Android.

## For Modders
MCEF is LGPL, as long as your project doesn't modify or include MCEF source code, you can choose a different license. See the full license in the LICENSE file.

### Using MCEF in Your Project
Use a pinned checkout with a Gradle composite build that substitutes `net.ccbluex:mcef`
with this project. No publication to CCBlueX's Maven repository is needed.

### Building & Modifying MCEF
After cloning this repo, you will need to clone the java-cef git submodule using the provided gradle task: `./gradlew cloneJcef`.

## Fork Hierarchy
- [CCBlueX/mcef](https://github.com/CCBlueX/mcef) - Current LiquidBounce-optimized version
- [CinemaMod/mcef](https://github.com/CinemaMod/mcef)
- [montoyo/mcef](https://github.com/montoyo/mcef)

