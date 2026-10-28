package com.samsung.android.service.health.tracking.data;

import java.util.List;

public class ValueKey<T> {
    public static class HeartRateSet {
        public static final ValueKey<Integer> HEART_RATE = null;
        public static final ValueKey<Integer> HEART_RATE_STATUS = null;
        public static final ValueKey<List<Integer>> IBI_LIST = null;
        public static final ValueKey<List<Integer>> IBI_STATUS_LIST = null;
    }

    public static class EdaSet {
        public static final ValueKey<Float> SKIN_CONDUCTANCE = null;
        public static final ValueKey<Integer> STATUS = null;
    }

    public static class AccelerometerSet {
        public static final ValueKey<Integer> ACCELEROMETER_X = null;
        public static final ValueKey<Integer> ACCELEROMETER_Y = null;
        public static final ValueKey<Integer> ACCELEROMETER_Z = null;
    }

    public static class SkinTemperatureSet {
        public static final ValueKey<Float> OBJECT_TEMPERATURE = null;
        public static final ValueKey<Float> AMBIENT_TEMPERATURE = null;
        public static ValueKey<Integer> STATUS = null; // not final in Samsung's reference
    }

    public static class SpO2Set {
        public static final ValueKey<Integer> STATUS = null;
        public static final ValueKey<Integer> SPO2 = null;
        public static final ValueKey<Integer> HEART_RATE = null;
    }

    public static class PpgSet {
        public static final ValueKey<Integer> PPG_GREEN = null;
        public static final ValueKey<Integer> GREEN_STATUS = null;
        public static final ValueKey<Integer> PPG_IR = null;
        public static final ValueKey<Integer> IR_STATUS = null;
        public static final ValueKey<Integer> PPG_RED = null;
        public static final ValueKey<Integer> RED_STATUS = null;
    }
}
