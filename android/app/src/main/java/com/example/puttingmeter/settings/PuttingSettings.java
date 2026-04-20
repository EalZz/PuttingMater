package com.example.puttingmeter.settings;

import com.example.puttingmeter.format.SpeedUnit;

public class PuttingSettings {
    private static final int MIN_CORRECTION_STEP = 1;
    private static final int MAX_CORRECTION_STEP = 10;

    private int correctionStep;
    private SpeedUnit speedUnit;

    public PuttingSettings() {
        correctionStep = 5;
        speedUnit = SpeedUnit.CM_PER_SEC;
    }

    public int getCorrectionStep() {
        return correctionStep;
    }

    public void setCorrectionStep(int correctionStep) {
        this.correctionStep = Math.max(MIN_CORRECTION_STEP, Math.min(MAX_CORRECTION_STEP, correctionStep));
    }

    public SpeedUnit getSpeedUnit() {
        return speedUnit;
    }

    public void setSpeedUnit(SpeedUnit speedUnit) {
        if (speedUnit != null) {
            this.speedUnit = speedUnit;
        }
    }
}
