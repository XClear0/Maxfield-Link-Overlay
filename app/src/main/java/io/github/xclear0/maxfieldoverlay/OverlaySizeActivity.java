package io.github.xclear0.maxfieldoverlay;

import android.app.Activity;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

public final class OverlaySizeActivity extends Activity {
    private static final String STATE_WIDTH = "sizeWidth";
    private static final String STATE_HEIGHT = "sizeHeight";
    private static final String STATE_LEFT = "previewLeft";
    private static final String STATE_TOP = "previewTop";

    private FrameLayout stage;
    private PopupWindow popup;
    private TextView sizeLabel;
    private OverlaySize.Dimensions size;
    private int naturalHeight;
    private int previewMargin;
    private int previewLeft = -1;
    private int previewTop = -1;
    private int previewBaseWidth;
    private int previewBaseHeight;
    private boolean started;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.localizedContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        OverlaySize.Dimensions saved = OverlaySize.load(this);
        size = savedInstanceState == null ? saved : new OverlaySize.Dimensions(
                savedInstanceState.getInt(STATE_WIDTH, saved.width()),
                savedInstanceState.getInt(STATE_HEIGHT, saved.height()));
        if (savedInstanceState != null) {
            previewLeft = savedInstanceState.getInt(STATE_LEFT, -1);
            previewTop = savedInstanceState.getInt(STATE_TOP, -1);
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        }

        addLabel(root, R.string.overlay_size_title, 26, Ui.PRIMARY);
        addLabel(root, R.string.overlay_size_hint, 14, Ui.SECONDARY);
        addSpaced(root, buildSizeControls());

