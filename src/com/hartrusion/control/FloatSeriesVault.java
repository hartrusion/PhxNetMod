/*
 * The MIT License
 *
 * Copyright 2025 Viktor Alexander Hartung.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.hartrusion.control;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import static com.hartrusion.util.ArraysExt.*;

/**
 * Holds series of float values in arrays for time series plots.
 *
 * @author Viktor Alexander Hartung
 */
public class FloatSeriesVault {

    private final Map<String, float[]> data = new ConcurrentHashMap<>();
    private int seriesLength = 0;
    private float[] time;

    private int countDiv = 1;

    /**
     * Has to be called before using, this inits the time axis and sets the
     * overall array lengths.
     *
     * @param steps number of steps, as it begins with 0, use smthn like 401
     * @param stepTime time per step
     */
    public void initTime(int steps, float stepTime) {
        seriesLength = steps;
        time = new float[steps];
        for (int idx = 0; idx < time.length; idx++) {
            time[idx] = (-(float) (seriesLength - 1) * stepTime + ((float) idx) * stepTime) / 60.0F;
        }
    }

    public void insertValue(String component, float value) {
        float[] series = getArray(component);
        rightShiftInsert(series, value);
    }

    /**
     * Get the reference to the array designated as component. If it does not
     * exist, create it and return a reference to it.
     *
     * @param component Identifies the series name
     * @return Reference to float array
     */
    private float[] getArray(String component) {
        float[] series;
        if (data.containsKey(component)) {
            series = data.get(component);
        } else {
            series = new float[seriesLength];
            for (int idx = 0; idx < series.length; idx++) {
                series[idx] = Float.NaN;
            }
            data.put(component, series);
        }
        return series;
    }

    public float[] getSeries(String component) {
        return data.get(component);
    }

    /**
     *
     * @return
     */
    public float[] getTime() {
        return time;
    }

    public int getSeriesLength() {
        return seriesLength;
    }

    public void setSeriesLength(int seriesLength) {
        this.seriesLength = seriesLength;
    }

    /**
     * A value saved in this object to describe how many time cycles will be
     * skipped before a new value will be inserted. If the value is 1, every
     * cycle will be recorded, if it is 5, only every fitht value is saved.
     *
     * @return countDiv
     */
    public int getCountDiv() {
        return countDiv;
    }

    /**
     * A value saved in this object to describe how many time cycles will be
     * skipped before a new value will be inserted. If the value is 1, every
     * cycle will be recorded, if it is 5, only every fitht value is saved.
     *
     * @param countDiv
     */
    public void setCountDiv(int countDiv) {
        this.countDiv = countDiv;
    }
}
