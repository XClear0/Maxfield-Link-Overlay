package io.github.xclear0.maxfieldoverlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

final class OverlayPanel extends LinearLayout {
    private Context textContext;
    private final LinearLayout header;
    private final TextView progressView;
    private final TextView agentView;
    private final TextView originView;
    private final TextView keysView;
    private final TextView destinationView;
    private final ImageButton searchControl;
    private final TextView minimizeControl;
    private final TextView closeControl;
    private final TextView linkArrow;
    private final Button previousButton;
    private final Button nextButton;

    OverlayPanel(Context context) {
        super(context);
        LinearLayout root = this;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                Ui.dp(context, 14), Ui.dp(context, 10), Ui.dp(context, 14), Ui.dp(context, 14));
        root.setBackground(Ui.background(Ui.OVERLAY_SURFACE, 18, context));

        header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 0, 0, Ui.dp(context, 8));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressView = Ui.text(context, "", 13, Ui.ACCENT);
        progressView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        progressView.setLetterSpacing(0.04f);
        header.addView(progressView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        searchControl = new ImageButton(context);
        searchControl.setImageResource(R.drawable.ic_overlay_search);
        searchControl.setColorFilter(Ui.SECONDARY);
        searchControl.setScaleType(ImageView.ScaleType.CENTER);
        searchControl.setBackgroundColor(Color.TRANSPARENT);
        searchControl.setPadding(0, 0, 0, 0);

        header.addView(searchControl, new LinearLayout.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32)));

        minimizeControl = Ui.text(context, "—", 20, Ui.SECONDARY);
        minimizeControl.setGravity(Gravity.CENTER);
        minimizeControl.setPadding(Ui.dp(context, 12), 0, Ui.dp(context, 6), 0);

        header.addView(minimizeControl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        closeControl = Ui.text(context, "×", 25, Ui.SECONDARY);
        closeControl.setGravity(Gravity.CENTER);
        closeControl.setPadding(Ui.dp(context, 6), 0, 0, 0);

        header.addView(closeControl, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        agentView = Ui.text(context, "", 12, Ui.SECONDARY);
        agentView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(agentView, fullWidth());

        originView = portalView(Ui.ORIGIN);
        LinearLayout.LayoutParams originParams = fullWidth();
        originParams.topMargin = Ui.dp(context, 8);
        root.addView(originView, originParams);

        keysView = Ui.text(context, "", 12, Ui.ORIGIN);
        LinearLayout.LayoutParams keyParams = fullWidth();
        keyParams.topMargin = Ui.dp(context, 4);
        root.addView(keysView, keyParams);

        linkArrow = Ui.text(context, "", 12, Ui.ACCENT);
        linkArrow.setGravity(Gravity.CENTER_HORIZONTAL);
        linkArrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        linkArrow.setPadding(0, Ui.dp(context, 5), 0, Ui.dp(context, 5));
        root.addView(linkArrow, fullWidth());

        destinationView = portalView(Ui.DESTINATION);
        root.addView(destinationView, fullWidth());

        LinearLayout controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlsParams = fullWidth();
        controlsParams.topMargin = Ui.dp(context, 12);
        root.addView(controls, controlsParams);

        previousButton = Ui.button(context, "", false);

        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        previousParams.rightMargin = Ui.dp(context, 5);
        controls.addView(previousButton, previousParams);

        nextButton = Ui.button(context, "", true);

        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nextParams.leftMargin = Ui.dp(context, 5);
        controls.addView(nextButton, nextParams);
    }

    void setActions(View.OnTouchListener drag, Runnable search, Runnable minimize,
            Runnable close, Runnable previous, Runnable next) {
        header.setOnTouchListener(drag);
        progressView.setOnTouchListener(drag);
        searchControl.setOnClickListener(view -> search.run());
        minimizeControl.setOnClickListener(view -> minimize.run());
        closeControl.setOnClickListener(view -> close.run());
        previousButton.setOnClickListener(view -> previous.run());
        nextButton.setOnClickListener(view -> next.run());
    }

    void setLanguage(Context textContext) {
        this.textContext = textContext;
        progressView.setContentDescription(textContext.getText(R.string.overlay_drag_description));
        searchControl.setContentDescription(textContext.getText(R.string.overlay_search_destination));
        minimizeControl.setContentDescription(textContext.getText(R.string.overlay_minimize_description));
        closeControl.setContentDescription(textContext.getText(R.string.overlay_close_description));
        linkArrow.setText(textContext.getText(R.string.overlay_link_arrow));
        previousButton.setText(textContext.getText(R.string.overlay_previous));
        previousButton.setContentDescription(textContext.getText(R.string.overlay_previous_description));
        nextButton.setText(textContext.getText(R.string.overlay_next));
        nextButton.setContentDescription(textContext.getText(R.string.overlay_next_description));
    }

    void showLink(LinkStep step, int index, int count, int remainingKeys, boolean originChanged) {
        showLink(step, index, count, remainingKeys, originChanged, false);
    }

    void showLink(LinkStep step, int index, int count, int remainingKeys,
            boolean originChanged, boolean searchResults) {
        int surface = originChanged ? Ui.ORIGIN_CHANGE_SURFACE : Ui.OVERLAY_SURFACE;
        int accent = originChanged ? Ui.ORIGIN_CHANGE_ACCENT : Ui.ACCENT;
        setBackground(Ui.background(surface, 18, getContext()));
        progressView.setTextColor(accent);
        progressView.setText(textContext.getString(
                searchResults ? R.string.overlay_search_progress : R.string.overlay_progress,
                step.linkNumber, index + 1, count));
        searchControl.setVisibility(searchResults ? View.GONE : View.VISIBLE);
        agentView.setText(textContext.getString(R.string.overlay_agent, step.agentNumber));
        originView.setText(textContext.getString(R.string.overlay_origin, step.originNumber, step.originName));
        keysView.setText(textContext.getString(R.string.overlay_origin_keys, remainingKeys));
        destinationView.setText(textContext.getString(
                R.string.overlay_destination, step.destinationNumber, step.destinationName));
        previousButton.setEnabled(index > 0);
        previousButton.setAlpha(index > 0 ? 1f : 0.35f);
        nextButton.setEnabled(index < count - 1);
        nextButton.setAlpha(index < count - 1 ? 1f : 0.35f);
    }

    private TextView portalView(int accentColor) {
        Context context = getContext();
        TextView view = Ui.text(context, "", 18, Ui.PRIMARY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setMaxLines(3);
        view.setPadding(
                Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
        android.graphics.drawable.GradientDrawable background =
                Ui.background(Ui.BACKGROUND, 12, context);
        background.setStroke(Ui.dp(context, 1), accentColor);
        view.setBackground(background);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
