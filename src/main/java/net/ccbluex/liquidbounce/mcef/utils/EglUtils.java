package net.ccbluex.liquidbounce.mcef.utils;

import net.ccbluex.liquidbounce.mcef.MCEF;
import org.jspecify.annotations.Nullable;
import org.lwjgl.egl.EGL;
import org.lwjgl.egl.EGL14;
import org.lwjgl.egl.EGLCapabilities;
import org.lwjgl.egl.KHRImageBase;
import org.lwjgl.system.JNI;
import org.lwjgl.system.MemoryUtil;

import java.nio.IntBuffer;

/*
 * This file is part of LiquidBounce (https://github.com/CCBlueX/LiquidBounce)
 *
 * Copyright (c) 2026 CCBlueX
 *
 * LiquidBounce is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LiquidBounce is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LiquidBounce. If not, see <https://www.gnu.org/licenses/>.
 *
 */
public final class EglUtils {

    private static @Nullable EGLCapabilities eglCapabilities = null;
    private static long eglDisplay = EGL14.EGL_NO_DISPLAY;

    public static long getDisplay() {
        long display = EGL14.eglGetCurrentDisplay();
        // Import into the display owning Minecraft's current context. Opening the default
        // display can choose X11 on a Wayland session (or a different GPU).
        if (display == EGL14.EGL_NO_DISPLAY) {
            eglDisplay = EGL14.EGL_NO_DISPLAY;
            eglCapabilities = null;
            return EGL14.EGL_NO_DISPLAY;
        }
        if (display == eglDisplay && eglCapabilities != null) {
            return display;
        }
        // The owner already initialized this display; do not change its lifecycle.
        eglDisplay = display;
        eglCapabilities = EGL.createDisplayCapabilities(display);

        return eglDisplay;
    }

    public static EGLCapabilities getCapabilities() {
        if (eglCapabilities == null) {
            return EGL.getCapabilities();
        }
        return eglCapabilities;
    }

    /**
     * A copy of [{@link KHRImageBase#eglCreateImageKHR}] to bypass argument checks.
     */
    public static long eglCreateImageKHR(long display, long context, int target, long buffer, IntBuffer attribs) {
        long functionAddress = EGL.getCapabilities().eglCreateImageKHR;
        if (functionAddress == 0L) {
            MCEF.INSTANCE.LOGGER.error("eglCreateImageKHR is not available on this EGL implementation.");
            return 0L;
        }

        return JNI.callPPPPP(display, context, target, buffer, MemoryUtil.memAddress(attribs), functionAddress);
    }

}
