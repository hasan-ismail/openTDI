package com.samsung.android.service.health.tracking;

public class HealthTrackerException extends Exception {
    public static final int PACKAGE_NOT_INSTALLED = 0;
    public static final int OLD_PLATFORM_VERSION = 1;

    private HealthTrackerException() {
    }

    public int getErrorCode() {
        throw new UnsupportedOperationException();
    }

    public boolean hasResolution() {
        throw new UnsupportedOperationException();
    }

    public void resolve(Object activity) {
        throw new UnsupportedOperationException();
    }
}
