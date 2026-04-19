package com.example.puttingmeter.session;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import com.example.puttingmeter.model.PuttingRecord;
import java.util.List;

public class PuttingSessionTest {

    private PuttingSession session;

    @Before
    public void setUp() {
        session = new PuttingSession();
    }

    @Test
    public void testAddRecord_UpdatesList() {
        PuttingRecord record = new PuttingRecord("10:00", 100f, 80f, 1.5f);
        session.addRecord(record);
        
        List<PuttingRecord> list = session.getRecordList();
        assertEquals(1, list.size());
        assertEquals(record, list.get(0));
    }

    @Test
    public void testAddRecord_LimitsTo10() {
        for (int i = 0; i < 15; i++) {
            session.addRecord(new PuttingRecord("10:" + i, 100f, 80f, 1.0f));
        }
        
        assertEquals(10, session.getRecordList().size());
    }

    @Test
    public void testMaxDistance_UpdatesCorrectly() {
        session.addRecord(new PuttingRecord("10:00", 100f, 80f, 1.5f));
        assertEquals(1.5f, session.getMaxDistance(), 0.01f);
        
        session.addRecord(new PuttingRecord("10:01", 110f, 90f, 1.2f)); // Lower than max
        assertEquals(1.5f, session.getMaxDistance(), 0.01f);
        
        session.addRecord(new PuttingRecord("10:02", 120f, 95f, 2.0f)); // New max
        assertEquals(2.0f, session.getMaxDistance(), 0.01f);
    }

    @Test
    public void testIsNewBest() {
        session.addRecord(new PuttingRecord("10:00", 100f, 80f, 1.5f));
        
        assertFalse(session.isNewBest(1.2f));
        assertFalse(session.isNewBest(1.5f));
        assertTrue(session.isNewBest(1.6f));
    }

    @Test
    public void testClear_ResetsState() {
        session.addRecord(new PuttingRecord("10:00", 100f, 80f, 1.5f));
        session.clear();
        
        assertEquals(0, session.getRecordList().size());
        assertEquals(0f, session.getMaxDistance(), 0.01f);
    }
}
