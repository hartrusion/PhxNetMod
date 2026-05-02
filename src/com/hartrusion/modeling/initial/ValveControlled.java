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
package com.hartrusion.modeling.initial;

import com.hartrusion.control.ControlCommand;

/**
 * Extends the simple valve to a controlled valve assembly, which holds some 
 * additional state values about how it is controlled at the moment, depending 
 * on the controller that is used. The state space variables are not named, 
 * there are 2 of them useable for the state of a PIDT1 control element.
 * 
 * @author Viktor Alexander Hartung
 */
public class ValveControlled extends ValveManual {
    private double controllerInput;
    private double controllerOutput;
    private boolean isManual;
    private ControlCommand controlState;

    public double getControllerInput() {
        return controllerInput;
    }

    public void setControllerInput(double controllerInput) {
        this.controllerInput = controllerInput;
    }

    public double getControllerOutput() {
        return controllerOutput;
    }

    public void setControllerOutput(double controllerOutput) {
        this.controllerOutput = controllerOutput;
    }

    public ControlCommand getControlState() {
        return controlState;
    }

    public void setControlState(ControlCommand controlState) {
        this.controlState = controlState;
    }

    public boolean isIsManual() {
        return isManual;
    }

    public void setIsManual(boolean isManual) {
        this.isManual = isManual;
    }
}
