package net.ccbluex.liquidbounce.mcef.cef;

import org.cef.handler.CefAcceleratedPaintInfoLinux;
import org.lwjgl.egl.EGL14;
import org.lwjgl.egl.EXTImageDMABufImport;

import java.nio.IntBuffer;

/** Validates native metadata before constructing the EGL dma-buf descriptor. */
final class LinuxDmaBuf {
    static final long MODIFIER_INVALID = 0x00ffffffffffffffL;
    static final int MAX_ATTRIBUTE_INTS = 6 + 4 * 10 + 1;

    private LinuxDmaBuf() {}

    static void writeAttributes(CefAcceleratedPaintInfoLinux info, int width, int height,
                                boolean supportsModifiers, IntBuffer attributes) {
        if (width <= 0 || height <= 0 || !info.hasDmaBufPlanes()
                || info.plane_count > 4 || info.plane_fds.length < info.plane_count
                || info.plane_strides.length < info.plane_count || info.plane_offsets.length < info.plane_count) {
            throw new IllegalArgumentException("Invalid dma-buf dimensions or plane metadata");
        }
        int format = switch (info.format) {
            case CefConstants.CEF_COLOR_TYPE_RGBA_8888 -> CefConstants.DRM_FORMAT_ABGR8888;
            case CefConstants.CEF_COLOR_TYPE_BGRA_8888 -> CefConstants.DRM_FORMAT_ARGB8888;
            default -> throw new IllegalArgumentException("Unsupported dma-buf color format: " + info.format);
        };
        if (!supportsModifiers && (info.plane_count > 3
                || info.modifier != 0 && info.modifier != MODIFIER_INVALID)) {
            throw new IllegalArgumentException("This dma-buf requires EGL_EXT_image_dma_buf_import_modifiers");
        }
        boolean explicitModifier = supportsModifiers && info.modifier != MODIFIER_INVALID;
        for (int i = 0; i < info.plane_count; i++) {
            if (info.plane_fds[i] < 0 || info.plane_strides[i] <= 0
                    || info.plane_offsets[i] < 0 || info.plane_offsets[i] > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Invalid dma-buf plane " + i);
            }
        }
        attributes.put(EGL14.EGL_WIDTH).put(width);
        attributes.put(EGL14.EGL_HEIGHT).put(height);
        attributes.put(EXTImageDMABufImport.EGL_LINUX_DRM_FOURCC_EXT).put(format);
        for (int i = 0; i < info.plane_count; i++) {
            attributes.put(CefConstants.DMA_BUF_PLANE_FD_ATTRS[i]).put(info.plane_fds[i]);
            attributes.put(CefConstants.DMA_BUF_PLANE_OFFSET_ATTRS[i]).put((int) info.plane_offsets[i]);
            attributes.put(CefConstants.DMA_BUF_PLANE_PITCH_ATTRS[i]).put(info.plane_strides[i]);
            if (explicitModifier) {
                attributes.put(CefConstants.DMA_BUF_PLANE_MODIFIER_LO_ATTRS[i]).put((int) info.modifier);
                attributes.put(CefConstants.DMA_BUF_PLANE_MODIFIER_HI_ATTRS[i]).put((int) (info.modifier >>> 32));
            }
        }
        attributes.put(EGL14.EGL_NONE).flip();
    }
}
