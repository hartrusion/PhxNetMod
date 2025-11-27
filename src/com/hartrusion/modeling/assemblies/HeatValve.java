/*
 * Copyright (C) 2025 Viktor Alexander Hartung
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.modeling.assemblies;

import com.hartrusion.control.ParameterHandler;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import com.hartrusion.control.SetpointIntegrator;
import com.hartrusion.control.ValveActuatorMonitor;
import com.hartrusion.modeling.heatfluid.HeatLinearValve;
import com.hartrusion.mvc.ActionCommand;

/**
 * HeatLinearValve with SetpointIntegrator as actuator and a Monitor for firing
 * state change properties.
 * <p>
 * Received ActionCommands with the set name (to be set with initName) will be
 * used to control the value. Depending on the type, integers can be used to
 * perform opening or closing until a int 0 value is send. Boolean values will
 * either fully open or fully close the valve.
 * <p>
 * The attached monitor will fire property changes to all registered listeners
 * which are defined in
 *
 * @author Viktor Alexander Hartung
 */
public class HeatValve implements Runnable {

    /**
     * The valve element of the model itself.
     */
    protected final HeatLinearValve valve = new HeatLinearValve();
    
    /**
     * Generates limited values to mimic motor drive behavior.
     */
    protected final SetpointIntegrator swControl
            = new SetpointIntegrator();
    
    
    private final ValveActuatorMonitor monitor
            = new ValveActuatorMonitor();

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    
    /**
     * Updated output values (valve position) will be set to this parameter
     * handler.
     */
    private ParameterHandler outputValues;

    public HeatValve() {
        swControl.setMaxRate(25);
        swControl.setUpperLimit(100);
        swControl.setLowerLimit(-5.0); // safe closing
    }

    public void initName(String name) {
        valve.setName(name);
        monitor.setName(name);
    }

    /**
     * Makes the signal listener instance known to this class.
     *
     * @param signalListener Instance that will receive the event changes from
     * valves and pumps.
     */
    public void initSignalListener(PropertyChangeListener signalListener) {
        pcs.addPropertyChangeListener(signalListener);
        monitor.addPropertyChangeListener(signalListener);
    }

    /**
     * Initializes the valves characteristic.
     *
     * @param resistanceFullOpen Flow resistance on 100 % opening state, given
     * in Pa/kg*s
     * @param closedFactor set to less than 1.0 for default behavior, values
     * higher than 1.0 will be set as closedFactor of the valve.
     *
     */
    public void initCharacteristic(double resistanceFullOpen, double closedFactor) {
        valve.setResistanceFullOpen(resistanceFullOpen);
        if (closedFactor > 1.0) {
            valve.setCharacteristic(false, closedFactor);
        } else {
            valve.setCharacteristic(true, 0.0);
        }

    }

    public void initOpening(double opening) {
        valve.setOpening(opening);
        swControl.forceOutputValue(opening);
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
    public void run() {
        swControl.run();
        valve.setOpening(swControl.getOutput());
        monitor.setInput(swControl.getOutput());
        monitor.run();
        
         // Send valve position as parameter value for monitoring
        if (outputValues != null) {
            outputValues.setParameterValue(valve.toString(),
                    valve.getOpening());
        }
    }

    /**
     * Allows processing received events by the class itself. The Property name
     * must begin with the same name as this classes elements, which was
     * initialized with the initName function call.
     *
     * @param ac ActionCommand, will be further checked if it's matching.
     * @return true if event was processed by this instance.
     */
    public boolean handleAction(ActionCommand ac) {
        if (!ac.getPropertyName().equals(valve.toString())) {
            return false;
        }
        // Int values are sent from so-called Integral switches, as long as they
        // are pressed, value integrates. The press sends a +1 or -1 and the
        // release of the button sends a 0, but this is done with default.
        if (ac.getValue() instanceof Integer) {
            switch ((int) ac.getValue()) {
                case -1 ->
                    operateCloseValve();
                case +1 ->
                    operateOpenValve();
                default ->
                    stopValve();
            }
        } else if (ac.getValue() instanceof Boolean) {
            if ((boolean) ac.getValue()) {
                operateOpenValve();
            } else {
                operateCloseValve();
            }
        }
        return true;
    }

    public void operateOpenValve() {
        swControl.setInputMax();
    }

    public void operateCloseValve() {
        swControl.setInputMin();
    }

    public void operateSetOpening(double opening) {
        swControl.setInput(opening);
    }

    public void stopValve() {
        swControl.setStop();
    }

    public double getOpening() {
        return swControl.getOutput();
    }

    public HeatLinearValve getValveElement() {
        return valve;
    }
    
    public SetpointIntegrator getIntegrator() {
        return swControl;
    }

}
