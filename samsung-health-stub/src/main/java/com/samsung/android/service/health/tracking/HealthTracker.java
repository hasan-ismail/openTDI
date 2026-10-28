package com.samsung.android.service.health.tracking;

import com.samsung.android.service.health.tracking.data.DataPoint;
import java.util.List;

public class HealthTracker {
    public void setEventListener(TrackerEventListener listener) {
        throw new UnsupportedOperationException();
    }

    public void unsetEventListener() {
        throw new UnsupportedOperationException();
    }

    public boolean flush() {
        throw new UnsupportedOperationException();
    }

    public interface TrackerEventListener {
        void onDataReceived(List<DataPoint> dataPoints);

        void onFlushCompleted();

        void onError(TrackerError error);
    }

    public enum TrackerError {
        PERMISSION_ERROR,
        SDK_POLICY_ERROR
    }
}
