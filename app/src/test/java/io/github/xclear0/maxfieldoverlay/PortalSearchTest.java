package io.github.xclear0.maxfieldoverlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class PortalSearchTest {
    @Test
    public void matchesBothEndpointsByPartialNameAndKeepsSameNamedPortalsSeparate() {
        List<LinkStep> steps = List.of(
                new LinkStep(1, 1, 2, "East Gate", 3, "公园"),
                new LinkStep(2, 2, 4, "图书馆", 5, "East Gate"),
                new LinkStep(3, 1, 4, "图书馆", 2, "East Gate"));

        List<PortalSearch.Portal> matches = PortalSearch.findPortals(steps, "  gAtE ");

        assertEquals(2, matches.size());
        assertEquals(2, matches.get(0).number);
        assertEquals("East Gate", matches.get(0).name);
        assertEquals(5, matches.get(1).number);
        assertEquals(3, PortalSearch.findPortals(steps, "公园").get(0).number);
        assertTrue(PortalSearch.findPortals(steps, "  ").isEmpty());
        assertTrue(PortalSearch.findPortals(steps, "museum").isEmpty());
    }

    @Test
    public void returnsIncomingAndOutgoingLinksInPlanOrder() {
        LinkStep incoming = new LinkStep(7, 2, 1, "Library", 2, "East Gate");
        LinkStep unrelated = new LinkStep(8, 2, 3, "Park", 4, "East Gate");
        LinkStep outgoing = new LinkStep(9, 1, 2, "East Gate", 3, "Park");
        LinkStep selfLink = new LinkStep(10, 1, 2, "East Gate", 2, "East Gate");

        assertEquals(List.of(incoming, outgoing, selfLink),
                PortalSearch.findLinks(List.of(incoming, unrelated, outgoing, selfLink), 2));
    }
}
