package es.um.comov.p2;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.location.LocationManager;
import android.os.IBinder;
import android.preference.PreferenceManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import android.content.pm.PackageManager;

import android.net.Uri;

import android.provider.Settings;
import androidx.annotation.NonNull;

import es.um.comov.p2.model.Sample;

import com.google.android.material.snackbar.Snackbar;

import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();

    // Manejador broadcast de las emisiones del servicio de muestra
    private MainActivityBroadcastReceiver broadcastReceiver;

    //Referencia al servicio de muestras
    private SamplesService samplesService = null;

    // Servicio para desacoplar las peticiones de permisos de la actividad principal
    private PermissionsController permissionService;

    // Variable para seguir el estado de enlace con el servicio
    private boolean mBound = false;

    // Elementos de vista
    private Button requestLocationUpdatesButton;
    private Button removeLocationUpdatesButton;

    private String modeNetwork;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        permissionService = new PermissionsController(this);
        broadcastReceiver = new MainActivityBroadcastReceiver();
        setContentView(R.layout.activity_main);

        // Check that the user hasn't revoked permissions by going to Settings.
        if (Utils.requestingLocationUpdates(this)) {
            if (!permissionService.checkPermissions()) {
                permissionService.requestPermissions();
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);

        //requestLocationUpdatesButton = (Button) findViewById(R.id.request_location_updates_button);
        removeLocationUpdatesButton = (Button) findViewById(R.id.remove_location_updates_button);

        // Aquí la actividad se enlaza con el servicio
        Intent intent = new Intent(this, SamplesService.class);
        bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);

        // Seteamos el estado del botón segun esté el servicio lanzando actualizaciones de localizacio o no
        setButtonsState(Utils.requestingLocationUpdates(this));

    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver,
                new IntentFilter(SamplesService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
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
        PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    private void setButtonsState(boolean requestingLocationUpdates) {
        if (requestingLocationUpdates) {
            //requestLocationUpdatesButton.setEnabled(false);
            removeLocationUpdatesButton.setEnabled(true);
        } else {
            //requestLocationUpdatesButton.setEnabled(true);
            removeLocationUpdatesButton.setEnabled(false);
        }
    }

    public void statusCheck() {
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
    }

    public void onClick2g(View v) {
        statusCheck();
        if (!permissionService.checkPermissions()) {
            permissionService.requestPermissions();
        } else {
            this.modeNetwork = "2g";
            samplesService.requestLocationUpdates(this.modeNetwork);
            Intent intent = new Intent(this, CoverageMapActivity.class);
            startActivity(intent);
        }
    }

    public void onClick3g(View v) {
        statusCheck();
        if (!permissionService.checkPermissions()) {
            permissionService.requestPermissions();
        } else {
            this.modeNetwork = "3g";
            samplesService.requestLocationUpdates(this.modeNetwork);
            Intent intent = new Intent(this, CoverageMapActivity.class);
            startActivity(intent);
        }
    }

    public void onClick4g(View v) {
        statusCheck();
        if (!permissionService.checkPermissions()) {
            permissionService.requestPermissions();
        } else {
            this.modeNetwork = "4g";
            samplesService.requestLocationUpdates(this.modeNetwork);
            Intent intent = new Intent(this, CoverageMapActivity.class);
            startActivity(intent);
        }
    }

    public void onClickSettings(View v) {
        Intent intent = new Intent(Settings. ACTION_NETWORK_OPERATOR_SETTINGS);
        startActivity(intent);
    }

    /**
     * Manejador para escuchar los cambios realizados en las SharedPreferences
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String s) {
        // Cambia el estado de los botones según se estén recibiendo actualizaciones de estado o no
        if (s.equals(Utils.KEY_REQUESTING_LOCATION_UPDATES)) {
            setButtonsState(sharedPreferences.getBoolean(Utils.KEY_REQUESTING_LOCATION_UPDATES,
                    false));
        }
    }

    /**
     * Receptor broadcast para la MainActivity
     * El onReceive se ejecuta siempre que el SampleService envía un nuevo mensaje Broadcast
     */
    private class MainActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Sample newSample = (Sample) intent.getSerializableExtra(SamplesService.EXTRA_SAMPLE);
            if (newSample != null) {
                Toast.makeText(MainActivity.this, Utils.getLocationText(newSample.getLocation()),
                        Toast.LENGTH_SHORT).show();
            }
        }
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
                // If user interaction was interrupted, the permission request is cancelled and you
                // receive empty arrays.
                Log.i(TAG, "User interaction was cancelled.");
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted.
                samplesService.requestLocationUpdates(this.modeNetwork);
            } else {
                // Permission denied.
                setButtonsState(false);
                Snackbar.make(
                        findViewById(R.id.activity_main),
                        R.string.permission_denied_explanation,
                        Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.settings, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                // Build intent that displays the App settings screen.
                                Intent intent = new Intent();
                                intent.setAction(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                Uri uri = Uri.fromParts("package",
                                        BuildConfig.APPLICATION_ID, null);
                                intent.setData(uri);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        })
                        .show();
            }
        }
    }
}
