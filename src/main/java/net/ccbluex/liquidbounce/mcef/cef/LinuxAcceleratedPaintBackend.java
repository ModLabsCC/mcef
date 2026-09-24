/*
 * MCEF (Minecraft Chromium Embedded Framework)
 * Copyright (C) 2025 CCBlueX
 * Copyright (C) 2023 CinemaMod Group
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 */

package net.ccbluex.liquidbounce.mcef.cef;

import com.mojang.renderpearl.backend.opengl.GlStateManager;
import net.ccbluex.liquidbounce.mcef.MCEF;
import net.ccbluex.liquidbounce.mcef.utils.EglImageBinding;
import net.ccbluex.liquidbounce.mcef.utils.EglUtils;
import org.cef.handler.CefAcceleratedPaintInfo;
import org.cef.handler.CefAcceleratedPaintInfoLinux;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.lwjgl.egl.EGL14;
import org.lwjgl.egl.EXTImageDMABufImport;
import org.lwjgl.egl.KHRImageBase;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.opengl.GL11.*;

@NullMarked
final class LinuxAcceleratedPaintBackend implements AcceleratedPaintBackend {
    private boolean failureReported;

    @Override
    public boolean accepts(CefAcceleratedPaintInfo info) {
        return info instanceof CefAcceleratedPaintInfoLinux;
    }

    @Override
    public @Nullable AcceleratedPaintFrame importFrame(CefAcceleratedPaintInfo info, int width, int height) {
        var linuxInfo = (CefAcceleratedPaintInfoLinux) info;
        long display = EglUtils.getDisplay();
        if (display == EGL14.EGL_NO_DISPLAY || EGL14.eglGetCurrentContext() == EGL14.EGL_NO_CONTEXT) {
            reportFailure("No current EGL display/context for dma-buf import");
            return null;
        }
        if (!EglImageBinding.isSupported()) {
            reportFailure("No supported OpenGL EGLImage import entry point");
            return null;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            var attributes = stack.mallocInt(LinuxDmaBuf.MAX_ATTRIBUTE_INTS);
            try {
                LinuxDmaBuf.writeAttributes(linuxInfo, width, height,
                        EglUtils.getCapabilities().EGL_EXT_image_dma_buf_import_modifiers, attributes);
            } catch (IllegalArgumentException e) {
                reportFailure(e.getMessage());
                return null;
            }
            long image = EglUtils.eglCreateImageKHR(display, EGL14.EGL_NO_CONTEXT,
                    EXTImageDMABufImport.EGL_LINUX_DMA_BUF_EXT, 0L, attributes);
            if (image == 0L) {
                reportFailure("eglCreateImageKHR failed: EGL error 0x" + Integer.toHexString(EGL14.eglGetError())
                        + ", format=" + linuxInfo.format + ", planes=" + linuxInfo.plane_count
                        + ", modifier=0x" + Long.toHexString(linuxInfo.modifier));
                return null;
            }

            int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
            int texture = 0;
            boolean transferred = false;
            try {
                texture = glGenTextures();
                GlStateManager._bindTexture(texture);
                EglImageBinding.bind(image);
                int error = glGetError();
                if (error != GL_NO_ERROR) {
                    reportFailure("EGLImage texture binding failed: GL error 0x" + Integer.toHexString(error));
                    return null;
                }
                var directTexture = new MCEFDirectTexture();
                directTexture.setOwnedDirectTextureId(texture, width, height);
                // EGL interprets the DRM FourCC and exposes RGBA components in GL for both formats.
                var frame = new AcceleratedPaintFrame(directTexture.getTexture(), false, directTexture::close);
                transferred = true;
                failureReported = false;
                return frame;
            } finally {
                GlStateManager._bindTexture(previousTexture);
                if (!transferred && texture != 0) GlStateManager._deleteTexture(texture);
                KHRImageBase.eglDestroyImageKHR(display, image);
            }
        }
    }

    private void reportFailure(String reason) {
        if (!failureReported) {
            failureReported = true;
            MCEF.INSTANCE.LOGGER.error("Linux accelerated paint failed: {}", reason);
        }
    }

    @Override
    public void close() {}
}
