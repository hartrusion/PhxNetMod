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

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import com.hartrusion.control.SetpointIntegrator;
import com.hartrusion.modeling.heatfluid.HeatFlowSource;

/**
 * A controllable heat flow source, intended to have a setpoint integrator to
 * control a source flow setpoint value.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatControlledFlowSource implements Runnable {

    private final HeatFlowSource source = new HeatFlowSource();
    private final SetpointIntegrator control
            = new SetpointIntegrator();
//    private final ValveActuatorMonitor monitor
//            = new ValveActuatorMonitor();
//    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public HeatControlledFlowSource() {
        control.setMaxRate(50);
        control.setUpperLimit(1000);
        control.setLowerLimit(0.0);
    }

    public void initName(String name) {
        source.setName(name);
        // monitor.setName(name);
    }

    /**
     *
     * @param signalListener Instance that will receive the event changes from
     * valves and pumps.
     */
    public void initSignalListener(PropertyChangeListener signalListener) {
//        pcs.addPropertyChangeListener(signalListener);
//        monitor.addPropertyChangeListener(signalListener);
    }

    /**
     * Sets the initial state flow.
     *
     * @param flow
     */
    public void initFlow(double flow) {
        control.forceOutputValue(flow);
        source.setFlow(flow);
    }

    @Override
    public void run() {
        control.run();
        source.setFlow(control.getOutput());
//        monitor.setInput(swControl.getOutput());
//        monitor.run();
    }

    public HeatFlowSource getFlowSource() {
        return source;
    }

    public void setToMaxFlow() {
        control.setInputMax();
    }

    public void setToMinFlow() {
        control.setInputMin();
    }

    public void setStopAtCurrentFlow() {
        control.setStop();
    }
}
