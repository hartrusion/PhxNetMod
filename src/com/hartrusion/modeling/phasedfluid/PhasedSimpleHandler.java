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
package com.hartrusion.modeling.phasedfluid;

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;

/**
 * Handles heat energy managment as an object assigned to a phased element. An
 * instance of this object is definded in each phased element. This phased
 * handler does not support heat energy capacity for the element that is using
 * it, therefore it will just calculate heat energy distribution and pass
 * everything through as soon as it is possible - and if it is possible at all.
 *
 * <p>
 * Heat energy values of an element using this node will be strictly only be
 * saved in phased nodes connected to the element.
 *
 * <p>
 * This phased handler does simply mix all incoming heat flows towards the
 * element using this and sets a mixing heat energy toward nodes which have
 * flows which leave the element.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedSimpleHandler implements PhasedHandler {

    /**
     * Holds a list of all nodes connected to the element where this phased
     * handler is assigned.
     */
    private final List<PhasedNode> phasedNodes;

    /**
     * Reference to the element which is using this phased handler instance.
     */
    private final PhasedElement element;

    PhasedSimpleHandler(PhasedElement parent) {
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
        double flowIn = 0.0;
        double phasedVolumeFlowIn = 0.0;
        double mixedInHeat;

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

            // Calculate weighted mixture heat energy from all incoming flows
            for (PhasedNode pn : phasedNodes) { // all nodes connected to elem
                flow = pn.getFlow((AbstractElement) element);
                if (flow <= 0.0) {
                    continue; // flow is zero or leaving element
                }
                // all temperatures of incoming flow needs to be updated
                // to calculate something out of them
                if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                    return false; // if one is missing, terminate.
                }
                flowIn = flowIn + flow; // sum up
                if (pn.noHeatEnergy((AbstractElement) element)) {
                    // Due to numerical issues it is possible to have a flow, 
                    // like with a value of 1e-16, that is practically zero and
                    // therefore has a noHeatEnergy state. Ignore those.
                    continue;
                }
                phasedVolumeFlowIn = phasedVolumeFlowIn // also sum up V.*T
                        + flow * (pn.getHeatEnergy((AbstractElement) element));
            }
            // calculate specific mixture heat energy
            mixedInHeat = phasedVolumeFlowIn / flowIn;
            for (PhasedNode pn : phasedNodes) { // all nodes connected to element
                flow = pn.getFlow((AbstractElement) element);
                if (flow > 0.0) {
                    continue; // this flow goes in
                }
                if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                    pn.setHeatEnergy(mixedInHeat, (AbstractElement) element);
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

}
