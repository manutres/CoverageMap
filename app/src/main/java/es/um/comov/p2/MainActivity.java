package es.um.comov.p2;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.os.IBinder;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import android.content.pm.PackageManager;

import android.net.Uri;

import android.provider.Settings;
import androidx.annotation.NonNull;

import com.google.android.material.snackbar.Snackbar;

import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    //Referencia al servicio de muestras
    private SamplesService samplesService = null;

    // Servicio para desacoplar las peticiones de permisos de la actividad principal
    private PermissionsController permissionService;

    // Variable para seguir el estado de enlace con el servicio
    private boolean mBound = false;

    // Elementos de vista
    private Button removeLocationUpdatesButton;

    private String modeNetwork;

    private static final String[] LOCATION_AND_TELEPHONY_PERMISSIONS = { Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionService = new PermissionsController(this);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();
        removeLocationUpdatesButton = findViewById(R.id.remove_location_updates_button);

        // Aquí la actividad se enlaza con el servicio
        Intent intent = new Intent(this, SamplesService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        // Seteamos el estado del botón segun esté el servicio lanzando actualizaciones de localizacio o no
        if(mBound) {
            setButtonsState(samplesService.isRequestingLocationUpdates());
        }

    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mBound) {
            // Nos desvinculamos del servicio, esto puede hacer que el servicio se convierta
            // en un foreground service (servicio de primer plano)
            unbindService(mServiceConnection);
            mBound = false;
        }
        super.onStop();
    }

    private void setButtonsState(boolean requestingLocationUpdates) {
        if (requestingLocationUpdates) {
            removeLocationUpdatesButton.setEnabled(true);
        } else {
            removeLocationUpdatesButton.setEnabled(false);
        }
    }

    public void isGPSEnabled() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            buildAlertMessageNoGps();

        }
    }

    private void buildAlertMessageNoGps() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("El GPS está deshabilidtado, ¿Desea conectarlo?")
                .setCancelable(false)
                .setPositiveButton("Sí", (dialog, id) -> {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    setButtonsState(true);
                })
                .setNegativeButton("No", (dialog, id) -> {
                    dialog.cancel();
                    setButtonsState(false);
                });
        final AlertDialog alert = builder.create();
        alert.show();
    }



    public void onRemoveLocationUpdatesButton(View v) {
        samplesService.removeLocationUpdates();
        setButtonsState(false);
    }

    public void requestLocationUpdatesModeNetwork(String modeNetwork) {
        isGPSEnabled();
        if (!permissionService.checkPermissions(LOCATION_AND_TELEPHONY_PERMISSIONS)) {
            permissionService.requestPermissions(LOCATION_AND_TELEPHONY_PERMISSIONS);
        } else {
            this.modeNetwork = modeNetwork;
            samplesService.requestLocationUpdates(this.modeNetwork);
            Intent intent = new Intent(this, CoverageMapActivity.class);
            startActivity(intent);
        }
    }

    public void onClick2g(View v) {
        requestLocationUpdatesModeNetwork("2g");
    }

    public void onClick3g(View v) {
        requestLocationUpdatesModeNetwork("3g");
    }

    public void onClick4g(View v) {
        requestLocationUpdatesModeNetwork("4g");
    }

    public void onClickSettings(View v) {
        Intent intent = new Intent(Settings.ACTION_DATA_ROAMING_SETTINGS);
        startActivity(intent);
    }

    /**
     * Objeto para el manejo de la conexión con el servicio
     */
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SamplesService.LocalBinder binder = (SamplesService.LocalBinder) service;
            samplesService = binder.getService();
            mBound = true;
            setButtonsState(samplesService.isRequestingLocationUpdates());
            // Siempre que nos conectemos al servicio de samples hay que comprobar que tengamos permisos
            if (samplesService.isRequestingLocationUpdates()) {
                if (!permissionService.checkPermissions(LOCATION_AND_TELEPHONY_PERMISSIONS)) {
                    permissionService.requestPermissions(LOCATION_AND_TELEPHONY_PERMISSIONS);
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            samplesService = null;
            mBound = false;
        }
    };

    /**
     * Manejador de resultados para las peticiones de permisos
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.i(TAG, "onRequestPermissionResult");
        if (requestCode == PermissionsController.REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.length <= 0) {
                // Aquí entra si la petición de permisos es cancelada en cualquier momento
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Aquí entra si la petición de permisos ha sido aceptada
                samplesService.requestLocationUpdates(this.modeNetwork);
            } else {
                // Aquí entra si la petición de permisos ha sido denegada
                setButtonsState(false);
                Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, view -> {
                            // Build intent that displays the App settings screen.
                            Intent intent = new Intent();
                            intent.setAction(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package",
                                    BuildConfig.APPLICATION_ID, null);
                            intent.setData(uri);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        })
                        .show();
            }
        }
    }
}
