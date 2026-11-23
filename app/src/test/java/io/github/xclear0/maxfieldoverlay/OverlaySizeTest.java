package io.github.xclear0.maxfieldoverlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OverlaySizeTest {
    @Test
    public void choosesEdgeUsingTheExpandedBounds() {
        assertFalse(OverlaySize.closerToRight(25, 330, 400));
        assertTrue(OverlaySize.closerToRight(55, 330, 400));
        assertFalse(OverlaySize.closerToRight(35, 330, 400));
        assertTrue(OverlaySize.closerToRight(115, 330, 400));
        assertFalse(OverlaySize.closerToRight(-25, 330, 400));
    }

    @Test
    public void clampsWidthToTheAvailableScreenAndSupportedScale() {
        assertEquals(330, OverlaySize.pixels(330, 376, 100));
        assertEquals(376, OverlaySize.pixels(330, 376, 140));
        assertEquals(215, OverlaySize.pixels(330, 376, 30));
    }

    @Test
    public void resizingKeepsTheOppositeEdgeFixedAndRespectsTheWorkspace() {
        assertEquals(new OverlaySize.Axis(30, 110), OverlaySize.resizeEdge(30, 330, 330, 420, 1, 33));
        assertEquals(new OverlaySize.Axis(90, 80), OverlaySize.resizeEdge(30, 300, 300, 400, -1, 60));
        assertEquals(new OverlaySize.Axis(30, 65), OverlaySize.resizeEdge(30, 300, 300, 400, 1, -240));
        assertEquals(new OverlaySize.Axis(0, 120), OverlaySize.resizeEdge(0, 300, 300, 360, 1, 240));
    }

    @Test
    public void movingStaysInTheWorkspaceAndResetRestoresBothDimensions() {
        assertEquals(0, OverlaySize.boundedPosition(-20, 400, 330));
        assertEquals(45, OverlaySize.boundedPosition(45, 400, 330));
        assertEquals(70, OverlaySize.boundedPosition(100, 400, 330));
        assertEquals(new OverlaySize.Dimensions(100, 100), OverlaySize.defaults());
    }
}
