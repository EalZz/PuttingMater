package com.example.puttingmeter.session;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.puttingmeter.model.PuttingRecord;

public class PuttingSession {
    private static final int MAX_RECORDS = 10;
    private final List<PuttingRecord> recordList = new ArrayList<>();
    private float maxDistanceThisSession = 0f;

    public void addRecord(PuttingRecord record) {
        recordList.add(0, record);
        if (recordList.size() > MAX_RECORDS) {
            recordList.remove(recordList.size() - 1);
        }

        float distance = record.getDistance();
        if (distance > maxDistanceThisSession && distance > 0.1f) {
            maxDistanceThisSession = distance;
        }
    }

    public List<PuttingRecord> getRecordList() {
        return recordList;
    }

    public float getMaxDistance() {
        return maxDistanceThisSession;
    }

    public boolean isNewBest(float distance) {
        return distance > maxDistanceThisSession && distance > 0.1f;
    }

    public void clear() {
        recordList.clear();
        maxDistanceThisSession = 0f;
    }
}
