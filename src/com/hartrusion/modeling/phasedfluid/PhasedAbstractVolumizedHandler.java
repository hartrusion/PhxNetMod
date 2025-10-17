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
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.util.ArraysExt;

/**
 * Abstract handler class that does basics of assigning a mass with heat energy
 * and providing some common methods. Will be extended to more specialized
 * handler classes.
 *
 * <p>
 * Handles volumized heat energy.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class PhasedAbstractVolumizedHandler implements PhasedHandler {

    /**
     * Contains all nodes that the element which uses this phased handler has.
     */
    protected final List<PhasedNode> phasedNodes = new ArrayList<>();

    /**
     * Nodes that are placed on the upper side of a vessel will always output
     * the steam phase of the phased fuid while the lower nodes will always
     * output liquid. This array is to identify what kind of output there is.
     */
    protected boolean[] isSteamOutput = new boolean[1];

    /**
     * A reference to a phased element which has this handler assigned.
     */
    protected final PhasedElement element; // reference to element assigning this

    /**
     * Heated volume inside the element in the unit of the integral of the flow.
     * This has to be set in kg.
     */
    protected double innerHeatMass = 10;

    /**
     * Heat energy that is present in this element. The default value is for 25
     * deg Celsius with liquid water.
     */
    protected double heatEnergy = 1252230;

    protected double stepTime = 0.1;

    /**
     * The ultimate goal of this handler is to calculate the heat energy which
     * it will have during the next step. The prepareCalculation call will set
     * this as the new heat energy for the upcoming calculation run.
     */
    protected double nextHeatEnergy = 1252230;

    /**
     * Value of nextHeatEnergy is calculated and can be used for next cycle.
     * This will be set to true when the calculation run is complete and mark it
     * as such. When resetting the calculation by calling prepareCalculation,
     * the variable will be reset after the heat energy for the next cycle was
     * set as the current heat energy.
     */
    protected boolean heatEnergyPrepared = false;

    /**
     * Marks the value heatEnergy as valid and updated, which basically means it
     * was either set externally as first initial condition (that's why we
     * initialize it with true) or the heatEnergy was successfully set from the
     * nextHeatEnergy variable in prepareCalculation.
     */
    protected boolean heatEnergyUpdated = true;

    /**
     * To determine the first call of the prepare function, this can be called
     * multiple times by the solver. Will be initialized with false to not
     * trigger the updated/prepared-check on the first call.
     */
    protected boolean firstPrepareCalcCall = false;

    protected PhasedFluidProperties fluidProperties;

    PhasedAbstractVolumizedHandler(
            PhasedFluidProperties fluidProperties,
            PhasedElement parent) {
        this.fluidProperties = fluidProperties;
        this.element = parent;
    }

    @Override
    public boolean registerPhasedNode(PhasedNode pn) {
        if (!phasedNodes.contains(pn)) {
            phasedNodes.add(pn);
            if (isSteamOutput.length != phasedNodes.size()) {
                isSteamOutput = ArraysExt.newArrayLength(
                        isSteamOutput, phasedNodes.size());
            }
            return true;
        }
        return false;
    }

    @Override
    public void preparePhasedCalculation() {
        firstPrepareCalcCall = false;
        if (heatEnergyPrepared) {
            heatEnergy = nextHeatEnergy;
        }
        heatEnergyPrepared = false;
        // reset temperature updated is done by overriding precalculating
        // of GeneralNode in PhasedNode, so no need to do it here again.
    }

    @Override
    public boolean doPhasedCalculation() {
        firstPrepareCalcCall = true; // reset this
        return false;
    }

    /**
     * Checks if all heat energy values from nodes connected to the element
     * which is assigned to this handler are updated.
     *
     * @return true if all nodes have updated heat energies.
     */
    protected boolean allHeatEnergiesUpdated() {
        for (PhasedNode pn : phasedNodes) {
            if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if all heat energy properties on flows that come into this element
     * are updated.
     *
     * @return true if flows are updated and all incoming heat energies are also
     * in an updated state.
     */
    protected boolean allInboundHeatEnergiesUpdated() {
        for (PhasedNode pn : phasedNodes) {
            if (!pn.flowUpdated((AbstractElement) element)) {
                return false; // prevent exception if flow is not updated
            }
            if (pn.getFlow((AbstractElement) element) <= 0.0) {
                continue; // zero or outgoing flows irrelevant here
            }
            if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if all flow values from nodes toward the element which is assigned
     * to this handler are updated. This private method just provides a small
     * shortcut that is used internally.
     *
     * @return true if all nodes have their flows updated towards the assigned
     * element.
     */
    protected boolean allFlowsUpdated() {
        for (PhasedNode pn : phasedNodes) {
            if (!pn.flowUpdated((AbstractElement) element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPhasedCalulationFinished() {
        return heatEnergyPrepared && allHeatEnergiesUpdated();
    }

    @Override
    public void setInitialHeatEnergy(double q) {
        heatEnergy = q;
    }

    @Override
    public double getHeatEnergy() {
        return heatEnergy;
    }

    @Override
    public void setInnerHeatedMass(double heatedMass) {
        if (heatedMass < 0) {
            throw new ModelErrorException("Thermal mass must never be a "
                    + "negative value, this is physically impossible.");
        }
        this.innerHeatMass = heatedMass;
    }

    public void setStepTime(double dt) {
        stepTime = dt;
    }

    public PhasedFluidProperties getPhasedFluidProperties() {
        return fluidProperties;
    }

    public void setSteamOutput(PhasedNode pn, boolean isSteamOut) {
        if (!phasedNodes.contains(pn)) {
            throw new ModelErrorException("Provided node is not registered "
                    + "with this Element.");
        }
        isSteamOutput[phasedNodes.indexOf(pn)] = isSteamOut;
    }
    
    public boolean getSteamOutput(PhasedNode pn) {
        if (!phasedNodes.contains(pn)) {
            throw new ModelErrorException("Provided node is not registered "
                    + "with this Element.");
        }
        return isSteamOutput[phasedNodes.indexOf(pn)];
    }
}
