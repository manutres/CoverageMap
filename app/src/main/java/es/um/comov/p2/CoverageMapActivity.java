package es.um.comov.p2;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;

import androidx.fragment.app.FragmentActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CircleOptions;

import es.um.comov.p2.model.Path;
import es.um.comov.p2.model.Sample;

public class CoverageMapActivity extends FragmentActivity implements OnMapReadyCallback {

    public static final int CIRCLE_RADIUS = 3;
    private static final int ZOOM = 18;

    private GoogleMap mMap;

    // A reference to the service used to get location updates.
    private SamplesService mService = null;

    // Tracks the bound state of the service.
    private boolean mBound = false;

    private class MyReceiver extends BroadcastReceiver {
        @SuppressLint("MissingPermission")
        @Override
        public void onReceive(Context context, Intent intent) {
            Sample newSample = (Sample) intent.getSerializableExtra(SamplesService.EXTRA_SAMPLE);
            if (newSample != null) {
                mMap.addCircle(getSampleCircle(newSample));

                mMap.setMyLocationEnabled(true);

                // movemos la camara solo si es el primer sample
                if(mService.getPath().getSamples().size() == 1) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(newSample.getLatLng()));
                    mMap.animateCamera(CameraUpdateFactory.zoomTo(ZOOM));
                }
            }
        }
    }

    private MyReceiver myReceiver;

    // Monitors the state of the connection to the service.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SamplesService.LocalBinder binder = (SamplesService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myReceiver = new MyReceiver();
        bindService(new Intent(this, SamplesService.class), mServiceConnection,
                Context.BIND_AUTO_CREATE);



        setContentView(R.layout.activity_coverage_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map2);
        mapFragment.getMapAsync(this);

        Intent intent = getIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(myReceiver,
                new IntentFilter(SamplesService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(myReceiver);
        super.onPause();
    }

    @Override
    protected void onStop() {
        if (mBound) {
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            unbindService(mServiceConnection);
            mBound = false;
        }

        super.onStop();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        drawPath(mService.getPath());
    }

    private int getSignalColor(int signalLevel) {
        if(signalLevel >= 0 && signalLevel < 1) {
            return Color.rgb(255,66,66);
        }
        if(signalLevel >= 1 && signalLevel < 2) {
            return Color.rgb(255,180,66);
        }
        if(signalLevel >= 2 && signalLevel < 3) {
            return Color.rgb(255,249,66);
        }
        if(signalLevel >= 3 && signalLevel <= 4) {
            return Color.rgb(69,255,66);
        }
        if(signalLevel == 0) {
            return Color.rgb(255,0,0);
        }
        return 0;
    }

    /**
     * Función que devuelve un circulo dada una muestra (Sample)
     */
    private CircleOptions getSampleCircle(Sample sample) {
        return new CircleOptions()
                .center(sample.getLatLng())
                .radius(CIRCLE_RADIUS)
                .strokeColor(getSignalColor(sample.getSignal()))
                .fillColor(getSignalColor(sample.getSignal()));
    }

    /**
     * Función que dibuja un camino de circulos dado un camino (Path)
     */
    @SuppressLint("MissingPermission")
    private void drawPath(Path path) {
        for(Sample sample: path.getSamples()) {
            mMap.addCircle(getSampleCircle(sample));
        }

        // Move the camera to the last sample
        if(!path.getSamples().isEmpty()) {
            mMap.moveCamera(CameraUpdateFactory.newLatLng(path.getLastSample().getLatLng()));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(ZOOM));
            mMap.setMyLocationEnabled(true);
        }

    }

}