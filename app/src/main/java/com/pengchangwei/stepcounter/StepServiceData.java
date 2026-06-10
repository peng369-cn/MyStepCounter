package com.pengchangwei.stepcounter;

/**
 * 步数查询接口返回的单天数据，daily 接口直接返回该对象，
 * weekly/monthly 接口的 Map value 也用这个结构。
 */
public class StepServiceData {

    private int stepCount;
    private double distance;

    public int getStepCount() { return stepCount; }
    public void setStepCount(int stepCount) { this.stepCount = stepCount; }

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
}
