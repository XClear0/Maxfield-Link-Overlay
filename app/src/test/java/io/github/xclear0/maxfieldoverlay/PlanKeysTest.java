package io.github.xclear0.maxfieldoverlay;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public class PlanKeysTest {
    @Test
    public void countsFutureKeysForTheOriginByPortalNumberAcrossAgents() {
        List<LinkStep> steps = List.of(
                new LinkStep(1, 1, 1, "Library", 2, "Gate"),
                new LinkStep(2, 2, 1, "Library", 3, "Park"),
                new LinkStep(3, 2, 2, "Gate", 1, "Library"),
                new LinkStep(4, 1, 3, "Park", 4, "Library"),
                new LinkStep(5, 3, 2, "Gate", 1, "Library"));
        PlanKeys keys = PlanKeys.forPlan(steps);

        assertEquals(5, PlanKeys.totalForPlan(steps));
        assertEquals(2, keys.remainingForOriginAfter(0));
        assertEquals(2, keys.remainingForOriginAfter(1));
        assertEquals(0, keys.remainingForOriginAfter(2));
        assertEquals(0, keys.remainingForOriginAfter(4));
    }

    @Test
    public void excludesTheCurrentAndEarlierLinks() {
        List<LinkStep> steps = List.of(
                new LinkStep(1, 1, 1, "Library", 1, "Library"),
                new LinkStep(2, 1, 2, "Gate", 1, "Library"),
                new LinkStep(3, 1, 1, "Library", 2, "Gate"));
        PlanKeys keys = PlanKeys.forPlan(steps);

        assertEquals(1, keys.remainingForOriginAfter(0));
        assertEquals(1, keys.remainingForOriginAfter(1));
        assertEquals(0, keys.remainingForOriginAfter(2));
        assertEquals(0, PlanKeys.totalForPlan(List.of()));
    }
}
