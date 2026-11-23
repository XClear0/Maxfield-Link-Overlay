package io.github.xclear0.maxfieldoverlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.FrameLayout;

@SuppressLint("ViewConstructor") // This container is constructed in code with its panel.
final class ScaledOverlayPanel extends FrameLayout {
    private final OverlayPanel panel;
    private final int baseWidth;
    private int heightPercent = OverlaySize.DEFAULT_PERCENT;
    private int heightLimit = Integer.MAX_VALUE;

    ScaledOverlayPanel(Context context, OverlayPanel panel) {
        super(context);
        this.panel = panel;
        baseWidth = Ui.dp(context, OverlaySize.BASE_WIDTH_DP);
        panel.setPivotX(0);
        panel.setPivotY(0);
        addView(panel);
    }

    int measureNaturalHeight() {
        panel.measure(MeasureSpec.makeMeasureSpec(baseWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        return Math.max(1, panel.getMeasuredHeight());
    }

    void setHeightScale(int percent, int limit) {
        if (heightPercent != percent || heightLimit != limit) {
            heightPercent = percent;
            heightLimit = limit;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = Math.max(1, MeasureSpec.getSize(widthMeasureSpec));
        int naturalHeight = measureNaturalHeight();
        int height = resolveSize(OverlaySize.pixels(naturalHeight, heightLimit, heightPercent),
                heightMeasureSpec);
        panel.setScaleX((float) width / baseWidth);
        panel.setScaleY((float) height / naturalHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        panel.layout(0, 0, baseWidth, panel.getMeasuredHeight());
    }
}
