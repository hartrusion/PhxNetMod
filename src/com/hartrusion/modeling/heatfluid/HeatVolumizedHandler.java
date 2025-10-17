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

import com.hartrusion.modeling.general.AbstractElement;

/**
 * Handles temperature managment as an object inside heated elements. An
 * instance of this object is definded in each heat element. Every element has a
 * thermal capacity, no matter if the generalized dynamics of this element has a
 * capability of storing flow value. This represents the actual ability of all
 * real world elements to store thermal energy.
 *
 * This handler will set its own stored temperature to all flows leaving the
 * element, as soon as flows are known. The temperature is therefore delayed by
 * the capability of storing the temperature.
 *
 * The inner thermal volume must be of the same unit as the integral of the flow
 * value (if flow is in m^3/s, the thermal volume will be set in m^3).
 *
 * @author Viktor Alexander Hartung
 */
public class HeatVolumizedHandler extends HeatAbstractVolumizedHandler {

    public HeatVolumizedHandler(HeatElement parent) {
        super(parent);
    }

    @Override
    public boolean doThermalCalculation() {
        boolean didSomething = super.doThermalCalculation();
        double flow;
        double flowIn;
        double heatedVolumeFlowIn;

        // all updated flows leaving this element will get the temperature
        // assigned towards those nodes, resulting them in beeing updated.
        // This does NOT consider incoming temperatures and produces a delay.
        for (HeatNode hn : heatNodes) { // all nodes connected to element
            if (hn.flowUpdated((AbstractElement) element)
                    & !hn.temperatureUpdated((AbstractElement) element)) {
                flow = hn.getFlow((AbstractElement) element);
                if (flow == 0.0) {
                    // special: flow is exactly zero
                    hn.setNoTemperature((AbstractElement) element);
                    didSomething = true;
                } else if (flow < 0.0) {
                    // negative: flow is leaving this element, so it flows
                    // out with current elements temperature.
                    hn.setTemperature(temperature, (AbstractElement) element);
                    didSomething = true;
                }
            }
        }

        if (!temperaturePrepared
                && allTempsUpdated() && allFlowsUpdated()) {
            heatedVolumeFlowIn = 0.0;
            flowIn = 0.0;
            // sum up thermal energy going into element
            for (HeatNode hn : heatNodes) {
                flow = hn.getFlow((AbstractElement) element);
                if (flow > 0.0) { // positive: into element
                    // It is possible that a flow exists in terms of double
                    // precision issues but the temperature is in a non-
                    // temperature state. Simply skip these values and accept
                    // that there will always be uncertaincies.
                    if (hn.noTemperature((AbstractElement) element)) {
                        continue;
                    }
                    flowIn = flowIn + flow * stepTime; // sum up
                    heatedVolumeFlowIn = heatedVolumeFlowIn + flow * stepTime
                            * (hn.getTemperature((AbstractElement) element));
                }
            }

            if (flowIn > 0.0) {
                nextTemperature = (innerThermalMass * temperature
                        + heatedVolumeFlowIn) / (innerThermalMass + flowIn);
            } else {
                nextTemperature = temperature;
            }

            temperaturePrepared = true;
            didSomething = true;
        }
        return didSomething;
    }

}
