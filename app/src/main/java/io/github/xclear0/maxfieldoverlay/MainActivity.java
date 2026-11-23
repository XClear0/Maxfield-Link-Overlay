package io.github.xclear0.maxfieldoverlay;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int REQUEST_PLAN = 40;
    private static final int REQUEST_OVERLAY = 41;
    private static final int REQUEST_NOTIFICATIONS = 42;
    private static final String PRIVACY_POLICY_URL =
            "https://xclear0.github.io/Maxfield-Link-Overlay/privacy.html";
    private static final String LICENSE_URL =
            "https://github.com/XClear0/Maxfield-Link-Overlay/blob/main/LICENSE";
    private static final String INGRESS_PACKAGE = "com.nianticproject.ingress";
    private static final String STATE_TAB = "selected_tab";
    private static final int DOCK_HEIGHT = 60;
    private static final int DOCK_BOTTOM_GAP = 12;

    private enum Tab {
        HOME(R.string.tab_home, R.drawable.ic_dock_home),
        PLANS(R.string.tab_plans, R.drawable.ic_dock_plans),
        SETTINGS(R.string.tab_settings, R.drawable.ic_dock_settings);

        final int label;
        final int icon;

        Tab(int label, int icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    private Tab selectedTab = Tab.HOME;
    private FrameLayout pageHost;
    private final ImageButton[] tabButtons = new ImageButton[Tab.values().length];
    private LinearLayout savedPlanList;

    private TextView planStatus;
    private TextView preview;
    private TextView permissionStatus;
    private Button searchButton;
    private Button resetButton;
    private Button startButton;
    private boolean startWhenPermissionGranted;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.localizedContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppLanguage.migrateLegacyChoice(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }

        setContentView(buildShell());
        Tab initialTab = savedInstanceState == null ? Tab.HOME
                : Tab.valueOf(savedInstanceState.getString(STATE_TAB, Tab.HOME.name()));
        showTab(initialTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_TAB, selectedTab.name());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPage();
        if (startWhenPermissionGranted && Settings.canDrawOverlays(this)) {
            startWhenPermissionGranted = false;
            startOverlay();
        }
    }

    private View buildShell() {
        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF182736, Ui.BACKGROUND, 0xFF11232A}));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets safe = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        }

        pageHost = new FrameLayout(this);
        root.addView(pageHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, DOCK_HEIGHT),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockParams.bottomMargin = Ui.dp(this, DOCK_BOTTOM_GAP);
        root.addView(buildDock(), dockParams);
        return root;
    }

    private LinearLayout buildDock() {
        LinearLayout dock = new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setBackground(Ui.glass(this, 30, 0xDB526A7D, 0xEA1B2B3A, 0x66CFEBF8));
        dock.setElevation(Ui.dp(this, 14));
        dock.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6));
        for (Tab tab : Tab.values()) {
            ImageButton button = new ImageButton(this);
            button.setImageResource(tab.icon);
            button.setScaleType(ImageView.ScaleType.CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setStateListAnimator(null);
            button.setContentDescription(getText(tab.label));
            button.setTooltipText(getText(tab.label));
            button.setOnClickListener(view -> showTab(tab));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(this, 76), ViewGroup.LayoutParams.MATCH_PARENT);
            dock.addView(button, params);
            tabButtons[tab.ordinal()] = button;
        }
        return dock;
    }

    private void showTab(Tab tab) {
        selectedTab = tab;
        for (Tab item : Tab.values()) {
            ImageButton button = tabButtons[item.ordinal()];
            boolean selected = item == tab;
            button.setSelected(selected);
            button.setImageTintList(ColorStateList.valueOf(selected ? 0xFFC3FFF6 : 0xFFDBE7F0));
            GradientDrawable highlight = selected
                    ? Ui.glass(this, 24, 0x665EDBC9, 0x2651C7BA, 0x6699ECE4)
                    : null;
            button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3DE7FFF9),
                    highlight, Ui.background(Color.WHITE, 24, this)));
        }
        pageHost.removeAllViews();
        LinearLayout content = switch (tab) {
            case HOME -> buildHome();
            case PLANS -> buildPlans();
            case SETTINGS -> buildSettings();
        };
        ScrollView scroll = scrollPage(content);
        scroll.setBackground(null);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, Ui.dp(this, DOCK_HEIGHT + DOCK_BOTTOM_GAP + 8));
        pageHost.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshPage();
    }

    private void refreshPage() {
        switch (selectedTab) {
            case HOME -> updateHome();
            case PLANS -> updatePlanList();
            case SETTINGS -> { }
        }
    }

    private LinearLayout page() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(Ui.dp(this, 20), Ui.dp(this, 14), Ui.dp(this, 20), Ui.dp(this, 16));
        return root;
    }

    private LinearLayout buildHome() {
        LinearLayout root = page();

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

        TextView planLabel = sectionLabel(getString(R.string.current_plan_title));
        planCard.addView(planLabel);

        planStatus = Ui.text(this, getString(R.string.plan_status_none), 15, Ui.SECONDARY);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = Ui.dp(this, 10);
        planCard.addView(planStatus, statusParams);

        preview = Ui.text(this, "", 13, Ui.PRIMARY);
        preview.setBackground(Ui.background(Ui.BACKGROUND, 10, this));
        preview.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 8));
        LinearLayout.LayoutParams previewParams = fullWidth();
        previewParams.topMargin = Ui.dp(this, 12);
        planCard.addView(preview, previewParams);

        searchButton = Ui.button(this, getString(R.string.portal_search_title), false);
        searchButton.setOnClickListener(view ->
                startActivity(new Intent(this, PortalSearchActivity.class)));
        LinearLayout.LayoutParams searchParams = fullWidth();
        searchParams.topMargin = Ui.dp(this, 10);
        planCard.addView(searchButton, searchParams);

        resetButton = Ui.button(this, getString(R.string.reset_to_first_link), false);
        resetButton.setOnClickListener(view -> {
            String id = PlanRepository.activePlanId(this);
            if (!id.isEmpty()) {
                PlanRepository.setIndex(this, id, 0);
                reloadRunningOverlay();
                updateHome();
            }
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

        Button ingressButton = Ui.button(this, getString(R.string.open_ingress), false);
        ingressButton.setOnClickListener(view -> openIngress());
        addSpaced(overlayCard, ingressButton, 10);

        return root;
    }

    private LinearLayout buildPlans() {
        LinearLayout root = page();
        addPageTitle(root, R.string.tab_plans);
        Button importButton = Ui.button(this, getString(R.string.select_plan_file), true);
        importButton.setContentDescription(getString(R.string.import_plan_content_description));
        importButton.setOnClickListener(view -> choosePlan());
        addSpaced(root, importButton, 16);
        addSpaced(root, Ui.text(this, getString(R.string.saved_plans_help), 14, Ui.SECONDARY), 10);

        savedPlanList = new LinearLayout(this);
        savedPlanList.setOrientation(LinearLayout.VERTICAL);
        addSpaced(root, savedPlanList, 10);
        return root;
    }

    private void updatePlanList() {
        savedPlanList.removeAllViews();
        List<PlanRepository.PlanSummary> plans = PlanRepository.listPlans(this);
        if (plans.isEmpty()) {
            addSpaced(savedPlanList, Ui.text(this, getString(R.string.saved_plans_empty),
                    15, Ui.SECONDARY), 10);
            return;
        }
        String activeId = PlanRepository.activePlanId(this);
        DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM,
                getResources().getConfiguration().getLocales().get(0));
        for (PlanRepository.PlanSummary plan : plans) {
            boolean active = plan.id.equals(activeId);
            String progress = getString(R.string.saved_plan_progress,
                    Math.min(plan.index + 1, plan.stepCount), plan.stepCount);
            String date = dateFormat.format(new Date(plan.importedAt));
            String label = active
                    ? getString(R.string.saved_plan_active, plan.displayName, progress, date)
                    : getString(R.string.saved_plan_row, plan.displayName, progress, date);
            Button row = Ui.button(this, label, false);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setSelected(active);
            row.setTextColor(active ? Ui.ACCENT : Ui.PRIMARY);
            row.setOnClickListener(view -> selectSavedPlan(plan.id));
            addSpaced(savedPlanList, new SwipePlanRow(this, row, () -> confirmDeletePlan(plan)), 8);
        }
    }

    private void selectSavedPlan(String id) {
        if (!id.equals(PlanRepository.activePlanId(this))) {
            if (PlanRepository.selectPlan(this, id)) {
                reloadRunningOverlay();
            } else {
                Toast.makeText(this, R.string.saved_plan_unavailable, Toast.LENGTH_SHORT).show();
            }
            updatePlanList();
        }
    }

    private void confirmDeletePlan(PlanRepository.PlanSummary plan) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.plan_delete)
                .setMessage(getString(R.string.plan_delete_confirm, plan.displayName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.delete_action, (ignored, which) -> deleteSavedPlan(plan.id))
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Ui.DELETE_ACCENT);
    }

    private void deleteSavedPlan(String id) {
        boolean deletingActive = id.equals(PlanRepository.activePlanId(this));
        if (!PlanRepository.deletePlan(this, id)) {
            Toast.makeText(this, R.string.plan_delete_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (deletingActive && OverlayService.isRunning()) {
            boolean hasPlan = !PlanRepository.activePlanId(this).isEmpty();
            sendOverlayAction(hasPlan ? OverlayService.ACTION_RELOAD : OverlayService.ACTION_STOP);
        }
        updatePlanList();
    }

    private LinearLayout buildSettings() {
        LinearLayout root = page();
        root.addView(settingsHeader(), fullWidth());
        Button language = Ui.button(this, getString(R.string.settings_language_row,
                getString(AppLanguage.current(this).label)), false);
        language.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        language.setOnClickListener(view -> chooseLanguage());
        addSpaced(root, language, 16);

        Button about = Ui.button(this, getString(R.string.about_title), false);
        about.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        about.setOnClickListener(view -> showAbout());
        addSpaced(root, about, 8);
        return root;
    }

    private View settingsHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        header.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 12));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_settings_app_mark);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        icon.setBackground(Ui.glass(this, 22, 0xDF40657B, 0xEC1B3245, 0x77B0EAF1));
        icon.setElevation(Ui.dp(this, 6));
        header.addView(icon, new LinearLayout.LayoutParams(Ui.dp(this, 76), Ui.dp(this, 76)));

        TextView title = Ui.text(this, getString(R.string.app_name), 22, Ui.PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        addSpaced(header, title, 12);

        String version = versionName();
        if (version != null) {
            TextView label = Ui.text(this, getString(R.string.settings_version, version), 14, Ui.SECONDARY);
            label.setGravity(Gravity.CENTER);
            addSpaced(header, label, 3);
        }
        return header;
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private void showAbout() {
        LinearLayout content = page();
        content.addView(sectionLabel(getString(R.string.app_name)));
        addSpaced(content, Ui.text(this, getString(R.string.about_intro), 15, Ui.SECONDARY), 6);
        addSpaced(content, sectionLabel(getString(R.string.about_license_title)), 18);
        addSpaced(content, Ui.text(this, getString(R.string.about_license), 15, Ui.SECONDARY), 6);
        Button license = Ui.button(this, getString(R.string.about_license_link), false);
        license.setOnClickListener(view -> openDocument(LICENSE_URL));
        addSpaced(content, license, 10);
        addSpaced(content, sectionLabel(getString(R.string.about_declaration_title)), 18);
        addSpaced(content, Ui.text(this, getString(R.string.about_declaration), 15, Ui.SECONDARY), 6);
        Button privacy = Ui.button(this, getString(R.string.privacy_policy), false);
        privacy.setOnClickListener(view -> openDocument(PRIVACY_POLICY_URL));
        addSpaced(content, privacy, 10);

        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setView(scrollPage(content))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private ScrollView scrollPage(LinearLayout root) {
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

    private void addPageTitle(LinearLayout root, int title) {
        TextView heading = Ui.text(this, getString(title), 28, Ui.PRIMARY);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            heading.setAccessibilityHeading(true);
        }
        root.addView(heading, fullWidth());
    }

    private void addSpaced(LinearLayout root, View view, int topGap) {
        LinearLayout.LayoutParams params = fullWidth();
        params.topMargin = Ui.dp(this, topGap);
        root.addView(view, params);
    }

    private void chooseLanguage() {
        AppLanguage selected = AppLanguage.current(this);
        AppLanguage[] choices = AppLanguage.values();
        CharSequence[] labels = new CharSequence[choices.length];
        for (int i = 0; i < choices.length; i++) {
            labels[i] = getText(choices[i].label);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, selected.ordinal(), (dialog, position) -> {
                    dialog.dismiss();
                    AppLanguage choice = choices[position];
                    if (choice != selected) {
                        choice.apply(this);
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            recreate();
                        }
                    }
                })
                .show();
    }

    private void openDocument(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.about_link_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void openIngress() {
        Intent launch = getPackageManager().getLaunchIntentForPackage(INGRESS_PACKAGE);
        if (launch != null) {
            try {
                startActivity(launch);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The game may have become unavailable since the lookup.
            }
        }
        Toast.makeText(this, R.string.ingress_unavailable, Toast.LENGTH_SHORT).show();
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
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {
                "text/*", "application/csv", "application/octet-stream"
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
            reloadRunningOverlay();
            Toast.makeText(this, getString(R.string.plan_imported, steps.size()), Toast.LENGTH_LONG).show();
            showTab(Tab.PLANS);
        } catch (PlanParser.ParseException error) {
            Toast.makeText(this, error.messageRes, Toast.LENGTH_LONG).show();
        } catch (IOException | SecurityException error) {
            Toast.makeText(this, R.string.plan_import_failed, Toast.LENGTH_LONG).show();
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
        refreshPage();
    }

    private void reloadRunningOverlay() {
        if (OverlayService.isRunning()) {
            sendOverlayAction(OverlayService.ACTION_RELOAD);
        }
    }

    private void sendOverlayAction(String action) {
        Intent intent = new Intent(this, OverlayService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void updateHome() {
        PlanRepository.PlanData plan = PlanRepository.loadPlan(this);
        if (plan.steps.isEmpty()) {
            planStatus.setText(R.string.plan_status_none);
            preview.setText(R.string.preview_placeholder);
        } else {
            int index = PlanRepository.getIndex(this, plan.id, plan.steps.size());
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
        searchButton.setEnabled(!plan.steps.isEmpty());
        searchButton.setAlpha(plan.steps.isEmpty() ? 0.45f : 1f);
        resetButton.setEnabled(!plan.steps.isEmpty());
        resetButton.setAlpha(plan.steps.isEmpty() ? 0.45f : 1f);
        startButton.setEnabled(!plan.steps.isEmpty());
        startButton.setAlpha(plan.steps.isEmpty() ? 0.45f : 1f);
    }
}
