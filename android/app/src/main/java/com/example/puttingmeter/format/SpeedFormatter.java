package com.example.puttingmeter.format;

import java.util.Locale;

public final class SpeedFormatter {
    private SpeedFormatter() {
        // Utility class
    }

    public static String formatValue(double mmPerSec, SpeedUnit unit) {
        double converted = unit.convertFromMmPerSec(mmPerSec);
        return String.format(Locale.getDefault(), "%.1f", converted);
    }

    public static String formatWithUnit(double mmPerSec, SpeedUnit unit) {
        return formatValue(mmPerSec, unit) + " " + unit.getDisplayName();
    }
}
