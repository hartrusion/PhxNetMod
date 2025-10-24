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
package com.hartrusion.modeling.assemblies;

import com.hartrusion.control.AbstractController;
import com.hartrusion.control.ControlCommand;
import com.hartrusion.control.PControl;
import com.hartrusion.mvc.ActionCommand;
import java.beans.PropertyChangeListener;

/**
 * Extends the Heat valve to a controlled heat valve by providing a controller
 * instance and means of accessing it, running it and process events that
 * control the controller.
 * <p>
 *
 *
 * @author Viktor Alexander Hartung
 */
public class HeatValveControlled extends HeatValve {

    private AbstractController controller;

    private String componentControlCommand;
    private String componentControlState;
    private String componentControlUpdateU;

    private ControlCommand controlState;
    private ControlCommand oldControlState;

    @Override
    public void initName(String name) {
        super.initName(name);
        controller.setName(name);

        // Strings that will be sent
        componentControlCommand = name + "ControlCommand";
        componentControlState = name + "ControlState";
        componentControlUpdateU = name + "ControlUpdateU";
    }

    @Override
    public void initSignalListener(PropertyChangeListener signalListener) {
        super.initSignalListener(signalListener);
        controller.addPropertyChangeListener(signalListener);
    }

    public void initControllerProportional() {
        controller = new PControl();
    }

    @Override
    public void run() {
        controller.run(); // updates controller output
        super.run(); // sets value to SWI b4 valves and runs monitor

        if (controller.isManualMode()) {
            controlState = ControlCommand.MANUAL_OPERATION;
        } else {
            controlState = ControlCommand.AUTOMATIC;
        }
        
        if (controlState != oldControlState) {
            // Send Manual/Auto state on change.
            pcs.firePropertyChange(componentControlState,
                    oldControlState, controlState);
            oldControlState = controlState;
        }
    }

    @Override
    public boolean handleAction(ActionCommand ac) {
        return super.handleAction(ac);
    }

    /**
     * Sets the feedback input value which is to be controlled by this instance.
     *
     * @param input
     */
    public void setInput(double input) {
        controller.setInput(input);
    }

}
