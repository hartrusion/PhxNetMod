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

import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;

/**
 * Adds the capability to handle temperature changes by adding thermal elements
 * to the heat domain. It serves as the core for the HeatExchanger. It gets a
 * bit more complicated as it may has to handle some variables from a connected
 * thermal gradient log resistance element.
 *
 * <p>
 * Even if the heat exchanger element is limited to be a flow through element,
 * this handler can handle n connections to make it more versatile for other
 * usages.
 *
 * @author Viktor Alexander Hartung
 */
public final class HeatThermalVolumeHandler extends HeatAbstractVolumizedHandler {

    /**
     * Reference to the effort source representing the temperature for the
     * thermal flow.
     */
    EffortSource thermalSource;

    double heatFlowIn;
    double massflowIn;
    boolean noFlowIn;
    boolean inValuesUpdated;

    double specHeatCap = 4200; // J/kg/K - Init for water

    public HeatThermalVolumeHandler(HeatElement parent) {
        super(parent);
    }

    @Override
    public void prepareHeatCalculation() {
        super.prepareHeatCalculation(); // sets nextTemperature as temperature
        massflowIn = 0.0;
        heatFlowIn = 0.0;
        noFlowIn = false;
        inValuesUpdated = false;
        // Set the temperature as effort on the temperature source, representing 
        // a connection between the two domains.
        thermalSource.setEffort(temperature);
    }

