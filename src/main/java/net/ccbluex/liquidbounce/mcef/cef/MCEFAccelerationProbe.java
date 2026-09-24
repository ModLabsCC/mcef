package net.ccbluex.liquidbounce.mcef.cef;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.backend.opengl.GlStateManager;
import com.mojang.renderpearl.backend.opengl.GlTexture;
import net.ccbluex.liquidbounce.mcef.MCEF;
import org.cef.browser.CefBrowser;
import org.lwjgl.system.MemoryStack;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER;
import static org.lwjgl.opengl.GL21.GL_PIXEL_PACK_BUFFER_BINDING;

/**
 * Tests the entire accelerated paint/import/copy path before application browsers are created.
 * Pump CEF normally, then poll on the render thread. Close on completion or cancellation.
 * Results are deliberately not persisted: a fresh launch tests updated drivers/native binaries.
 */
public final class MCEFAccelerationProbe implements AutoCloseable {
    public enum Result { PENDING, PASSED, FAILED }

    private static final String URL = "data:text/html," + URLEncoder.encode(
            "<!doctype html><html><head><style>html,body{margin:0;width:100%;height:100%;overflow:hidden}"
                    + "body{background:#494949}div{position:absolute;left:50%;top:0;width:50%;height:100%;"
                    + "background:#b5b5b5}</style></head><body><div></div></body></html>",
            StandardCharsets.UTF_8).replace("+", "%20");

    private final ProbeBrowser browser;
    private final AccelerationProbeState state = new AccelerationProbeState(System.nanoTime());
    private boolean loadRequested;
    private boolean closed;
    private long nextRead;
    private Result result = Result.PENDING;

    public MCEFAccelerationProbe() {
        RenderSystem.assertOnRenderThread();
        browser = new ProbeBrowser(Objects.requireNonNull(MCEF.INSTANCE.getClient()));
        browser.setCloseAllowed();
        try {
            browser.createImmediately();
            browser.resize(AccelerationProbeState.SIZE, AccelerationProbeState.SIZE);
        } catch (RuntimeException | LinkageError e) {
            browser.close();
            throw e;
        }
    }

    /** Identifies probe callbacks, including late callbacks after the probe has been closed. */
    public static boolean isProbeBrowser(CefBrowser browser) {
        return browser instanceof ProbeBrowser;
    }

    public Result poll(boolean windowVisible) {
        RenderSystem.assertOnRenderThread();
        if (closed || result != Result.PENDING) return result;
        long now = System.nanoTime();
        boolean matches = false;
        if (windowVisible) {
            if (!loadRequested && browser.getIdentifier() > 0) {
                // Same post-creation navigation workaround needed by normal OSR browsers.
                browser.loadURL(URL);
                loadRequested = true;
            }
            if (now >= nextRead) {
                nextRead = now + 250_000_000L;
                try {
                    matches = readPattern();
                } catch (RuntimeException | LinkageError e) {
                    MCEF.INSTANCE.LOGGER.warn("Accelerated paint probe readback failed", e);
                    result = Result.FAILED;
                    return result;
                }
            }
        }
        result = Result.valueOf(state.update(now, windowVisible, matches).name());
        return result;
    }

    private boolean readPattern() {
        var renderer = browser.getRenderer();
        if (!renderer.isAccelerated() || !renderer.isTextureReady()
                || renderer.getTextureWidth() != AccelerationProbeState.SIZE
                || renderer.getTextureHeight() != AccelerationProbeState.SIZE
                || !(renderer.getTexture() instanceof GlTexture texture)) return false;

        int binding = glGetInteger(GL_TEXTURE_BINDING_2D);
        int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        int rowLength = glGetInteger(GL_PACK_ROW_LENGTH);
        int skipRows = glGetInteger(GL_PACK_SKIP_ROWS);
        int skipPixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var pixels = stack.calloc(AccelerationProbeState.BYTES);
            try {
                glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
                glPixelStorei(GL_PACK_ALIGNMENT, 1);
                glPixelStorei(GL_PACK_ROW_LENGTH, 0);
                glPixelStorei(GL_PACK_SKIP_ROWS, 0);
                glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
                GlStateManager._bindTexture(texture.glId());
                glGetTexImage(GL_TEXTURE_2D, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
                return AccelerationProbeState.matches(pixels);
            } finally {
                GlStateManager._bindTexture(binding);
                glPixelStorei(GL_PACK_ALIGNMENT, alignment);
                glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
                glPixelStorei(GL_PACK_SKIP_ROWS, skipRows);
                glPixelStorei(GL_PACK_SKIP_PIXELS, skipPixels);
                glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        if (result == Result.PENDING) result = Result.FAILED;
        browser.close();
    }

    private static final class ProbeBrowser extends MCEFBrowser {
        ProbeBrowser(MCEFClient client) {
            super(client, URL, true, new MCEFBrowserSettings(10, true));
        }

        @Override
        public boolean onCursorChange(CefBrowser browser, int cursorType) {
            return true; // The offscreen test page must not change the application's cursor.
        }
    }
}
