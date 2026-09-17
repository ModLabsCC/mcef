package net.ccbluex.liquidbounce.mcef.cef;

import java.util.List;

final class LinuxBrowserSwitches {
    private LinuxBrowserSwitches() {}

    static void apply(List<String> switches) {
        // Shared-texture OSR needs ANGLE's EGL backend (CEF issue #3953).
        if (switches.stream().noneMatch(s -> s.equals("--use-angle") || s.startsWith("--use-angle="))) {
            switches.add("--use-angle=gl-egl");
        }
        // CEF's GTK integration still uses X11. This also works under XWayland;
        // it does not select the Minecraft window's GLFW/EGL platform.
        if (switches.stream().noneMatch(s -> s.equals("--ozone-platform") || s.startsWith("--ozone-platform="))) {
            switches.add("--ozone-platform=x11");
        }
    }
}
