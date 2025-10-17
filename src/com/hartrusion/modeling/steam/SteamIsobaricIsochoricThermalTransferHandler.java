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
package com.hartrusion.modeling.steam;

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;

/**
 * Handler for the steam evaporator.
 *
 * <p>
 * The expected behaviour is that the model sets a flow value into the assigned
 * steam element with set steam properties. This handler will then calculate the
 * steam or water output out of this element and consideres a thermal capacity
 * itself which is to be heated up. As the saturated mixture expands when
 * heating up, the mass will be less if the heating increases and the mass
 * difference will be applied to the out node as an additional flow. Therefore
 * only one flow value must be set towards this element so the handler here can
 * calculate the corresponding out flow.
 *
 * <p>
 * The element does however not support any kind of suction by contraction. To
 * model it anyways, it has a so called negative mass that will be accumulated.
 * The negative mass represents the amount of water that needs to be put into
 * the element to fill it up until more steam will leave the element. So, if,
 * anyhow, cold water suddenly flows into the element and this would cool down
 * the present steam/water mix in a way that it would suck in on the output, the
 * element will not do this but set the output flow to zero. This is not that
 * much of a problem, as usually we have recirculation that is higher than this
 * effect but the implementation of this behaviour is there to have at least
 * something to handle the situation if it ever occurs.
 *
 * <p>
 * Designed calculation run (thermal flow heats up the evaporator, one updated
 * water/steam mixture is flowing into the element is as follows:
 *
 * <ul>
 * <li>Total Mass is known from previous state, as well as local steam
 * property.</li>
 * <li>Add absolute enthalpy: Enthalpy of the contained mass gets the enthalpy
 * of the in flow and the thermal source added for the set steptime.</li>
 * <li>Add the in flow mass which came in in the steptime to the total
 * mass.</li>
 * <li>Calculate nextSteamProperties by using the new specific enthalpy from
 * that absolute enthalpy and the new updated mass.</li>
 * <li>Use the specific volume from steam table to calculate how much volume the
 * now calculated mass in its now calulated next state would need.</li>
 * <li>As the volume of this element is fixed, we know how much additional
 * volume we will have. This is usually a positive value, a negative value will
 * be considered also but thats a special case already.</li>
 * <li>The volume that is additional is the mass out, that mass can be
 * calculated by using the specific volume value again.</li>
 * <li>The out flow mass gets the current steam parameters assigned, not the
 * next steam parameters. I forgot why i did it that way but there was a
 * reason.</li>
 * <li>If the volume would shrink due to cool down and not enough flow comes in
 * to compensate for that shrinkage, the element will accumulate that missing
 * mass as a so called negativeMass and set the flow out to zero. If any mass
 * would go out of the element again later, it will first fill up that negative
 * mass value before somethign goes out of the element.</li>
 * </ul>
 *
 *
 * @author Viktor Alexander Hartung
 */
