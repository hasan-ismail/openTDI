package com.samsung.android.service.health.tracking;

public interface ConnectionListener {
    void onConnectionSuccess();

    void onConnectionEnded();

    void onConnectionFailed(HealthTrackerException e);
}
