package net.ccbluex.liquidbounce.mcef.cef;

import java.awt.Rectangle;
import java.nio.ByteBuffer;

/** Selects a transfer layout without expanding the GPU destination damage. */
record DirtyUploadLayout(int x, int y, int width, int height, int bytes,
                         boolean packed, boolean separateRectangles) {
    static DirtyUploadLayout plan(int sourceWidth, int sourceHeight, Rectangle[] damage) {
        int fullBytes = Math.toIntExact((long) sourceWidth * sourceHeight * 4);
        if (damage.length == 0) return new DirtyUploadLayout(0, 0, sourceWidth, sourceHeight, fullBytes, false, false);
        int left = sourceWidth, top = sourceHeight, right = 0, bottom = 0;
        long separateBytes = 0;
        for (var rect : damage) {
            left = Math.min(left, rect.x);
            top = Math.min(top, rect.y);
            right = Math.max(right, rect.x + rect.width);
            bottom = Math.max(bottom, rect.y + rect.height);
            separateBytes += (long) rect.width * rect.height * 4;
        }
        long boundsBytes = (long) (right - left) * (bottom - top) * 4;
        // Bound per-rectangle packing overhead for extremely fragmented pages.
        if (damage.length <= 64 && separateBytes * 2 < boundsBytes) {
            return new DirtyUploadLayout(0, 0, 0, 0, Math.toIntExact(separateBytes), true, true);
        }
        if (boundsBytes * 2 < fullBytes) {
            return new DirtyUploadLayout(left, top, right - left, bottom - top,
                    Math.toIntExact(boundsBytes), true, false);
        }
        // Dense updates avoid a redundant CPU packing copy.
        return new DirtyUploadLayout(0, 0, sourceWidth, sourceHeight, fullBytes, false, false);
    }

    void pack(ByteBuffer source, int sourceWidth, Rectangle[] damage, ByteBuffer target) {
        if (!packed) throw new IllegalStateException("Full-frame layout does not need packing");
        if (separateRectangles) {
            int offset = 0;
            for (var rect : damage) {
                copyRows(source, sourceWidth, rect.x, rect.y, rect.width, rect.height, target, offset);
                offset += rect.width * rect.height * 4;
            }
        } else {
            copyRows(source, sourceWidth, x, y, width, height, target, 0);
        }
        target.position(0).limit(bytes);
    }

    private static void copyRows(ByteBuffer source, int sourceWidth, int x, int y,
                                  int width, int height, ByteBuffer target, int targetOffset) {
        int rowBytes = width * 4;
        int sourceOffset = (y * sourceWidth + x) * 4;
        if (width == sourceWidth) {
            target.put(targetOffset, source, sourceOffset, rowBytes * height);
        } else {
            for (int row = 0; row < height; row++) {
                target.put(targetOffset + row * rowBytes, source, sourceOffset + row * sourceWidth * 4, rowBytes);
            }
        }
    }

    static int nextCapacity(int required, int current, int limit) {
        if (required < 0 || required > limit) throw new IllegalArgumentException("Invalid staging capacity");
        if (current >= required) return current;
        long grown = Math.max(64 * 1024L, current + (long) current / 2);
        return (int) Math.min(limit, Math.max(required, grown));
    }
}
