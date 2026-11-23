package io.github.xclear0.maxfieldoverlay;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.List;
import org.junit.Test;

public class SearchOverlaySessionTest {
    private final List<LinkStep> plan = List.of(
            new LinkStep(1, 1, 1, "Library", 2, "Gate"),
            new LinkStep(2, 2, 3, "Park", 4, "Mall"),
            new LinkStep(3, 2, 2, "Gate", 1, "Library"),
            new LinkStep(4, 1, 4, "Mall", 2, "Gate"));

    @Test
    public void beginsAtSelectedResultAndSkipsUnmatchedLinksInBothDirections() {
        SearchOverlaySession session = SearchOverlaySession.open("page", "plan", plan, 1, 1);
        assertEquals(2, session.size());
        assertEquals(3, session.current().linkNumber);
        assertEquals(1, session.remainingKeys()); // Includes the later Link outside the filter.
        session.moveBy(-1);
        assertEquals(1, session.current().linkNumber);
        session.moveBy(-1);
        assertEquals(1, session.current().linkNumber);
        session.moveBy(1);
        session.moveBy(1);
        assertEquals(3, session.current().linkNumber);
        assertEquals(2, plan.get(1).linkNumber);
    }

    @Test
    public void rejectsSelectionsOutsideTheResults() {
        assertNull(SearchOverlaySession.open("page", "plan", plan, 5, 0));
        assertNull(SearchOverlaySession.open("page", "plan", plan, 1, 2));
        assertNull(SearchOverlaySession.open("page", "plan", plan, 1, -1));
    }
}
