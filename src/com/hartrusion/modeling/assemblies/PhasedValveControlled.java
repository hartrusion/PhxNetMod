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
import static com.hartrusion.control.ControlCommand.AUTOMATIC;
import static com.hartrusion.control.ControlCommand.MANUAL_OPERATION;
import static com.hartrusion.control.ControlCommand.OUTPUT_CONTINUE;
import static com.hartrusion.control.ControlCommand.OUTPUT_DECREASE;
import static com.hartrusion.control.ControlCommand.OUTPUT_INCREASE;
import com.hartrusion.control.ParameterHandler;
import com.hartrusion.mvc.ActionCommand;
import java.beans.PropertyChangeListener;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedValveControlled extends PhasedValve {

    private AbstractController controller;

    private String name = "unnamed";
    private String actionCommand;
    private String componentControlState;

    private ControlCommand controlState;
    private ControlCommand oldControlState;

    private boolean outputOverride;

    /**
     * Updated output values (valve position) will be set to this parameter
     * handler.
     */
    private ParameterHandler outputValues;

    @Override
    public void initName(String name) {
        super.initName(name);
        this.name = name;
        if (controller != null) {
            controller.setName(name);
        }

        // Strings that will be sent or received
        actionCommand = name + "ControlCommand";
        componentControlState = name + "ControlState";
    }

    /**
     * Sets a ParameterHandler that will get the valve position on each run
     * call.
     *
     * @param h reference to ParameterHandler
     */
    public void initParameterHandler(ParameterHandler h) {
        outputValues = h;
    }

    @Override
    public void initSignalListener(PropertyChangeListener signalListener) {
        super.initSignalListener(signalListener);
        controller.addPropertyChangeListener(signalListener);
    }

    /**
     * Initializes a controller instance for this class. Either a already
     * existing or a new instance of a PI or P control element can be given.
     *
     * @param controller
     */
    public void initController(AbstractController controller) {
        controller.setName(name);
        this.controller = controller;
    }

    @Override
    public void run() {
        controller.run(); // updates controller output

        // Write the output of the controller to the Valve SWI if the
        // controller is in auto mode
        if (!controller.isManualMode()) {
            swControl.setInput(controller.getOutput());
        }

        super.run(); // sets value to SWI, update SWI, set valve value.

        // Follow-Up value is the valves position
        if (controller.isManualMode()) {
            controlState = ControlCommand.MANUAL_OPERATION;
            // For non-jump behaviour of output
            controller.setFollowUp(swControl.getOutput());
        } else {
            controlState = ControlCommand.AUTOMATIC;
        }

        if ((controlState != oldControlState) && !outputOverride) {
            // Send Manual/Auto state on change, but not during temporary overr
            pcs.firePropertyChange(componentControlState,
                    oldControlState, controlState);
            oldControlState = controlState;
        }

        // Send valve position as parameter value for monitoring
        if (outputValues != null) {
            outputValues.setParameterValue(valve.toString(),
                    valve.getOpening());
        }
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
                operateOpenValve(); // set swi to max
            }
            case OUTPUT_DECREASE -> {
                outputOverride = !controller.isManualMode();
                operateCloseValve(); // set swi to max
            }
            case OUTPUT_CONTINUE -> {
                stopValve(); // set SWI to current value
                if (outputOverride) {
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
