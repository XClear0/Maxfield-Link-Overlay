package io.github.xclear0.maxfieldoverlay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class PortalSearch {
    private PortalSearch() {}

    static List<Portal> findPortals(List<LinkStep> steps, String query) {
        String keyword = query.trim().toLowerCase(Locale.ROOT);
        if (keyword.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Integer, Portal> matches = new LinkedHashMap<>();
        for (LinkStep step : steps) {
            addMatch(matches, keyword, step.originNumber, step.originName);
            addMatch(matches, keyword, step.destinationNumber, step.destinationName);
        }
        return new ArrayList<>(matches.values());
    }

    private static void addMatch(Map<Integer, Portal> matches, String keyword, int number, String name) {
        if (!matches.containsKey(number) && name.toLowerCase(Locale.ROOT).contains(keyword)) {
            matches.put(number, new Portal(number, name));
        }
    }

    static List<LinkStep> findLinks(List<LinkStep> steps, int portalNumber) {
        List<LinkStep> links = new ArrayList<>();
        for (LinkStep step : steps) {
            if (step.originNumber == portalNumber || step.destinationNumber == portalNumber) {
                links.add(step);
            }
        }
        return links;
    }

    static final class Portal {
        final int number;
        final String name;

        Portal(int number, String name) {
            this.number = number;
            this.name = name;
        }
    }
}
