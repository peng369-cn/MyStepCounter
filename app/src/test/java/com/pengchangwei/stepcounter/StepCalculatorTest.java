package com.pengchangwei.stepcounter;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * StepCalculator 纯函数单测，不依赖任何 Android API。
 */
public class StepCalculatorTest {

    @Test
    public void isNewDay_不同日期返回true() {
        assertEquals(true, StepCalculator.isNewDay("2026-06-02", "2026-06-01"));
    }

    @Test
    public void isNewDay_相同日期返回false() {
        assertEquals(false, StepCalculator.isNewDay("2026-06-01", "2026-06-01"));
    }

    @Test
    public void isSensorReset_total小于baseline返回true() {
        assertEquals(true, StepCalculator.isSensorReset(100f, 200f));
    }

    @Test
    public void isSensorReset_total大于等于baseline返回false() {
        assertEquals(false, StepCalculator.isSensorReset(200f, 100f));
        assertEquals(false, StepCalculator.isSensorReset(100f, 100f));
    }

    @Test
    public void computeDelta_正常差值() {
        assertEquals(500, StepCalculator.computeDelta(1500f, 1000f));
    }

    @Test
    public void computeDelta_total小于baseline返回0() {
        assertEquals(0, StepCalculator.computeDelta(500f, 1000f));
    }

    @Test
    public void 传感器正常时今日步数等于增量() {
        assertEquals(500, StepCalculator.calculateTodaySteps(500, false, 0));
    }

    @Test
    public void 传感器重置后合并历史步数和新增步数() {
        assertEquals(3200, StepCalculator.calculateTodaySteps(200, true, 3000));
    }

    @Test
    public void 传感器重置后增量可能为负取历史步数() {
        assertEquals(3000, StepCalculator.calculateTodaySteps(-100, true, 3000));
    }
}
