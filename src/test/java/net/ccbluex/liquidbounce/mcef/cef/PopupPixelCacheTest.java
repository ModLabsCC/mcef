package net.ccbluex.liquidbounce.mcef.cef;

import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class PopupPixelCacheTest {
    private static ByteBuffer source() {
        var source = ByteBuffer.allocate(8 * 8 * 4);
        for (int i = 0; i < source.capacity(); i++) source.put(i, (byte) (i + 1));
        return source;
    }

    @Test
    void firstPaintInitializesTheEntireCacheEvenWithPartialDamage() {
        var source = source();
        var cache = ByteBuffer.allocate(source.capacity());
        PaintRegions.copyPopupPixels(source, cache, 8, 8, new Rectangle[]{new Rectangle(2, 2, 1, 1)}, false);
        assertArrayEquals(source.array(), cache.array());
    }

    @Test
    void subsequentPaintOnlyCopiesDamagedRowsAndColumns() {
        var source = source();
        var cache = ByteBuffer.allocate(source.capacity());
        var damage = new Rectangle[]{new Rectangle(1, 2, 2, 3), new Rectangle(6, 6, 2, 2)};
        PaintRegions.copyPopupPixels(source, cache, 8, 8, damage, true);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                boolean changed = damage[0].contains(x, y) || damage[1].contains(x, y);
                for (int channel = 0; channel < 4; channel++) {
                    int index = (y * 8 + x) * 4 + channel;
                    assertEquals(changed ? source.get(index) : (byte) 0, cache.get(index));
                }
            }
        }
    }

    @Test
    void fullWidthRowsCopyContiguouslyAndKeepBufferPositions() {
        var source = source().position(3);
        var cache = ByteBuffer.allocate(source.capacity()).position(7);
        PaintRegions.copyPopupPixels(source, cache, 8, 8, new Rectangle[]{new Rectangle(0, 2, 8, 2)}, true);
        assertEquals(3, source.position());
        assertEquals(7, cache.position());
        for (int index = 0; index < source.capacity(); index++) {
            assertEquals(index >= 64 && index < 128 ? source.get(index) : (byte) 0, cache.get(index));
        }
    }

    @Test
    void outOfBoundsDamageCannotWriteOutsideCache() {
        var source = source();
        var cache = ByteBuffer.allocate(source.capacity());
        PaintRegions.copyPopupPixels(source, cache, 8, 8, new Rectangle[]{new Rectangle(-3, -3, 20, 20)}, true);
        assertArrayEquals(source.array(), cache.array());
    }
}
