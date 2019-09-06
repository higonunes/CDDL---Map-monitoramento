package com.example.myapplication;

import com.google.android.gms.maps.model.LatLng;

public class PointColor {
    public LatLng pontolatLng;
    public int cor;

    public PointColor(LatLng pontolatLng, int cor) {
        this.pontolatLng = pontolatLng;
        this.cor = cor;
    }
}
