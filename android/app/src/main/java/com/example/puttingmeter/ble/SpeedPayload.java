package com.example.puttingmeter.ble;

public class SpeedPayload {
    private final float peakSpeed;
    private final float avgSpeed;

    public SpeedPayload(float peakSpeed, float avgSpeed) {
        this.peakSpeed = peakSpeed;
        this.avgSpeed = avgSpeed;
    }

    public float getPeakSpeed() { return peakSpeed; }
    public float getAvgSpeed() { return avgSpeed; }
}
