package io.github.xclear0.maxfieldoverlay;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.widget.Toast;

final class IngressLauncher {
    private static final String INGRESS_PACKAGE = "com.nianticproject.ingress";

    private IngressLauncher() {}

    static void open(Activity activity) {
        Intent launch = activity.getPackageManager().getLaunchIntentForPackage(INGRESS_PACKAGE);
        if (launch != null) {
            try {
                activity.startActivity(launch);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // The game may have become unavailable since the lookup.
            }
        }
        Toast.makeText(activity, R.string.ingress_unavailable, Toast.LENGTH_SHORT).show();
    }
}
