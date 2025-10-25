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

import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedExpandingThermalVolumeHandler
        extends PhasedAbstractVolumizedHandler {

    private static final Logger LOGGER = Logger.getLogger(
            PhasedExpandingThermalVolumeHandler.class.getName());

    /**
     * Reference to the effort source representing the temperature for the
     * thermal flow.
     */
    EffortSource thermalSource;

    PhasedExpandingThermalVolumeHandler(
            PhasedFluidProperties fluidProperties,
            PhasedElement parent) {
        super(fluidProperties, parent);
    }

    /**
     * The total volume of the handled element, this will not change. Given in
     * m^3. Has to be set with initial conditions.
     */
    private double volume = 1.0;

    public double getVolume() {
        return volume;
    }

    public void setVolume(double volume) {
        this.volume = volume;
    }

    /**
     * Calculates a voiding based on a reference effort value, the voiding will
     * be the volume in percent that is present compared with the volume that
     * would be there on the referenceEffort at boiling temperature.
     *
     * @param referenceEffort Pressure in Pa defining the saturation point.
     *
     * @return Value between 0 and 100 % with 0 % being solid filled, 100 %
     * being completely empty.
     */
    public double getVoiding(double referenceEffort) {
        double satTemp
                = fluidProperties.getSaturationTemperature(referenceEffort);
        double satEnergy = satTemp * fluidProperties.getSpecificHeatCapacity();
        // Density at referenceEffort on boiling point
        double refRho = fluidProperties.getDensity(satEnergy, referenceEffort);
        // Mass inside the element at reference effort
        double refMass = volume * refRho;

        return (1 - innerHeatMass / refMass) * 100;
    }

    /**
     * Prepared mass that will be set in the next calculation cycle as the
     * totalMass.
     */
    private double nextInnerHeatMass;

    private double outMassQuantity;

    private boolean reverseFlowRequest = false;

    private boolean waitForReverseOutProperties;

    private double reverseOutMassCorretion;

    private double nextDelayedInHeatEnergy;
    private double delayedInHeatEnergy;

    private double negativeMass;

    private double previousPressure;

    @Override
    public void preparePhasedCalculation() {
        if (heatEnergyPrepared) {
            if (nextInnerHeatMass <= 0.0) {
                throw new ModelErrorException("Evaporator element empty, "
                        + "calulation impossible. Make sure there is always "
                        + "mass inside the element!");
            }
            innerHeatMass = nextInnerHeatMass;
            delayedInHeatEnergy = nextDelayedInHeatEnergy;
            nextDelayedInHeatEnergy = 0.0;
            nextInnerHeatMass = 0.0;
            outMassQuantity = 0.0;
            waitForReverseOutProperties = false;
            reverseFlowRequest = false;
        }
        super.preparePhasedCalculation(); // sets heatEnergyPrepared to false
        // Set the temperature as effort on the temperature source, making the 
        // connection between the two domains.
        thermalSource.setEffort(
                fluidProperties.getTemperature(heatEnergy, previousPressure));
    }

    @Override
    public boolean doPhasedCalculation() {
        // this will only do a reset usually
        boolean didSomething = super.doPhasedCalculation();
        AbstractElement aElement;
        int updatedHeatFlows = 0;
        boolean updatedHeatFlowsZero = false;
        PhasedNode firstUpdatedNode = null;
        PhasedNode otherNode = null; // not the firstUpdatedNode
        int updatedFlowsWithoutTemperature = 0;
        boolean pressureUpdated = false;
        double energyWithoutOutflow, energyWithoutInFlow;
        double pressure = 0.0;
        boolean thermalFlowUpdated = false;
        boolean thermalFlowZero = false;
        double density, massCapacity;
        double massOut, massIn;

        if (heatEnergyPrepared) {
            return false; // nothing more to do
        }

        // Perform cast as we need this type for some arguments
        aElement = (AbstractElement) element;

        // Check state of ports regarding the flow. Expect a phased node which
        // has an updated flow with updated heat values that has a flow into
        // the element.
        for (PhasedNode pn : phasedNodes) {
            if (pn.flowUpdated(aElement)
                    && pn.heatEnergyUpdated(aElement)) {
                if (updatedHeatFlows == 0) { // first one in loop:
                    updatedHeatFlowsZero = true; // init variable here...
                }
                updatedHeatFlowsZero = updatedHeatFlowsZero
                        && pn.getFlow(aElement) == 0.0; // ...and check here.
                // Remember the first port which got updated
                if (firstUpdatedNode == null) {
                    firstUpdatedNode = pn;
                }
                updatedHeatFlows++;
            } else if (pn.flowUpdated(aElement)) { // only flow is updated (!)
                updatedFlowsWithoutTemperature++;
            }
        }

        // Check if we have a valid pressure value
        for (PhasedNode pn : phasedNodes) {
            if (pn.effortUpdated()) {
                pressureUpdated = true;
                pressure = pn.getEffort();
                previousPressure = pressure;
                break;
            }
        }

        // check state of thermal connection
        if (thermalSource.flowUpdated()) {
            thermalFlowUpdated = true;
            thermalFlowZero = thermalSource.getFlow() == 0.0;
        }

        if (updatedHeatFlows == 0 && updatedFlowsWithoutTemperature == 1) {
            // Check if a so called reverse flow is present, this is unintended but
            // but there will be a proper solution for this.
            for (PhasedNode pn : phasedNodes) {
                if (pn.flowUpdated(aElement)) {
                    if (pn.getFlow(aElement) < 0.0) {
                        firstUpdatedNode = pn;
                        reverseFlowRequest = true; // mark this once
                        break;
                    }
                }
            }
        }

        if (firstUpdatedNode != null) { // get the other node
            for (PhasedNode pn : phasedNodes) {
                if (pn != firstUpdatedNode) {
                    otherNode = pn;
                    break;
                }
            }
        }

        // Now all different cases will be handled.
        if (!heatEnergyPrepared
                && updatedHeatFlows == 1
                && thermalFlowZero
                && updatedHeatFlowsZero
                && !reverseFlowRequest) {
            // special case: no thermal flow and no in flow. everything
            // stays as it is, nothing changes. No heat calculation will
            // be called. A pressure value is not necessary.
            // Set zero flow and no temperature for the remaining port:
            for (PhasedNode pn : phasedNodes) {
                if (!pn.flowUpdated(aElement)) {
                    pn.setFlow(0.0, aElement, true);
                    pn.setNoHeatEnergy(aElement);
                }
            }
            nextHeatEnergy = heatEnergy;
            nextInnerHeatMass = innerHeatMass; // reuse previous
            heatEnergyPrepared = true;

            outMassQuantity = 0.0;
            didSomething = true;
        } else if (!heatEnergyPrepared
                && updatedHeatFlows == 1
                && firstUpdatedNode != null // nullpointer warning
                && otherNode != null // another null
                && thermalFlowUpdated
                && pressureUpdated
                && !reverseFlowRequest) {

            // Designed, normal operating behaviour: Flow is coming into the
            // element and thermal flow will heat up the mass by adding its 
            // energy.
            // The specific heat energy after ADDING is defined as:
            // - mass inside times specific energy
            // - added mass with energy from in flow
            // - added heat energy from thermal source
            // all divided by the mass and the added mass part.
            if (firstUpdatedNode.noHeatEnergy(aElement)
                    || firstUpdatedNode.getFlow(aElement) == 0.0) {
                // no flow in forced (closed valves etc):
                energyWithoutOutflow = (innerHeatMass * heatEnergy
                        - thermalSource.getFlow() * stepTime)
                        / innerHeatMass; // divided by next mass;
            } else {
                energyWithoutOutflow = (innerHeatMass * heatEnergy
                        + firstUpdatedNode.getFlow(aElement) * stepTime
                        * firstUpdatedNode.getHeatEnergy(aElement)
                        - thermalSource.getFlow() * stepTime)
                        / (innerHeatMass // divided by next mass
                        + firstUpdatedNode.getFlow(aElement) * stepTime);
            }

            // Problem: If we use the outDensity for the full volume, this will
            // throw out a lot of mass immediately instead of having a point
            // where it boils.
            // Solution: Have a density distribution with the input density on
            // one side and outDensity on the other side.
            // Next problem: A change of the in density will immediately affect
            // the density distribution, immediately throwing out a lot of mass
            // when the input heat goes up.
            // Better solution: We need a weighted density over the evaporation
            // which is basically a function density = f(energy) with a given
            // pressure (that is known). As we know how the density is calced
            // we can make an integral to this function to get the average 
            // density. The density is linear to pressure in the phased model.
            // for solid and vapour phase, as the area between is interpolated
            // this is also linear.
            // How to do that? Delay the input energy by a time constant 
            // depending on the flow rate to mass ratio. Fast flow rates will
            // result in fast input energy adaption, low flow rates will 
            // result in a way slower response in terms of mass ejection.
            // This is completely made up but it behaves at least a bit as I 
            // expect it and the best thing is: It can be calculated and is 
            // a stable solution for most cases we will encounter in our rbmk
            // evaporatior. This delayed input temperature will be used for the
            // 
            // Some T1-behaviour with massflow/mass as time constant
            if (firstUpdatedNode.noHeatEnergy(aElement)
                    || firstUpdatedNode.getFlow(aElement) == 0.0) {
                nextDelayedInHeatEnergy = delayedInHeatEnergy; // keep
            } else {
                nextDelayedInHeatEnergy = delayedInHeatEnergy + stepTime
                        * firstUpdatedNode.getFlow(aElement) / innerHeatMass
                        * (firstUpdatedNode.getHeatEnergy()
                        - delayedInHeatEnergy);
            }

            // Actually the function of the average density between two energies
            // has been moved to the fluid properties to not blow up the code
            // here. Get the average density between those two energies:
            density = fluidProperties.getAvgDensity(
                    nextDelayedInHeatEnergy, energyWithoutOutflow,
                    pressure);

            massCapacity = density * volume;

            if (firstUpdatedNode.noHeatEnergy(aElement)
                    || firstUpdatedNode.getFlow(aElement) == 0.0) {
                massOut = innerHeatMass - massCapacity;
            } else {
                massOut = innerHeatMass - massCapacity
                        + firstUpdatedNode.getFlow(aElement) * stepTime;
            }

            nextHeatEnergy = energyWithoutOutflow;

            // The behaviour of the PhasedExpandingThermalExchanger is defined
            // to never have a kind of "suction" mass flow, but what to do if
            // the massOut ever would be negative? This can happen if
            // a steam volume gets cold water injection. In this case, we will
            // accumulate the amount of mass that is missing.
            // The model permits suction from the second port, so if in any 
            // occasion the mass out would be negative, it will be set to zero.
            if (massOut <= 0.0) {
                // negative (or exactly zero) outflow
                outMassQuantity = 0.0;
                negativeMass -= massOut; // accumulate the missing mass
                if (!otherNode.flowUpdated(aElement)
                        && !otherNode.heatEnergyUpdated(aElement)) {
                    otherNode.setNoHeatEnergy(aElement);
                    otherNode.setFlow(0.0, aElement, true);
                } else if (massOut != 0.0) {
                    // actually, trying to set a zero flow to a port that
                    // has no flow is ok.
                    throw new ModelErrorException("Flow or steam properties"
                            + " already set to a port which was expected "
                            + "to be set by this calculation call. The "
                            + "element must be able to freely set a flow "
                            + "towards a port.");
                }
            } else if (massOut > negativeMass) {
                // positive out flow and at least more flow than the accumulated
                // negative mass quantity. Remove any leftover negative mass.
                outMassQuantity = massOut - negativeMass;
                negativeMass = 0.0;
                // Get the only non-updated port and write the result flow
                if (!otherNode.flowUpdated(aElement)
                        && !otherNode.heatEnergyUpdated(aElement)) {
                    otherNode.setHeatEnergy(heatEnergy, aElement);
                    otherNode.setFlow(-outMassQuantity / stepTime,
                            aElement, true);
                } else {
                    throw new ModelErrorException("Flow or steam properties"
                            + " already set to a port which was expected "
                            + "to be set by this calculation call. The "
                            + "element must be able to freely set a flow "
                            + "towards a port.");
                }
            } else if (massOut <= negativeMass) {
                // There is theroretically something going out but it is fully
                // consumed by the negative mass
                outMassQuantity = 0.0;
                negativeMass -= massOut; // remove from the accumulation
                if (!otherNode.flowUpdated(aElement)
                        && !otherNode.heatEnergyUpdated(aElement)) {
                    otherNode.setNoHeatEnergy(aElement);
                    otherNode.setFlow(0.0, aElement, true);
                } else {
                    throw new ModelErrorException("Flow or steam properties"
                            + " already set to a port which was expected "
                            + "to be set by this calculation call. The "
                            + "element must be able to freely set a flow "
                            + "towards a port.");
                }
            }

            if (firstUpdatedNode.noHeatEnergy(aElement)
                    || firstUpdatedNode.getFlow(aElement) == 0.0) {
                nextInnerHeatMass = innerHeatMass - outMassQuantity;
            } else {
                nextInnerHeatMass = innerHeatMass - outMassQuantity
                        + firstUpdatedNode.getFlow(aElement) * stepTime;
            }

            heatEnergyPrepared = true;

            // reset this here in case the flow direction changes back and again
            reverseOutMassCorretion = 0.0;

            didSomething = true;
        } else if (!heatEnergyPrepared
                && reverseFlowRequest
                && firstUpdatedNode != null // prevent compiler warning
                && otherNode != null
                && thermalFlowUpdated
                && !waitForReverseOutProperties) {
            // Reverse flow, meaning a flow out is set on one port. Not the 
            // desired behaviour and very inacurate but at least some solution.
            // The port that has the requested flow out is set as
            // firstUpdatedNode.
            // The flow in has to be set first so the model can then self-
            // assign the heat properties to the nodes.
            if (!firstUpdatedNode.heatEnergyUpdated(aElement)) {
                firstUpdatedNode.setHeatEnergy(heatEnergy, aElement);
            }

            // TODO, this works better on the steam element but fuck it.
            // just dont do that mass thing and ignore the thermal part.
            massOut = -firstUpdatedNode.getFlow(aElement) * stepTime;
            otherNode.setFlow(massOut, aElement, true);
            nextHeatEnergy = heatEnergy;
            nextInnerHeatMass = innerHeatMass;
            heatEnergyPrepared = true;

//            energyWithoutInFlow = ((innerHeatMass - massOut) * heatEnergy
//                    - thermalSource.getFlow() * stepTime)
//                    / (innerHeatMass - massOut); // divided by next mass
//
//            // As it can be seen we use the previous delayedInHeatEnergy here
//            // to calculate a density. We have not yet set an flow towards
//            // this element so this value is unknown.
//            density = fluidProperties.getAvgDensity(
//                    delayedInHeatEnergy, energyWithoutInFlow,
//                    pressure);
//
//            massCapacity = density * volume;
//
//            // Same formula as above for massOut in the default case.
//            massIn = innerHeatMass - massCapacity
//                    + firstUpdatedNode.getFlow(aElement) * stepTime;
//
//            // Set this mass as the inflow to the element.
//            // consider the mass correction from previous cycle.
//            otherNode.setFlow(massIn - reverseOutMassCorretion,
//                    aElement, true);
//            
//            waitForReverseOutProperties = true;
            LOGGER.log(Level.WARNING,
                    "Reverse Flow on Thermal Expanding Handler occured, "
                    + "this is not yet implemented properly.");

            didSomething = true;

            // waitForReverseOutProperties = true;
        } else if (waitForReverseOutProperties
                && updatedHeatFlows == 2) { // this will be called later
            // The model will now (hopefully!) be updated as the massIn flow
            // was set above. This will allow whatever mixture calculations now
            // to happen and at some point, the proper input heat energy will
            // become available on the node with the inFlow.

            nextDelayedInHeatEnergy = delayedInHeatEnergy + stepTime
                    * firstUpdatedNode.getFlow(aElement) / innerHeatMass
                    * (firstUpdatedNode.getHeatEnergy() - delayedInHeatEnergy);

            throw new UnsupportedOperationException("Not yet implemented.");
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

    public void setInitialInHeatEnergy(double heatEnergyIn) {
        delayedInHeatEnergy = heatEnergyIn;
        nextDelayedInHeatEnergy = delayedInHeatEnergy;
    }

    @Override
    public void setSteamOutput(PhasedNode pn, boolean isSteamOut) {
        // The element using this handler does not make a difference between
        // steam or liquid output.
        throw new ModelErrorException("Not supported.");
    }
}
