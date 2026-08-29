package io.github.xclear0.maxfieldoverlay;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

final class Ui {
    static final int BACKGROUND = 0xFF101820;
    static final int SURFACE = 0xFF17232E;
    static final int SURFACE_LIGHT = 0xFF213342;
    static final int ACCENT = 0xFF2ED6C3;
    static final int OVERLAY_SURFACE = 0xF017232E;
    static final int ORIGIN_CHANGE_SURFACE = 0xF0682F17;
    static final int ORIGIN_CHANGE_ACCENT = 0xFFFFC857;
    static final int PRIMARY = 0xFFF4F7FA;
    static final int SECONDARY = 0xFFAABBC8;
    static final int ORIGIN = 0xFF65D1FF;
    static final int DESTINATION = 0xFF7BE495;

    private Ui() {}

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable background(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, Math.round(radiusDp)));
        return drawable;
    }

    static TextView text(Context context, String value, float sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setLineSpacing(0f, 1.08f);
        return view;
    }

    static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? BACKGROUND : PRIMARY);
        button.setBackground(background(primary ? ACCENT : SURFACE_LIGHT, 12, context));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(context, 16), dp(context, 12), dp(context, 16), dp(context, 12));
        button.setStateListAnimator(null);
        return button;
    }

    static void margin(View view, int left, int top, int right, int bottom) {
        if (view.getLayoutParams() instanceof android.view.ViewGroup.MarginLayoutParams) {
            android.view.ViewGroup.MarginLayoutParams params =
                    (android.view.ViewGroup.MarginLayoutParams) view.getLayoutParams();
            params.setMargins(left, top, right, bottom);
            view.setLayoutParams(params);
        }
    }
}
