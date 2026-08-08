package io.github.xclear0.maxfieldoverlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

public final class OverlayService extends Service {
    static final String ACTION_SHOW = "io.github.xclear0.maxfieldoverlay.SHOW";
    static final String ACTION_STOP = "io.github.xclear0.maxfieldoverlay.STOP";
    static final String ACTION_RELOAD = "io.github.xclear0.maxfieldoverlay.RELOAD";

    private static final String CHANNEL_ID = "maxfield_overlay";
    private static final int NOTIFICATION_ID = 9017;
    private static volatile boolean running;

    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private View overlay;
    private TextView progressView;
    private TextView agentView;
    private TextView originView;
    private TextView destinationView;
    private Button previousButton;
    private Button nextButton;
    private List<LinkStep> steps = Collections.emptyList();
    private int index;

    private boolean isMinimized;
    private View contentView;
    private View minimizedBar;
    private int expandedWidth;

    static boolean isRunning() {
        return running;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        switch (action) {
            case ACTION_STOP -> {
                stopOverlay();
                return START_NOT_STICKY;
            }
        }

        startForeground(NOTIFICATION_ID, buildNotification(null));
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "悬浮窗权限已被关闭", Toast.LENGTH_LONG).show();
            stopOverlay();
            return START_NOT_STICKY;
        }

        reloadPlan();
        if (steps.isEmpty()) {
            Toast.makeText(this, "没有可显示的 Link", Toast.LENGTH_LONG).show();
            stopOverlay();
            return START_NOT_STICKY;
        }

        if (overlay == null) {
            showOverlay();
        }
        updateLink();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        removeOverlay();
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void reloadPlan() {
        PlanRepository.PlanData data = PlanRepository.loadPlan(this);
        steps = data.steps;
        index = PlanRepository.getIndex(this, steps.size());
    }

    private void showOverlay() {
        overlay = buildOverlayView();
        expandedWidth = Math.min(
                Ui.dp(this, 330),
                getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 24));
        windowParams = new WindowManager.LayoutParams(
                expandedWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = PlanRepository.getX(this, Ui.dp(this, 12));
        windowParams.y = PlanRepository.getY(this, Ui.dp(this, 96));
        windowParams.alpha = 0.92f;

        try {
            windowManager.addView(overlay, windowParams);
            running = true;
        } catch (RuntimeException error) {
            overlay = null;
            running = false;
            Toast.makeText(this, "无法创建悬浮窗：" + error.getMessage(), Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private View buildOverlayView() {
        FrameLayout container = new FrameLayout(this);

        // 创建最小化状态的视图 (4dp 视觉条，位于 28dp 宽度的感应区中心)
        FrameLayout barContainer = new FrameLayout(this);
        barContainer.setVisibility(View.GONE);
        barContainer.setOnClickListener(v -> expand());

        View bar = new View(this);
        bar.setBackground(Ui.background(Ui.ACCENT, 4, this));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 4), Ui.dp(this, 120));
        barParams.gravity = Gravity.CENTER;
        barContainer.addView(bar, barParams);

        minimizedBar = barContainer;
        container.addView(minimizedBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        contentView = buildContentView();
        container.addView(contentView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return container;
    }

    private View buildContentView() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(
                Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 14));
        root.setBackground(Ui.background(0xF017232E, 18, this));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setPadding(0, 0, 0, Ui.dp(this, 8));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        progressView = Ui.text(this, "", 13, Ui.ACCENT);
        progressView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        progressView.setLetterSpacing(0.04f);
        progressView.setContentDescription("拖动悬浮窗");
        header.addView(progressView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView minimize = Ui.text(this, "—", 20, Ui.SECONDARY);
        minimize.setGravity(Gravity.CENTER);
        minimize.setContentDescription("收起悬浮窗");
        minimize.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 6), 0);
        minimize.setOnClickListener(view -> minimize());
        header.addView(minimize, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView close = Ui.text(this, "×", 25, Ui.SECONDARY);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("关闭悬浮窗");
        close.setPadding(Ui.dp(this, 6), 0, 0, 0);
        close.setOnClickListener(view -> stopOverlay());
        header.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View.OnTouchListener dragListener = dragListener();
        header.setOnTouchListener(dragListener);
        progressView.setOnTouchListener(dragListener);

        agentView = Ui.text(this, "", 12, Ui.SECONDARY);
        agentView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(agentView, fullWidth());

        originView = portalView(Ui.ORIGIN);
        LinearLayout.LayoutParams originParams = fullWidth();
        originParams.topMargin = Ui.dp(this, 8);
        root.addView(originView, originParams);

        TextView arrow = Ui.text(this, "↓  LINK", 12, Ui.ACCENT);
        arrow.setGravity(Gravity.CENTER_HORIZONTAL);
        arrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        arrow.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 5));
        root.addView(arrow, fullWidth());

        destinationView = portalView(Ui.DESTINATION);
        root.addView(destinationView, fullWidth());

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams controlsParams = fullWidth();
        controlsParams.topMargin = Ui.dp(this, 12);
        root.addView(controls, controlsParams);

        previousButton = Ui.button(this, "‹  上一条", false);
        previousButton.setContentDescription("显示上一条 Link");
        previousButton.setOnClickListener(view -> moveBy(-1));
        LinearLayout.LayoutParams previousParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        previousParams.rightMargin = Ui.dp(this, 5);
        controls.addView(previousButton, previousParams);

        nextButton = Ui.button(this, "下一条  ›", true);
        nextButton.setContentDescription("显示下一条 Link");
        nextButton.setOnClickListener(view -> moveBy(1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        nextParams.leftMargin = Ui.dp(this, 5);
        controls.addView(nextButton, nextParams);

        return root;
    }

    private void minimize() {
        if (isMinimized || overlay == null) return;
        isMinimized = true;
        contentView.setVisibility(View.GONE);
        minimizedBar.setVisibility(View.VISIBLE);
        windowParams.width = Ui.dp(this, 28); // 扩大物理点击区域
        windowManager.updateViewLayout(overlay, windowParams);
    }

    private void expand() {
        if (!isMinimized || overlay == null) return;
        isMinimized = false;
        minimizedBar.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        windowParams.width = expandedWidth;
        windowManager.updateViewLayout(overlay, windowParams);
    }

    private TextView portalView(int accentColor) {
        TextView view = Ui.text(this, "", 18, Ui.PRIMARY);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setMaxLines(3);
        view.setPadding(
                Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        android.graphics.drawable.GradientDrawable background =
                Ui.background(Ui.BACKGROUND, 12, this);
        background.setStroke(Ui.dp(this, 1), accentColor);
        view.setBackground(background);
        return view;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void moveBy(int change) {
        int next = Math.max(0, Math.min(index + change, steps.size() - 1));
        if (next == index) {
            return;
        }
        index = next;
        PlanRepository.setIndex(this, index);
        updateLink();
    }

    private void updateLink() {
        if (steps.isEmpty() || progressView == null) {
            return;
        }
        index = Math.max(0, Math.min(index, steps.size() - 1));
        LinkStep step = steps.get(index);
        progressView.setText(getString(R.string.overlay_progress, step.linkNumber, index + 1, steps.size()));
        agentView.setText(getString(R.string.overlay_agent, step.agentNumber));
        originView.setText(getString(R.string.overlay_origin, step.originNumber, step.originName));
        destinationView.setText(getString(R.string.overlay_destination, step.destinationNumber, step.destinationName));
        previousButton.setEnabled(index > 0);
        previousButton.setAlpha(index > 0 ? 1f : 0.35f);
        nextButton.setEnabled(index < steps.size() - 1);
        nextButton.setAlpha(index < steps.size() - 1 ? 1f : 0.35f);
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID, buildNotification(step));
    }

    private View.OnTouchListener dragListener() {
        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                if (windowParams == null || overlay == null) {
                    return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        view.performClick();
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        int proposedX = initialX + Math.round(event.getRawX() - initialTouchX);
                        int proposedY = initialY + Math.round(event.getRawY() - initialTouchY);
                        int screenWidth = getResources().getDisplayMetrics().widthPixels;
                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        int visibleWidth = Math.max(Ui.dp(OverlayService.this, 64), overlay.getWidth());
                        windowParams.x = Math.max(
                                -visibleWidth + Ui.dp(OverlayService.this, 64),
                                Math.min(proposedX, screenWidth - Ui.dp(OverlayService.this, 64)));
                        windowParams.y = Math.max(
                                0,
                                Math.min(proposedY, screenHeight - Ui.dp(OverlayService.this, 64)));
                        windowManager.updateViewLayout(overlay, windowParams);
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        PlanRepository.savePosition(
                                OverlayService.this, windowParams.x, windowParams.y);
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        };
    }

    private void stopOverlay() {
        removeOverlay();
        running = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void removeOverlay() {
        if (overlay != null && windowManager != null) {
            try {
                windowManager.removeView(overlay);
            } catch (RuntimeException ignored) {
                // The system may already have removed the overlay after permission revocation.
            }
        }
        overlay = null;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notification_channel_description));
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(LinkStep step) {
        Intent openIntent = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(
                this,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, OverlayService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String detail = step == null
                ? "正在载入规划…"
                : step.originName + " → " + step.destinationName;
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_link)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_link),
                        getString(R.string.notification_stop),
                        stopPending).build())
                .build();
    }
}
