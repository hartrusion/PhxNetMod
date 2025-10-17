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
import com.hartrusion.modeling.general.EffortSource;

/**
 * Adds the capability to handle temperature changes by adding thermal elements
 * to the phased domain. It serves as the core for the HeatExchanger.
 *
 * <p>
 * Even if the heat exchanger element is limited to be a flow through element,
 * this handler can handle n connections to make it more versatile for other
 * usages.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedThermalVolumeHandler extends PhasedAbstractVolumizedHandler {

    /**
     * Reference to the effort source representing the temperature for the
     * thermal flow.
     */
    EffortSource thermalSource;

    private double stepTime = 0.1;

    /**
     * Effort value from the previous cycle. To be able to provide a full
     * solution we need to set a temperature which we only can set using this
     * previous value. The fluidProperties are pressure dependend.
     */
    private double previousPressure;

    public PhasedThermalVolumeHandler(PhasedFluidProperties fluidProperties,
            PhasedElement parent) {
        super(fluidProperties, parent);
    }

    @Override
    public void preparePhasedCalculation() {
        super.preparePhasedCalculation();
        // Set the temperature as effort on the temperature source, making the 
        // connection between the two domains.
        thermalSource.setEffort(
                fluidProperties.getTemperature(heatEnergy, previousPressure));
    }

    @Override
    public boolean doPhasedCalculation() {
        boolean didSomething = super.doPhasedCalculation();
        double flow;
        double flowIn;
        double heatedVolumeFlowIn; // mass times Temperature

        // all updated flows leaving this element will get the heat energy
        // assigned towards those ports, resulting them in beeing updated.
        // This does NOT consider incoming heat energy and produces a delay.
        for (PhasedNode pn : phasedNodes) { // all ports connected to element
            if (pn.flowUpdated((AbstractElement) element)
                    & !pn.heatEnergyUpdated((AbstractElement) element)) {
                flow = pn.getFlow((AbstractElement) element);
                if (flow == 0.0) {
                    // special: flow is exactly zero
                    pn.setNoHeatEnergy((AbstractElement) element);
                    didSomething = true;
                } else if (flow < 0.0) {
                    // negative: flow is leaving this element, so it flows
                    // out with current elements heat energy.
                    pn.setHeatEnergy(heatEnergy, (AbstractElement) element);
                    didSomething = true;
                }
            }
        }

        // try to calculate new internal Temperature. for this, all flows and 
        // temperatures and the external heat flow need to be known. The heat
        // flow is a value of the openOrigin
        if (!heatEnergyPrepared && thermalSource.flowUpdated()
                && allHeatEnergiesUpdated() && allFlowsUpdated()) {
            heatedVolumeFlowIn = 0.0;
            flowIn = 0.0;
            // sum up thermal energy going into element
            for (PhasedNode tp : phasedNodes) {
                flow = tp.getFlow((AbstractElement) element);
                if (flow > 0.0) { // positive: into element
                    flowIn += flow * stepTime; // sum up
                    if (!tp.noHeatEnergy((AbstractElement) element)) {
                        // Same situation as above, we need to explitly check
                        // if we have a flow due to numeric issue.
                        heatedVolumeFlowIn += flow * stepTime
                                * (tp.getHeatEnergy(
                                        (AbstractElement) element));
                    }
                }
            }
            // Depending on values, different equations are used. If there is
            // no heat exchange or whatsoever, there will be no calculations
            // performed to preserve exact bit values of the physical values.
            if (flowIn > 0.0 && thermalSource.getFlow() != 0.0) {
                nextHeatEnergy = (innerHeatMass * heatEnergy
                        + heatedVolumeFlowIn
                        - thermalSource.getFlow() * stepTime)
                        / (innerHeatMass + flowIn);
            } else if (flowIn > 0.0 && thermalSource.getFlow() == 0.0) {
                nextHeatEnergy = (innerHeatMass * heatEnergy
                        + heatedVolumeFlowIn) / (innerHeatMass + flowIn);
            } else if (flowIn == 0.0 && thermalSource.getFlow() != 0.0) {
                nextHeatEnergy = (innerHeatMass * heatEnergy
                        - thermalSource.getFlow() * stepTime)
                        / innerHeatMass;
            } else {
                nextHeatEnergy = heatEnergy;
            }

            heatEnergyPrepared = true;
            didSomething = true;
        }
        return didSomething;
    }

    /**
     * Makes the thermal source element, which acts as a connection to the
     * thermal domain, known to this handler.
     *
     * @param element
     */
    public void setThermalEffortSource(EffortSource element) {
        thermalSource = element;
    }
}
