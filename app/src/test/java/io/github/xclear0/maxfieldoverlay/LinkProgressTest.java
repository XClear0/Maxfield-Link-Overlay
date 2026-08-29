package io.github.xclear0.maxfieldoverlay;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class LinkProgressTest {
    private final List<LinkStep> steps = Arrays.asList(
            step(1, 10, "Origin A"),
            step(2, 10, "Origin A"),
            step(3, 20, "Origin B"),
            step(4, 20, "Origin B"),
            step(5, 10, "Origin A"));

    @Test
    public void firstLinkDoesNotReportOriginChange() {
        assertFalse(LinkProgress.hasOriginChanged(steps, 0));
    }

    @Test
    public void consecutiveLinksFromSamePortalDoNotReportChange() {
        assertFalse(LinkProgress.hasOriginChanged(steps, 1));
        assertFalse(LinkProgress.hasOriginChanged(steps, 3));
    }

    @Test
    public void differentPortalFromPreviousLinkReportsChange() {
        assertTrue(LinkProgress.hasOriginChanged(steps, 2));
        assertTrue(LinkProgress.hasOriginChanged(steps, 4));
    }

    @Test
    public void invalidProgressDoesNotReportChange() {
        assertFalse(LinkProgress.hasOriginChanged(null, 1));
        assertFalse(LinkProgress.hasOriginChanged(steps, -1));
        assertFalse(LinkProgress.hasOriginChanged(steps, steps.size()));
    }

    private static LinkStep step(int linkNumber, int originNumber, String originName) {
        return new LinkStep(
                linkNumber,
                1,
                originNumber,
                originName,
                99,
                "Destination");
    }
}
