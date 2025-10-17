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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import com.hartrusion.control.SetpointIntegrator;
import com.hartrusion.control.ValveActuatorMonitor;
import com.hartrusion.modeling.heatfluid.HeatEffortSource;
import com.hartrusion.modeling.heatfluid.HeatLinearValve;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.mvc.ActionCommand;

/**
 * Represents an assembly of a pump with suction and discharge valves. A working
 * point has to be specified as well as the total head (all in SI units), the
 * assembly wil generate a linear characteristic by those values.
 * <p>
 * It has some methods to allow controlling it and can process events itself.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFluidPump implements Runnable {

    private final HeatLinearValve suctionValve = new HeatLinearValve();
    private final HeatLinearValve dischargeValve = new HeatLinearValve();
    protected final HeatEffortSource pump = new HeatEffortSource();
    private final HeatNode suctionNode = new HeatNode();
    private final HeatNode dischargeNode = new HeatNode();

    protected final SetpointIntegrator suctionControl
            = new SetpointIntegrator();
    protected final SetpointIntegrator dischargeControl
            = new SetpointIntegrator();

    private final ValveActuatorMonitor suctionMonitor
            = new ValveActuatorMonitor();
    private final ValveActuatorMonitor dischargeMonitor
            = new ValveActuatorMonitor();

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    protected boolean pumpState = false;
    protected boolean oldPumpState = false;

    private double totalHead;

    String name;

    public HeatFluidPump() {
        suctionValve.connectToVia(pump, suctionNode);
        dischargeValve.connectToVia(pump, dischargeNode);

        suctionControl.setMaxRate(15);
        suctionControl.setUpperLimit(100);
        suctionControl.setLowerLimit(-5.0);
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
        suctionValve.setResistanceFullOpen(flowResistance * 0.5);
        dischargeValve.setResistanceFullOpen(flowResistance * 0.5);
    }

    public void initName(String name) {
        this.name = name;
        suctionValve.setName(name + "SuctionValve");
        pump.setName(name + "Pump");
        dischargeValve.setName(name + "DischargeValve");
        suctionNode.setName(name + "SuctionNode");
        dischargeNode.setName(name + "DischargeNode");
        suctionMonitor.setName(name + "SuctionValve");
        dischargeMonitor.setName(name + "DischargeValve");
    }

    public void setInitialCondition(boolean pumpActive, boolean suctionOpen,
            boolean dischargeOpen) {
        if (dischargeOpen) {
            dischargeControl.forceOutputValue(105);
        }
        if (suctionOpen) {
            suctionControl.forceOutputValue(105);
        }
        pumpState = pumpActive;
        if (pumpActive) {
            pump.setEffort(totalHead);
        } else {
            pump.setEffort(0);
        }
    }

    /**
     *
     * @param signalListener Instance that will receive the event changes from
     * valves and pumps.
     */
    public void initSignalListener(PropertyChangeListener signalListener) {
        pcs.addPropertyChangeListener(signalListener);
        suctionMonitor.addPropertyChangeListener(signalListener);
        dischargeMonitor.addPropertyChangeListener(signalListener);
    }

    @Override
    public void run() {
        suctionControl.run();
        dischargeControl.run();

        suctionValve.setOpening(suctionControl.getOutput());

        if (pumpState) {
            dischargeValve.setOpening(dischargeControl.getOutput());
        } else {
            // Such pumps always have a check valve. As we can not simulate or
            // calculate a check valve, just set this valve to closed to
            // mimic such a behaviour.
            dischargeValve.setOpening(0.0);
        }

        suctionMonitor.setInput(suctionControl.getOutput());
        dischargeMonitor.setInput(dischargeControl.getOutput());

        suctionMonitor.run();
        dischargeMonitor.run();

        if (pumpState != oldPumpState) {
            pcs.firePropertyChange(pump.toString() + "_State",
                    oldPumpState, pumpState);
        }
        oldPumpState = pumpState;
    }

    /**
     * Allows proessing received events by the class itself. The Property name
     * must beginn with the same name as this classes elements, which was 
     * initialized with the initName function call.
     *
     * @param ac
     * @return true if event was processed by this instance.
     */
    public boolean updateProperty(ActionCommand ac) {
        if (!ac.getPropertyName().startsWith(name)) {
            return false;
        } else if (ac.getPropertyName().equals(suctionValve.toString())) {
            if ((boolean) ac.getValue()) {
                operateOpenSuctionValve();
            } else {
                operateCloseSuctionValve();
            }
            return true;
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

    public void operateOpenSuctionValve() {
        suctionControl.setInputMax();
    }

    public void operateCloseSuctionValve() {
        suctionControl.setInputMin();
    }

    public void operateOpenDischargeValve() {
        dischargeControl.setInputMax();
    }

    public void operateCloseDischargeValve() {
        dischargeControl.setInputMin();
    }

    public void operateStartPump() {
        if (dischargeControl.getOutput() > 1.0) {
            return; // Protection: not start if discharge is not closed.
        }
        pumpState = true;
        pump.setEffort(totalHead);
    }

    public void operateStopPump() {
        pumpState = false;
        pump.setEffort(0.0);
    }

    public HeatLinearValve getSuctionValve() {
        return suctionValve;
    }

    public HeatLinearValve getDischargeValve() {
        return dischargeValve;
    }
}
