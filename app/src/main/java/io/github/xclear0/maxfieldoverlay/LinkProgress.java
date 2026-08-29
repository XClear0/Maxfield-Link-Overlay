package io.github.xclear0.maxfieldoverlay;

import java.util.List;

final class LinkProgress {
    private LinkProgress() {}

    static boolean hasOriginChanged(List<LinkStep> steps, int index) {
        if (steps == null || index <= 0 || index >= steps.size()) {
            return false;
        }
        return steps.get(index).originNumber != steps.get(index - 1).originNumber;
    }
}
