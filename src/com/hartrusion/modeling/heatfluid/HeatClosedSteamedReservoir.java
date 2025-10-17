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

import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.initial.InitialConditions;
import com.hartrusion.modeling.steam.SteamTable;

/**
 * Represents a tank for a heat fluid that can built up pressure if the fluid
 * exceeds the set ambient pressure. This can be used as outer boundary for
 * steam systems as this will prevent vacuum.
 *
 * <p>
 * To be used where a heat fluid is kept under pressure but no calculation of
 * steam properties is necessary.
 *
 * <p>
 * The forced effort on the nodes will be calculated by adding the following
 * values:
 *
 * <ul>
 * <li>Hydrostaic pressure, represented by the stateValue of the
 * SelfCapacitance</li>
 * <li>Absolute ambient pressure</li>
 * <li>Saturated steam pressure as p_sat = f(T_sat)</li>
 * </ul>
 *
 * <p>
 * The saturated steam pressure is only applied additonally if the temperature
 * exceeds the saturation temperature of the ambient pressure. Before that, only
 * the ambient pressure is applied. This will keep the effort value on an
 * absolute pressure level.
 *
 * <p>
 * This was built to have a representation of vessels which were built to have
 * high internal pressure but can not sustain a vacuum which would be there if
 * they cool down without breaking the vakuum by filling the vessel with air.
 * Such vessels are one reason on how air gets into process water in power
 * plants. Also lots of gaskets and armatures can be designed to withstand only
 * positive pressures greater than ambient pressure and may fail in vacuum.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatClosedSteamedReservoir extends HeatFluidTank
        implements InitialConditions {

    /**
     * Pressure in Pa which is always maintained in the vessel. Initialized for
     * the ambient pressure which is present when the saturation temperature of
     * steam is at exactly 100 degree celsius according to IF97 steam tables.
     */
    private final double ambientPressure = 101417.97792131014;

    /**
     * Temperature where evaporation starts. This is linked to the
     * ambientPressure and pre set to 100 deg Celsius for water.
     */
    private final double evaporationTemperature = 273.15 + 100;

    /**
     * To calculate the fill height of the reservoir, this constant represents
     * the hydrostatic pressure for a fill level of 1 m or per meter, depending
     * on putting * 1m in there or not, doesnt matter in SI unit system. Preset
     * for water with 997 kg/m^3 on earth.
     *
     * <p>
     * rho * g * h = 997 kg / m^3 * 9.81 m / s^2 * 1 m = 9780.57 Pa
     */
    private final double hydrostaticPressurePerFillHeight = 9780.57;

    SteamTable propertyTable;

    private double steamPressure;

    public HeatClosedSteamedReservoir(SteamTable propertyTable) {
        super();
        this.propertyTable = propertyTable;
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation(); // set new stateValue, reset deltacalulated

        // As the temperature is a value that is calulated by the call of super
        // prepareCalculation, the pressure which is dependend on this value
        // must be available before starting a calculation to provide proper
        // starting conditions for the calulation run. Otherwise the effort
        // value would change but it is vital for capacitance classes to have
        // the exact final effort value before the calculation run starts.
        if (heatHandler.getTemperature() > evaporationTemperature) {
            steamPressure = propertyTable.get("pSat_T",
                    heatHandler.getTemperature());
        } else {
            steamPressure = ambientPressure;
        }
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        HeatNode tp;
        
        didSomething = didSomething
                || setEffortValueOnNodes(stateValue + steamPressure);

        // Sum up all incoming flows like its done in SelfCapacitance class.
        // This will call the .integrate() method from EnergyStorage.
        didSomething = didSomething || calculateNewDeltaValue();

        // Add call for thermalhandler calculation
        didSomething = didSomething || heatHandler.doThermalCalculation();

        // call calulation on thermal nodes - contrary to flow, it is not
        // possible to do this with the set-operation as it is unknown when 
        // that calculation will be possible.
        for (GeneralNode p : nodes) {
            tp = (HeatNode) p;
            didSomething = didSomething || tp.doCalculateTemperature();
        }

        return didSomething;
    }

    @Override
    public double getEffort() {
        // this has to be overwritten as at least transfersubnet solver will
        // use this to configure a replacement source element
        return stateValue + steamPressure;
    }

    /**
     * Returns the total stored mass in kg. The stored mass is the integral
     * value of the mass flows into the element without any factor.
     *
     * @return mass in kg
     */
    public double getStoredMass() {
        return stateValue / tau;
    }

    /**
     * Retruns the fill height of the reservoir.
     *
     * @return level in m
     */
    public double getFillHeight() {
        return stateValue / hydrostaticPressurePerFillHeight;
    }

    public void setInitialFillHeight(double level) {
        stateValue = hydrostaticPressurePerFillHeight * level;
    }
}
