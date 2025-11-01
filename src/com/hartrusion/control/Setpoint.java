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

import com.hartrusion.mvc.ActionCommand;

/**
 * Manages a setpoint value that can be changed with commands. Will provide the
 * setpoint to a parameter handler. It basically extends the SetpointIntegrator
 * as a single usable object that can be used to get and manage a value from
 * a GUI controller.
 * <p>
 * It listens to actions with the same name as the setpoint object.
 * <p>
 * It provides an output value to a parameter handler object that gets updated
 * with the same name as this setpoint object.
 *
 * @author Viktor Alexander Hartung
 */
public class Setpoint extends SetpointIntegrator {

    private ParameterHandler outputValues;

    private String name;

    public void initName(String name) {
        this.name = name;
    }

    /**
     * Sets a ParameterHandler that will get the setpoint value on each call.
     *
     * @param h reference to ParameterHandler
     */
    public void initParameterHandler(ParameterHandler h) {
        outputValues = h;
    }

    @Override
    public void run() {
        super.run();
        outputValues.setParameterValue(name, value);
    }

    /**
     * Allows processing received events by the class itself. The Property name
     * must match the name that was set when calling initName.
     *
     * @param ac ActionCommand, will be further checked if it's matching.
     * @return true if event was processed by this instance.
     */
    public boolean handleAction(ActionCommand ac) {
        if (!ac.getPropertyName().equals(name)) {
            return false;
        }
        switch ((ControlCommand) ac.getValue()) {
            case SETPOINT_INCREASE -> setInputMax();
            case SETPOINT_DECREASE -> setInputMin();
            case SETPOINT_STOP -> setStop();
        }
        return true;
    }
}
