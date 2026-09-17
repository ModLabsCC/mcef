package net.ccbluex.liquidbounce.mcef.cef;

import java.nio.ByteBuffer;

/** Pure timing/pixel-validation logic for the one-shot startup probe. */
final class AccelerationProbeState {
    enum Result { PENDING, PASSED, FAILED }

    static final int SIZE = 32;
    static final int BYTES = SIZE * SIZE * 4;
    private static final long TIMEOUT_NANOS = 8_000_000_000L;
    private long lastTime;
    private long activeTime;
    private boolean wasActive;
    private Result result = Result.PENDING;

    AccelerationProbeState(long now) {
        lastTime = now;
    }

    Result update(long now, boolean active, boolean correctPixels) {
        if (result != Result.PENDING) return result;
        if (active && wasActive) {
            // Time spent suspended or blocked outside the message pump is not GPU failure.
            activeTime += Math.min(Math.max(0L, now - lastTime), 250_000_000L);
        }
        lastTime = now;
        wasActive = active;
        if (active && correctPixels) result = Result.PASSED;
        else if (activeTime >= TIMEOUT_NANOS) result = Result.FAILED;
        return result;
    }

    static boolean matches(ByteBuffer pixels) {
        if (pixels.limit() < BYTES) return false;
        for (int y : new int[]{8, 24}) {
            for (int x : new int[]{8, 24}) {
                int expected = x < SIZE / 2 ? 73 : 181;
                int offset = (y * SIZE + x) * 4;
                for (int channel = 0; channel < 3; channel++) {
                    if (Math.abs(Byte.toUnsignedInt(pixels.get(offset + channel)) - expected) > 8) return false;
                }
                if (Byte.toUnsignedInt(pixels.get(offset + 3)) < 247) return false;
            }
        }
        return true;
    }
}
