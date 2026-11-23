package io.github.xclear0.maxfieldoverlay;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class PlanRepository {
    private static final String LEGACY_FILE = "imported_plan.json";
    private static final String LEGACY_ID = "legacy";
    private static final String PREFS = "overlay_state";
    private static final String LEGACY_INDEX = "current_index";
    private static final String ACTIVE = "active_plan";
    private static final String PLAN_PREFIX = "plan_info_";
    private static final String INDEX_PREFIX = "plan_index_";
    private static final String DISPLAY_NAME = "displayName";
    private static final String STEP_COUNT = "stepCount";
    private static final String KEY_COUNT = "keyCount";
    private static final String IMPORTED_AT = "importedAt";
    private static final String X = "overlay_x";
    private static final String Y = "overlay_y";

    private PlanRepository() {}

    static int savePlan(Context context, String displayName, List<LinkStep> steps)
            throws IOException {
        migrateLegacyPlan(context);
        String id = UUID.randomUUID().toString();
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        int totalKeys = PlanKeys.totalForPlan(steps);
        String info;
        try {
            root.put(DISPLAY_NAME, displayName);
            for (LinkStep step : steps) {
                array.put(step.toJson());
            }
            root.put("steps", array);
            info = createInfo(displayName, steps.size(), totalKeys, System.currentTimeMillis());
        } catch (JSONException error) {
            throw new IOException("Unable to serialize the plan", error);
        }

        AtomicFile file = new AtomicFile(planFile(context, id));
        FileOutputStream output = file.startWrite();
        try {
            output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException | RuntimeException error) {
            file.failWrite(output);
            throw error;
        }
        prefs(context).edit().putString(PLAN_PREFIX + id, info).putString(ACTIVE, id).apply();
        return totalKeys;
    }

    static String activePlanId(Context context) {
        migrateLegacyPlan(context);
        SharedPreferences prefs = prefs(context);
        String id = prefs.getString(ACTIVE, "");
        if (!id.isEmpty() && !planFile(context, id).isFile()) {
            String fallback = newestPlanId(context);
            prefs.edit().remove(PLAN_PREFIX + id).remove(INDEX_PREFIX + id)
                    .putString(ACTIVE, fallback).apply();
            return fallback;
        }
        return id;
    }

    static boolean selectPlan(Context context, String id) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(PLAN_PREFIX + id) || !planFile(context, id).isFile()) {
            return false;
        }
        prefs.edit().putString(ACTIVE, id).apply();
        return true;
    }

    static boolean renamePlan(Context context, String id, String displayName) {
        SharedPreferences prefs = prefs(context);
        String key = PLAN_PREFIX + id;
        String info = prefs.getString(key, null);
        String name = displayName.trim();
        if (info == null || name.isEmpty() || !planFile(context, id).isFile()) {
            return false;
        }
        try {
            prefs.edit().putString(key, new JSONObject(info).put(DISPLAY_NAME, name).toString()).apply();
            return true;
        } catch (JSONException error) {
            return false;
        }
    }

    static boolean deletePlan(Context context, String id) {
        SharedPreferences prefs = prefs(context);
        if (!prefs.contains(PLAN_PREFIX + id)) {
            return false;
        }

        File file = planFile(context, id);
        new AtomicFile(file).delete();
        if (file.exists()) {
            return false;
        }

        SharedPreferences.Editor editor = prefs.edit().remove(PLAN_PREFIX + id).remove(INDEX_PREFIX + id);
        if (LEGACY_ID.equals(id)) {
            editor.remove(LEGACY_INDEX);
        }
        if (id.equals(prefs.getString(ACTIVE, ""))) {
            editor.putString(ACTIVE, newestPlanId(context));
        }
        editor.apply();
        return true;
    }

    private static String newestPlanId(Context context) {
        List<PlanSummary> plans = listPlans(context);
        return plans.isEmpty() ? "" : plans.get(0).id;
    }

    static List<PlanSummary> listPlans(Context context) {
        migrateLegacyPlan(context);
        List<PlanSummary> plans = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            if (entry.getKey().startsWith(PLAN_PREFIX) && entry.getValue() instanceof String value) {
                String id = entry.getKey().substring(PLAN_PREFIX.length());
                if (!planFile(context, id).isFile()) {
                    continue;
                }
                try {
                    JSONObject info = new JSONObject(value);
                    int count = info.getInt(STEP_COUNT);
                    plans.add(new PlanSummary(id, displayName(context, info), count, info.optInt(KEY_COUNT, count),
                            getIndex(context, id, count), info.getLong(IMPORTED_AT)));
                } catch (JSONException ignored) {
                    // Keep the remaining saved plans available if one entry is damaged.
                }
            }
        }
        plans.sort(Comparator.comparingLong((PlanSummary plan) -> plan.importedAt).reversed()
                .thenComparing(plan -> plan.id));
        return plans;
    }

    static PlanData loadPlan(Context context) {
        String id = activePlanId(context);
        if (id.isEmpty()) {
            return PlanData.empty();
        }
        try {
            return readPlan(context, id);
        } catch (IOException | JSONException error) {
            return PlanData.empty();
        }
    }

    private static void migrateLegacyPlan(Context context) {
        SharedPreferences prefs = prefs(context);
        File legacy = planFile(context, LEGACY_ID);
        if (prefs.contains(PLAN_PREFIX + LEGACY_ID) || !legacy.isFile()) {
            return;
        }
        try {
            PlanData plan = readPlan(context, LEGACY_ID);
            SharedPreferences.Editor editor = prefs.edit().putString(PLAN_PREFIX + LEGACY_ID,
                    createInfo(plan.displayName, plan.steps.size(), PlanKeys.totalForPlan(plan.steps),
                            legacy.lastModified()));
            editor.putInt(INDEX_PREFIX + LEGACY_ID, prefs.getInt(LEGACY_INDEX, 0));
            if (!prefs.contains(ACTIVE)) {
                editor.putString(ACTIVE, LEGACY_ID);
            }
            editor.apply();
        } catch (IOException | JSONException ignored) {
            // Leave the original file available if it cannot be read.
        }
    }

    private static PlanData readPlan(Context context, String id) throws IOException, JSONException {
        byte[] bytes = new AtomicFile(planFile(context, id)).readFully();
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONArray array = root.getJSONArray("steps");
        List<LinkStep> steps = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            steps.add(LinkStep.fromJson(array.getJSONObject(i)));
        }
        return new PlanData(id, savedDisplayName(context, id, root), steps);
    }

    private static String savedDisplayName(Context context, String id, JSONObject root) {
        String info = prefs(context).getString(PLAN_PREFIX + id, null);
        if (info != null) {
            try {
                return displayName(context, new JSONObject(info));
            } catch (JSONException ignored) {
                // Fall back to the imported name if the catalog entry cannot be read.
            }
        }
        return displayName(context, root);
    }

    private static String displayName(Context context, JSONObject data) {
        return data.optString(DISPLAY_NAME, context.getString(R.string.default_plan_name));
    }

    private static String createInfo(String name, int count, int keyCount, long importedAt) throws JSONException {
        return new JSONObject().put(DISPLAY_NAME, name).put(STEP_COUNT, count).put(KEY_COUNT, keyCount)
                .put(IMPORTED_AT, importedAt).toString();
    }

    private static File planFile(Context context, String id) {
        return new File(context.getFilesDir(), LEGACY_ID.equals(id) ? LEGACY_FILE : "plan-" + id + ".json");
    }

    static int getIndex(Context context, String id, int stepCount) {
        int saved = prefs(context).getInt(INDEX_PREFIX + id, 0);
        return Math.max(0, Math.min(saved, stepCount - 1));
    }

    static void setIndex(Context context, String id, int index) {
        SharedPreferences prefs = prefs(context);
        if (prefs.contains(PLAN_PREFIX + id)) {
            prefs.edit().putInt(INDEX_PREFIX + id, Math.max(index, 0)).apply();
        }
    }

    static int getX(Context context, int fallback) {
        return prefs(context).getInt(X, fallback);
    }

    static int getY(Context context, int fallback) {
        return prefs(context).getInt(Y, fallback);
    }

    static void savePosition(Context context, int x, int y) {
        prefs(context).edit().putInt(X, x).putInt(Y, y).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static final class PlanData {
        final String id;
        final String displayName;
        final List<LinkStep> steps;

        PlanData(String id, String displayName, List<LinkStep> steps) {
            this.id = id;
            this.displayName = displayName;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        static PlanData empty() {
            return new PlanData("", "", Collections.emptyList());
        }
    }

    static final class PlanSummary {
        final String id;
        final String displayName;
        final int stepCount;
        final int keyCount;
        final int index;
        final long importedAt;

        PlanSummary(String id, String displayName, int stepCount, int keyCount, int index, long importedAt) {
            this.id = id;
            this.displayName = displayName;
            this.stepCount = stepCount;
            this.keyCount = keyCount;
            this.index = index;
            this.importedAt = importedAt;
        }
    }
}
