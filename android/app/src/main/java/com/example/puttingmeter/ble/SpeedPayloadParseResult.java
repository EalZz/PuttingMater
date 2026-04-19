package com.example.puttingmeter.ble;

public class SpeedPayloadParseResult {
    private final SpeedPayload payload;
    private final String error;
    private final boolean success;

    private SpeedPayloadParseResult(SpeedPayload payload, String error, boolean success) {
        this.payload = payload;
        this.error = error;
        this.success = success;
    }

    public static SpeedPayloadParseResult success(SpeedPayload payload) {
        return new SpeedPayloadParseResult(payload, null, true);
    }

    public static SpeedPayloadParseResult failure(String error) {
        return new SpeedPayloadParseResult(null, error, false);
    }

    public boolean isSuccess() { return success; }
    public SpeedPayload getPayload() { return payload; }
    public String getError() { return error; }
}
