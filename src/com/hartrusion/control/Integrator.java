/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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

import java.util.function.DoubleSupplier;

/**
 * Discrete time step integrator element that provides an output which is
 * updated after invoking the run method.
 *
 * @author Viktor Alexander Hartung
 */
public class Integrator implements Runnable {

    private double uInput;
    private double yOutput;

    private double yMax = 100;
    private double yMin = 0;
    private double stepTime = 0.1;
    
    private double tI = 10;

    protected DoubleSupplier inputProvider;
    
    public void setStepTime(double stepTime) {
        this.stepTime = stepTime;
    }

    @Override
    public void run() {
        // If there is an input provider set, use it to set the controller
        // difference input by the class itself.
        if (inputProvider != null) {
            setInput(inputProvider.getAsDouble());
        }
        
        double integralValue = yOutput + uInput * stepTime / tI;

        // Limit Output
        if (integralValue > yMax) {
            yOutput = yMax;
        } else if (integralValue < yMin) {
            yOutput = yMin;
        } else { // default
            yOutput = integralValue;
        }
    }
    
    public double getInput() {
        return uInput;
    }

    /**
     * Sets the input value that is integrated.
     *
     * @param uInput value
     */
    public void setInput(double uInput) {
        this.uInput = uInput;
    }
    
    /**
     * Immediately sets the output value to the provided value. Can be used to
     * set initial conditions.
     *
     * @param out
     */
    public void forceOutputValue(double out) {
        yOutput = Math.min(yMax, Math.max(yMin, out));
    }
    
    public double getOutput() {
        return yOutput;
    }
    
    public double getMaxOutput() {
        return yMax;
    }

    /**
     * Set the maximum value limitation for the output value. Default: 100
     *
     * @param uMax
     */
    public void setMaxOutput(double uMax) {
        this.yMax = uMax;
    }

    public double getMinOutput() {
        return yMin;
    }

    /**
     * Set the minimum value limitation for the output value. Default: 0
     *
     * @param uMin
     */
    public void setMinOutput(double uMin) {
        this.yMin = uMin;
    }
    
    /**
     * Sets the integrator time constant in time unit.
     * 
     * @param tI in Seconds
     */
    public void setTi(double tI) {
        this.tI = tI;
    }
}
