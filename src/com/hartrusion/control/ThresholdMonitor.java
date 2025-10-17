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
 * Monitors a double value. A threshold monitor can be added to monitor all
 * kinds of values, and it will fire off property change events when certain
 * limits are reached or if they are no longer reached.
 *
 * <p>
 * This is an abstract class with basic functionality for all kinds of monitors,
 * providing common methods and structure. Different extensions can throw
 * different messages, while they all have in common that they observe the value
 * of one value.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class ThresholdMonitor implements Runnable {

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    protected double value;
    protected String monitorName = "UNNAMED";

    public void setInput(double value) {
        this.value = value;
        checkNewValue();
    }

    @Override
    public void run() {
        checkNewValue();
    }

    protected abstract void checkNewValue();

    /**
     * Adds a listener for events of this threshold monitor. Listeners will get
     * the events which are created by this class.
     *
     * @param l The property change listener
     */
    public void addPropertyChangeListener(PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void setName(String name) {
        monitorName = name;
    }

    @Override
    public String toString() {
        if (monitorName == null) {
            return super.toString();
        } else {
            return monitorName + "-Monitor";
        }
    }
}
