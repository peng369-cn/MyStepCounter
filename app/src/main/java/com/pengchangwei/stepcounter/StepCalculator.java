package com.pengchangwei.stepcounter;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 计步核心计算逻辑，全是纯函数，不依赖 Android API，方便单测。
 */
public class StepCalculator {

    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static boolean isNewDay(String currentDate, String todayDate) {
        return !currentDate.equals(todayDate);
    }

    public static boolean isSensorReset(float sensorTotal, float baselineSteps) {
        return sensorTotal < baselineSteps;
    }

    public static int computeDelta(float sensorTotal, float baselineSteps) {
        return (int) Math.max(0, sensorTotal - baselineSteps);
    }

    public static int calculateTodaySteps(int delta, boolean sensorWasReset, int restoredSteps) {
        if (sensorWasReset) {
            return Math.max(restoredSteps, restoredSteps + delta);
        }
        return Math.max(0, delta);
    }

    public static String todayDate() {
        return SDF.format(new Date());
    }
}
