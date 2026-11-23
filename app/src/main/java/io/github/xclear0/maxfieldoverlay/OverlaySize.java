package io.github.xclear0.maxfieldoverlay;

import android.content.Context;

final class OverlaySize {
    static final int BASE_WIDTH_DP = 330;
    static final int DEFAULT_PERCENT = 100;
    static final int MIN_PERCENT = 65;
    static final int MAX_PERCENT = 140;

    private static final String PREFS = "overlay_display";
    private static final String LEGACY_SIZE = "size_percent";
    private static final String WIDTH = "width_percent";
    private static final String HEIGHT = "height_percent";

    private OverlaySize() {}

    record Dimensions(int width, int height) {
        Dimensions {
            width = clamp(width);
            height = clamp(height);
        }
    }

    record Axis(int position, int percent) {}

    static Dimensions defaults() {
        return new Dimensions(DEFAULT_PERCENT, DEFAULT_PERCENT);
    }

    static Dimensions load(Context context) {
        var prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int legacy = prefs.getInt(LEGACY_SIZE, DEFAULT_PERCENT);
        return new Dimensions(prefs.getInt(WIDTH, legacy), prefs.getInt(HEIGHT, legacy));
    }

    static void save(Context context, Dimensions size) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putInt(WIDTH, size.width()).putInt(HEIGHT, size.height()).remove(LEGACY_SIZE).apply();
    }

    static int clamp(int percent) {
        return Math.max(MIN_PERCENT, Math.min(percent, MAX_PERCENT));
    }

    static int pixels(int natural, int limit, int percent) {
        return Math.max(1, Math.min(Math.round(natural * clamp(percent) / 100f), limit));
    }

    static int boundedPosition(int position, int available, int length) {
        return Math.max(0, Math.min(position, available - length));
    }

    static Axis resizeEdge(int position, int initialPixels, int natural,
            int available, int edge, float movement) {
        int oppositeEdge = edge < 0 ? position + initialPixels : position;
        int limit = edge < 0 ? oppositeEdge : available - oppositeEdge;
        int requested = Math.round((initialPixels + edge * movement) * 100 / natural);
        int percent = Math.min(clamp(requested), clamp(limit * 100 / natural));
        int length = pixels(natural, available, percent);
        int offset = edge < 0 ? oppositeEdge - length : oppositeEdge;
        return new Axis(boundedPosition(offset, available, length), percent);
    }

    static boolean closerToRight(int left, int width, int screenWidth) {
        int leftGap = Math.max(0, left);
        int rightGap = Math.max(0, screenWidth - left - width);
        return rightGap < leftGap;
    }
}
