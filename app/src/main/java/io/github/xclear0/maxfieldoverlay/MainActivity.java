package io.github.xclear0.maxfieldoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_PLAN = 40;
    private static final int REQUEST_OVERLAY = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;

    private TextView planStatus;
    private TextView preview;
    private TextView permissionStatus;
    private Button startButton;
    private Button stopButton;
    private boolean startWhenPermissionGranted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContent());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }

        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateScreen();
        if (startWhenPermissionGranted && Settings.canDrawOverlays(this)) {
            startWhenPermissionGranted = false;
            startOverlay();
        }
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);
        int horizontal = Ui.dp(this, 20);
        int vertical = Ui.dp(this, 14);
        root.setPadding(horizontal, vertical, horizontal, vertical);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets cutout = insets.getInsets(WindowInsets.Type.displayCutout());
                view.setPadding(horizontal, vertical + cutout.top, horizontal, vertical + cutout.bottom);
                return insets;
            });
        }

        TextView eyebrow = Ui.text(this, "INGRESS OPERATION TOOL", 12, Ui.ACCENT);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);

        TextView title = Ui.text(this, "Maxfield\nLink Overlay", 28, Ui.PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = Ui.dp(this, 4);
        root.addView(title, titleParams);

        TextView description = Ui.text(
                this,
                "导入 Maxfield 的行动清单，在游戏上方逐条显示 Link 起点和终点。",
                14,
                Ui.SECONDARY);
        LinearLayout.LayoutParams descriptionParams = fullWidth();
        descriptionParams.topMargin = Ui.dp(this, 6);
        root.addView(description, descriptionParams);

        LinearLayout planCard = card();
        LinearLayout.LayoutParams planCardParams = fullWidth();
        planCardParams.topMargin = Ui.dp(this, 12);
        root.addView(planCard, planCardParams);

        TextView planLabel = sectionLabel("1  导入规划");
        planCard.addView(planLabel);

        planStatus = Ui.text(this, "尚未导入规划", 15, Ui.SECONDARY);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = Ui.dp(this, 10);
        planCard.addView(planStatus, statusParams);

        Button importButton = Ui.button(this, "选择 Maxfield 规划文件", true);
        importButton.setContentDescription("导入 Maxfield 规划文件");
        importButton.setOnClickListener(view -> choosePlan());
        LinearLayout.LayoutParams importParams = fullWidth();
        importParams.topMargin = Ui.dp(this, 16);
        planCard.addView(importButton, importParams);

        preview = Ui.text(this, "", 13, Ui.PRIMARY);
        preview.setBackground(Ui.background(Ui.BACKGROUND, 10, this));
        preview.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams previewParams = fullWidth();
        previewParams.topMargin = Ui.dp(this, 12);
        planCard.addView(preview, previewParams);

        Button resetButton = Ui.button(this, "从第一条 Link 开始", false);
        resetButton.setOnClickListener(view -> {
            PlanRepository.setIndex(this, 0);
            if (OverlayService.isRunning()) {
                sendOverlayAction(OverlayService.ACTION_RELOAD);
            }
            updateScreen();
        });
        LinearLayout.LayoutParams resetParams = fullWidth();
        resetParams.topMargin = Ui.dp(this, 10);
        planCard.addView(resetButton, resetParams);

        LinearLayout overlayCard = card();
        LinearLayout.LayoutParams overlayCardParams = fullWidth();
        overlayCardParams.topMargin = Ui.dp(this, 8);
        root.addView(overlayCard, overlayCardParams);

        overlayCard.addView(sectionLabel("2  开启悬浮窗"));
        permissionStatus = Ui.text(this, "", 15, Ui.SECONDARY);
        LinearLayout.LayoutParams permissionParams = fullWidth();
        permissionParams.topMargin = Ui.dp(this, 10);
        overlayCard.addView(permissionStatus, permissionParams);

        Button permissionButton = Ui.button(this, "授予悬浮窗权限", false);
        permissionButton.setOnClickListener(view -> openOverlaySettings(false));
        LinearLayout.LayoutParams permissionButtonParams = fullWidth();
        permissionButtonParams.topMargin = Ui.dp(this, 14);
        overlayCard.addView(permissionButton, permissionButtonParams);

        startButton = Ui.button(this, "在游戏上方显示", true);
        startButton.setOnClickListener(view -> prepareAndStartOverlay());
        LinearLayout.LayoutParams startParams = fullWidth();
        startParams.topMargin = Ui.dp(this, 10);
        overlayCard.addView(startButton, startParams);

        stopButton = Ui.button(this, "关闭悬浮窗", false);
        stopButton.setOnClickListener(view -> sendOverlayAction(OverlayService.ACTION_STOP));
        LinearLayout.LayoutParams stopParams = fullWidth();
        stopParams.topMargin = Ui.dp(this, 10);
        overlayCard.addView(stopButton, stopParams);

        TextView tips = Ui.text(
                this,
                "支持文件\n"
                        + "• agent_assignments.txt（推荐，多 Agent 总顺序）\n"
                        + "• agent_N_assignment.txt（只看某位 Agent）\n"
                        + "• agent_assignments.csv\n\n"
                        + "悬浮窗顶部可拖动；上一条/下一条会自动保存当前位置。"
                        + "切回本应用可重置进度或关闭悬浮窗。",
                14,
                Ui.SECONDARY);
        LinearLayout.LayoutParams tipsParams = fullWidth();
        tipsParams.topMargin = Ui.dp(this, 10);
        root.addView(tips, tipsParams);

        return root;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(
                Ui.dp(this, 14), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        card.setBackground(Ui.background(Ui.SURFACE, 16, this));
        return card;
    }

    private TextView sectionLabel(String text) {
        TextView label = Ui.text(this, text, 16, Ui.PRIMARY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return label;
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void choosePlan() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "text/plain", "text/csv", "application/csv", "application/octet-stream"
        });
        startActivityForResult(intent, REQUEST_PLAN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PLAN || resultCode != RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }

        try (InputStream input = getContentResolver().openInputStream(uri)) {
            List<LinkStep> steps = PlanParser.parse(input);
            String displayName = queryDisplayName(uri);
            PlanRepository.savePlan(this, displayName, steps);
            if (OverlayService.isRunning()) {
                sendOverlayAction(OverlayService.ACTION_RELOAD);
            }
            Toast.makeText(this, "已导入 " + steps.size() + " 条 Link", Toast.LENGTH_LONG).show();
            updateScreen();
        } catch (IOException | SecurityException error) {
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String queryDisplayName(Uri uri) {
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (column >= 0) {
                    String value = cursor.getString(column);
                    if (value != null && !value.trim().isEmpty()) {
                        return value;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A document provider may not expose a display name.
        }
        return "Maxfield 规划";
    }

    private void prepareAndStartOverlay() {
        PlanRepository.PlanData plan = PlanRepository.loadPlan(this);
        if (plan.steps.isEmpty()) {
            Toast.makeText(this, "请先导入规划文件", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings(true);
            return;
        }
        requestNotificationPermissionIfNeeded();
        startOverlay();
    }

    private void openOverlaySettings(boolean startAfterGrant) {
        startWhenPermissionGranted = startAfterGrant;
        Intent intent = new Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQUEST_OVERLAY);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[] {Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATIONS);
        }
    }

    private void startOverlay() {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(OverlayService.ACTION_SHOW);
        startForegroundService(intent);
        Toast.makeText(this, "悬浮窗已开启，可切换到游戏", Toast.LENGTH_SHORT).show();
        updateScreen();
    }

    private void sendOverlayAction(String action) {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(action);
        startService(intent);
        updateScreen();
    }

    private void updateScreen() {
        if (planStatus == null) {
            return;
        }
        PlanRepository.PlanData plan = PlanRepository.loadPlan(this);
        if (plan.steps.isEmpty()) {
            planStatus.setText("尚未导入规划");
            preview.setText("导入后将在这里预览第一条 Link");
        } else {
            int index = PlanRepository.getIndex(this, plan.steps.size());
            LinkStep step = plan.steps.get(index);
            planStatus.setText(plan.displayName + "\n" + plan.steps.size() + " 条 Link");
            preview.setText(
                    "当前 " + (index + 1) + " / " + plan.steps.size()
                            + "  ·  Agent " + step.agentNumber + "\n"
                            + "#" + step.originNumber + "  " + step.originName + "\n"
                            + "↓\n"
                            + "#" + step.destinationNumber + "  " + step.destinationName);
        }

        boolean permission = Settings.canDrawOverlays(this);
        permissionStatus.setText(permission
                ? "✓ 已获得“显示在其他应用上层”权限"
                : "需要“显示在其他应用上层”权限");
        permissionStatus.setTextColor(permission ? Ui.ACCENT : Ui.SECONDARY);
        startButton.setEnabled(!plan.steps.isEmpty());
        startButton.setAlpha(plan.steps.isEmpty() ? 0.45f : 1f);
        stopButton.setEnabled(OverlayService.isRunning());
        stopButton.setAlpha(OverlayService.isRunning() ? 1f : 0.45f);
    }
}
