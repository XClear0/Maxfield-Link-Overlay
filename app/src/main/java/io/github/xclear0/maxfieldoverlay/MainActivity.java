package io.github.xclear0.maxfieldoverlay;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_PLAN = 40;
    private static final int REQUEST_OVERLAY = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;
    private static final String PRIVACY_POLICY_URL =
            "https://xclear0.github.io/Maxfield-Link-Overlay/privacy.html";

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

        TextView eyebrow = Ui.text(this, getString(R.string.main_eyebrow), 12, Ui.ACCENT);
        eyebrow.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        eyebrow.setLetterSpacing(0.12f);
        root.addView(eyebrow);

        TextView title = Ui.text(this, getString(R.string.main_title), 28, Ui.PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = fullWidth();
        titleParams.topMargin = Ui.dp(this, 4);
        root.addView(title, titleParams);

        TextView description = Ui.text(
                this,
                getString(R.string.main_description),
                14,
                Ui.SECONDARY);
        LinearLayout.LayoutParams descriptionParams = fullWidth();
        descriptionParams.topMargin = Ui.dp(this, 6);
        root.addView(description, descriptionParams);

        LinearLayout planCard = card();
        LinearLayout.LayoutParams planCardParams = fullWidth();
        planCardParams.topMargin = Ui.dp(this, 12);
        root.addView(planCard, planCardParams);

        TextView planLabel = sectionLabel(getString(R.string.section_import_plan));
        planCard.addView(planLabel);

        planStatus = Ui.text(this, getString(R.string.plan_status_none), 15, Ui.SECONDARY);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = Ui.dp(this, 10);
        planCard.addView(planStatus, statusParams);

        Button importButton = Ui.button(this, getString(R.string.select_plan_file), true);
        importButton.setContentDescription(getString(R.string.import_plan_content_description));
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

        Button resetButton = Ui.button(this, getString(R.string.reset_to_first_link), false);
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

        overlayCard.addView(sectionLabel(getString(R.string.section_enable_overlay)));
        permissionStatus = Ui.text(this, "", 15, Ui.SECONDARY);
        LinearLayout.LayoutParams permissionParams = fullWidth();
        permissionParams.topMargin = Ui.dp(this, 10);
        overlayCard.addView(permissionStatus, permissionParams);

        Button permissionButton = Ui.button(this, getString(R.string.grant_overlay_permission), false);
        permissionButton.setOnClickListener(view -> openOverlaySettings(false));
        LinearLayout.LayoutParams permissionButtonParams = fullWidth();
        permissionButtonParams.topMargin = Ui.dp(this, 14);
        overlayCard.addView(permissionButton, permissionButtonParams);

        startButton = Ui.button(this, getString(R.string.show_over_game), true);
        startButton.setOnClickListener(view -> prepareAndStartOverlay());
        LinearLayout.LayoutParams startParams = fullWidth();
        startParams.topMargin = Ui.dp(this, 10);
        overlayCard.addView(startButton, startParams);

        stopButton = Ui.button(this, getString(R.string.close_overlay), false);
        stopButton.setOnClickListener(view -> sendOverlayAction(OverlayService.ACTION_STOP));
        LinearLayout.LayoutParams stopParams = fullWidth();
        stopParams.topMargin = Ui.dp(this, 10);
        overlayCard.addView(stopButton, stopParams);

        TextView tips = Ui.text(
                this,
                getString(R.string.supported_files_help),
                14,
                Ui.SECONDARY);
        LinearLayout.LayoutParams tipsParams = fullWidth();
        tipsParams.topMargin = Ui.dp(this, 10);
        root.addView(tips, tipsParams);

        TextView privacyLink = Ui.text(this, getString(R.string.privacy_policy), 14, Ui.ACCENT);
        privacyLink.setPaintFlags(privacyLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
        privacyLink.setContentDescription(getString(R.string.privacy_policy_description));
        privacyLink.setOnClickListener(view -> openPrivacyPolicy());
        LinearLayout.LayoutParams privacyParams = fullWidth();
        privacyParams.topMargin = Ui.dp(this, 12);
        privacyParams.bottomMargin = Ui.dp(this, 8);
        root.addView(privacyLink, privacyParams);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(Ui.BACKGROUND);
        scrollView.setFillViewport(true);
        scrollView.addView(
                root,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
        return scrollView;
    }

    private void openPrivacyPolicy() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)));
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.privacy_policy_unavailable, Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(this, getString(R.string.plan_imported, steps.size()), Toast.LENGTH_LONG).show();
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
        return getString(R.string.default_plan_name);
    }

    private void prepareAndStartOverlay() {
        PlanRepository.PlanData plan = PlanRepository.loadPlan(this);
        if (plan.steps.isEmpty()) {
            Toast.makeText(this, R.string.import_plan_first, Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, R.string.overlay_started, Toast.LENGTH_SHORT).show();
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
            planStatus.setText(R.string.plan_status_none);
            preview.setText(R.string.preview_placeholder);
        } else {
            int index = PlanRepository.getIndex(this, plan.steps.size());
            LinkStep step = plan.steps.get(index);
            planStatus.setText(getString(R.string.plan_status_count, plan.displayName, plan.steps.size()));
            preview.setText(getString(R.string.preview_current_step,
                    index + 1, plan.steps.size(), step.agentNumber,
                    step.originNumber, step.originName,
                    step.destinationNumber, step.destinationName));
        }

        boolean permission = Settings.canDrawOverlays(this);
        permissionStatus.setText(permission
                ? R.string.overlay_permission_granted
                : R.string.overlay_permission_required);
        permissionStatus.setTextColor(permission ? Ui.ACCENT : Ui.SECONDARY);
        startButton.setEnabled(!plan.steps.isEmpty());
        startButton.setAlpha(plan.steps.isEmpty() ? 0.45f : 1f);
        stopButton.setEnabled(OverlayService.isRunning());
        stopButton.setAlpha(OverlayService.isRunning() ? 1f : 0.45f);
    }
}
