package com.example.puttingmeter.format;

import static org.junit.Assert.*;
import org.junit.Test;

public class SpeedUnitTest {

    @Test
    public void testConvertFromMmPerSec() {
        double mmPerSec = 1000.0;
        
        assertEquals(1000.0, SpeedUnit.MM_PER_SEC.convertFromMmPerSec(mmPerSec), 0.01);
        assertEquals(100.0, SpeedUnit.CM_PER_SEC.convertFromMmPerSec(mmPerSec), 0.01);
        assertEquals(1.0, SpeedUnit.M_PER_SEC.convertFromMmPerSec(mmPerSec), 0.01);
    }

    @Test
    public void testGetDisplayName() {
        assertEquals("mm/s", SpeedUnit.MM_PER_SEC.getDisplayName());
        assertEquals("cm/s", SpeedUnit.CM_PER_SEC.getDisplayName());
        assertEquals("m/s", SpeedUnit.M_PER_SEC.getDisplayName());
    }

    @Test
    public void testFromString() {
        assertEquals(SpeedUnit.MM_PER_SEC, SpeedUnit.fromString("mm/s"));
        assertEquals(SpeedUnit.CM_PER_SEC, SpeedUnit.fromString("CM/S")); // Case insensitive
        assertEquals(SpeedUnit.M_PER_SEC, SpeedUnit.fromString("m/s"));
        assertEquals(SpeedUnit.MM_PER_SEC, SpeedUnit.fromString("invalid")); // Default
    }
}
