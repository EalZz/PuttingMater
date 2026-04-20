package com.example.puttingmeter.settings;

import static org.junit.Assert.assertEquals;

import com.example.puttingmeter.format.SpeedUnit;

import org.junit.Test;

public class PuttingSettingsTest {
    @Test
    public void defaultValues_matchCurrentAppDefaults() {
        PuttingSettings settings = new PuttingSettings();

        assertEquals(5, settings.getCorrectionStep());
        assertEquals(SpeedUnit.CM_PER_SEC, settings.getSpeedUnit());
    }

    @Test
    public void setCorrectionStep_clampsToSupportedRange() {
        PuttingSettings settings = new PuttingSettings();

        settings.setCorrectionStep(0);
        assertEquals(1, settings.getCorrectionStep());

        settings.setCorrectionStep(11);
        assertEquals(10, settings.getCorrectionStep());
    }

    @Test
    public void setSpeedUnit_ignoresNullValue() {
        PuttingSettings settings = new PuttingSettings();

        settings.setSpeedUnit(SpeedUnit.M_PER_SEC);
        settings.setSpeedUnit(null);

        assertEquals(SpeedUnit.M_PER_SEC, settings.getSpeedUnit());
    }
}
