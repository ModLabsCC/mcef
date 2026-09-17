package net.ccbluex.liquidbounce.mcef.cef;

import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;
import static net.ccbluex.liquidbounce.mcef.cef.AccelerationProbeState.Result.*;

class AccelerationProbeStateTest {
    private static ByteBuffer pattern() {
        var pixels = ByteBuffer.allocate(AccelerationProbeState.BYTES);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                byte color = (byte) (x < 16 ? 73 : 181);
                pixels.put(color).put(color).put(color).put((byte) 255);
            }
        }
        return pixels.flip();
    }

    @Test
    void acceptsKnownOpaqueGpuPattern() {
        assertTrue(AccelerationProbeState.matches(pattern()));
    }

    @Test
    void rejectsTransparentOutputEvenWithCorrectRgb() {
        var pixels = pattern();
        for (int i = 3; i < pixels.limit(); i += 4) pixels.put(i, (byte) 0);
        assertFalse(AccelerationProbeState.matches(pixels));
    }

    @Test
    void rejectsBlankBlackWhiteAndIncompleteBuffers() {
        assertFalse(AccelerationProbeState.matches(ByteBuffer.allocate(AccelerationProbeState.BYTES)));
        var pixels = ByteBuffer.allocate(AccelerationProbeState.BYTES);
        for (int i = 0; i < pixels.limit(); i++) pixels.put(i, (byte) 255);
        assertFalse(AccelerationProbeState.matches(pixels));
        assertFalse(AccelerationProbeState.matches(ByteBuffer.allocate(4)));
    }

    @Test
    void requiresBothHalvesOfThePattern() {
        var pixels = pattern();
        int offset = (8 * 32 + 24) * 4;
        pixels.put(offset, (byte) 73);
        assertFalse(AccelerationProbeState.matches(pixels));
    }

    @Test
    void missingFramesFailAfterEightSecondsOfActivePolling() {
        var state = new AccelerationProbeState(0);
        assertEquals(PENDING, state.update(0, true, false));
        for (int tick = 1; tick < 32; tick++) {
            assertEquals(PENDING, state.update(tick * 250_000_000L, true, false));
        }
        assertEquals(FAILED, state.update(8_000_000_000L, true, false));
    }

    @Test
    void validPixelsKeepAccelerationEnabledWithoutFurtherMonitoring() {
        var state = new AccelerationProbeState(0);
        assertEquals(PASSED, state.update(1, true, true));
        assertEquals(PASSED, state.update(60_000_000_000L, true, false));
    }

    @Test
    void minimizedWindowDoesNotConsumeTimeoutOrPass() {
        var state = new AccelerationProbeState(0);
        assertEquals(PENDING, state.update(0, true, false));
        assertEquals(PENDING, state.update(1_000_000_000L, false, true));
        assertEquals(PENDING, state.update(60_000_000_000L, false, false));
        assertEquals(PENDING, state.update(61_000_000_000L, true, false));
        assertEquals(PASSED, state.update(61_250_000_000L, true, true));
    }

    @Test
    void longMessagePumpStallIsNotAnImmediateFailure() {
        var state = new AccelerationProbeState(0);
        state.update(0, true, false);
        assertEquals(PENDING, state.update(60_000_000_000L, true, false));
    }

    @Test
    void failureIsTerminalButNextLaunchRetries() {
        var state = new AccelerationProbeState(0);
        for (int tick = 0; tick <= 32; tick++) state.update(tick * 250_000_000L, true, false);
        assertEquals(FAILED, state.update(9_000_000_000L, true, true));
        var nextLaunch = new AccelerationProbeState(10_000_000_000L);
        assertEquals(PASSED, nextLaunch.update(10_250_000_000L, true, true));
    }
}
