package net.ccbluex.liquidbounce.mcef.utils;

import org.lwjgl.egl.EGL14;
import org.lwjgl.opengl.EXTEGLImageStorage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.JNI;

import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_MAX_LEVEL;
import static org.lwjgl.opengl.GL30.*;

/** Both import entry points used by Linux desktop GL drivers. */
public final class EglImageBinding {
    private static GLCapabilities contextCapabilities;
    private static long oesImageTarget;

    private EglImageBinding() {}

    public static boolean isSupported() {
        var caps = GL.getCapabilities();
        if (caps.GL_EXT_EGL_image_storage && caps.glEGLImageTargetTexStorageEXT != 0L) return true;
        return getOesImageTarget(caps) != 0L;
    }

    public static void bind(long image) {
        var caps = GL.getCapabilities();
        if (caps.GL_EXT_EGL_image_storage && caps.glEGLImageTargetTexStorageEXT != 0L) {
            EXTEGLImageStorage.glEGLImageTargetTexStorageEXT(GL_TEXTURE_2D, image, (IntBuffer) null);
        } else {
            long function = getOesImageTarget(caps);
            if (function == 0L) throw new IllegalStateException("No supported GL EGLImage import entry point");
            // GL_OES_EGL_image is exposed by some desktop drivers, but LWJGL's desktop
            // GL module has no generated wrapper for this ES-named extension.
            JNI.callPV(GL_TEXTURE_2D, image, function);
        }
        // The OES path creates mutable storage: do not require nonexistent mipmaps.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 0);
    }

    private static long getOesImageTarget(GLCapabilities caps) {
        if (contextCapabilities != caps) {
            contextCapabilities = caps;
            oesImageTarget = 0L;
            for (int i = 0, count = glGetInteger(GL_NUM_EXTENSIONS); i < count; i++) {
                if ("GL_OES_EGL_image".equals(glGetStringi(GL_EXTENSIONS, i))) {
                    oesImageTarget = EGL14.eglGetProcAddress("glEGLImageTargetTexture2DOES");
                    break;
                }
            }
        }
        return oesImageTarget;
    }
}