    @Override
    public boolean doThermalCalculation() {
        boolean didSomething = super.doThermalCalculation();
        double flow;
        double flowIn;
        double heatedVolumeFlowIn; // mass times Temperature

        // all updated flows leaving this element will get the temperature
        // assigned towards those ports, resulting them in beeing updated.
        // This does NOT consider incoming temperatures and produces a delay.
        for (HeatNode tp : heatNodes) { // all ports connected to element
            if (tp.flowUpdated((AbstractElement) element)
                    & !tp.temperatureUpdated((AbstractElement) element)) {
                flow = tp.getFlow((AbstractElement) element);
                if (flow == 0.0) {
                    // special: flow is exactly zero
                    tp.setNoTemperature((AbstractElement) element);
                    didSomething = true;
                } else if (flow < 0.0) {
                    // negative: flow is leaving this element, so it flows
                    // out with current elements temperature.
                    tp.setTemperature(temperature, (AbstractElement) element);
                    didSomething = true;
                }
            }
        }

        // For the thermalLogGradientResistance, this temperature value needs
        // to be known. As we can have multiple inlets, we sum up and make a
        // weighted middle value. 
        if (!inValuesUpdated && allInboundTempsUpdated()) {
            flowIn = 0.0;
            heatedVolumeFlowIn = 0.0;
            noFlowIn = true;
            for (HeatNode tp : heatNodes) {
                flow = tp.getFlow((AbstractElement) element);
                if (flow > 0.0) {
                    flowIn += flow; // m
                    // Theroetically, there should be a temperature assigned to
                    // the flow. Practically there can be a next-to-zero-flow
                    // due to numeric limitations that is detected as a flow
                    // but the flow actually does not exist, then we have some
                    // kind of 1e-14 flow but the "noTemperature" setting.
                    if (!tp.noTemperature((AbstractElement) element)) {
                        heatedVolumeFlowIn += flow // m * T
                                * tp.getTemperature((AbstractElement) element);
                    }
                    noFlowIn = false;
                }
            }
            massflowIn = flowIn;
            heatFlowIn = heatedVolumeFlowIn;
            inValuesUpdated = true;
            didSomething = true;
        }

        // try to calculate new internal Temperature. for this, all flows and 
        // temperatures and the external heat flow need to be known. The heat
        // flow is a value of the openOrigin
        if (!temperaturePrepared && thermalSource.flowUpdated()
                && allTempsUpdated() && allFlowsUpdated()) {
            heatedVolumeFlowIn = 0.0;
            flowIn = 0.0;
            // sum up thermal energy going into element
            for (HeatNode tp : heatNodes) {
                flow = tp.getFlow((AbstractElement) element);
                if (flow > 0.0) { // positive: into element
                    flowIn += flow * stepTime; // sum up
                    if (!tp.noTemperature((AbstractElement) element)) {
                        // Same situation as above, we need to explitly check
                        // if we have a flow due to numeric issue.
                        heatedVolumeFlowIn += flow * stepTime
                                * (tp.getTemperature(
                                        (AbstractElement) element));
                    }
                }
            }
            // Depending on values, different equations are used. If there is
            // no heat exchange or whatsoever, there will be no calculations
            // performed to preserve exact bit values of the physical values.
            if (flowIn > 0.0 && thermalSource.getFlow() != 0.0) {
                nextTemperature = (innerThermalMass * temperature
                        + heatedVolumeFlowIn
                        - thermalSource.getFlow() / specHeatCap * stepTime)
                        / (innerThermalMass + flowIn);
            } else if (flowIn > 0.0 && thermalSource.getFlow() == 0.0) {
                nextTemperature = (innerThermalMass * temperature
                        + heatedVolumeFlowIn) / (innerThermalMass + flowIn);
            } else if (flowIn == 0.0 && thermalSource.getFlow() != 0.0) {
                nextTemperature = (innerThermalMass * temperature
                        - thermalSource.getFlow() / specHeatCap * stepTime)
                        / innerThermalMass;
            } else {
                nextTemperature = temperature;
            }

            temperaturePrepared = true;
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

    /**
     * Checks if all inbound values, including temperature properties, are
     * updated. Intended to be called from the ThermalLogGradientResistance
     * class or ThermalInflowAdjustedResistance which needs to have knowledge
     * about the inlet temperature of the heat exchanger. However, usage is not
     * restricted to this.
     *
     * @return true if all temperatures on inoming flows to the assigned
     * elements are in updated state.
     */
    public boolean inboundValuesUpdated() {
        return inValuesUpdated;
    }

    /**
     * Checks whether there is a flow incoming or not. Inteded to be called from
     * the ThermalInflowAdjustedResistance class which have to consider a zero
     * flow on one or both sides of a heat exchanger. However, usage is not
     * restricted to this.
     *
     * @return true if there is no flow coming into the heat exchager.
     */
    public boolean hasNoInboundFlows() {
        return noFlowIn;
    }

    /**
     * Get the mass flow flowing into the element whcih has this handler
     * assigned. Inteded to be called from the ThermalInflowAdjustedResistance
     * class which have to know about the flow to calculate an average inbound
     * temperature. However, usage is not restricted to this.
     *
     * @return Mass flow towars element in kg/s
     */
    public double getInboundMassFlow() {
        if (!inValuesUpdated) {
            throw new CalculationException("Inbound mass flow requestet but "
                    + "that value is not yet updated.");
        }
        return massflowIn;
    }

    /**
     * Get the heated mass flow flowing into the element whcih has this handler
     * assigned. Inteded to be called from the ThermalInflowAdjustedResistance
     * class which have to know about the heated flow to calculate an average
     * inbound temperature. However, usage is not restricted to this.
     *
     * @return Mass flow times temperature in K*kg/s
     */
    public double getInboundHeatFlow() {
        if (!inValuesUpdated) {
            throw new CalculationException("Inbound heat flow requestet but "
                    + "that value is not yet updated.");
        }
        return heatFlowIn;
    }

    /**
     * Thermal mass inside the heat exchanger which can be heated up (constant)
     *
     * @return Mass in kg
     */
    public double getStoredThermalMass() {
        return innerThermalMass;
    }

    /**
     * Returns flow through the parent element, requiring the parent element to
     * be of a flow through type. Positive flow value on this element means:
     * flow into port 0, out of port 1.
     *
     * @return flow value
     */
    public double getFlow() {
        if (heatNodes.size() != 2) {
            throw new ModelErrorException("getFlow can only be called if "
                    + "parent is of FlowThrough type element.");
        }
        return heatNodes.get(0).getFlow((AbstractElement) element);
    }

    /**
     * Sets a specific heat capacity. The default preset value is water, only
     * needed if the default value (4200 J/kg/K) is not sufficient.
     *
     * @param c J/kg/K - note the SI units, usually in literature its given in
     * kJ but we use SI units here.
     */
    public void setSpecificHeatCapacity(double c) {
        specHeatCap = c;
    }

}