public class SteamIsobaricIsochoricThermalTransferHandler
        implements SteamHandler {

    /**
     * Reference to the effort source representing the temperature for the
     * thermal flow.
     */
    EffortSource thermalSource;

    /**
     * Contains all ports that the element which uses this steam handler has.
     */
    private final List<SteamNode> steamNodes = new ArrayList<>();

    /**
     * A reference to a steam element which has this handler assigned.
     */
    private final SteamElement parent;

    /**
     * A reference to a steam element which has this handler assigned as a
     * reference to the abstract element type. Its a separate reference variable
     * to make code shorter by removing the necessity of casting to this one for
     * some function arguments.
     */
    private final AbstractElement element;

    /**
     * State space variable that contains the current state of the steam. This
     * will be updated each cycle and it will be used to calculate the steam
     * properties leaving this element.
     */
    private final double[] steamProperties
            = new double[SteamNode.STEAM_PROPERTIES_LENGTH];

    private final double[] nextSteamProperties
            = new double[SteamNode.STEAM_PROPERTIES_LENGTH];

    private boolean nextPropertiesPrepared;

    private double stepTime = 0.1;

    /**
     * The total volume of the handled element, this will not change. Given in
     * m^3. Has to be set with initial conditions.
     */
    private double volume = 1.0;

    /**
     * State value, descirbes the mass that is inside the element for the
     * current calculation run.
     */
    public double totalMass;

    /**
     * Prepared mass that will be set in the next calculation cycle as the
     * totalMass.
     */
    private double nextTotalMass;
    private double negativeMass;

    private double outMassQuantity;

    /**
     * Wrong operation mode: Instead of having a flow set into the element
     * first, a flow out of the elment was set first. This will trigger a very
     * bad behaving calculaton way to be at least able to calculate something.
     */
    boolean reverseFlowRequest;

    /**
     * When haveing the reverseFlow, the calulation must be called twice as the
     * first calculation will only set the flow but as there was no flow, no
     * steam parameters were assigned. This marks that now we have to wait for
     * those parameters to be assigned externally by any solver. Usually the
     * iterative solver call will do this.
     */
    private boolean waitForReverseOutProperties;

    /**
     * A big downside is that the wrong direction operation has to set a
     * calculated mass to keep the constraint of the fixed volume, so there will
     * be some parts missing. This value is the missing part that will not be
     * "pulled" out of the other port. Note that if that reverseOut is no longer
     * used, there will be an error as this is no longer considered. But as
     * said, initially this handler was never designed to be used like that.
     */
    private double reverseOutMassCorretion;

    SteamTable propertyTable;

    SteamIsobaricIsochoricThermalTransferHandler(SteamElement parent,
            SteamTable propertyTable) {
        this.parent = parent;
        element = (AbstractElement) parent;
        this.propertyTable = propertyTable;
    }

    @Override
    public void prepareSteamCalculation() {
        // Set temperature to the thermal systems effort source
        thermalSource.setEffort(steamProperties[0]);

        if (nextPropertiesPrepared) {
            // Set next steam properties as new properties
            System.arraycopy(nextSteamProperties, 0, steamProperties, 0,
                    steamProperties.length);

            // apply calculated values for this calculation run
            totalMass = nextTotalMass;

            outMassQuantity = 0.0;
            nextTotalMass = 0.0;

            nextPropertiesPrepared = false;
        }
        reverseFlowRequest = false;
        waitForReverseOutProperties = false;
    }

    @Override
    public boolean doSteamCalculation() {
        boolean didSomething = false;
        boolean pressureUpdated = false;
        double pressure = 0.0;
        int updatedFlowsWithoutSteamProperties = 0;
        int updatedSteamFlows = 0;
        boolean updatedSteamFlowsZero = false;
        boolean thermalFlowUpdated = false;
        boolean thermalFlowZero = false;
        double mixtureMass, absEnthalpy, mixtureSpecificVolume,
                inSpecificVolume, nextSpecificVolume, nextVolume,
                deltaVolume, massOut, massIn, specificEnthalpy, satEnthalpy,
                enthalpyIn, enthalpyIncrease, liquidVoulume, vapourVolume,
                liquidMass, vapourMass;
        SteamNode firstUpdatedNode = null;
        SteamNode otherNode = null;
        // this is an isobaric element. it gets its pressure from the connected
        // ports, so check if at least one pressure is known:
        for (SteamNode sp : steamNodes) {
            if (sp.effortUpdated()) {
                pressureUpdated = true;
                pressure = sp.getEffort();
                break;
            }
        }
        // Set the pressure to all remaining connections (should be none).
        if (pressureUpdated) {
            for (SteamNode sp : steamNodes) {
                if (!sp.effortUpdated()) {
                    sp.setEffort(pressure, element, true);
                    didSomething = true;
                }
            }
        }

        // Check state of ports regarding the flow. Expect a steam port which
        // has an updated flow with updated steam values that has a flow into
        // the element.
        for (SteamNode sp : steamNodes) {
            if (sp.flowUpdated(element)
                    && sp.steamPropertiesUpdated(element)) {
                if (updatedSteamFlows == 0) { // first one in loop:
                    updatedSteamFlowsZero = true; // init variable here
                }
                updatedSteamFlowsZero = updatedSteamFlowsZero
                        && sp.getFlow(element) == 0.0;
                // Remember the first port which got updated
                if (firstUpdatedNode == null) {
                    firstUpdatedNode = sp;
                }
                updatedSteamFlows++;
            } else if (sp.flowUpdated(element)) { // only flow is updated (!)
                updatedFlowsWithoutSteamProperties++;
            }
        }

        // check state of thermal connection
        if (thermalSource.flowUpdated()) {
            thermalFlowUpdated = true;
            thermalFlowZero = thermalSource.getFlow() == 0.0;
        }

        // Check for reverse flow, meaning: Something should go out of the 
        // element but in flow is not known.
        if (updatedSteamFlows == 0 && updatedFlowsWithoutSteamProperties == 1) {
            for (SteamNode sp : steamNodes) {
                if (sp.flowUpdated(element)) {
                    if (sp.getFlow(element) < 0.0) {
                        reverseFlowRequest = true; // mark this once
                        firstUpdatedNode = sp;
                    }
                }
            }
        }

        // Now we will handle all the possible cases.
        if (!nextPropertiesPrepared
                && updatedSteamFlows == 1
                && thermalFlowZero
                && updatedSteamFlowsZero
                && !reverseFlowRequest) {
            // special case: no thermal flow and no in flow. everything
            // stays as it is, nothing changes. No steam calculation will
            // be called. A pressure value is not necessary.
            // Set zero flow and no steam for the remaining port:
            for (SteamNode sp : steamNodes) {
                if (!sp.flowUpdated(element)) {
                    sp.setFlow(0.0, element, true);
                    sp.setNoSteamProperties(element);
                }
            }
            System.arraycopy(steamProperties, 0,
                    nextSteamProperties, 0, steamProperties.length);

            nextTotalMass = totalMass;
            nextPropertiesPrepared = true;

            outMassQuantity = 0.0;
            didSomething = true;
        } else if (!nextPropertiesPrepared
                && updatedSteamFlows == 1
                && thermalFlowUpdated
                && pressureUpdated
                && !reverseFlowRequest) {
            // Designed, normal operating behaviour: Steam/Water with known
            // Properties enter the element. There might be thermal flow also.

            // Add the thermal flow and the incoming flow to the existing
            // enthalpy, first, calculate the energy inside this element:
            absEnthalpy = totalMass * steamProperties[1];
            // get the only updated flow and its value if its not zero
            // and add it to the enthalpy value
            mixtureMass = totalMass; // init
            if (!updatedSteamFlowsZero) {
                for (SteamNode sp : steamNodes) {
                    if (sp.flowUpdated(element)
                            && sp.steamPropertiesUpdated(element)) {
                        // add massflow * time * enthalpy
                        absEnthalpy += sp.getFlow(element) * stepTime
                                * sp.getSteamProperty(1, element);
                        // add (or remove) the flow
                        mixtureMass += sp.getFlow(element) * stepTime;
                        break;
                    }
                }
            }
            // same for thermal flow, check if its there and if it is, add it:
            if (!thermalFlowZero) {
                // as we talk about adding we have to use a negative sign due
                // to the convention on the sign and the direction of placement
                // of the flow source.
                absEnthalpy -= thermalSource.getFlow() * stepTime;
            }
            // Calculate the new specific enthalpy value relative to the mass
            nextSteamProperties[1] = absEnthalpy / mixtureMass;
            // calculate temperature, entropy and vapourfract of the mixture
            nextSteamProperties[0] = propertyTable.get("T_ph",
                    pressure, nextSteamProperties[1]);
            nextSteamProperties[2] = propertyTable.get("s_ph",
                    pressure, nextSteamProperties[1]);
            nextSteamProperties[3] = propertyTable.get("x_ph",
                    pressure, nextSteamProperties[1]);
            // Now comes the fancy part. We need to know the new volume and
            // compare it to the elements volume.
            mixtureSpecificVolume = propertyTable.get("v_ph",
                    pressure, nextSteamProperties[1]);
            // In first revisions, we used mixtureSpecificVolume as the
            // nextSpecificVolume - this has the massive downside as it does
            // not consider that large parts of the evaporator are actually
            // still water. we can not set the out part for the whole element
            // as the specific volume dramatically changes when it comes to
            // evaporation. Therefore we also take a look on the steam
            // parameters coming into the element.
            satEnthalpy = propertyTable.get("hLiq_p", pressure);
            // We will weight the volume over the enthalpy increase
            enthalpyIn = firstUpdatedNode.getSteamProperty(1, element);

            enthalpyIncrease = nextSteamProperties[1] - enthalpyIn;
            // But: This only works as long as we actually have evaporation
            // and heat up happening. check for an enthalpy increase and that
            // we have the evaporation happening between the in and out 
            // enthalpy values. nextSteamProperties[1] is the out enthalpy
            if (enthalpyIncrease >= 1.0 && satEnthalpy > enthalpyIn
                    && satEnthalpy < nextSteamProperties[1]) {
                // calculate the volume that is available to the liquid
                // and the vapour part by using a ratio of enthalpy until
                // the saturation. Yes, these are some really wild assumtions
                // here but it allows at least something to calculate fast.
                liquidVoulume = (satEnthalpy - enthalpyIn) / enthalpyIncrease
                        * volume;
                vapourVolume = (nextSteamProperties[1] - satEnthalpy)
                        / enthalpyIncrease * volume;
                // We only need this here so we calculate it inside the if now.
                inSpecificVolume = propertyTable.get("v_ph",
                        pressure, enthalpyIn);
                // calculate how much mass fits inisde according to the fixed
                // element volume and the ratious calculated:
                liquidMass = liquidVoulume / inSpecificVolume;
                // use avergae of liquid and mixture specific volume. As we
                // divide by 2 we make * 2.
                vapourMass = vapourVolume / (mixtureSpecificVolume
                        + inSpecificVolume) * 2;
                // Now simply check the mixture mass and everything that is
                // not inside the volume goes out. Can also be in.
                massOut = mixtureMass - liquidMass - vapourMass;
            } else {
                // if no evaporation zone is inside, fall back to the old
                // calculation:
                nextSpecificVolume = mixtureSpecificVolume;
                // Use the spec. volume to calculate the isobaric total volume:
                nextVolume = mixtureMass * nextSpecificVolume;
                // How much additional volume do wie have now?
                deltaVolume = nextVolume - volume;
                // Calculate the mass accordingly using the specific volume again:
                massOut = deltaVolume / nextSpecificVolume;
            }

            // The behaviour of the SteamFixedVolumeThermalExchanger is defined
            // to never have a kind of "suction" mass flow, but what to do if
            // the outMassQuantity ever would be negative? This can happen if
            // a steam volume gets cold water injection. In this case, we will
            // accumulate the amount of mass that is missing.
            // The model permits suction from the second port, so if in any 
            // occasion the mass out would be negative, it will be set to zero.
            if (massOut <= 0.0) {
                // negative (or exactly zero) outflow
                outMassQuantity = 0.0;
                negativeMass -= massOut; // accumulate the missing mass
                for (SteamNode sp : steamNodes) {
                    if (sp == firstUpdatedNode) {
                        continue;
                    }
                    if (!sp.flowUpdated(element)
                            && !sp.steamPropertiesUpdated(element)) {
                        sp.setNoSteamProperties(element);
                        sp.setFlow(0.0, element, true);
                    } else if (massOut != 0.0) {
                        // actually, trying to set a zero flow to a port that
                        // has no flow is ok.
                        throw new ModelErrorException("Flow or steam properties"
                                + " already set to a port which was expected "
                                + "to be set by this calculation call. The "
                                + "element must be able to freely set a flow "
                                + "towards a port.");
                    }
                }
            } else if (massOut > negativeMass) {
                // positive out flow and at least more flow than the accumulated
                // negative mass quantity. Remove any leftover negative mass.
                outMassQuantity = massOut - negativeMass;
                negativeMass = 0.0;
                // Get the only non-updated port and write the result flow
                for (SteamNode sp : steamNodes) {
                    if (sp == firstUpdatedNode) {
                        continue;
                    }
                    if (!sp.flowUpdated(element)
                            && !sp.steamPropertiesUpdated(element)) {
                        sp.setSteamProperties(steamProperties, element);
                        sp.setFlow(-outMassQuantity / stepTime, element, true);
                    } else {
                        throw new ModelErrorException("Flow or steam properties"
                                + " already set to a port which was expected "
                                + "to be set by this calculation call. The "
                                + "element must be able to freely set a flow "
                                + "towards a port.");
                    }

                }
            } else if (massOut <= negativeMass) {
                // There is theroretically something going out but it is fully
                // consumed by the negative mass
                outMassQuantity = 0.0;
                negativeMass -= massOut; // remove from the accumulation
                for (SteamNode sp : steamNodes) {
                    if (sp == firstUpdatedNode) {
                        continue;
                    }
                    if (!sp.flowUpdated(element)
                            && !sp.steamPropertiesUpdated(element)) {
                        sp.setNoSteamProperties(element);
                        sp.setFlow(0.0, element, true);
                    } else {
                        throw new ModelErrorException("Flow or steam properties"
                                + " already set to a port which was expected "
                                + "to be set by this calculation call. The "
                                + "element must be able to freely set a flow "
                                + "towards a port.");
                    }
                }
            }
            // consider the mass to be changed, no matter if the mass was
            // sent out with the port or beeing accumulated and released in
            // the negative mass.
            nextTotalMass = mixtureMass - massOut;
            nextPropertiesPrepared = true;

            // reset this here in case the flow direction changes back and again
            reverseOutMassCorretion = 0.0;

            didSomething = true;
        } else if (!nextPropertiesPrepared
                && reverseFlowRequest
                && firstUpdatedNode != null // prevent compiler warning
                && thermalFlowUpdated
                && !waitForReverseOutProperties) {
            // Reverse flow, meaning a flow out is set on one port. Not the 
            // desired behaviour and very inacurate but at least some solution.
            // The port that has the requested flow out is set as
            // firstUpdatedNode.

            // get the other node
            for (SteamNode sn : steamNodes) {
                if (sn != firstUpdatedNode) {
                    otherNode = sn;
                    if (otherNode.flowUpdated(element)) {
                        otherNode = null; // why? WHY?
                    }
                    break;
                }
            }
            // Set the out steam properties to the properties of the inner mass
            firstUpdatedNode.setSteamProperties(steamProperties, element);

            massOut = -firstUpdatedNode.getFlow(element) * stepTime;
            absEnthalpy = (totalMass - massOut) * steamProperties[1];
            if (!thermalFlowZero) { // consider thermal source here
                absEnthalpy -= thermalSource.getFlow() * stepTime;
                // Calculate a temporary specific enthalpy and spec. volume
                specificEnthalpy = absEnthalpy / (totalMass - massOut);
                nextSpecificVolume = propertyTable.get("v_ph",
                        pressure, specificEnthalpy);
                // Use that specific volume to determine the missing mass that
                // would be needed to have the elements volume.
                nextVolume = (totalMass - massOut) * nextSpecificVolume;
                deltaVolume = volume - nextVolume; // this is missing
                massIn = deltaVolume / nextSpecificVolume;

            } else {
                // no change in specific enthalpy if there is no thermal flow.
                // therefore the spec. volume does not change in this step so 
                // we just have the same mass in like it goes out. It is obvious
                // that there is now a change in volume not considered when the
                // steam expands. This will be considered in next call.
                massIn = massOut;
            }
            // Set this mass as the inflow to the element.
            if (otherNode != null) {
                // consider the mass correction from previous cycle.
                otherNode.setFlow(massIn - reverseOutMassCorretion,
                        element, false);
                didSomething = true;
            }
            waitForReverseOutProperties = true;
        } else if (reverseFlowRequest
                && waitForReverseOutProperties) { // this will be called later
            // more doCalculation runs of the model must now assign steam
            // properties to the inbound flow.
            otherNode = null; // init to re-assign
            for (SteamNode sn : steamNodes) {
                if (sn.steamPropertiesUpdated(element)
                        && sn.flowUpdated(element)
                        && sn.getFlow(element) > 0.0) {
                    otherNode = sn; // in here
                } else if (sn.steamPropertiesUpdated(element)
                        && sn.flowUpdated(element)
                        && sn.getFlow(element) < 0.0) {
                    firstUpdatedNode = sn; // out node
                }
            }
            // nodes are in the expected state, both flows and steam properties
            // are now known.
            if (otherNode != null && firstUpdatedNode != null) {
                // Calculate the total enthalpy:
                absEnthalpy = (totalMass
                        + // flow out is negative, so its +
                        firstUpdatedNode.getFlow(element) * stepTime)
                        * steamProperties[1]
                        + otherNode.getFlow(element) * stepTime
                        * otherNode.getSteamProperty(1, element);
                // As volume is given by this element, we have a set mass
                // that must be inside the element. 
                // Re-use variable, ignore the name here. Mixture mass describes
                // the mass when consideren in and out flow:
                mixtureMass = (totalMass
                        + firstUpdatedNode.getFlow(element) * stepTime
                        + otherNode.getFlow(element) * stepTime);
                specificEnthalpy = absEnthalpy / mixtureMass;
                nextSpecificVolume = propertyTable.get("v_ph",
                        pressure, specificEnthalpy);
                nextTotalMass = volume / nextSpecificVolume;

                // Set that spec. enthalpy as new property and calculate the
                // missing other properties.
                nextSteamProperties[1] = specificEnthalpy;
                nextSteamProperties[0] = propertyTable.get("T_ph",
                        pressure, nextSteamProperties[1]);
                nextSteamProperties[2] = propertyTable.get("s_ph",
                        pressure, nextSteamProperties[1]);
                nextSteamProperties[3] = propertyTable.get("x_ph",
                        pressure, nextSteamProperties[1]);

                // Calculate a really bad correction factor. Positive means
                // that there is too much mass now according to the flow than
                // was set, so the value will be subtracted on next cycle.
                reverseOutMassCorretion = mixtureMass - nextTotalMass;

                nextPropertiesPrepared = true;
                waitForReverseOutProperties = false;
                didSomething = true;
            }
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

    @Override
    public boolean registerSteamPort(SteamNode sp) {
        if (!steamNodes.contains(sp)) {
            steamNodes.add(sp);
            return true;
        }
        return false;
    }

    @Override // this is part of the SteamHandler interface
    public void setTotalMass(double mass) {
        totalMass = mass;
    }

    @Override
    public double getTotalMass() {
        return totalMass;
    }

    public double getNegativeMass() {
        return negativeMass;
    }

    /**
     * Set the volume of this vessel on initialization of the model. Not
     * intended to be used between calculation runs.
     *
     * @param volume in m^3
     */
    public void setVolume(double volume) {
        this.volume = volume;
    }

    @Override
    public boolean isSteamCalulationFinished() {
        return nextPropertiesPrepared && !waitForReverseOutProperties;
    }

    @Override
    public void setSteamProperty(int index, double value) {
        steamProperties[index] = value;
        // Temperature has to be present on effort source also
        if (index == 0) {
            thermalSource.setEffort(value);
        }
    }

    @Override
    public double getSteamProperty(int index) {
        return steamProperties[index];
    }

    public void setStepTime(double dt) {
        stepTime = dt;
    }

}
