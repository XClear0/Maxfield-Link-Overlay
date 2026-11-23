package io.github.xclear0.maxfieldoverlay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PortalSearchActivity extends Activity {
    private static final String EXTRA_PORTAL_NUMBER = "portalNumber";
    private static final String EXTRA_PORTAL_NAME = "portalName";
    private static final String EXTRA_DESTINATION_QUERY = "destinationQuery";
    private static final String STATE_QUERY = "query";
    private static final String STATE_QUERY_PENDING = "queryPending";

    private EditText searchInput;
    private boolean queryPending;
    private List<PortalSearch.Portal> matches = Collections.emptyList();

    static Intent destinationSearchIntent(Context context, String destination) {
        return new Intent(context, PortalSearchActivity.class)
                .putExtra(EXTRA_DESTINATION_QUERY, destination);
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(AppLanguage.localizedContext(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BACKGROUND);
        root.setFocusableInTouchMode(true);
        int horizontal = Ui.dp(this, 20);
        int vertical = Ui.dp(this, 14);
        root.setPadding(horizontal, vertical, horizontal, vertical);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                view.setPadding(horizontal + safe.left, vertical + safe.top,
                        horizontal + safe.right, vertical + safe.bottom);
                return insets;
            });
        }

        List<LinkStep> steps = PlanRepository.loadPlan(this).steps;
        Intent intent = getIntent();
        boolean showLinks = intent.hasExtra(EXTRA_PORTAL_NUMBER);
        int backLabel = showLinks ? R.string.portal_search_back_to_matches
                : intent.hasExtra(EXTRA_DESTINATION_QUERY) ? R.string.portal_search_back_from_overlay
                : R.string.portal_search_back;
        Button back = Ui.button(this, getString(backLabel), false);
        back.setOnClickListener(view -> finish());
        root.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int portalNumber = intent.getIntExtra(EXTRA_PORTAL_NUMBER, 0);
        String title = showLinks
                ? getString(R.string.portal_search_portal, intent.getStringExtra(EXTRA_PORTAL_NAME), portalNumber)
                : getString(R.string.portal_search_title);
        setTitle(title);
        TextView heading = Ui.text(this, title, 24, Ui.PRIMARY);
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            heading.setAccessibilityHeading(true);
        }
        addAboveList(root, heading);

        if (showLinks) {
            showLinks(root, PortalSearch.findLinks(steps, portalNumber));
        } else {
            showSearch(root, steps, savedInstanceState);
        }
        setContentView(root);
        root.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        OverlayService.setSearchScreenVisible(true);
    }

    @Override
    protected void onStop() {
        OverlayService.setSearchScreenVisible(false);
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (searchInput != null) {
            outState.putString(STATE_QUERY, searchInput.getText().toString());
            outState.putBoolean(STATE_QUERY_PENDING, queryPending);
        }
    }

    private void showSearch(LinearLayout root, List<LinkStep> steps, Bundle savedInstanceState) {
        String destination = getIntent().getStringExtra(EXTRA_DESTINATION_QUERY);
        queryPending = savedInstanceState == null ? destination != null && !destination.isEmpty()
                : savedInstanceState.getBoolean(STATE_QUERY_PENDING);
        String initialQuery = savedInstanceState == null ? destination
                : savedInstanceState.getString(STATE_QUERY, "");

        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setBackground(Ui.background(Ui.SURFACE_LIGHT, 12, this));

        searchInput = new EditText(this);
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setTextSize(16);
        searchInput.setTextColor(Ui.PRIMARY);
        searchInput.setHintTextColor(Ui.SECONDARY);
        searchInput.setHint(R.string.portal_search_hint);
        searchInput.setBackgroundColor(Color.TRANSPARENT);
        searchInput.setMinimumHeight(Ui.dp(this, 48));
        searchInput.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 10));
        searchInput.setText(initialQuery);
        searchInput.setSelection(searchInput.length());
        searchBar.addView(searchInput, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button clear = Ui.button(this, "×", false);
        clear.setTextSize(24);
        clear.setTextColor(Ui.SECONDARY);
        clear.setBackgroundColor(Color.TRANSPARENT);
        clear.setPadding(0, 0, 0, 0);
        clear.setContentDescription(getString(R.string.portal_search_clear));
        clear.setVisibility(searchInput.length() > 0 ? View.VISIBLE : View.GONE);
        clear.setOnClickListener(view -> {
            searchInput.setText("");
            searchInput.requestFocus();
            InputMethodManager keyboard = getSystemService(InputMethodManager.class);
            if (keyboard != null) {
                keyboard.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        searchBar.addView(clear, new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        addAboveList(root, searchBar);

        Button submit = Ui.button(this, getString(R.string.portal_search_submit), true);
        submit.setVisibility(queryPending ? View.VISIBLE : View.GONE);
        addAboveList(root, submit);

        TextView searchStatus = Ui.text(this, "", 14, Ui.SECONDARY);
        searchStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        addAboveList(root, searchStatus);

        ArrayAdapter<String> rows = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        ListView list = addList(root, rows);
        list.setOnItemClickListener((parent, row, position, id) -> openPortal(matches.get(position)));
        Runnable search = () -> {
            queryPending = false;
            submit.setVisibility(View.GONE);
            clear.setVisibility(searchInput.length() > 0 ? View.VISIBLE : View.GONE);
            updateMatches(steps, searchInput.getText().toString(), searchStatus, rows);
        };
        submit.setOnClickListener(view -> search.run());
        searchInput.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) {
                return false;
            }
            search.run();
            hideKeyboard();
            return true;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                search.run();
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
        if (queryPending) {
            searchStatus.setVisibility(View.GONE);
        } else {
            search.run();
        }
    }

    private void updateMatches(List<LinkStep> steps, String query, TextView status, ArrayAdapter<String> rows) {
        matches = PortalSearch.findPortals(steps, query);
        rows.setNotifyOnChange(false);
        rows.clear();
        for (PortalSearch.Portal portal : matches) {
            rows.add(getString(R.string.portal_search_portal, portal.name, portal.number));
        }
        rows.notifyDataSetChanged();

        status.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
        status.setText(query.trim().isEmpty()
                ? R.string.portal_search_prompt : R.string.portal_search_no_matches);
    }

    private void hideKeyboard() {
        InputMethodManager keyboard = getSystemService(InputMethodManager.class);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(searchInput.getWindowToken(), 0);
        }
    }

    private void openPortal(PortalSearch.Portal portal) {
        hideKeyboard();
        Intent intent = new Intent(this, PortalSearchActivity.class);
        intent.putExtra(EXTRA_PORTAL_NUMBER, portal.number);
        intent.putExtra(EXTRA_PORTAL_NAME, portal.name);
        startActivity(intent);
    }

    private void showLinks(LinearLayout root, List<LinkStep> links) {
        int count = links.size();
        addAboveList(root, Ui.text(this, getResources().getQuantityString(
                R.plurals.portal_search_link_count, count, count), 14, Ui.SECONDARY));

        List<String> rows = new ArrayList<>(count);
        for (LinkStep link : links) {
            rows.add(getString(R.string.portal_search_link,
                    link.linkNumber, link.agentNumber, link.originNumber, link.originName,
                    link.destinationNumber, link.destinationName));
        }
        addList(root, new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rows));
    }

    private void addAboveList(LinearLayout root, View view) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(this, 12);
        root.addView(view, params);
    }

    private ListView addList(LinearLayout root, ListAdapter rows) {
        ListView list = new ListView(this);
        list.setAdapter(rows);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        params.topMargin = Ui.dp(this, 6);
        root.addView(list, params);
        return list;
    }
}
