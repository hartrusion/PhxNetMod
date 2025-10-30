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

/**
 * Limits value changes to a certain rate per time. Can be used for setpoints
 * for control engineering, but can also represent a valve motor actuator which
 * is limited in a maximum movement per time.
 *
 * @author Viktor Alexander Hartung
 */
public class SetpointIntegrator implements Runnable {

    private double stepTime = 0.1; // assume 100 ms as default
    private double maxRate = 1; // rate per timeunit (seconds, usually...)
    private double targetValue = 0;
    protected double value;
    private double maxDelta = 0.1; // rate per step, pre init for maxRate = 1
    private double posLim = 100;
    private double negLim = 0;

    /**
     * Performs one calculation step and updates value, considers the set min
     * and max values.
     * <p>
     * There is no check of how often this is getting called, the invoking
     * architecture has to make sure this is only called once per cyclic run.
     */
    @Override
    public void run() {
        if (targetValue > value) {
            value = Math.min(posLim,
                    Math.min(targetValue, value + maxDelta));
        } else if (targetValue < value) {
            value = Math.max(negLim,
                    Math.max(targetValue, value - maxDelta));
        } else { // same values: at least check for changed limits
            value = Math.min(posLim, Math.max(negLim, value));
        }
    }

    /**
     * Causes the current value to be frozen by internally setting the
     * targetValue to the output value.
     */
    public void setStop() {
        targetValue = value;
    }

    public void setUpperLimit(double posLim) {
        this.posLim = posLim;
    }

    public void setLowerLimit(double negLim) {
        this.negLim = negLim;
    }

    /**
     * Set the target value where the output should run towards.
     *
     * @param value
     */
    public void setInput(double value) {
        targetValue = value;
    }

    /**
     * Set the output to move to the highest allowed value.
     */
    public void setInputMax() {
        targetValue = posLim;
    }

    /**
     * Set the output to move to the lowest allowed value.
     */
    public void setInputMin() {
        targetValue = negLim;
    }

    /**
     * Returns the current limited value.
     *
     * @return value
     */
    public double getOutput() {
        return value;
    }

    /**
     * Set the maximum change of output value per time. Default: 1.0 (units per
     * second).
     *
     * @param rate Rate in units per time
     */
    public void setMaxRate(double rate) {
        maxRate = rate;
        maxDelta = stepTime * maxRate;
    }

    public void setStepTime(double dt) {
        stepTime = dt;
        maxDelta = stepTime * maxRate;
    }

    /**
     * Immediately sets the output value to the provided value. Can be used to
     * set initial conditions or trim operations.
     *
     * @param out
     */
    public void forceOutputValue(double out) {
        value = Math.min(posLim, Math.max(negLim, out));
        targetValue = value;
    }
}
