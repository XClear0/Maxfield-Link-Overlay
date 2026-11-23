package io.github.xclear0.maxfieldoverlay;

import java.util.ArrayList;
import java.util.List;

/** In-memory playback of a Portal's matching Links. Never writes plan progress. */
final class SearchOverlaySession {
    final String owner;
    final String planId;
    private final List<LinkStep> matches;
    private final List<Integer> originalIndices;
    private final PlanKeys planKeys;
    private int index;

    private SearchOverlaySession(String owner, String planId, List<LinkStep> matches,
            List<Integer> originalIndices, PlanKeys planKeys, int index) {
        this.owner = owner;
        this.planId = planId;
        this.matches = matches;
        this.originalIndices = originalIndices;
        this.planKeys = planKeys;
        this.index = index;
    }

    static SearchOverlaySession open(String owner, String planId, List<LinkStep> plan,
            int portal, int index) {
        List<LinkStep> matches = new ArrayList<>();
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            if (PortalSearch.connects(plan.get(i), portal)) {
                matches.add(plan.get(i));
                positions.add(i);
            }
        }
        if (index < 0 || index >= matches.size()) return null;
        return new SearchOverlaySession(owner, planId, matches, positions, PlanKeys.forPlan(plan), index);
    }

    void moveBy(int change) {
        index = Math.max(0, Math.min(index + change, matches.size() - 1));
    }

    int index() { return index; }
    int size() { return matches.size(); }
    LinkStep current() { return matches.get(index); }
    int remainingKeys() { return planKeys.remainingForOriginAfter(originalIndices.get(index)); }
    boolean originChanged() { return LinkProgress.hasOriginChanged(matches, index); }
}
