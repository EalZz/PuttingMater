package com.example.puttingmeter.model;

public class PuttingRecord {
    private final String time;
    private final float peakSpeed;
    private final float avgSpeed;
    private final float distance;

    public PuttingRecord(String time, float peakSpeed, float avgSpeed, float distance) {
        this.time = time;
        this.peakSpeed = peakSpeed;
        this.avgSpeed = avgSpeed;
        this.distance = distance;
    }

    public String getTime() { return time; }
    public float getPeakSpeed() { return peakSpeed; }
    public float getAvgSpeed() { return avgSpeed; }
    public float getDistance() { return distance; }
}
