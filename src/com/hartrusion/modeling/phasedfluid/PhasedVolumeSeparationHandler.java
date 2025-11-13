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

import com.hartrusion.modeling.general.AbstractElement;

/**
 * Splits the phased fluid according to the set kind of node, either steam or
 * liquid. The assumption is that the stored fluid has no energy stored in
 * steam phase.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedVolumeSeparationHandler
        extends PhasedAbstractVolumizedHandler {

    public PhasedVolumeSeparationHandler(PhasedFluidProperties fluidProperties,
            PhasedElement parent) {
        super(fluidProperties, parent);
    }

    @Override
    public boolean doPhasedCalculation() {
        double flow, heatedVolumeFlowIn, flowIn;
        boolean didSomething = super.doPhasedCalculation();

        // all flows leaving will get the heat energy assigned.
        for (PhasedNode pn : phasedNodes) { // all nodes connected to element
            if (pn.flowUpdated((AbstractElement) element)
                    & !pn.heatEnergyUpdated((AbstractElement) element)) {
                flow = pn.getFlow((AbstractElement) element);
                if (flow == 0.0) {
                    // special: flow is exactly zero
                    pn.setNoHeatEnergy((AbstractElement) element);
                    didSomething = true;
                } else if (flow < 0.0) {
                    // negative: flow is leaving this element
                    if (isSteamOutput[phasedNodes.indexOf(pn)]) {
                        pn.setHeatEnergy(
                                nextHeatEnergy 
                                + fluidProperties.getVaporizationHeatEnergy(),
                                (AbstractElement) element);
                    } else {
                        pn.setHeatEnergy(nextHeatEnergy,
                                (AbstractElement) element);
                    }

                    didSomething = true;
                }
            }
        }

        if (!heatEnergyPrepared
                && allHeatEnergiesUpdated() && allFlowsUpdated()) {
            heatedVolumeFlowIn = 0.0;
            flowIn = 0.0;
            // sum up thermal energy going into element
            for (PhasedNode pn : phasedNodes) {
                flow = pn.getFlow((AbstractElement) element);
                // It is possible that a flow exists in terms of double
                // precision issues but the heat energy is in a non-present
                // state. Simply skip these values and accept
                // that there will always be uncertainties.
                if (pn.noHeatEnergy((AbstractElement) element)) {
                    continue;
                }
                flowIn = flowIn + flow * stepTime; // sum up
                heatedVolumeFlowIn = heatedVolumeFlowIn + flow * stepTime
                        * (pn.getHeatEnergy((AbstractElement) element));
            }
            if (flowIn != 0.0) {
                nextHeatEnergy = (innerHeatMass * heatEnergy
                        + heatedVolumeFlowIn) / (innerHeatMass + flowIn);
            } else {
                nextHeatEnergy = heatEnergy;
            }

            heatEnergyPrepared = true;
            didSomething = true;
        }
        return didSomething;
    }
}
