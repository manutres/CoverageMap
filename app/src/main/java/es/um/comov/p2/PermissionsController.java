package es.um.comov.p2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;

public class PermissionsController {

    private static String TAG = PermissionsController.class.getSimpleName();

    // For checking runtime permissions
    public static final int REQUEST_PERMISSIONS_REQUEST_CODE = 101;

    private Activity activity;

    public PermissionsController(Activity activity) {
        this.activity = activity;
    }

    public boolean checkPermissions() {
        return  PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) + ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.READ_PHONE_STATE);
    }

    public void requestPermissions() {
        boolean shouldProvideRationale =
                ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.ACCESS_FINE_LOCATION ) && ActivityCompat.shouldShowRequestPermissionRationale(activity,
                        Manifest.permission.READ_PHONE_STATE);

        // Provide an additional rationale to the user. This would happen if the user denied the
        // request previously, but didn't check the "Don't ask again" checkbox.
        if (shouldProvideRationale) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    activity.findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, view -> {
                        // Request permission
                        ActivityCompat.requestPermissions(activity,
                                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE},
                                REQUEST_PERMISSIONS_REQUEST_CODE);
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Request permission. It's possible this can be auto answered if device policy
            // sets the permission in a given state or the user denied the permission
            // previously and checked "Never ask again".
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE},
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }


}
