/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package es.um.comov.p2;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.location.Location;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.telephony.CellInfo;
import android.telephony.CellInfoCdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.CellSignalStrengthCdma;
import android.telephony.CellSignalStrengthGsm;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthWcdma;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import es.um.comov.p2.model.Path;
import es.um.comov.p2.model.Sample;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.List;


public class SamplesService extends Service {

    private static final String PACKAGE_NAME = "es.um.comov.p2";

    private static final String TAG = SamplesService.class.getSimpleName();

    private static final String CHANNEL_ID = "channel_01";
    static final String ACTION_BROADCAST = PACKAGE_NAME + ".broadcast";
    static final String EXTRA_SAMPLE = PACKAGE_NAME + ".sample";
    private static final String EXTRA_STARTED_FROM_NOTIFICATION = PACKAGE_NAME + ".started_from_notification";

    /**
     * Clase que se devuelve a las actividades que se bindean con el servicio.
     * En este caso de volvemos el servicio al completo, por lo que las actividades que se enlancen
     * con el servicio tendrán acceso a todos los métodos públicos de este
     */
    public class LocalBinder extends Binder {
        SamplesService getService() {
            return SamplesService.this;
        }
    }
    private final IBinder mBinder = new LocalBinder();

    // Handler para el hilo propio del servicio
    private Handler mServiceHandler;

    // Intervalo de actualización de localización DESEADO
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 5000;


    // Ratio máximo de actualización de localización
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    // Identificador de las notificaciones mostradas en el modo primer plano(foreground)
    private static final int NOTIFICATION_ID = 12345678;

    /*
     * Used to check whether the bound activity has really gone away and not unbound as part of an
     * orientation change. We create a foreground service notification only if the former takes
     * place.
     */
    private boolean mChangingConfiguration = false;

    private NotificationManager mNotificationManager;

    // Fused Location API
    private FusedLocationProviderClient mFusedLocationClient;

    //Parámetros de configuración en la creación del FusedLocation client
    private LocationRequest mLocationRequest;

    // Callback para manejar los cambios de localización
    private LocationCallback mLocationCallback;

    // Última localización disponible
    private Location mLocation;
    private TelephonyManager telephonyManager;
    private Path path;

    public Path getPath() {
        return this.path;
    }
    public Location getLocation() {
        return this.mLocation;
    }

    public SamplesService() {
    }

    @Override
    public void onCreate() {
        // Con esto hacemos que el servicio corra en un hilo a parte
        // porque normalmente un servicio corre en el mismo hilo que la actividad que se bindea
        // (el hilo principal) y las llamadas a este serían bloqueantes
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mServiceHandler = new Handler(handlerThread.getLooper());


        path = new Path(CoverageMapActivity.CIRCLE_RADIUS*2+1);
        telephonyManager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        mLocationCallback = new LocationCallback() {
            @SuppressLint("MissingPermission")
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                onNewLocation(locationResult.getLastLocation(), getSignalStrength(telephonyManager));
            }
        };