        stage = new FrameLayout(this);
        stage.setBackground(Ui.background(Ui.SURFACE, 16, this));
        previewMargin = Ui.dp(this, 12);
        popup = new PopupWindow(buildPreview(), baseWidth(), naturalHeight, false);
        popup.setAnimationStyle(0);
        stage.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                renderPreview());
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        stageParams.setMargins(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), 0);
        root.addView(stage, stageParams);
        root.addView(buildFooter());
        setContentView(root);
        renderPreview();
    }

    @Override
    protected void onStart() {
        super.onStart();
        OverlayService.setOverlayPageVisible(true);
        started = true;
        stage.post(this::renderPreview);
    }

    @Override
    protected void onStop() {
        started = false;
        popup.dismiss();
        OverlayService.setOverlayPageVisible(false);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_WIDTH, size.width());
        outState.putInt(STATE_HEIGHT, size.height());
        outState.putInt(STATE_LEFT, previewLeft);
        outState.putInt(STATE_TOP, previewTop);
    }

    private ResizeFrame buildPreview() {
        OverlayPanel panel = new OverlayPanel(this);
        panel.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        panel.setLanguage(this);
        LinkStep sample = new LinkStep(1, 1, 1, getString(R.string.overlay_size_sample_origin),
                2, getString(R.string.overlay_size_sample_destination));
        panel.showLink(sample, 0, 3, 2, false);
        ScaledOverlayPanel scaledPanel = new ScaledOverlayPanel(this, panel);
        naturalHeight = scaledPanel.measureNaturalHeight();
        ResizeFrame frame = new ResizeFrame(this);
        frame.addView(scaledPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        frame.setContentDescription(getString(R.string.overlay_size_handle));
        return frame;
    }

    private LinearLayout buildSizeControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        sizeLabel = Ui.text(this, "", 14, Ui.ACCENT);
        sizeLabel.setFocusable(true);
        sizeLabel.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int width = size.width();
            int height = size.height();
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_LEFT -> width -= 5;
                case KeyEvent.KEYCODE_DPAD_RIGHT -> width += 5;
                case KeyEvent.KEYCODE_DPAD_UP -> height -= 5;
                case KeyEvent.KEYCODE_DPAD_DOWN -> height += 5;
                default -> { return false; }
            }
            size = new OverlaySize.Dimensions(width, height);
            renderPreview();
            return true;
        });
        controls.addView(sizeLabel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button reset = Ui.button(this, getString(R.string.overlay_size_restore_default), false);
        reset.setOnClickListener(view -> {
            size = OverlaySize.defaults();
            previewLeft = previewTop = -1;
            renderPreview();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(Ui.dp(this, 8));
        controls.addView(reset, params);
        return controls;
    }

    private int widthLimit() {
        return Math.max(1, stage.getWidth() - 2 * previewMargin);
    }

    private int heightLimit() {
        return Math.max(1, stage.getHeight() - 2 * previewMargin);
    }

    @SuppressLint("RtlHardcoded") // Coordinates describe physical edges of the preview area.
    private void renderPreview() {
        sizeLabel.setText(getString(R.string.overlay_size_dimensions, size.width(), size.height()));
        if (!started || stage.getWindowToken() == null
                || stage.getWidth() <= 2 * previewMargin || stage.getHeight() <= 2 * previewMargin) {
            return;
        }

        // On compact screens, fit the demo into the canvas without changing the saved scale.
        float fit = Math.min(1f, Math.min(0.92f * widthLimit() / baseWidth(),
                0.92f * heightLimit() / naturalHeight));
        previewBaseWidth = Math.max(1, Math.round(baseWidth() * fit));
        previewBaseHeight = Math.max(1, Math.round(naturalHeight * fit));
        int width = OverlaySize.pixels(previewBaseWidth, widthLimit(), size.width());
        int height = OverlaySize.pixels(previewBaseHeight, heightLimit(), size.height());
        previewLeft = previewLeft == -1 ? (widthLimit() - width) / 2
                : OverlaySize.boundedPosition(previewLeft, widthLimit(), width);
        previewTop = previewTop == -1 ? (heightLimit() - height) / 2
                : OverlaySize.boundedPosition(previewTop, heightLimit(), height);

        // Position the popup inside the canvas, relative to the anchor's bottom edge.
        int x = previewMargin + previewLeft;
        int y = previewMargin + previewTop - stage.getHeight();
        if (popup.isShowing()) {
            popup.update(stage, x, y, width, height);
        } else {
            popup.setWidth(width);
            popup.setHeight(height);
            popup.showAsDropDown(stage, x, y, Gravity.LEFT);
        }
    }

    private final class ResizeFrame extends FrameLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int touchBand;
        private final int touchSlop;
        private int horizontalEdge;
        private int verticalEdge;
        private float dragX;
        private float dragY;
        private int initialWidth;
        private int initialHeight;
        private int initialLeft;
        private int initialTop;
        private OverlaySize.Dimensions initialSize;
        private boolean dragging;

        ResizeFrame(Context context) {
            super(context);
            touchBand = Ui.dp(context, 24);
            touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            float inset = Ui.dp(getContext(), 4);
            float right = getWidth() - inset;
            float bottom = getHeight() - inset;
            paint.setColor(Ui.ACCENT);
            paint.setStrokeWidth(Ui.dp(getContext(), 1));
            paint.setStyle(Paint.Style.STROKE);
            canvas.drawRoundRect(inset, inset, right, bottom, inset, inset, paint);
            paint.setStyle(Paint.Style.FILL);
            float radius = Ui.dp(getContext(), 3);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            for (float x : new float[]{inset, cx, right}) {
                for (float y : new float[]{inset, cy, bottom}) {
                    if (x != cx || y != cy) canvas.drawCircle(x, y, radius, paint);
                }
            }
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (event.getActionMasked() != MotionEvent.ACTION_DOWN) return false;
            horizontalEdge = edge(event.getX(), getWidth());
            verticalEdge = edge(event.getY(), getHeight());
            return true;
        }

        private int edge(float position, int length) {
            int band = Math.min(touchBand, length / 3);
            return position < band ? -1 : position > length - band ? 1 : 0;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN -> {
                    dragX = event.getRawX();
                    dragY = event.getRawY();
                    initialWidth = getWidth();
                    initialHeight = getHeight();
                    initialLeft = previewLeft;
                    initialTop = previewTop;
                    initialSize = size;
                    dragging = false;
                    return true;
                }
                case MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                    float dx = event.getRawX() - dragX;
                    float dy = event.getRawY() - dragY;
                    if (!dragging && Math.max(Math.abs(dx), Math.abs(dy)) < touchSlop) {
                        if (event.getActionMasked() == MotionEvent.ACTION_UP) performClick();
                        return true;
                    }
                    dragging = true;
                    if (horizontalEdge == 0 && verticalEdge == 0) {
                        previewLeft = OverlaySize.boundedPosition(initialLeft + Math.round(dx), widthLimit(), initialWidth);
                        previewTop = OverlaySize.boundedPosition(initialTop + Math.round(dy), heightLimit(), initialHeight);
                    } else {
                        OverlaySize.Axis x = horizontalEdge == 0 ? new OverlaySize.Axis(initialLeft, initialSize.width())
                                : OverlaySize.resizeEdge(initialLeft, initialWidth, previewBaseWidth, widthLimit(),
                                        horizontalEdge, dx);
                        OverlaySize.Axis y = verticalEdge == 0 ? new OverlaySize.Axis(initialTop, initialSize.height())
                                : OverlaySize.resizeEdge(initialTop, initialHeight, previewBaseHeight, heightLimit(),
                                        verticalEdge, dy);
                        size = new OverlaySize.Dimensions(x.percent(), y.percent());
                        previewLeft = x.position();
                        previewTop = y.position();
                    }
                    renderPreview();
                    return true;
                }
                case MotionEvent.ACTION_CANCEL -> {
                    size = initialSize;
                    previewLeft = initialLeft;
                    previewTop = initialTop;
                    renderPreview();
                    return true;
                }
                default -> { return true; }
            }
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }
    }

    private int baseWidth() {
        return Ui.dp(this, OverlaySize.BASE_WIDTH_DP);
    }

    private LinearLayout buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 12));
        Button cancel = Ui.button(this, getString(android.R.string.cancel), false);
        cancel.setOnClickListener(view -> finish());
        footer.addView(cancel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button save = Ui.button(this, getString(R.string.save_action), true);
        save.setOnClickListener(view -> {
            OverlaySize.save(this, size);
            OverlayService.applySavedSize();
            finish();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        params.setMarginStart(Ui.dp(this, 8));
        footer.addView(save, params);
        return footer;
    }

    private void addLabel(LinearLayout content, int string, int size, int color) {
        TextView label = Ui.text(this, getString(string), size, color);
        label.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        addSpaced(content, label);
    }

    private void addSpaced(LinearLayout content, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 12);
        content.addView(view, params);
    }
}
