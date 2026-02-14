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
package com.hartrusion.modeling.phasedfluid;

import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedLimitSimpleHandler implements PhasedHandler {

    /**
     * Holds a list of all nodes connected to the element where this phased
     * handler is assigned.
     */
    private final List<PhasedNode> phasedNodes;

    /**
     * Reference to the element which is using this phased handler instance.
     */
    private final PhasedElement element;

    private double vaporFraction = 1.0;
    private double excessEnergy;

    PhasedLimitSimpleHandler(PhasedElement parent) {
        phasedNodes = new ArrayList<>();
        element = parent;
    }

    @Override
    public boolean registerPhasedNode(PhasedNode pn) {
        if (!phasedNodes.contains(pn)) {
            phasedNodes.add(pn);
            return true;
        }
        return false;
    }

    @Override
    public void preparePhasedCalculation() {
        // nothing to do here
    }

    @Override
    public boolean doPhasedCalculation() {
        boolean didSomething = false;
        double flow;
        boolean allFlowsZero = true;
        PhasedNode inNode = null, outNode = null;
        double inHeatEnergy;

        if (allFlowsUpdated()) {
            // First of all, if there is no flow at all (valves closed etc),
            // update the model properly to handle this situation.
            for (PhasedNode hn : phasedNodes) { // all nodes connected to elem
                allFlowsZero = allFlowsZero
                        && hn.getFlow((AbstractElement) element) == 0.0;
            }
            if (allFlowsZero) {
                // check non-updated heat nodes and set them to noHeatEnergy.
                for (PhasedNode pn : phasedNodes) { // all nodes connected
                    if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                        pn.setNoHeatEnergy((AbstractElement) element);
                        didSomething = true;
                    }
                }
                return didSomething; // terminate further execution of mehtod
            }

            // Get both nodes which are the in and out nodes regarding flow
            for (PhasedNode pn : phasedNodes) { // all nodes connected to elem
                flow = pn.getFlow((AbstractElement) element);
                if (flow <= 0.0) {
                    outNode = pn;
                } else if (flow >= 0.0) {
                    inNode = pn;
                }
            }

            if (outNode == null || inNode == null) {
                return didSomething; // prevent warnings here
            }

            if (outNode.heatEnergyUpdated((AbstractElement) element)) {
                return didSomething; // finished, nothing more to do here.
            }

            if (!inNode.heatEnergyUpdated((AbstractElement) element)) {
                return didSomething; // nothing to do if this is unknown yet
            }
            
            if (inNode.noHeatEnergy((AbstractElement) element)) {
                // Problem: There might be no heat energy set even with a set 
                // flow direction, this is a result of numeric calculation 
                // errors and a failure to detect them. In this case, we just
                // propagate the no-energy state to keep the model running.
                outNode.setNoHeatEnergy((AbstractElement) element);
                return true;
            }
                  
            inHeatEnergy = inNode.getHeatEnergy((AbstractElement) element);

            // Calculate the heat energy for the out node for a given 
            // vapor fraction, this value will be compared to the energy that
            // could be available from the inNode.
            double referenceHeatEnergy = element.getPhasedFluidProperties()
                    .getLiquidHeatCapacity(outNode.getEffort())
                    + element.getPhasedFluidProperties()
                            .getVaporizationHeatEnergy() * vaporFraction;

            // Limit the energy to the given reference value and provide this
            // as excess energy value.
            if (inHeatEnergy > referenceHeatEnergy) {
                excessEnergy = inHeatEnergy - referenceHeatEnergy;
                outNode.setHeatEnergy(referenceHeatEnergy,
                        (AbstractElement) element);
            } else {
                excessEnergy = 0;
                outNode.setHeatEnergy(inHeatEnergy,
                        (AbstractElement) element);
            }
            didSomething = true;
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
        for (PhasedNode pn : phasedNodes) {
            allUpdated = allUpdated
                    && pn.flowUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    @Override
    public boolean isPhasedCalulationFinished() {
        boolean allUpdated = true;
        for (PhasedNode pn : phasedNodes) {
            allUpdated = allUpdated
                    && pn.heatEnergyUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    @Override
    public void setInitialHeatEnergy(double q) {
        throw new NonexistingStateVariableException(
                "Simple phased handler does not support heat volume.");
    }

    @Override
    public double getHeatEnergy() {
        throw new NonexistingStateVariableException(
                "Simple phased handler does not support heat volume.");
    }

    @Override
    public void setInnerHeatedMass(double heatedMass) {
        throw new NonexistingStateVariableException(
                "Simple phased handler does not support heat volume.");
    }

    public void setOutVaporFraction(double vaporFraction) {
        this.vaporFraction = vaporFraction;
    }
    
    public double getExcessEnergy() {
        return excessEnergy;
    }
}
