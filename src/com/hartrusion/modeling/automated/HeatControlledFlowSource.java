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
package com.hartrusion.modeling.automated;

import com.hartrusion.control.ParameterHandler;
import com.hartrusion.control.Setpoint;
import com.hartrusion.control.ValveActuatorMonitor;
import java.beans.PropertyChangeListener;
import com.hartrusion.modeling.heatfluid.HeatFlowSource;
import com.hartrusion.mvc.ActionCommand;

/**
 * A controllable heat flow source, intended to have a setpoint integrator to
 * control a source flow setpoint value.
 * <p>
 * As it uses the Setpoint class, processing of commands for the setpoint class
 * can be used to control the value. Using the getSetpointObject method, actions
 * can be directed to the setpoint object itself.
 * <p>
 * It is intended to be used as a valve replacement
 *
 * @author Viktor Alexander Hartung
 */
public class HeatControlledFlowSource implements Runnable {

    private final HeatFlowSource source = new HeatFlowSource();
    private final Setpoint value = new Setpoint();
    private String name;
    private final ValveActuatorMonitor monitor
            = new ValveActuatorMonitor();

    /**
     * Updated output values (valve position) will be set to this parameter
     * handler.
     */
    private ParameterHandler outputValues;

    /**
     * Value in kg/s that will flow on 100 % valve position.
     */
    private double maxFlow = 80;

    public HeatControlledFlowSource() {
        value.setMaxRate(20);
        value.setUpperLimit(80);
        value.setLowerLimit(0.0);
    }

    public void initName(String name) {
        source.setName(name);
        value.initName(name);
        monitor.setName(name);
        this.name = name;
    }

    /**
     *
     * @param signalListener Instance that will receive the event changes from
     * valves and pumps.
     */
    public void registerSignalListener(PropertyChangeListener signalListener) {
        monitor.addPropertyChangeListener(signalListener);
    }

    /**
     * Sets a ParameterHandler that will get the valve position on each run
     * call.
     *
     * @param h reference to ParameterHandler
     */
    public void registerParameterHandler(ParameterHandler h) {
        outputValues = h;
    }

    /**
     * Sets the initial state flow.
     *
     * @param flow
     */
    public void initFlow(double flow) {
        value.forceOutputValue(flow);
        source.setFlow(flow);
    }

    @Override
    public void run() {
        value.run();
        source.setFlow(value.getOutput());
        
        double valvePosition = value.getOutput() / maxFlow * 100;

        if (outputValues != null) {
            // Send flow rate as 0..100 % to mimic valve position.
            outputValues.setParameterValue(name,valvePosition);
        }
        
        monitor.setInput(valvePosition);
        monitor.run();
    }

    public void initCharacteristic(double maxFlow, double time) {
        if (maxFlow <= 0.0) {
            throw new IllegalArgumentException(
                    "maxFlow must be a positive value.");
        }
        if (time <= 0.0) {
            throw new IllegalArgumentException(
                    "time must be a positive value.");
        }
        this.maxFlow = maxFlow;
        value.setUpperLimit(maxFlow);
        value.setMaxRate(maxFlow / time);
    }

    public HeatFlowSource getFlowSource() {
        return source;
    }

    public void setToMaxFlow() {
        value.setInputMax();
    }

    public void setToMinFlow() {
        value.setInputMin();
    }

    public void setStopAtCurrentFlow() {
        value.setStop();
    }

    /**
     * Allows access to the Setpoint class that is used to generate the value
     * for the flow source.
     *
     * @return
     */
    public Setpoint getSetpointObject() {
        return value;
    }

    public boolean handleAction(ActionCommand ac) {
        if (!ac.getPropertyName().equals(name)) {
            return false;
        }
        // Depending on what kind of switch is intended to be used, it can 
        // either be a signal for full close or open or some command that will
        // be integrated to the valve position.
        if (ac.getValue() instanceof Integer) {
            switch ((int) ac.getValue()) {
                case -1 ->
                    setToMinFlow();
                case +1 ->
                    setToMaxFlow();
                default ->
                    setStopAtCurrentFlow();
            }
        } else if (ac.getValue() instanceof Boolean) {
            if ((boolean) ac.getValue()) {
                setToMaxFlow();
            } else {
                setToMinFlow();
            }
        }
        return true;
    }
}
