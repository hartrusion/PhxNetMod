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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 *
 * @author Viktor Alexander Hartung
 */
public abstract class AbstractController implements Runnable {

    protected double eInput;
    protected double uFollowUp;
    protected double uOutput;
    protected double uMax = 100;
    protected double uMin = 100;
    protected boolean manualMode = true;
    protected double stepTime = 0.1;

    private String controllerName;

    /**
     * Events from controller, namely switching from manual to auto, will be
     * sent to all listeners attached to this class.
     */
    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public void setStepTime(double stepTime) {
        this.stepTime = stepTime;
    }

    public double getInput() {
        return eInput;
    }

    public void setInput(double eInput) {
        this.eInput = eInput;
    }

    public double getFollowUp() {
        return uFollowUp;
    }

    public void setFollowUp(double abgl) {
        this.uFollowUp = abgl;
    }

    public double getOutput() {
        return uOutput;
    }

    public double getMaxOutput() {
        return uMax;
    }

    public void setMaxOutput(double uMax) {
        this.uMax = uMax;
    }

    public double getMinOutput() {
        return uMin;
    }

    public void setMinOutput(double uMin) {
        this.uMin = uMin;
    }

    public boolean isManualMode() {
        return manualMode;
    }

    public void setManualMode(boolean hnd) {
        this.manualMode = hnd;
    }

    /**
     * Adds a listener for events of this controller. Listeners will get the
     * events which are created by this class.
     *
     * @param l The property change listener
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void setName(String name) {
        controllerName = name;
    }

    @Override
    public String toString() {
        if (controllerName == null) {
            return super.toString();
        } else {
            return controllerName + "-Controller";
        }
    }
}
