package io.github.xclear0.maxfieldoverlay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

// This row is assembled in code around the plan button and its delete action.
@SuppressLint("ViewConstructor")
final class SwipePlanRow extends FrameLayout {
    private final Button planButton;
    private final TextView deleteLabel;
    private final Runnable requestDeletion;
    private final int touchSlop;
    private final int swipeLimit;
    private float startX;
    private float startY;
    private boolean dragging;
    private boolean revealingDelete;

    SwipePlanRow(Context context, Button planButton, Runnable requestDeletion) {
        super(context);
        this.planButton = planButton;
        this.requestDeletion = requestDeletion;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        swipeLimit = Ui.dp(context, 116);
        setBackground(Ui.background(Ui.SURFACE_LIGHT, 12, context));
        setClipToOutline(true);

        deleteLabel = Ui.text(context, context.getString(R.string.plan_delete), 15, Ui.PRIMARY);
        deleteLabel.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        deleteLabel.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        deleteLabel.setPadding(Ui.dp(context, 16), 0, Ui.dp(context, 16), 0);
        deleteLabel.setBackgroundColor(Ui.DELETE_SURFACE);
        deleteLabel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        deleteLabel.setVisibility(INVISIBLE);
        addView(deleteLabel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(planButton, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        int dismiss = AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.getId();
        planButton.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        dismiss, context.getString(R.string.plan_delete)));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == dismiss) {
                    requestDeletion.run();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                startX = event.getX();
                startY = event.getY();
                dragging = false;
            }
            case MotionEvent.ACTION_MOVE -> {
                float distanceX = startX - event.getX();
                float distanceY = Math.abs(event.getY() - startY);
                if (Math.abs(distanceX) > touchSlop && Math.abs(distanceX) > distanceY * 1.3f) {
                    dragging = true;
                    revealingDelete = distanceX > 0;
                    getParent().requestDisallowInterceptTouchEvent(true);
                    if (revealingDelete) {
                        reveal(distanceX);
                    }
                    return true;
                }
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    // Taps and accessible delete actions belong to the child button. This handles swipes only.
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE -> {
                if (revealingDelete) {
                    reveal(startX - event.getX());
                }
            }
            case MotionEvent.ACTION_UP -> {
                float threshold = Math.min(Ui.dp(getContext(), 76), getWidth() / 3f);
                boolean complete = revealingDelete && startX - event.getX() >= threshold;
                reset();
                if (complete) {
                    requestDeletion.run();
                }
            }
            case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> reset();
        }
        return true;
    }

    private void reveal(float distance) {
        planButton.setTranslationX(-Math.max(0, Math.min(distance, swipeLimit)));
        deleteLabel.setVisibility(distance > 0 ? VISIBLE : INVISIBLE);
    }

    private void reset() {
        dragging = false;
        revealingDelete = false;
        planButton.setTranslationX(0);
        deleteLabel.setVisibility(INVISIBLE);
    }
}
