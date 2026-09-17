package net.ccbluex.liquidbounce.mcef.cef;

import org.junit.jupiter.api.Test;
import java.awt.Rectangle;
import static org.junit.jupiter.api.Assertions.*;

class PaintRegionsTest {
    @Test
    void clipsPopupAtBottomRightOfView() {
        assertArrayEquals(new Rectangle[]{new Rectangle(0, 0, 10, 20)},
                PaintRegions.clip(new Rectangle[]{new Rectangle(0, 0, 50, 50)},
                        50, 50, 100, 100, 90, 80));
    }

    @Test
    void clipsNegativePopupOriginInSourceCoordinates() {
        assertArrayEquals(new Rectangle[]{new Rectangle(10, 20, 40, 30)},
                PaintRegions.clip(new Rectangle[]{new Rectangle(0, 0, 50, 50)},
                        50, 50, 100, 100, -10, -20));
    }

    @Test
    void neverReadsOutsideSourceBuffer() {
        assertArrayEquals(new Rectangle[]{new Rectangle(0, 0, 30, 40)},
                PaintRegions.clip(new Rectangle[]{new Rectangle(-5, -5, 100, 100)},
                        30, 40, 200, 200, 0, 0));
    }

    @Test
    void ignoresEmptyAndOffscreenDamage() {
        assertEquals(0, PaintRegions.clip(new Rectangle[]{new Rectangle(0, 0, 50, 50)},
                50, 50, 100, 100, 100, 0).length);
        assertEquals(0, PaintRegions.clip(new Rectangle[]{new Rectangle(0, 0, 0, 5)},
                50, 50, 100, 100, 0, 0).length);
    }

    @Test
    void preservesSparseDamageWithoutMutatingCefRectangles() {
        var damage = new Rectangle[]{new Rectangle(1, 2, 3, 4), new Rectangle(80, 90, 10, 5)};
        var result = PaintRegions.clip(damage, 100, 100, 100, 100, 0, 0);
        assertArrayEquals(damage, result);
        assertNotSame(damage[0], result[0]);
    }
}
