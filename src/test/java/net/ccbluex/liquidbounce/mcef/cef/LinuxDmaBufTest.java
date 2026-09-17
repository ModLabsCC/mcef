package net.ccbluex.liquidbounce.mcef.cef;

import org.cef.handler.CefAcceleratedPaintInfoLinux;
import org.junit.jupiter.api.Test;
import org.lwjgl.egl.EGL14;
import org.lwjgl.egl.EXTImageDMABufImport;
import java.nio.IntBuffer;
import static org.junit.jupiter.api.Assertions.*;

class LinuxDmaBufTest {
    private static CefAcceleratedPaintInfoLinux frame(int count, long modifier) {
        var info = new CefAcceleratedPaintInfoLinux(CefConstants.CEF_COLOR_TYPE_BGRA_8888, 64, 64);
        info.plane_count = count;
        info.modifier = modifier;
        info.plane_fds = new int[]{3, 4, 5, 6};
        info.plane_strides = new int[]{256, 256, 256, 256};
        info.plane_offsets = new long[]{0, 4096, 8192, 12288};
        return info;
    }

    private static IntBuffer attributes(CefAcceleratedPaintInfoLinux info, boolean modifiers) {
        var result = IntBuffer.allocate(LinuxDmaBuf.MAX_ATTRIBUTE_INTS);
        LinuxDmaBuf.writeAttributes(info, 64, 64, modifiers, result);
        return result;
    }

    private static Integer value(IntBuffer buffer, int key) {
        for (int i = 0; i < buffer.limit() - 1; i += 2) {
            if (buffer.get(i) == key) return buffer.get(i + 1);
        }
        return null;
    }

    @Test
    void preservesFourthAuxiliaryPlaneAndFull64BitModifier() {
        var result = attributes(frame(4, 0x0200000012345678L), true);
        assertEquals(47, result.remaining());
        assertEquals(6, value(result, CefConstants.DMA_BUF_PLANE_FD_ATTRS[3]));
        assertEquals(12288, value(result, CefConstants.DMA_BUF_PLANE_OFFSET_ATTRS[3]));
        assertEquals(0x12345678, value(result, CefConstants.DMA_BUF_PLANE_MODIFIER_LO_ATTRS[3]));
        assertEquals(0x02000000, value(result, CefConstants.DMA_BUF_PLANE_MODIFIER_HI_ATTRS[3]));
        assertEquals(EGL14.EGL_NONE, result.get(result.limit() - 1));
    }

    @Test
    void omitsUnspecifiedModifierButKeepsExplicitLinear() {
        assertNull(value(attributes(frame(1, LinuxDmaBuf.MODIFIER_INVALID), true),
                CefConstants.DMA_BUF_PLANE_MODIFIER_LO_ATTRS[0]));
        assertEquals(0, value(attributes(frame(1, 0), true), CefConstants.DMA_BUF_PLANE_MODIFIER_LO_ATTRS[0]));
        assertNull(value(attributes(frame(1, 0), false), CefConstants.DMA_BUF_PLANE_MODIFIER_LO_ATTRS[0]));
    }

    @Test
    void refusesToSilentlyDiscardRequiredModifierOrPlane() {
        assertThrows(IllegalArgumentException.class, () -> attributes(frame(1, 0x0200000000000001L), false));
        assertThrows(IllegalArgumentException.class, () -> attributes(frame(4, 0), false));
        assertThrows(IllegalArgumentException.class, () -> attributes(frame(5, 0), true));
    }

    @Test
    void rejectsTruncatedAndMissingNativeMetadata() {
        var info = frame(2, 0);
        info.plane_fds = new int[]{3};
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
        info.plane_fds = null;
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
    }

    @Test
    void rejectsInvalidDescriptorsStridesAndOffsets() {
        var info = frame(1, 0);
        info.plane_fds[0] = -1;
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
        info.plane_fds[0] = 0; // fd zero is valid
        info.plane_strides[0] = 0;
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
        info.plane_strides[0] = 256;
        info.plane_offsets[0] = -1;
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
        info.plane_offsets[0] = (long) Integer.MAX_VALUE + 1;
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
        info.plane_offsets[0] = 0;
        assertDoesNotThrow(() -> attributes(info, true));
    }

    @Test
    void mapsBothPixelFormatsThroughDrmFourCc() {
        var info = frame(1, 0);
        assertEquals(CefConstants.DRM_FORMAT_ARGB8888,
                value(attributes(info, true), EXTImageDMABufImport.EGL_LINUX_DRM_FOURCC_EXT));
        info.format = CefConstants.CEF_COLOR_TYPE_RGBA_8888;
        assertEquals(CefConstants.DRM_FORMAT_ABGR8888,
                value(attributes(info, true), EXTImageDMABufImport.EGL_LINUX_DRM_FOURCC_EXT));
        info.format = -1;
        assertThrows(IllegalArgumentException.class, () -> attributes(info, true));
    }
}
