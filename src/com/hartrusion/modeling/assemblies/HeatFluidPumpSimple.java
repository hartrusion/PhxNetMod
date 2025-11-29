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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import com.hartrusion.control.SetpointIntegrator;
import com.hartrusion.control.ValveActuatorMonitor;
import com.hartrusion.modeling.heatfluid.HeatEffortSource;
import com.hartrusion.modeling.heatfluid.HeatLinearValve;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.mvc.ActionCommand;
import java.time.Duration;
import java.time.Instant;

/**
 * Represents an assembly of a pump with a discharge valve. A working point has
 * to be specified as well as the total head (all in SI units), the assembly
 * will generate a linear characteristic by those values.
 * <p>
 * It has some methods to allow controlling it and can process events itself.
 * <p>
 * The assembly also features a state machine that controls basic pump controls,
 * it is not allowed to do things that you must not do with such pumps.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFluidPumpSimple implements Runnable {

    private final HeatLinearValve dischargeValve = new HeatLinearValve();
    protected final HeatEffortSource pump = new HeatEffortSource();
    private final HeatNode dischargeNode = new HeatNode();

    protected final SetpointIntegrator dischargeControl
            = new SetpointIntegrator();
    
    private final ValveActuatorMonitor dischargeMonitor
            = new ValveActuatorMonitor();

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    protected PumpState pumpState;
    protected PumpState oldPumpState = null;

    private double totalHead;

    private boolean ready;
    private Instant stateTime; // for state machine control
    private Instant switchOnTime;

    /**
     * Describes the state of the startup (and shutdown) procedure.
     *
     */
    private int state;

    String name;

    public HeatFluidPumpSimple() {
        dischargeValve.connectToVia(pump, dischargeNode);

        dischargeControl.setMaxRate(15);
        dischargeControl.setUpperLimit(100);
        dischargeControl.setLowerLimit(-5.0);
    }

    /**
     * Initializes the assembly.
     *
     * @param totalHead Pressure that will be applied by this pump assembly if
     * it pumps against a closed valve
     * @param workingPressure Pressure that the pump will add in its designed
     * working point
     * @param workingFlow Flow in the design working point.
     *
     */
    public void initCharacteristic(double totalHead,
            double workingPressure, double workingFlow) {
        if (workingPressure >= totalHead) {
            throw new IllegalArgumentException(
                    "totalHead has to be higher than the working pressure.");
        }
        if (workingFlow <= 0.0) {
            throw new IllegalArgumentException(
                    "workingFlow has to be a positive value.");
        }
        if (workingPressure <= 0.0) {
            throw new IllegalArgumentException(
                    "workingPressure has to be a positive value.");
        }

        this.totalHead = totalHead;
        double flowResistance = (totalHead - workingPressure) / workingFlow;
        dischargeValve.setResistanceFullOpen(flowResistance);
    }

    public void initName(String name) {
        this.name = name;
        pump.setName(name + "Pump");
        dischargeValve.setName(name + "DischargeValve");
        dischargeNode.setName(name + "DischargeNode");
        dischargeMonitor.setName(name + "DischargeValve");
    }

    public void setInitialCondition(boolean pumpActive, boolean dischargeOpen) {
        if (dischargeOpen) {
            dischargeControl.forceOutputValue(105);
        }
        if (pumpActive) {
            pumpState = PumpState.RUNNING;
        } else {
            pumpState = PumpState.OFFLINE;
        }
        if (pumpActive) {
            pump.setEffort(totalHead);
        } else {
            pump.setEffort(0);
        }
        if (pumpActive && dischargeOpen) {
            state = 5;
        }
    }

    /**
     *
     * @param signalListener Instance that will receive the event changes from
     * valves and pumps.
     */
    public void registerSignalListener(PropertyChangeListener signalListener) {
        pcs.addPropertyChangeListener(signalListener);
        dischargeMonitor.addPropertyChangeListener(signalListener);
    }

    @Override
    public void run() {
        dischargeControl.run();

        if (pumpState == PumpState.RUNNING) {
            dischargeValve.setOpening(dischargeControl.getOutput());
        } else {
            // Such pumps always have a check valve. As we can not simulate or
            // calculate a check valve, just set this valve to closed to
            // mimic such a behaviour.
            dischargeValve.setOpening(0.0);
        }
        
        dischargeMonitor.setInput(dischargeControl.getOutput());

        dischargeMonitor.run();

        // Fire changes in pump state
        if (pumpState != oldPumpState) {
            pcs.firePropertyChange(pump.toString() + "_State",
                    oldPumpState, pumpState);
        }
        oldPumpState = pumpState;

        // get current time
        Instant now = Instant.now();

        if (switchOnTime == null) {
            ready = dischargeControl.getOutput() <= 1.0;
        } else {
            // if there is a switch-on-time recorded, some time must pass as 
            // its not allowed to turn on the pump immediately.
            ready = dischargeControl.getOutput() <= 1.0
                    && Duration.between(switchOnTime, now).toMillis() >= 10000;
        }

        switch (state) {
            case 0: // inactive
                if (ready) {
                    state = 1;
                    stateTime = Instant.now();
                }
                break;
            case 1: // ready time delay
                if (Duration.between(stateTime, now).toMillis() >= 1500) {
                    pumpState = PumpState.READY;
                    state = 2;
                }
            case 2: // Pump ready to be switched on
                if (!ready) { // no more ready state 
                    state = 0;
                    pumpState = PumpState.OFFLINE;
                }
                break;
            case 3: // Start the startup phase after short delay
                if (Duration.between(stateTime, now).toMillis() >= 800) {
                    stateTime = Instant.now();
                    pumpState = PumpState.STARTUP;
                    state = 4;
                } else if (!ready) { // abort
                    state = 0;
                    pumpState = PumpState.OFFLINE;
                }
                break;
            case 4: // Startup phase
                if (Duration.between(stateTime, now).toMillis() >= 3000) {
                    stateTime = Instant.now();
                    state = 5;
                    // Remember this time for restart lock time
                    switchOnTime = Instant.now();
                    // Switch on:
                    pumpState = PumpState.RUNNING;
                    pump.setEffort(totalHead);
                } else if (!ready) { // abort
                    state = 0;
                    pumpState = PumpState.OFFLINE;
                }
                break;
            case 5: // Pump is running
                // nothing to automate so far
                break;
        }
    }

    /**
     * Allows processing received events by the class itself. The Property name
     * must begin with the same name as this classes elements, which was
     * initialized with the initName function call.
     *
     * @param ac
     * @return true if event was processed by this instance.
     */
    public boolean handleAction(ActionCommand ac) {
        if (!ac.getPropertyName().startsWith(name)) {
            return false;
        } else if (ac.getPropertyName().equals(pump.toString())) {
            if ((boolean) ac.getValue()) {
                operateStartPump();
            } else {
                operateStopPump();
            }
            return true;
        } else if (ac.getPropertyName().equals(dischargeValve.toString())) {
            if ((boolean) ac.getValue()) {
                operateOpenDischargeValve();
            } else {
                operateCloseDischargeValve();
            }
            return true;
        }
        return false;
    }

    public void operateOpenDischargeValve() {
        dischargeControl.setInputMax();
    }

    public void operateCloseDischargeValve() {
        dischargeControl.setInputMin();
    }

    public void operateStartPump() {
        if (state == 2 && dischargeControl.getOutput() <= 1.0) {
            state = 3; // switch state machine
            stateTime = Instant.now();
        }
    }

    public void operateStopPump() {
        state = 0;
        pumpState = PumpState.OFFLINE;
        pump.setEffort(0.0);
    }
    
    public HeatEffortSource getPumpEffortSource() {
        return pump;
    }

    public HeatLinearValve getDischargeValve() {
        return dischargeValve;
    }
}
