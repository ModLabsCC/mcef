package net.ccbluex.liquidbounce.mcef.cef;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.nio.ByteBuffer;

/** Clips CEF damage in source coordinates before native reads and GPU copies. */
final class PaintRegions {
    private static final Rectangle[] EMPTY = new Rectangle[0];
    private PaintRegions() {}

    static Rectangle[] clip(Rectangle[] damage, int sourceWidth, int sourceHeight,
                            int targetWidth, int targetHeight, int offsetX, int offsetY) {
        long left = Math.max(0L, -(long) offsetX), top = Math.max(0L, -(long) offsetY);
        long right = Math.min(sourceWidth, (long) targetWidth - offsetX);
        long bottom = Math.min(sourceHeight, (long) targetHeight - offsetY);
        if (right <= left || bottom <= top || damage.length == 0) return EMPTY;
        boolean contained = true;
        for (var rect : damage) {
            if (rect.width <= 0 || rect.height <= 0 || rect.x < left || rect.y < top
                    || (long) rect.x + rect.width > right || (long) rect.y + rect.height > bottom) {
                contained = false;
                break;
            }
        }
        // Common path: callers read these rectangles and never mutate them.
        if (contained) return damage;
        var available = new Rectangle((int) left, (int) top, (int) (right - left), (int) (bottom - top));
        var clipped = new ArrayList<Rectangle>(damage.length);
        for (var rect : damage) {
            var intersection = rect.intersection(available);
            if (!intersection.isEmpty()) clipped.add(intersection);
        }
        return clipped.toArray(Rectangle[]::new);
    }

    static boolean intersects(Rectangle[] damage, Rectangle area) {
        for (var rect : damage) if (rect.intersects(area)) return true;
        return false;
    }

    static void copyPopupPixels(ByteBuffer source, ByteBuffer target, int width, int height,
                                Rectangle[] damage, boolean initialized) {
        if (!initialized) {
            target.put(0, source, 0, Math.multiplyExact(Math.multiplyExact(width, height), 4));
            return;
        }
        for (var rect : clip(damage, width, height, width, height, 0, 0)) {
            int offset = (rect.y * width + rect.x) * 4;
            if (rect.width == width) {
                target.put(offset, source, offset, rect.width * rect.height * 4);
            } else {
                for (int row = 0; row < rect.height; row++) {
                    int rowOffset = offset + row * width * 4;
                    target.put(rowOffset, source, rowOffset, rect.width * 4);
                }
            }
        }
    }
}
