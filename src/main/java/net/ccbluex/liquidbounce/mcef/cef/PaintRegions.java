package net.ccbluex.liquidbounce.mcef.cef;

import java.awt.Rectangle;
import java.util.ArrayList;

/** Clips CEF damage in source coordinates before native reads and GPU copies. */
final class PaintRegions {
    private PaintRegions() {}

    static Rectangle[] clip(Rectangle[] damage, int sourceWidth, int sourceHeight,
                            int targetWidth, int targetHeight, int offsetX, int offsetY) {
        var available = new Rectangle(0, 0, sourceWidth, sourceHeight)
                .intersection(new Rectangle(-offsetX, -offsetY, targetWidth, targetHeight));
        if (available.isEmpty()) return new Rectangle[0];
        var clipped = new ArrayList<Rectangle>(damage.length);
        for (var rect : damage) {
            var intersection = rect.intersection(available);
            if (!intersection.isEmpty()) clipped.add(intersection);
        }
        return clipped.toArray(Rectangle[]::new);
    }
}
