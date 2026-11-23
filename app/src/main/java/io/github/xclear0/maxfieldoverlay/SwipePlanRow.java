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
import android.widget.LinearLayout;

import java.util.function.Consumer;

// This row is assembled in code around the plan button and its actions.
@SuppressLint("ViewConstructor")
final class SwipePlanRow extends FrameLayout {
    private final LinearLayout actions;
    private final Consumer<SwipePlanRow> onOpened;
    private final int touchSlop;
    private final int actionWidth;
    private float startX;
    private float startY;
    private boolean dragging;
    private boolean open;

    SwipePlanRow(Context context, Button planButton, Runnable requestRename,
            Runnable requestDeletion, Consumer<SwipePlanRow> onOpened) {
        super(context);
        this.onOpened = onOpened;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        actionWidth = Ui.dp(context, 176);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        planButton.setLayoutDirection(context.getResources().getConfiguration().getLayoutDirection());
        setBackground(Ui.background(Ui.SURFACE_LIGHT, 12, context));
        setClipToOutline(true);

        actions = new LinearLayout(context);
        actions.setVisibility(INVISIBLE);
        actions.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        addAction(R.string.rename_action, 0xFF31586A, requestRename);
        addAction(R.string.delete_action, Ui.DELETE_SURFACE, requestDeletion);
        addView(planButton, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(actions, new LayoutParams(actionWidth, LayoutParams.MATCH_PARENT, Gravity.END));

        int delete = AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS.getId();
        planButton.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        R.id.plan_accessibility_rename, context.getString(R.string.plan_rename)));
                info.addAction(new AccessibilityNodeInfo.AccessibilityAction(
                        delete, context.getString(R.string.plan_delete)));
            }

            @Override
            public boolean performAccessibilityAction(View host, int action, Bundle args) {
                if (action == R.id.plan_accessibility_rename) {
                    requestRename.run();
                    return true;
                }
                if (action == delete) {
                    requestDeletion.run();
                    return true;
                }
                return super.performAccessibilityAction(host, action, args);
            }
        });
    }

    private void addAction(int label, int color, Runnable onClick) {
        Context context = getContext();
        Button action = Ui.button(context, context.getString(label), false);
        action.setPadding(Ui.dp(context, 4), 0, Ui.dp(context, 4), 0);
        action.setBackgroundColor(color);
        action.setOnClickListener(view -> onClick.run());
        actions.addView(action, new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
    }

    boolean isOpen() {
        return open;
    }

    void closeActions() {
        settle(false);
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
                    getParent().requestDisallowInterceptTouchEvent(true);
                    reveal((open ? actionWidth : 0) + distanceX);
                    return true;
                }
            }
        }
        return super.onInterceptTouchEvent(event);
    }

    @Override
    // The child buttons handle taps and accessible actions. This handles swipes only.
    @SuppressLint("ClickableViewAccessibility")
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) {
            return super.onTouchEvent(event);
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE -> reveal((open ? actionWidth : 0) + startX - event.getX());
            case MotionEvent.ACTION_UP -> {
                float threshold = Math.min(Ui.dp(getContext(), 64), getWidth() / 3f);
                float distance = startX - event.getX();
                settle(open ? distance > -threshold : distance >= threshold);
            }
            case MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> settle(open);
        }
        return true;
    }

    private void reveal(float distance) {
        // Slide in from the right while keeping the affected plan's name in view.
        actions.setTranslationX(actionWidth - Math.max(0, Math.min(distance, actionWidth)));
        actions.setVisibility(distance > 0 ? VISIBLE : INVISIBLE);
    }

    private void settle(boolean showActions) {
        dragging = false;
        boolean justOpened = showActions && !open;
        open = showActions;
        actions.setImportantForAccessibility(open ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        reveal(open ? actionWidth : 0);
        if (justOpened) {
            onOpened.accept(this);
        }
    }
}
