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
package com.hartrusion.modeling.heatfluid;

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;

/**
 * Handles temperature managment as an object assigned to a heated element. An
 * instance of this object is definded in each heat element. This heat handler
 * does not support heat capacity for the element that is using it, therefore it
 * will just calculate temperature distribution and pass everything through as
 * soon as it is possible - and if it is possible at all.
 *
 * <p>
 * Temperature values of an element using this node will be strictly only be
 * saved in heat nodes connected to the element.
 *
 * <p>
 * This heat handler does simply mix all incoming heat flows towards the element
 * using this and sets a mixing temperature toward nodes which have flows which
 * leave the element.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatSimpleHandler implements HeatHandler {

    /**
     * Holds a list of all nodes connected to the element where this heat
     * handler is assigned.
     */
    private final List<HeatNode> heatNodes;

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final HeatElement element;

    HeatSimpleHandler(HeatElement parent) {
        heatNodes = new ArrayList<>();
        element = parent;
    }

    @Override
    public boolean registerHeatNode(HeatNode tp) {
        if (!heatNodes.contains(tp)) {
            heatNodes.add(tp);
            return true;
        }
        return false;
    }

    @Override
    public void prepareHeatCalculation() {
        // nothign to do here
    }

    @Override
    public boolean doThermalCalculation() {
        boolean didSomething = false;
        double flow;
        boolean allFlowsZero = true;
        double flowIn = 0.0;
        double heatedVolumeFlowIn = 0.0;
        double mixedInTemp;

        if (allFlowsUpdated()) {
            // First of all, if there is no flow at all (valves closed etc),
            // update the model properly to handle this situation.
            for (HeatNode hn : heatNodes) { // all nodes connected to element
                allFlowsZero = allFlowsZero
                        && hn.getFlow((AbstractElement) element) == 0.0;
            }
            if (allFlowsZero) {
                // check non-updated heat nodes and set them to notemperature.
                for (HeatNode tp : heatNodes) { // all nodes connected to eleme
                    if (!tp.temperatureUpdated((AbstractElement) element)) {
                        tp.setNoTemperature((AbstractElement) element);
                        didSomething = true;
                    }
                }
                return didSomething; // terminate further execution of mehtod
            }

            // Calculate weighted mixture temperature from all incoming flows
            for (HeatNode hn : heatNodes) { // all nodes connected to element
                flow = hn.getFlow((AbstractElement) element);
                if (flow <= 0.0) {
                    continue; // flow is zero or leaving element
                }
                // all temperatures of incoming flow needs to be updated
                // to calculate something out of them
                if (!hn.temperatureUpdated((AbstractElement) element)) {
                    return false; // if one is missing, terminate.
                }
                flowIn = flowIn + flow; // sum up
                if (hn.noTemperature((AbstractElement) element)) {
                    // Due to numerical issues it is possible to have a flow, 
                    // like with a value of 1e-16, that is practically zero and
                    // therefore has a noTemperature state. Ignore those.
                    continue;
                }
                heatedVolumeFlowIn = heatedVolumeFlowIn // also sum up V.*T
                        + flow * (hn.getTemperature((AbstractElement) element));
            }
            // calculate mixture temperature
            mixedInTemp = heatedVolumeFlowIn / flowIn;
            for (HeatNode hn : heatNodes) { // all nodes connected to element
                flow = hn.getFlow((AbstractElement) element);
                if (flow > 0.0) {
                    continue; // this flow goes in
                }
                if (!hn.temperatureUpdated((AbstractElement) element)) {
                    hn.setTemperature(mixedInTemp, (AbstractElement) element);
                    didSomething = true;
                }
            }
        }
        return didSomething;
    }

    /**
     * Checks if all flow values from nodes toward the element which is assigned
     * to this handler are updated. This private method just provides a small
     * shortcut for used internally.
     *
     * @return
     */
    private boolean allFlowsUpdated() {
        boolean allUpdated = true;
        for (HeatNode tp : heatNodes) {
            allUpdated = allUpdated
                    && tp.flowUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    @Override
    public boolean isHeatCalulationFinished() {
        boolean allUpdated = true;
        for (HeatNode tp : heatNodes) {
            allUpdated = allUpdated
                    && tp.temperatureUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    // Throw exeptions in case of temperature is set or asked, this is only
    // possible if the handler actually has a temperature, which this one
    // doesnt.
    @Override
    public void setInitialTemperature(double t) {
        throw new NonexistingStateVariableException(
                "Simple Heat handler does not have its own temperature");
    }

    @Override
    public double getTemperature() {
        throw new NonexistingStateVariableException(
                "Simple Heat handler does not have its own temperature");
    }

    @Override
    public void setInnerThermalMass(double storedMass) {
        throw new NonexistingStateVariableException(
                "Simple Heat handler does not support heat volume.");
    }

}
