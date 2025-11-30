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
import com.hartrusion.mvc.ActionCommand;
import java.beans.PropertyChangeListener;

/**
 * Extends the Heat valve to a controlled heat valve by providing a controller
 * instance and means of accessing it, running it and process events that
 * control the controller.
 * <p>
 * To get the
 *
 * @author Viktor Alexander Hartung
 */
public class HeatValveControlled extends HeatValve {

    private AbstractController controller;

    private String name = "unnamed";
    private String actionCommand;

    private boolean outputOverride;

    @Override
    public void initName(String name) {
        super.initName(name);
        this.name = name;
        if (controller != null) {
            controller.setName(name);
        }

        // Strings that will be sent or received
        actionCommand = name + "ControlCommand";
    }

    @Override
    public void registerSignalListener(PropertyChangeListener signalListener) {
        super.registerSignalListener(signalListener);
        if (controller != null) {
            controller.addPropertyChangeListener(signalListener);
        }
    }

    /**
     * Initializes a controller instance for this class. Either a already
     * existing or a new instance of a PI or P control element can be given.
     *
     * @param controller
     */
    public void registerController(AbstractController controller) {
        controller.setName(name);
        // Add all listeners which are known to this class already
        PropertyChangeListener[] listeners = pcs.getPropertyChangeListeners();
        for (PropertyChangeListener l : listeners) {
            controller.addPropertyChangeListener(l);
        }
        this.controller = controller;
    }

    @Override
    public void run() {
        // Write the output of the controller to the Valve SWI if the
        // controller is in auto mode
        if (!controller.isManualMode()) {
            swControl.setInput(controller.getOutput());
        }

        super.run(); // sets value to SWI, update SWI, set valve value.

        // Follow-Up value is the valves position
        controller.setFollowUp(swControl.getOutput());
        
        controller.run(); // updates controller output
    }

    @Override
    public boolean handleAction(ActionCommand ac) {
        // no super call, valve cannot be operated with the non-controller
        // commands.
        if (!ac.getPropertyName().equals(actionCommand)) {
            return false;
        }
        switch ((ControlCommand) ac.getValue()) {
            case AUTOMATIC ->
                controller.setManualMode(false);
            case MANUAL_OPERATION -> {
                controller.setManualMode(true);
                stopValve();
            }
            case OUTPUT_INCREASE -> {
                // remember state to have the correct state at the end
                // of this user operation.
                outputOverride = !controller.isManualMode();
                controller.setManualMode(true);
                operateOpenValve(); // set swi to max
            }
            case OUTPUT_DECREASE -> {
                outputOverride = !controller.isManualMode();
                controller.setManualMode(true);
                operateCloseValve(); // set swi to max
            }
            case OUTPUT_CONTINUE -> {
                stopValve(); // set SWI to current value
                if (outputOverride) {
                    // If the output was to be changed during automatic, switch
                    // the controller back to auto mode.
                    controller.setManualMode(false);
                    outputOverride = false;
                }
            }
        }
        return true;
    }

    /**
     * Sets the feedback input value which is to be controlled by this instance.
     *
     * @param input
     */
    public void setInput(double input) {
        controller.setInput(input);
    }

    /**
     * Returns the instance of the used controller.
     *
     * @return AbstractController
     */
    public AbstractController getController() {
        return controller;
    }

}
