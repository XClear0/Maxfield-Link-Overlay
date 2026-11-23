package io.github.xclear0.maxfieldoverlay;

import android.annotation.SuppressLint;
import android.app.LocaleManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;

import java.util.Locale;

enum AppLanguage {
    SYSTEM("", R.string.language_system),
    ENGLISH("en", R.string.language_english),
    CHINESE("zh-CN", R.string.language_chinese);

    private static final String PREFS = "app_language";
    private static final String LOCALE_TAG = "locale_tag";

    final int label;
    private final String tag;

    AppLanguage(String tag, int label) {
        this.tag = tag;
        this.label = label;
    }

    static AppLanguage current(Context context) {
        String selectedTag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            LocaleList locales = context.getSystemService(LocaleManager.class).getApplicationLocales();
            selectedTag = locales.isEmpty() ? "" : locales.get(0).toLanguageTag();
        } else {
            selectedTag = legacyPreferences(context).getString(LOCALE_TAG, "");
        }
        String language = Locale.forLanguageTag(selectedTag).getLanguage();
        return switch (language) {
            case "en" -> ENGLISH;
            case "zh" -> CHINESE;
            default -> SYSTEM;
        };
    }

    void apply(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager.class)
                    .setApplicationLocales(LocaleList.forLanguageTags(tag));
        } else {
            legacyPreferences(context).edit().putString(LOCALE_TAG, tag).apply();
        }
    }

    static void migrateLegacyChoice(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            SharedPreferences prefs = legacyPreferences(context);
            if (prefs.contains(LOCALE_TAG)) {
                LocaleManager manager = context.getSystemService(LocaleManager.class);
                String savedTag = prefs.getString(LOCALE_TAG, "");
                if (!savedTag.isEmpty() && manager.getApplicationLocales().isEmpty()) {
                    manager.setApplicationLocales(LocaleList.forLanguageTags(savedTag));
                }
                prefs.edit().remove(LOCALE_TAG).apply();
            }
        }
    }

    // The app bundle disables language splits so all translations are installed together.
    @SuppressLint("AppBundleLocaleChanges")
    static Context localizedContext(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context;
        }
        String tag = legacyPreferences(context).getString(LOCALE_TAG, "");
        if (tag.isEmpty()) {
            return context;
        }
        Configuration override = new Configuration();
        override.setLocale(Locale.forLanguageTag(tag));
        return context.createConfigurationContext(override);
    }

    static SharedPreferences legacyPreferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
