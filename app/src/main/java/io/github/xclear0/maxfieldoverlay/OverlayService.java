package io.github.xclear0.maxfieldoverlay;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public final class OverlayService extends Service {
    static final String ACTION_SHOW = "io.github.xclear0.maxfieldoverlay.SHOW";
    static final String ACTION_STOP = "io.github.xclear0.maxfieldoverlay.STOP";
    static final String ACTION_RELOAD = "io.github.xclear0.maxfieldoverlay.RELOAD";
    private static final String ACTION_SEARCH_RESULTS = "io.github.xclear0.maxfieldoverlay.SEARCH_RESULTS";
    private static final String ACTION_CLOSE_DISPLAYED = "io.github.xclear0.maxfieldoverlay.CLOSE_DISPLAYED";
    private static final String EXTRA_SEARCH_OWNER = "searchOwner";
    private static final String EXTRA_SEARCH_PLAN = "searchPlan";
    private static final String EXTRA_SEARCH_PORTAL = "searchPortal";
    private static final String EXTRA_SEARCH_INDEX = "searchIndex";

    private static final String CHANNEL_ID = "maxfield_overlay";
    private static final int NOTIFICATION_ID = 9017;
    private static volatile boolean running;
    // Service and overlay-related activity lifecycle callbacks run on the same main thread.
    private static WeakReference<OverlayService> activeService = new WeakReference<>(null);
    private static int visibleOverlayPages;
    private static String visibleResultsPage;
    private static String requestedSearchOwner;

    private WindowManager windowManager;
    private Context textContext;
    private final SharedPreferences.OnSharedPreferenceChangeListener languageListener =
            (preferences, key) -> refreshLanguage();
    private WindowManager.LayoutParams windowParams;
    private View overlay;
    private OverlayPanel panel;
    private List<LinkStep> steps = Collections.emptyList();
    private PlanKeys keys = PlanKeys.forPlan(steps);
    private String planId = "";
    private int index;
    private boolean normalOverlayRequested;
    private SearchOverlaySession searchResults;

    private boolean isMinimized;
    private ScaledOverlayPanel contentView;
    private View minimizedBar;
    private View visualBar;
    private int expandedWidth;
    private boolean isDraggingMinimized;
    private GestureDetector minimizedGestureDetector;

    static boolean isRunning() {
        return running;
    }

    static void setOverlayPageVisible(boolean visible) {
        visibleOverlayPages = Math.max(0, visibleOverlayPages + (visible ? 1 : -1));
        refreshOverlayVisibility();
    }

    static void setResultsPageVisible(String owner, boolean visible) {
        if (visible) visibleResultsPage = owner;
        else if (owner.equals(visibleResultsPage)) visibleResultsPage = null;
        refreshOverlayVisibility();
    }

    private static void refreshOverlayVisibility() {
        OverlayService service = activeService.get();
        if (service != null) {
            service.updateOverlayVisibility();
        }
    }

    static void showSearchResults(Context context, String owner, String plan, int portal, int index) {
        requestedSearchOwner = owner;
        try {
            context.startForegroundService(new Intent(context, OverlayService.class)
                    .setAction(ACTION_SEARCH_RESULTS)
                    .putExtra(EXTRA_SEARCH_OWNER, owner)
                    .putExtra(EXTRA_SEARCH_PLAN, plan)
                    .putExtra(EXTRA_SEARCH_PORTAL, portal)
                    .putExtra(EXTRA_SEARCH_INDEX, index));
        } catch (RuntimeException error) {
            requestedSearchOwner = null;
            throw error;
        }
    }

    static void releaseSearchResults(String owner) {
        if (owner.equals(requestedSearchOwner)) requestedSearchOwner = null;
        OverlayService service = activeService.get();
        if (service != null && service.searchResults != null && owner.equals(service.searchResults.owner)) {
            service.closeDisplayedOverlay();
        }
    }

    static void applySavedSize() {
        OverlayService service = activeService.get();
        if (service != null) {
            service.resizeOverlay();
        }
    }

    private void updateOverlayVisibility() {
        if (overlay != null) {
            boolean allowedOnResults = visibleResultsPage == null
                    || searchResults != null && searchResults.owner.equals(visibleResultsPage);
            overlay.setVisibility(visibleOverlayPages == 0 && allowedOnResults ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        activeService = new WeakReference<>(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        refreshLanguage();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppLanguage.legacyPreferences(this).registerOnSharedPreferenceChangeListener(languageListener);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshLanguage();
        resizeOverlay();
    }

    private void refreshLanguage() {
        textContext = AppLanguage.localizedContext(this);
        createNotificationChannel();
        if (overlay != null) {
            panel.setLanguage(textContext);
            updateLink();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_SHOW : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopOverlay();
            return START_NOT_STICKY;
        }
        if (ACTION_CLOSE_DISPLAYED.equals(action)) {
            closeDisplayedOverlay();
            return restartPolicy();
        }

        if (overlay == null) {
            startForeground(NOTIFICATION_ID, buildNotification(null));
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(textContext, R.string.overlay_permission_revoked, Toast.LENGTH_LONG).show();
            stopOverlay();
            return START_NOT_STICKY;
        }

        if (ACTION_SEARCH_RESULTS.equals(action) && intent != null) {
            String owner = intent.getStringExtra(EXTRA_SEARCH_OWNER);
            if (owner == null || !owner.equals(requestedSearchOwner)) {
                if (overlay == null) stopOverlay();
                return restartPolicy();
            }
            PlanRepository.PlanData plan = PlanRepository.loadPlan(this);
            SearchOverlaySession results = plan.id.equals(intent.getStringExtra(EXTRA_SEARCH_PLAN))
                    ? SearchOverlaySession.open(owner, plan.id, plan.steps,
                            intent.getIntExtra(EXTRA_SEARCH_PORTAL, 0),
                            intent.getIntExtra(EXTRA_SEARCH_INDEX, -1))
                    : null;
            if (results == null) {
                Toast.makeText(textContext, R.string.portal_search_overlay_failed, Toast.LENGTH_SHORT).show();
                if (overlay == null) stopOverlay();
                return restartPolicy();
            }
            searchResults = results;
            if (isMinimized) expand();
        } else if (ACTION_RELOAD.equals(action)) {
            if (normalOverlayRequested) reloadPlan();
            if (searchResults != null && !searchResults.planId.equals(PlanRepository.activePlanId(this))) {
                clearSearchResults();
            }
        } else {
            clearSearchResults();
            normalOverlayRequested = true;
            reloadPlan();
        }

        if (searchResults == null && (!normalOverlayRequested || steps.isEmpty())) {
            Toast.makeText(textContext, R.string.overlay_no_links, Toast.LENGTH_LONG).show();
            stopOverlay();
            return START_NOT_STICKY;
        }

        if (overlay == null) {
            showOverlay();
        }
        updateOverlayVisibility();
        updateLink();
        return restartPolicy();
    }

    private int restartPolicy() {
        return normalOverlayRequested ? START_STICKY : START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (activeService.get() == this) {
            activeService.clear();
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            AppLanguage.legacyPreferences(this).unregisterOnSharedPreferenceChangeListener(languageListener);
        }
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
        planId = data.id;
        steps = data.steps;
        keys = PlanKeys.forPlan(steps);
        index = PlanRepository.getIndex(this, planId, steps.size());
    }

    @SuppressLint("RtlHardcoded") // Window positions and edge gestures use physical left/right.
    private void showOverlay() {
        overlay = buildOverlayView();
        applySize();
        windowParams = new WindowManager.LayoutParams(
                expandedWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowParams.x = PlanRepository.getX(this, Ui.dp(this, 12));
        windowParams.y = PlanRepository.getY(this, Ui.dp(this, 96));
        windowParams.alpha = 0.92f;

        try {
            windowManager.addView(overlay, windowParams);
            updateOverlayVisibility();
            running = true;
        } catch (RuntimeException error) {
            overlay = null;
            running = false;
            Toast.makeText(
                    this,
                    textContext.getString(R.string.overlay_create_failed, error.getMessage()),
                    Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void applySize() {
        OverlaySize.Dimensions size = OverlaySize.load(this);
        var screen = getResources().getDisplayMetrics();
        int margins = Ui.dp(this, 24);
        expandedWidth = OverlaySize.pixels(Ui.dp(this, OverlaySize.BASE_WIDTH_DP),
                screen.widthPixels - margins, size.width());
        contentView.setHeightScale(size.height(), screen.heightPixels - margins);
    }

    private void resizeOverlay() {
        if (overlay == null) {
            return;
        }
        int oldWidth = expandedWidth;
        applySize();
        if (oldWidth == expandedWidth) {
            return;
        }
        if (!isMinimized) {
            windowParams.width = expandedWidth;
            int screenWidth = getResources().getDisplayMetrics().widthPixels;
            windowParams.x = Math.max(0, Math.min(windowParams.x, screenWidth - expandedWidth));
            windowManager.updateViewLayout(overlay, windowParams);
            PlanRepository.savePosition(this, windowParams.x, windowParams.y);
        } else {
            snapToEdge(OverlaySize.closerToRight(windowParams.x, windowParams.width,
                    getResources().getDisplayMetrics().widthPixels));
            windowManager.updateViewLayout(overlay, windowParams);
        }
    }

    private View buildOverlayView() {
        FrameLayout container = new FrameLayout(this);

        // 创建最小化状态的视图 (4dp 视觉条，位于 28dp 宽度的感应区中心)
        FrameLayout barContainer = new FrameLayout(this);
        barContainer.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        barContainer.setVisibility(View.GONE);

        visualBar = new View(this);
        visualBar.setBackground(Ui.background(Ui.ACCENT, 4, this));
        FrameLayout.LayoutParams barParams = new FrameLayout.LayoutParams(
                Ui.dp(this, 4), Ui.dp(this, 120));
        barParams.gravity = Gravity.CENTER;
        barContainer.addView(visualBar, barParams);

        minimizedBar = barContainer;
        minimizedBar.setOnTouchListener(minimizedTouchListener());

        container.addView(minimizedBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new OverlayPanel(this);
        panel.setLanguage(textContext);
        panel.setActions(dragListener(), this::openDestinationSearch, this::minimize,
                this::closeDisplayedOverlay, () -> moveBy(-1), () -> moveBy(1));
        contentView = new ScaledOverlayPanel(this, panel);
        container.addView(contentView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return container;
    }

    private void openDestinationSearch() {
        if (steps.isEmpty()) {
            return;
        }
        String destination = steps.get(index).destinationName;
        Intent intent = PortalSearchActivity.destinationSearchIntent(this, destination)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(textContext, R.string.overlay_search_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    private void minimize() {
        if (isMinimized || overlay == null) return;
        int[] location = new int[2];
        overlay.getLocationOnScreen(location);
        boolean right = OverlaySize.closerToRight(location[0], overlay.getWidth(),
                getResources().getDisplayMetrics().widthPixels);
        isMinimized = true;
        contentView.setVisibility(View.GONE);
        minimizedBar.setVisibility(View.VISIBLE);

        windowParams.width = Ui.dp(this, 48); // 扩大物理点击区域 (从 28dp 到 48dp)

        snapToEdge(right);
        windowManager.updateViewLayout(overlay, windowParams);
    }

    private void snapToEdge(boolean right) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int barWidth = windowParams.width;

        windowParams.x = right ? screenWidth - barWidth : 0;
        updateVisualBarGravity(right ? Gravity.END : Gravity.START);
        PlanRepository.savePosition(this, windowParams.x, windowParams.y);
    }

    private void updateVisualBarGravity(int gravity) {
        if (visualBar != null) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) visualBar.getLayoutParams();
            lp.gravity = gravity | Gravity.CENTER_VERTICAL;
            visualBar.setLayoutParams(lp);
        }
    }

    private void expand() {
        if (!isMinimized || overlay == null) return;
        isMinimized = false;
        minimizedBar.setVisibility(View.GONE);
        contentView.setVisibility(View.VISIBLE);
        windowParams.width = expandedWidth;

        // 展开时如果太靠右可能会出屏，简单处理一下
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (windowParams.x + expandedWidth > screenWidth) {
            windowParams.x = screenWidth - expandedWidth;
        }

        windowManager.updateViewLayout(overlay, windowParams);
        PlanRepository.savePosition(this, windowParams.x, windowParams.y);
    }

    private View.OnTouchListener minimizedTouchListener() {
        minimizedGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (minimizedBar != null) {
                    minimizedBar.performClick();
                }
                expand();
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                isDraggingMinimized = true;
            }
        });

        return new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                minimizedGestureDetector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        initialX = windowParams.x;
                        initialY = windowParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        if (isDraggingMinimized) {
                            int proposedX = initialX + Math.round(event.getRawX() - initialTouchX);
                            int proposedY = initialY + Math.round(event.getRawY() - initialTouchY);
                            int screenWidth = getResources().getDisplayMetrics().widthPixels;
                            int screenHeight = getResources().getDisplayMetrics().heightPixels;

                            windowParams.x = Math.max(0, Math.min(proposedX, screenWidth - windowParams.width));
                            windowParams.y = Math.max(0, Math.min(proposedY, screenHeight - Ui.dp(OverlayService.this, 64)));

                            windowManager.updateViewLayout(overlay, windowParams);
                        } else {
                            // 滑动展开逻辑: 从边缘向内滑动超过阈值即展开
                            float deltaX = event.getRawX() - initialTouchX;
                            int threshold = Ui.dp(OverlayService.this, 30);
                            int screenWidth = getResources().getDisplayMetrics().widthPixels;

                            boolean isAtLeft = windowParams.x <= 0;
                            boolean isAtRight = windowParams.x >= screenWidth - windowParams.width;

                            if ((isAtLeft && deltaX > threshold) || (isAtRight && deltaX < -threshold)) {
                                expand();
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDraggingMinimized) {
                            isDraggingMinimized = false;
                            snapToEdge(OverlaySize.closerToRight(windowParams.x, windowParams.width,
                                    getResources().getDisplayMetrics().widthPixels));
                            windowManager.updateViewLayout(overlay, windowParams);
                        }
                        return true;
                    }
                }
                return false;
            }
        };
    }

    private void moveBy(int change) {
        if (searchResults != null) {
            searchResults.moveBy(change);
            updateLink();
            return;
        }
        int next = Math.max(0, Math.min(index + change, steps.size() - 1));
        if (next == index) {
            return;
        }
        index = next;
        PlanRepository.setIndex(this, planId, index);
        updateLink();
    }

    private void updateLink() {
        if (overlay == null || panel == null) {
            return;
        }
        LinkStep step;
        boolean originChanged;
        if (searchResults != null) {
            step = searchResults.current();
            originChanged = searchResults.originChanged();
            panel.showLink(step, searchResults.index(), searchResults.size(), searchResults.remainingKeys(),
                    originChanged, true);
        } else {
            if (steps.isEmpty()) return;
            index = Math.max(0, Math.min(index, steps.size() - 1));
            step = steps.get(index);
            originChanged = LinkProgress.hasOriginChanged(steps, index);
            panel.showLink(step, index, steps.size(), keys.remainingForOriginAfter(index), originChanged);
        }
        int accent = originChanged ? Ui.ORIGIN_CHANGE_ACCENT : Ui.ACCENT;
        visualBar.setBackground(Ui.background(accent, 4, this));
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

    private void clearSearchResults() {
        if (searchResults != null && searchResults.owner.equals(requestedSearchOwner)) {
            requestedSearchOwner = null;
        }
        searchResults = null;
    }

    private void closeDisplayedOverlay() {
        if (searchResults != null && normalOverlayRequested && !steps.isEmpty()) {
            clearSearchResults();
            updateOverlayVisibility();
            updateLink();
        } else {
            stopOverlay();
        }
    }

    private void stopOverlay() {
        clearSearchResults();
        normalOverlayRequested = false;
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
                textContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(textContext.getString(R.string.notification_channel_description));
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
        stopIntent.setAction(ACTION_CLOSE_DISPLAYED);
        PendingIntent stopPending = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String detail = step == null
                ? textContext.getString(R.string.notification_loading)
                : step.originName + " → " + step.destinationName;
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_link)
                .setContentTitle(textContext.getString(R.string.notification_title))
                .setContentText(detail)
                .setStyle(new Notification.BigTextStyle().bigText(detail))
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .addAction(new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_link),
                        textContext.getString(R.string.notification_stop),
                        stopPending).build())
                .build();
    }
}
