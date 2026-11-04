package de.klar.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Liest die installierten Apps aus – ohne Empfehlungen, ohne Werbezeilen. */
final class AppLoader {
    private AppLoader() {}

    static List<AppEntry> load(Context context, Prefs prefs) {
        PackageManager pm = context.getPackageManager();
        Map<String, ActivityInfo> byKey = new LinkedHashMap<>();
        Map<String, Boolean> isTvActivity = new HashMap<>();

        // Zuerst TV-Apps (bringen meist ein 16:9-Banner mit), dann normale Android-Apps.
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, context.getPackageName(), byKey, isTvActivity);
        collect(pm, Intent.CATEGORY_LAUNCHER, false, context.getPackageName(), byKey, isTvActivity);

        Map<String, String> labels = new HashMap<>();
        for (Map.Entry<String, ActivityInfo> e : byKey.entrySet()) {
            labels.put(e.getKey(), e.getValue().loadLabel(pm).toString());
        }

        List<String> pinned = prefs.pinned();
        Set<String> hidden = prefs.hidden();
        List<AppEntry> result = new ArrayList<>();
        for (String key : AppOrder.order(labels, pinned, hidden)) {
            ActivityInfo info = byKey.get(key);
            boolean tv = Boolean.TRUE.equals(isTvActivity.get(key));
            ComponentName cn = new ComponentName(info.packageName, info.name);
            Intent launch = new Intent(Intent.ACTION_MAIN)
                    .addCategory(tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                    .setComponent(cn)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            Drawable banner = info.loadBanner(pm);
            if (banner == null) banner = info.applicationInfo.loadBanner(pm);
            result.add(new AppEntry(key, labels.get(key), info.packageName, banner,
                    info.loadIcon(pm), launch, pinned.contains(key)));
        }
        result.add(new AppEntry(AppEntry.SETTINGS_KEY, context.getString(R.string.settings), null,
                null, context.getDrawable(R.drawable.ic_settings), null, false));
        return result;
    }

    /** Alle installierten App-Namen (auch ausgeblendete), für den Einstellungsdialog. */
    static Map<String, String> allLabels(Context context) {
        PackageManager pm = context.getPackageManager();
        Map<String, ActivityInfo> byKey = new LinkedHashMap<>();
        Map<String, Boolean> tv = new HashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, true, context.getPackageName(), byKey, tv);
        collect(pm, Intent.CATEGORY_LAUNCHER, false, context.getPackageName(), byKey, tv);
        Map<String, String> labels = new HashMap<>();
        for (Map.Entry<String, ActivityInfo> e : byKey.entrySet()) {
            labels.put(e.getKey(), e.getValue().loadLabel(pm).toString());
        }
        return labels;
    }

    private static void collect(PackageManager pm, String category, boolean tv, String ownPackage,
                                Map<String, ActivityInfo> byKey, Map<String, Boolean> isTv) {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(category);
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            ActivityInfo info = ri.activityInfo;
            if (info == null || ownPackage.equals(info.packageName)) continue;
            // Eine Kachel pro App: Schlüssel ist das Paket.
            String key = info.packageName;
            if (!byKey.containsKey(key)) {
                byKey.put(key, info);
                isTv.put(key, tv);
            }
        }
    }
}
