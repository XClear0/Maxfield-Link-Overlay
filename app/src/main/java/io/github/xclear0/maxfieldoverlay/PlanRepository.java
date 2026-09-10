package io.github.xclear0.maxfieldoverlay;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PlanRepository {
    private static final String PLAN_FILE = "imported_plan.json";
    private static final String PREFS = "overlay_state";
    private static final String INDEX = "current_index";
    private static final String X = "overlay_x";
    private static final String Y = "overlay_y";

    private PlanRepository() {}

    static void savePlan(Context context, String displayName, List<LinkStep> steps)
            throws IOException {
        JSONObject root = new JSONObject();
        JSONArray array = new JSONArray();
        try {
            root.put("displayName", displayName);
            for (LinkStep step : steps) {
                array.put(step.toJson());
            }
            root.put("steps", array);
        } catch (JSONException error) {
            throw new IOException("无法保存规划", error);
        }

        try (OutputStreamWriter writer = new OutputStreamWriter(
                context.openFileOutput(PLAN_FILE, Context.MODE_PRIVATE),
                StandardCharsets.UTF_8)) {
            writer.write(root.toString());
        }
        setIndex(context, 0);
    }

    static PlanData loadPlan(Context context) {
        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.openFileInput(PLAN_FILE), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            JSONObject root = new JSONObject(content.toString());
            JSONArray array = root.getJSONArray("steps");
            List<LinkStep> steps = new ArrayList<>(array.length());
            for (int i = 0; i < array.length(); i++) {
                steps.add(LinkStep.fromJson(array.getJSONObject(i)));
            }
            return new PlanData(
                    root.optString("displayName", context.getString(R.string.default_plan_name)),
                    steps);
        } catch (FileNotFoundException ignored) {
            return PlanData.empty();
        } catch (IOException | JSONException ignored) {
            return PlanData.empty();
        }
    }

    static int getIndex(Context context, int stepCount) {
        if (stepCount <= 0) {
            return 0;
        }
        int saved = prefs(context).getInt(INDEX, 0);
        return Math.max(0, Math.min(saved, stepCount - 1));
    }

    static void setIndex(Context context, int index) {
        prefs(context).edit().putInt(INDEX, Math.max(index, 0)).apply();
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
        final String displayName;
        final List<LinkStep> steps;

        PlanData(String displayName, List<LinkStep> steps) {
            this.displayName = displayName;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }

        static PlanData empty() {
            return new PlanData("", Collections.emptyList());
        }
    }
}
