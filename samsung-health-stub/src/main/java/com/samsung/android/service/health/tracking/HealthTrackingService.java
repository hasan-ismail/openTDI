package com.samsung.android.service.health.tracking;

import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.PpgType;
import java.util.Set;

public class HealthTrackingService {
    public HealthTrackingService(ConnectionListener listener, Object context) {
        throw new UnsupportedOperationException("Samsung Health Sensor SDK not bundled");
    }

    public void connectService() {
        throw new UnsupportedOperationException();
    }

    public void disconnectService() {
        throw new UnsupportedOperationException();
    }

    public HealthTracker getHealthTracker(HealthTrackerType type) {
        throw new UnsupportedOperationException();
    }

    public HealthTracker getHealthTracker(HealthTrackerType type, Set<PpgType> ppgTypeSet) {
        throw new UnsupportedOperationException();
    }

    public HealthTrackerCapability getTrackingCapability() {
        throw new UnsupportedOperationException();
    }
}
