package io.github.xclear0.maxfieldoverlay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class PlanKeys {
    private final int[] remainingForOrigin;

    private PlanKeys(int[] remainingForOrigin) {
        this.remainingForOrigin = remainingForOrigin;
    }

    static int totalForPlan(List<LinkStep> steps) {
        return steps.size();
    }

    static PlanKeys forPlan(List<LinkStep> steps) {
        Map<Integer, Integer> keysByDestination = new HashMap<>();
        int[] remaining = new int[steps.size()];
        for (int i = steps.size() - 1; i >= 0; i--) {
            LinkStep step = steps.get(i);
            remaining[i] = keysByDestination.getOrDefault(step.originNumber, 0);
            keysByDestination.merge(step.destinationNumber, 1, Integer::sum);
        }
        return new PlanKeys(remaining);
    }

    int remainingForOriginAfter(int index) {
        return remainingForOrigin[index];
    }
}
