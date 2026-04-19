package com.example.puttingmeter.ble;

import static org.junit.Assert.*;
import org.junit.Test;

public class SpeedPayloadParserTest {

    @Test
    public void testParseSingleValue() {
        SpeedPayloadParseResult result = SpeedPayloadParser.parse("100.5");
        assertTrue(result.isSuccess());
        assertEquals(100.5f, result.getPayload().getPeakSpeed(), 0.01f);
        assertEquals(100.5f, result.getPayload().getAvgSpeed(), 0.01f);
    }

    @Test
    public void testParseDoubleValue() {
        SpeedPayloadParseResult result = SpeedPayloadParser.parse("120.0|85.5");
        assertTrue(result.isSuccess());
        assertEquals(120.0f, result.getPayload().getPeakSpeed(), 0.01f);
        assertEquals(85.5f, result.getPayload().getAvgSpeed(), 0.01f);
    }

    @Test
    public void testParseEmptyPayload() {
        SpeedPayloadParseResult result = SpeedPayloadParser.parse("");
        assertFalse(result.isSuccess());
        assertEquals("Empty payload", result.getError());
    }

    @Test
    public void testParseInvalidFormat_ExtraSeparator() {
        // Test regression: should fail if more than 2 parts
        SpeedPayloadParseResult result = SpeedPayloadParser.parse("100|80|99");
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("expected 2 parts"));
    }

    @Test
    public void testParseInvalidFormat_MissingValue() {
        SpeedPayloadParseResult result = SpeedPayloadParser.parse("100|");
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("expected 2 parts"));
    }

    @Test
    public void testParseMalformedNumber() {
        SpeedPayloadParseResult result = SpeedPayloadParser.parse("abc|100");
        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("Number format error"));
    }
}
