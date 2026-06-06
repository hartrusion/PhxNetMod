/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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

import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFrictionedHandler implements HeatHandler {

    /**
     * Holds a list of all nodes connected to the element where this heat
     * handler is assigned.
     */
    private final List<HeatNode> heatNodes;

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final AbstractElement element;

    /**
     * Volumetric heat capacity (rho * specHeatCap) used to calculate the
     * temperature increase. Default setting is water.
     */
    private double volHeatCap = 4200000;

    HeatFrictionedHandler(HeatElement parent) {
        heatNodes = new ArrayList<>();
        element = (AbstractElement) parent;
    }

    @Override
    public boolean registerHeatNode(HeatNode tp) {
        if (!heatNodes.contains(tp)) {
            if (heatNodes.size() >= 2) {
                throw new ModelErrorException("Only 2 nodes are supported "
                        + "for this kind of handler.");
            }
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
                        && hn.getFlow(element) == 0.0;
            }
            if (allFlowsZero) {
                // check non-updated heat nodes and set them to notemperature.
                for (HeatNode tp : heatNodes) { // all nodes connected to eleme
                    if (!tp.temperatureUpdated(element)) {
                        tp.setNoTemperature(element);
                        didSomething = true;
                    }
                }
                return didSomething; // terminate further execution of mehtod
            }

            // Determine direction
            HeatNode inNode = null, outNode = null;
            for (HeatNode hn : heatNodes) { // all nodes connected to element
                flow = hn.getFlow(element);
                if (flow < 0.0) {
                    outNode = hn;
                } else if (flow > 0.0) {
                    inNode = hn;
                }
            }

            // Lets hope no more numeric suprises come up here but check it:
            if (outNode == null || inNode == null) {
                throw new CalculationException("Unable to determine nodes "
                        + "for both directions?");
            }

            if (outNode.temperatureUpdated(element)
                    && inNode.temperatureUpdated(element)) {
                return didSomething; // already finished
            }

            if (!inNode.temperatureUpdated(element)) {
                return didSomething; // wait for temperature
            }

            if (inNode.noTemperature(element)) {
                // No temperature value: pass this property and do not calculate
                // anything.
                outNode.setNoTemperature(element);
                didSomething = true;
            } else {
                // Calculate the temperature increase value
                double tempIncr = (inNode.getEffort() - outNode.getEffort())
                        / volHeatCap;

                // Set the increased temperature value to the out node
                outNode.setTemperature(inNode.getTemperature(element) + tempIncr,
                        element);
                didSomething = true;
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
                    && tp.flowUpdated(element);
        }
        return allUpdated;
    }

    @Override
    public boolean isHeatCalulationFinished() {
        boolean allUpdated = true;
        for (HeatNode tp : heatNodes) {
            allUpdated = allUpdated
                    && tp.temperatureUpdated(element);
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

    public void setVolHeatCap(double volHeatCap) {
        this.volHeatCap = volHeatCap;
    }

}
