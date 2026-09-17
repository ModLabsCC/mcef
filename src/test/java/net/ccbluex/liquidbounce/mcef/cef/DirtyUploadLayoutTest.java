package net.ccbluex.liquidbounce.mcef.cef;

import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class DirtyUploadLayoutTest {
    @Test
    void oppositeHudCornersOnlyUploadTheirOwnPixels() {
        var layout = DirtyUploadLayout.plan(1920, 1080, new Rectangle[]{
                new Rectangle(0, 0, 64, 64), new Rectangle(1856, 1016, 64, 64)
        });
        assertTrue(layout.separateRectangles());
        assertEquals(32_768, layout.bytes());
        assertTrue(layout.bytes() < 1920 * 1080 * 4 / 250);
    }

    @Test
    void nearbyDamageUsesCompactBoundingBox() {
        var layout = DirtyUploadLayout.plan(100, 100, new Rectangle[]{
                new Rectangle(10, 10, 10, 10), new Rectangle(20, 10, 10, 10)
        });
        assertTrue(layout.packed());
        assertFalse(layout.separateRectangles());
        assertEquals(800, layout.bytes());
    }

    @Test
    void denseDamageAvoidsAdditionalCpuCopy() {
        var layout = DirtyUploadLayout.plan(100, 100, new Rectangle[]{new Rectangle(0, 0, 100, 100)});
        assertFalse(layout.packed());
        assertEquals(40_000, layout.bytes());
    }

    @Test
    void growingDamageNeedsFewAllocationsAndStaysBounded() {
        int capacity = 0, allocations = 0;
        for (int required = 4; required <= 1_000_000; required += 4) {
            int next = DirtyUploadLayout.nextCapacity(required, capacity, 1_000_000);
            if (next != capacity) allocations++;
            capacity = next;
            assertTrue(capacity >= required && capacity <= 1_000_000);
        }
        assertTrue(allocations < 10, "Growing damage should not allocate for every extra pixel");
        assertEquals(128, DirtyUploadLayout.nextCapacity(100, 0, 128));
        assertThrows(IllegalArgumentException.class, () -> DirtyUploadLayout.nextCapacity(129, 0, 128));
    }

    @Test
    void fullWidthRowsPackContiguouslyWithoutMovingSourcePosition() {
        var source = ByteBuffer.allocate(8 * 8 * 4);
        for (int i = 0; i < source.capacity(); i++) source.put(i, (byte) i);
        source.position(7);
        var damage = new Rectangle[]{new Rectangle(0, 3, 8, 2)};
        var layout = DirtyUploadLayout.plan(8, 8, damage);
        var packed = ByteBuffer.allocate(layout.bytes());
        layout.pack(source, 8, damage, packed);
        assertEquals(7, source.position());
        for (int i = 0; i < layout.bytes(); i++) assertEquals(source.get(3 * 8 * 4 + i), packed.get(i));
    }

    @Test
    void allLayoutsMatchDirectPixelCopiesIncludingClippedOverlappingDamage() {
        var random = new Random(731);
        for (int iteration = 0; iteration < 500; iteration++) {
            int width = 16 + random.nextInt(64), height = 16 + random.nextInt(64);
            int offsetX = random.nextInt(17) - 8, offsetY = random.nextInt(17) - 8;
            var raw = new Rectangle[1 + random.nextInt(12)];
            for (int i = 0; i < raw.length; i++) {
                raw[i] = new Rectangle(random.nextInt(width + 16) - 8, random.nextInt(height + 16) - 8,
                        1 + random.nextInt(width), 1 + random.nextInt(height));
            }
            var damage = PaintRegions.clip(raw, width, height, width, height, offsetX, offsetY);
            if (damage.length == 0) continue;
            byte[] original = new byte[width * height * 4];
            random.nextBytes(original);
            byte[] expected = new byte[original.length], actual = new byte[original.length];
            Arrays.fill(expected, (byte) 0x5a);
            Arrays.fill(actual, (byte) 0x5a);
            var layout = DirtyUploadLayout.plan(width, height, damage);
            var upload = iteration % 2 == 0 ? ByteBuffer.allocateDirect(layout.bytes()) : ByteBuffer.allocate(layout.bytes());
            var source = iteration % 2 == 0 ? ByteBuffer.allocateDirect(original.length).put(0, original)
                    : ByteBuffer.wrap(original);
            if (layout.packed()) layout.pack(source, width, damage, upload);
            else upload.put(0, original);
            int packedOffset = 0;
            for (var rect : damage) {
                int rowWidth = layout.separateRectangles() ? rect.width : layout.width();
                int sourceStart = layout.separateRectangles() ? packedOffset
                        : ((rect.y - layout.y()) * rowWidth + rect.x - layout.x()) * 4;
                for (int row = 0; row < rect.height; row++) {
                    int destination = ((rect.y + offsetY + row) * width + rect.x + offsetX) * 4;
                    System.arraycopy(original, ((rect.y + row) * width + rect.x) * 4,
                            expected, destination, rect.width * 4);
                    upload.get(sourceStart + row * rowWidth * 4, actual, destination, rect.width * 4);
                }
                if (layout.separateRectangles()) packedOffset += rect.width * rect.height * 4;
            }
            assertArrayEquals(expected, actual, "Pixel mismatch in case " + iteration);
        }
    }
}
