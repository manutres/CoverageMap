package com.google.android.gms.location.sample.locationupdatesforegroundservice;

import android.location.Location;

import com.google.android.gms.maps.model.LatLng;

import java.io.Serializable;

public class Sample implements Serializable {

    private Location location;
    private int signal;

    public Sample(){
    }

    public Sample(Location location, int signal) {
        this.location = location;
        this.signal = signal;
    }

    public LatLng getLatLng() {
        return new LatLng(location.getLatitude(), location.getLongitude());
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public int getSignal() {
        return signal;
    }

    public void setSignal(int signal) {
        this.signal = signal;
    }
}
