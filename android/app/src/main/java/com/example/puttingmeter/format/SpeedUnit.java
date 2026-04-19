package com.example.puttingmeter.format;

public enum SpeedUnit {
    MM_PER_SEC("mm/s", 1.0),
    CM_PER_SEC("cm/s", 0.1),
    M_PER_SEC("m/s", 0.001);

    private final String displayName;
    private final double conversionFactor; // from mm/s

    SpeedUnit(String displayName, double conversionFactor) {
        this.displayName = displayName;
        this.conversionFactor = conversionFactor;
    }

    public double convertFromMmPerSec(double value) {
        return value * conversionFactor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static SpeedUnit fromString(String unitStr) {
        for (SpeedUnit unit : values()) {
            if (unit.displayName.equalsIgnoreCase(unitStr)) {
                return unit;
            }
        }
        return MM_PER_SEC; // Default
    }
}
