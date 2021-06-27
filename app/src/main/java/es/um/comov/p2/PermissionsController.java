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

    public boolean checkPermissions(String[] permissions) {
        for(String iterator: permissions) {
            if(PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(activity,
                    iterator)) {
                return false;
            }
        }
        return true;
    }

    // Función que dados unos permisos devuelve si es necesario motrar un aviso al usuario
    // explicando por que son necesarios los permisos (Rationale)
    public boolean shouldProvideRationale(String[] permissions) {
        for(String iterator: permissions) {
            if(!ActivityCompat.shouldShowRequestPermissionRationale(activity,
                    iterator )) {
                return true;
            }
        }
        return false;
    }

    public void requestPermissions(String[] permissions) {
        // Comprobamos si es necesario mostrar el rationale al usuario
        if (shouldProvideRationale(permissions)) {
            Log.i(TAG, "Displaying permission rationale to provide additional context.");
            Snackbar.make(
                    activity.findViewById(R.id.activity_main),
                    R.string.permission_rationale,
                    Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.ok, view -> {
                        // Aquí es ddonde se realiza la petición de permisos
                        ActivityCompat.requestPermissions(activity,
                                permissions,
                                REQUEST_PERMISSIONS_REQUEST_CODE);
                    })
                    .show();
        } else {
            Log.i(TAG, "Requesting permission");
            // Aquí entrariamos si el usuario ha marcado la casilla de no volver a mostrar
            ActivityCompat.requestPermissions(activity,
                    permissions,
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }


}