        // Inicialización del objeto location request
        createLocationRequest();
        getLastLocation();

        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        // Android O requiere canales de notificacion.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.app_name);
            // Create the channel for the notification
            NotificationChannel mChannel =
                    new NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);

            // Crea el canal de notificaciones para el manejador de notificaciones
            mNotificationManager.createNotificationChannel(mChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service started");
        boolean startedFromNotification = intent.getBooleanExtra(EXTRA_STARTED_FROM_NOTIFICATION,
                false);

        // Aquí se llega cuando el usuario decide dejar de recibir la posición desde las notificaciones
        if (startedFromNotification) {
            removeLocationUpdates();
            stopSelf();
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "in onBind()");
        stopForeground(true);
        mChangingConfiguration = false;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        // Called when a client (MainActivity in case of this sample) returns to the foreground
        // and binds once again with this service. The service should cease to be a foreground
        // service when that happens.
        Log.i(TAG, "in onRebind()");
        stopForeground(true);
        mChangingConfiguration = false;
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.i(TAG, "Last client unbound from service");

        // Comprueba que la MainActivity esté bindeada
        if (!mChangingConfiguration && Utils.requestingLocationUpdates(this)) {
            Log.i(TAG, "Starting foreground service");

            startForeground(NOTIFICATION_ID, getNotification());
        }
        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onDestroy() {
        mServiceHandler.removeCallbacksAndMessages(null);
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mChangingConfiguration = true;
    }


    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void getLastLocation() {
        try {
            mFusedLocationClient.getLastLocation()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            mLocation = task.getResult();
                        } else {
                            Log.w(TAG, "Failed to get location.");
                        }
                    });
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission." + unlikely);
        }
    }

    /**
     * Hace la petición para obtener actualizaciones periódicas de localización
     */
    public void requestLocationUpdates() {
        Utils.setRequestingLocationUpdates(this, true);
        startService(new Intent(getApplicationContext(), SamplesService.class));
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.myLooper());
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Se han perdido los permisos de localización. " + unlikely);
            Utils.setRequestingLocationUpdates(this, false);
        }
    }

    /**
     * Hace la petición para terminar las actualizaciones periodicas de localización
     */
    public void removeLocationUpdates() {
        Log.i(TAG, "Removing location updates");
        try {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
            Utils.setRequestingLocationUpdates(this, false);
            stopSelf();
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Se han perdido los permisos de localización. " + unlikely);
            Utils.setRequestingLocationUpdates(this, true);
        }
    }

    /**
     * Metodo para consultar si el servicio se encuentra en modo primer plano (foreground)
     * El servicio se encontrará en este modo si no hay ninguna actividad enlazada (bind) con él
     */
    public boolean serviceIsRunningInForeground(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(
                Integer.MAX_VALUE)) {
            if (getClass().getName().equals(service.service.getClassName())) {
                if (service.foreground) {
                    return true;
                }
            }
        }
        return false;
    }


    private static int getSignalStrength(TelephonyManager telephonyManager) throws SecurityException {
        int strength = 0;
        int cont = 0;
        List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
        if(cellInfos != null) {
            for (CellInfo cell: cellInfos) {
                if (cell.isRegistered()) {
                    cont++;
                    if (cell instanceof CellInfoWcdma) {
                        CellInfoWcdma cellInfoWcdma = (CellInfoWcdma) cell;
                        CellSignalStrengthWcdma cellSignalStrengthWcdma = cellInfoWcdma.getCellSignalStrength();
                        strength += cellSignalStrengthWcdma.getLevel();
                    } else if (cell instanceof CellInfoGsm) {
                        CellInfoGsm cellInfogsm = (CellInfoGsm) cell;
                        CellSignalStrengthGsm cellSignalStrengthGsm = cellInfogsm.getCellSignalStrength();
                        strength += cellSignalStrengthGsm.getLevel();
                    } else if (cell instanceof CellInfoLte) {
                        CellInfoLte cellInfoLte = (CellInfoLte) cell;
                        CellSignalStrengthLte cellSignalStrengthLte = cellInfoLte.getCellSignalStrength();
                        strength += cellSignalStrengthLte.getLevel();
                    } else if (cell instanceof CellInfoCdma) {
                        CellInfoCdma cellInfoCdma = (CellInfoCdma) cell;
                        CellSignalStrengthCdma cellSignalStrengthCdma = cellInfoCdma.getCellSignalStrength();
                        strength += cellSignalStrengthCdma.getLevel();
                    }
                }
            }
        }
        return strength/cont;
    }

    /**
     * Manejador para los eventos de nueva localización
     */
    private void onNewLocation(Location location, int signalStrength) {
        Log.i(TAG, "New location: " + location);

        mLocation = location;

        Sample newSample = new Sample(location, signalStrength);
        if(this.path.addSample(newSample)) {
            // Notifica a todos los que se hayan suscrito al broadcast
            Intent intent = new Intent(ACTION_BROADCAST);
            intent.putExtra(EXTRA_SAMPLE, newSample);
            LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

            // Actualiza el contenido de la tarjeta de notificación si
            // el servicio está funcionando en modo primer plano (foreground)
            if (serviceIsRunningInForeground(this)) {
                mNotificationManager.notify(NOTIFICATION_ID, getNotification());
            }
        }
    }


    /**
     * Gestión de las notificaciones para el modo primer plano (foreground) del servicio
     */

    /**
     * Crea la notificación que se le muestra al usuario cuando el servicio se encuentra
     * en modo primer plano (foreground service)
     */
    private Notification getNotification() {
        Intent intent = new Intent(this, SamplesService.class);

        CharSequence text = Utils.getLocationText(mLocation);

        // Extra to help us figure out if we arrived in onStartCommand via the notification or not.
        intent.putExtra(EXTRA_STARTED_FROM_NOTIFICATION, true);

        // The PendingIntent that leads to a call to onStartCommand() in this service.
        PendingIntent servicePendingIntent = PendingIntent.getService(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        // The PendingIntent to launch activity.
        PendingIntent activityPendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .addAction(R.drawable.ic_launch, getString(R.string.launch_activity),
                        activityPendingIntent)
                .addAction(R.drawable.ic_cancel, getString(R.string.remove_location_updates),
                        servicePendingIntent)
                .setContentText(text)
                .setContentTitle(Utils.getLocationTitle(this))
                .setOngoing(true)
                .setPriority(NotificationManager.IMPORTANCE_HIGH)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setTicker(text)
                .setWhen(System.currentTimeMillis());

        // Set the Channel ID for Android O.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId(CHANNEL_ID); // Channel ID
        }

        return builder.build();
    }
}
