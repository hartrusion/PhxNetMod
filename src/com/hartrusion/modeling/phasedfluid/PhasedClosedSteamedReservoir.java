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

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.SelfCapacitance;

/**
 * A closed reservoir filled with phased fluid. The fluid can be heated up to
 * its boiling temperature and the pressure inside the vessel is then defined by
 * the saturation pressure. It is possible to set an ambient pressure which will
 * always be present (for example 1 bar or 1e5 Pa) and the fluid will be stored
 * as cold, non saturated fluid with lower temperature.
 *
 * <p>
 * Nodes can be configured to be either liquid or steam state to define where on
 * this element they are connected.
 *
 * <p>
 * The stateValue of this element is defined to be the absolute mass in kg
 * inside this element. As we strictly use kg/s for mass flow, time constant tau
 * will be 1.0.
 *
 * <p>
 * The element will not apply hydrostatic pressure on the nodes as this would
 * require the steam nodes to not have this pressure and this would be contrary
 * to the definition of the self capacitance element, which applies same
 * pressure on all nodes and this is expected by the solving algorithms.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedClosedSteamedReservoir extends SelfCapacitance
        implements PhasedElement {

    private PhasedAbstractVolumizedHandler phasedHandler;

    private final PhasedFluidProperties fluidProperties;

    /**
     * Pressure in Pa which is always maintained in the vessel. Initialized for
     * 1 bar ambient pressure for 100 deg C boiling temperature. The pressure or
     * effort value will never be lower than this value.
     */
    private final double ambientPressure = 1e5;

    /**
     * Absolute pressure of the fluid. Will not drop below ambient pressure.
     * This is applied as effort value to connected nodes.
     */
    private double absoluteFluidPressure;

    /**
     * Current temperature of the fluid, calculated from the heat energy
     * contained in it assuming that there is no energy stored in evaporation.
     */
    private double fluidTemperature = 293.15;

    /**
     * Area in m^2, allows calculation of fill height.
     */
    private double baseArea = 1.0;

    /**
     * Sets the base area in m^2 of the reservoir. This value is used to
     * calculate a fill height in meter.
     *
     * @param baseArea
     */
    public void setBaseArea(double baseArea) {
        if (baseArea <= 0.0) {
            throw new IllegalArgumentException("Base area must be a positive"
                    + " value.");
        }
        this.baseArea = baseArea;
    }

    public PhasedClosedSteamedReservoir(PhasedFluidProperties fluidProperties) {
        super(PhysicalDomain.PHASEDFLUID);
        setTimeConstant(1.0); // see doc header
        this.fluidProperties = fluidProperties;
        // attach the default volumized handler
        // phasedHandler = new PhasedVolumizedHandler(fluidProperties, this);
        // updated: attach the separation handler for fluid and steam separation
        phasedHandler = new PhasedVolumeSeparationHandler(
                fluidProperties, this);
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        phasedHandler.setInnerHeatedMass(stateValue);
        phasedHandler.preparePhasedCalculation();

        // Get the current temperature once from the now set heat energy, 
        // assuming that there is no evaporation, or, more precisely, no 
        // energy was used for evaporation.
        fluidTemperature = phasedHandler.getHeatEnergy()
                / fluidProperties.getSpecificHeatCapacity();

        // Let the pressure rise as soon as the temperature is above the 
        // temperature where the fluid would evaporate in theory on ambient
        // pressure.
        if (fluidTemperature
                >= fluidProperties.getSaturationTemperature(ambientPressure)) {
            absoluteFluidPressure = fluidProperties.getSaturationEffort(
                    fluidTemperature);
        } else {
            absoluteFluidPressure = ambientPressure;
        }
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        PhasedNode pn;

        didSomething = didSomething
                || setEffortValueOnNodes(absoluteFluidPressure);

        // Sum up all incoming flows like its done in SelfCapacitance class.
        // This will call the .integrate() method from EnergyStorage.
        didSomething = didSomething || calculateNewDeltaValue();

        // Add call for thermalhandler calculation
        didSomething = didSomething || phasedHandler.doPhasedCalculation();

        // call calulation on thermal nodes - contrary to flow, it is not
        // possible to do this with the set-operation as it is unknown when 
        // that calculation will be possible.
        for (GeneralNode p : nodes) {
            pn = (PhasedNode) p;
            didSomething = didSomething || pn.doCalculateHeatEnergy();
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for phasedHandler calculationfinished
        return super.isCalculationFinished()
                && phasedHandler.isPhasedCalulationFinished();
    }

    @Override
    public void registerNode(GeneralNode p) {
        super.registerNode(p);
        phasedHandler.registerPhasedNode((PhasedNode) p);
    }

    @Override
    public PhasedHandler getPhasedHandler() {
        return phasedHandler;
    }

    /**
     * Allows replacing the attached phased handler with an other handler class.
     * Intended ussage is to be able to attach the PhasedThermalVolume handler
     * to be able to simulate thermal/temperature exchange with the reservoir.
     * For this case, the handler has to be replaced.
     *
     * @param phasedHandler
     */
    public void setPhasedHandler(PhasedAbstractVolumizedHandler phasedHandler) {
        this.phasedHandler = phasedHandler;
    }

    @Override
    public void setEffort(double effort, PhasedNode source) {
        setEffort(effort, (GeneralNode) source);
    }

    @Override
    public void setFlow(double flow, PhasedNode source) {
        setFlow(flow, (GeneralNode) source);
    }

    @Override
    public void setStepTime(double dt) {
        super.setStepTime(dt);
        phasedHandler.setStepTime(dt);
    }

    // Todo: Initial condition, see HeatFluidTank element
    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return fluidProperties;
    }

    /**
     * Returns the total stored mass in kg. The stored mass is the integral
     * value of the mass flows into the element without any factor.
     *
     * @return mass in kg
     */
    public double getStoredMass() {
        return stateValue;
    }

    @Override
    public double getEffort() {
        // this has to be overwritten as at least transfersubnet solver will
        // use this to configure a replacement source element
        return absoluteFluidPressure;
    }

    /**
     * Gets the current temperature of the fluid.
     *
     * @return
     */
    public double getTemperature() {
        return fluidTemperature;
    }

    /**
     * Get the current fill height of the reservoir.
     *
     * @return level in m
     */
    public double getFillHeight() {
        double rhoFluid = fluidProperties
                .getDensityLiquid(absoluteFluidPressure);

        return stateValue / rhoFluid / baseArea;
    }

    public void setInitialState(double storedMass, double temperature) {
        stateValue = storedMass;
        phasedHandler.setInitialHeatEnergy(
                fluidProperties.getSpecificHeatCapacity() * temperature);
    }

    /**
     * Defines the given connected node to be a steam output node. Flow out of
     * this element towards this node will always be in gas phase, requiring the
     * element to be in a near evaporatin state. If the contained fluid is far
     * from evaporation, a warning will be sent to the log. If you use this the
     * model has to be made in a way that ensures no flow is going out below the
     * evaporation. This can be achieve with automatic valves which open only if
     * a certain pressure is reaced.
     *
     * @param pn
     */
    public void setSteamOut(PhasedNode pn) {
        if (!nodes.contains(pn)) {
            throw new ModelErrorException("Provided node is not connected"
                    + "to this element.");
        }
    }

    public boolean isSteamOut(PhasedNode pn) {
        if (!nodes.contains(pn)) {
            throw new ModelErrorException("Provided node is not connected"
                    + "to this element.");
        }
        return false; // todo
    }

}
