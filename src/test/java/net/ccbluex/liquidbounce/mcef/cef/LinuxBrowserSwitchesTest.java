package net.ccbluex.liquidbounce.mcef.cef;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LinuxBrowserSwitchesTest {
    @Test
    void selectsEglForSharedTextureRendering() {
        var switches = new ArrayList<String>();
        LinuxBrowserSwitches.apply(switches);
        LinuxBrowserSwitches.apply(switches);
        assertEquals(List.of("--use-angle=gl-egl", "--ozone-platform=x11"), switches);
    }

    @Test
    void preservesExplicitBackendChoices() {
        var switches = new ArrayList<>(List.of("--use-angle=vulkan", "--ozone-platform=wayland"));
        LinuxBrowserSwitches.apply(switches);
        assertEquals(List.of("--use-angle=vulkan", "--ozone-platform=wayland"), switches);
    }

    @Test
    void unrelatedPlatformHintDoesNotSuppressRequiredDefault() {
        var switches = new ArrayList<>(List.of("--ozone-platform-hint=auto"));
        LinuxBrowserSwitches.apply(switches);
        assertTrue(switches.contains("--ozone-platform=x11"));
    }
}
