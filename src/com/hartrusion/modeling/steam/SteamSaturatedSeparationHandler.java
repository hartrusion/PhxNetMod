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
import java.util.Arrays;
import java.util.List;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.SelfCapacitance;
import static com.hartrusion.util.ArraysExt.newArrayLength;

/**
 * Handles the steam properties for a reservoir with the option to separate it.
 * During modeling of such a reservoir it turned out that using the specific
 * volume with a reverse lookup function does tend to be numerically unstable.
 * Therefore this class was designed to handle such calculations in a more
 * stable but also inaccurate way.
 *
 * <p>
 * The purpose of this was to model a steam drum with separation of steam and
 * water on the out flow, however, due to the way its modeled the drum must
 * never be empty and should always contain an amount of water that makes the
 * steam part above the water negletably small.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamSaturatedSeparationHandler implements SteamHandler {

    /**
     * Contains all nodes that the element which uses this heat handler has.
     */
    protected final List<SteamNode> steamNodes = new ArrayList<>();

    /**
     * For saturated steam, this value defines what phase of the steam will be
     * assigned to a port if its leaving the element which has this handler
     * assigned.
     */
    private boolean[] isLiquidNode = new boolean[1];

    /**
     * A reference to a steam element which has this handler assigned.
     */
    protected final SteamElement parent;

    /**
     * A reference to a steam element which has this handler assigned as a
     * reference to the abstract element type. Its a separate reference variable
     * to make code shorter by removing the necessity of casting to this one for
     * some function arguments.
     */
    private final AbstractElement element;

    /**
     * Marks the availibility of enthalpySaturatedLiquid and Gas variables and
     * prevents need for calulation on each call as this always calls a steam
     * table function.
     */
    private boolean staticPropertiesCalculated;

    /**
     * Enthalpy for flows that leave the handlers element as a saturated liquid.
     */
    private double enthalpySaturatedLiquid;

    /**
     * Enthalpy for flows that leave the handlers element as steam.
     */
    private double enthalpySaturatedGas;

    /**
     * Enthalpy for flows that leave the handlers element as a non-steam liquid.
     */
    private double enthalpyPressurizedLiquid;

    /**
     * Entropy for flows that leave the handlers element as a saturated liquid.
     */
    private double entropySaturatedLiquid;

    /**
     * Entropy for flows that leave the handlers element as steam.
     */
    private double entropySaturatedGas;

    /**
     * Entropy for flows that leave the handlers element as a non-steam liquid.
     */
    private double entropyPressurizedLiquid;

    /**
     * Prepared pressure for the next cycle, this will be determined by the
     * temperature of the next cycle. The actual pressure is stored as effort
     * variable and is also the stateVariable of the parent element.
     */
    private double nextPressure;

    private double nextTotalMass;
    private boolean nextTotalMassKnown;
    private double totalMass = 100;

    private double stepTime = 0.1;

    /**
     * State space variable that contains the current state of the steam. This
     * will be updated each cycle and it will be used to calculate the steam
     * properties leaving this element.
     */
    private final double[] steamProperties
            = new double[SteamNode.STEAM_PROPERTIES_LENGTH];

    private double saturationTemperature;
    private boolean pressurizedLiquidState;

    private final double[] nextSteamProperties
            = new double[SteamNode.STEAM_PROPERTIES_LENGTH];

    private boolean nextPropertiesPrepared;

    private double lowSpecEnthalpy, highSpecEnthalpy;

    private double m;

    private double b;

    /**
     * Pressure in Pa which is always maintained in the vessel. Initialized for
     * the ambient pressure which is present when the saturation temperature of
     * steam is at exactly 100 degree celsius according to IF97 steam tables.
     */
    private final double ambientPressure = 101417.97792131014;

    SteamTable propertyTable;

    SteamSaturatedSeparationHandler(SteamElement parent,
            SteamTable propertyTable) {
        this.parent = parent;
        element = (AbstractElement) parent;
        this.propertyTable = propertyTable;
    }

    public void initState(double initialTtemperature, double storedMass,
            double lowTemperature, double highTemperature, double pressure) {
        double lowPressure, highPressure;

        totalMass = storedMass;
        steamProperties[0] = initialTtemperature;

        lowPressure = propertyTable.get("pSat_T", lowTemperature);
        highPressure = propertyTable.get("pSat_T", highTemperature);

        lowSpecEnthalpy = propertyTable.get("hLiq_p", lowPressure);
        highSpecEnthalpy = propertyTable.get("hLiq_p", highPressure);

        // calculate linear coefficients. I do not feel well today.
        // y = m*x + b; using x = h and y = T: T = m * h + b <> b = T - m*h
        // y = m x + b
        m = (highTemperature - lowTemperature)
                / (highSpecEnthalpy - lowSpecEnthalpy);
        b = highTemperature - m * highSpecEnthalpy;

        // reverse this to get the initial spec enthalpy that corresponds to
        // the given inital temperature. We cheat the temperature to the state 
        // inital value this way instead of enthalpy. Instead of T = m*h + b we
        // need T - b = m*h ; h = (T-b)/m
        steamProperties[1] = (initialTtemperature - b) / m;

        // for now, we will not use spec. entropy and X in the entire class.
        totalMass = storedMass;
    }

    /**
     * Allows to register a steam port with the extend to define the phase on
     * which a saturated steam is leaving the handler.
     *
     * @param sp SteamNode, an extension of GeneralPort
     * @param isLiquid true: If saturated steam is present, liquid will leave
     * through this port.
     */
    public void registerSteamPort(SteamNode sp, boolean isLiquid) {
        if (registerSteamPort(sp)) {
            isLiquidNode = newArrayLength(isLiquidNode, steamNodes.size());
            isLiquidNode[steamNodes.size() - 1] = isLiquid;
        }
    }

    @Override
    public boolean registerSteamPort(SteamNode sp) {
        if (!steamNodes.contains(sp)) {
            steamNodes.add(sp);
            isLiquidNode = newArrayLength(isLiquidNode, steamNodes.size());
            isLiquidNode[steamNodes.size() - 1] = true;
            return true;
        }
        return false;
    }

    @Override
    public void prepareSteamCalculation() {
        if (nextPropertiesPrepared) {
            nextPropertiesPrepared = false;
            System.arraycopy(nextSteamProperties, 0, steamProperties, 0,
                    SteamNode.STEAM_PROPERTIES_LENGTH);
            Arrays.fill(nextSteamProperties, 0.0);
        }
        if (nextTotalMassKnown) {
            totalMass = nextTotalMass;
            nextTotalMass = -1.0;
            nextTotalMassKnown = false;
        }
        // reset steam props updated is done by overriding preparecalulation
        // of GeneralPort in SteamNode, so no need to do it here again.
        staticPropertiesCalculated = false;
    }

    @Override
    public boolean doSteamCalculation() {
        boolean didSomething = false;
        boolean allFlowsKnown, flowPresent;
        double deltaEnthalpy, enthalpy;
        // pressure is always known at parent element as it is enforcing it.
        double pressure = ((SelfCapacitance) element).getEffort();

        if (!staticPropertiesCalculated) { // do this only once as it is expensive.
            // To determine if we are in a sub-steam state with pressurized liq.
            // only, we will calculate the saturation temperature out of the
            // forced pressure. 
            saturationTemperature
                    = propertyTable.get("TSat_p", pressure);
            pressurizedLiquidState = saturationTemperature >= steamProperties[0];

            enthalpySaturatedLiquid
                    = propertyTable.get("hLiq_p", pressure);
            entropySaturatedLiquid
                    = propertyTable.get("sLiq_p", pressure);

            if (pressurizedLiquidState) {
                // h_pT can jump to the vapour value if not specified, but 
                // h_px will always use saturation properties and is 
                // too high if the liquid is pressurized and not in vapour
                // state. This will keep stability in the phase at the
                // end of the pressurized liquid state.
                enthalpyPressurizedLiquid = Math.min(propertyTable.get(
                        "h_pT", pressure, steamProperties[0]),
                        enthalpySaturatedLiquid);
                entropyPressurizedLiquid = Math.min(propertyTable.get(
                        "s_pT", pressure, steamProperties[0]),
                        entropySaturatedLiquid);
            }
            enthalpySaturatedGas
                    = propertyTable.get("hSteam_p", pressure);

            entropySaturatedGas
                    = propertyTable.get("sSteam_p", pressure);

            staticPropertiesCalculated = true;
            didSomething = true;
        }

        // Check that all flows are known. We can already assign the properties
        // that will be imposed by this element to all leaving flows.
        allFlowsKnown = true; // init
        for (SteamNode sp : steamNodes) {
            if (sp.flowUpdated(element)) {
                if (!sp.steamPropertiesUpdated(element)
                        && sp.getFlow(element) < 0.0
                        && staticPropertiesCalculated) {
                    // The saturated steam will be split to liquid and gas,
                    // depending on the configuration of that port. The values
                    // which are set here will be reused in this same method
                    // later for calculating the deltas for next state.
                    if (isLiquidNode[steamNodes.indexOf(sp)]) {
                        if (pressurizedLiquidState) {
                            sp.setSteamProperties(
                                    steamProperties[0],
                                    enthalpyPressurizedLiquid,
                                    entropyPressurizedLiquid,
                                    0.0,
                                    element);
                        } else {
                            sp.setSteamProperties(
                                    steamProperties[0],
                                    enthalpySaturatedLiquid,
                                    entropySaturatedLiquid,
                                    0.0,
                                    element);
                        }
                    } else {
                        sp.setSteamProperties(
                                steamProperties[0],
                                enthalpySaturatedGas,
                                entropySaturatedGas,
                                1.0,
                                element);
                    }
                    didSomething = true;
                }
            } else {
                allFlowsKnown = false;
            }
        }
        flowPresent = false;
        if (allFlowsKnown) {
            // check that there is at least one flow, otherwise 
            // theres nothing to do.
            for (SteamNode sp : steamNodes) {
                if (sp.getFlow(element) != 0.0) {
                    flowPresent = true;
                    break;
                }
            }
        }
        if (nextPropertiesPrepared) { // calculation is finished.
            return didSomething;
        }
        // Absolutely no flow: Re-use previous value.
        if (allFlowsKnown && !flowPresent) {
            System.arraycopy(steamProperties, 0, nextSteamProperties, 0,
                    SteamNode.STEAM_PROPERTIES_LENGTH);
            nextPropertiesPrepared = true;
            return true;
        }

        // Calculate the next absolute enthalpy based on the next total mass
        // (calculated in element) and the enthalpy in and out flow (calculated
        // here).
        if (allFlowsKnown && nextTotalMassKnown) {
            deltaEnthalpy = 0.0;
            for (SteamNode sp : steamNodes) {
                if (sp.getFlow(element) != 0.0) {
                    deltaEnthalpy = deltaEnthalpy
                            + sp.getSteamProperty(1, element)
                            * sp.getFlow(element) * stepTime;
                }
            }
            enthalpy = totalMass * steamProperties[1]; // absolute enthalpy H
            // next specific enthalpy:
            nextSteamProperties[1] = (enthalpy + deltaEnthalpy) / nextTotalMass;

            // Use the approximation for getting the new temperature for the
            // new enthapy.
            nextSteamProperties[0] = m * nextSteamProperties[1] + b;

            nextPressure = Math.max(ambientPressure,
                    propertyTable.get("pSat_T", nextSteamProperties[0]));

            nextPropertiesPrepared = true;
            return true;
        }
        return didSomething;
    }

    @Override
    public boolean isSteamCalulationFinished() {
        return nextPropertiesPrepared;
    }

    @Override
    public void setSteamProperty(int index, double value) {
        steamProperties[index] = value;
    }

    @Override
    public double getSteamProperty(int index) {
        return steamProperties[index];
    }

    @Override
    public double getTotalMass() {
        return totalMass;
    }

    @Override
    public void setTotalMass(double totalMass) {
        this.totalMass = totalMass;
    }

    /**
     * The corresponding element calculates the total mass and uses this method
     * to set it to the handler which stores the value.
     *
     * @param nextTotalMass Mass in Element in next cycle.
     */
    public void setNextTotalMass(double nextTotalMass) {
        if (nextTotalMass < 0.0) {
            throw new ModelErrorException("Tried to set no or negative mass. "
                    + "This can't be handled by this class.");
        }
        this.nextTotalMass = nextTotalMass;
        nextTotalMassKnown = true;
    }

    /**
     * The corresponding element will use this to get the prepared pressure for
     * the next calculation run from this element. The pressure is calulated
     * here but stored in element, therefore only the next-variable does exist.
     *
     * @return Pressure in Pascal
     */
    public double getNextPressure() {
        if (!nextPropertiesPrepared) {
            throw new ModelErrorException("Tried to request non-updated "
                    + "state value.");
        }
        return nextPressure;
    }

    public double getTemperature() {
        return steamProperties[0];
    }

    public void setStepTime(double dt) {
        this.stepTime = dt;
    }

}
