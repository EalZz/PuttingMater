package com.example.puttingmeter.ble;

public final class SpeedPayloadParser {
    private SpeedPayloadParser() {
        // Utility class
    }

    /**
     * Parses the raw speed payload from the BLE characteristic.
     * Supported formats:
     * - "peak|avg": peak speed and average speed separated by '|'
     * - "peak": a single peak speed, average speed is set to the same value
     * 
     * @param raw The raw string from BLE
     * @return Result object containing payload or error
     */
    public static SpeedPayloadParseResult parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return SpeedPayloadParseResult.failure("Empty payload");
        }

        try {
            if (raw.contains("|")) {
                String[] parts = raw.split("\\|");
                if (parts.length != 2) {
                    return SpeedPayloadParseResult.failure("Invalid format (expected 2 parts): " + raw);
                }
                float peak = Float.parseFloat(parts[0].trim());
                float avg = Float.parseFloat(parts[1].trim());
                return SpeedPayloadParseResult.success(new SpeedPayload(peak, avg));
            } else {
                float peak = Float.parseFloat(raw.trim());
                return SpeedPayloadParseResult.success(new SpeedPayload(peak, peak));
            }
        } catch (NumberFormatException e) {
            return SpeedPayloadParseResult.failure("Number format error: " + raw);
        }
    }
}
