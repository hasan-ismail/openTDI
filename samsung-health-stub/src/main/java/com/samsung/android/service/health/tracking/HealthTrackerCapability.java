package com.samsung.android.service.health.tracking;

import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import java.util.List;

public class HealthTrackerCapability {
    public List<HealthTrackerType> getSupportHealthTrackerTypes() {
        throw new UnsupportedOperationException();
    }

    public String getVersion() {
        throw new UnsupportedOperationException();
    }
}
