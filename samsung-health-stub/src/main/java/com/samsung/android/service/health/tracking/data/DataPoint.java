package com.samsung.android.service.health.tracking.data;

public class DataPoint {
    public <T> T getValue(ValueKey<T> key) {
        throw new UnsupportedOperationException();
    }

    public long getTimestamp() {
        throw new UnsupportedOperationException();
    }
}
