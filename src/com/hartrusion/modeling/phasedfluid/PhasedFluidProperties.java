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

/**
 *
 * @author Viktor Alexander Hartung
 */
public interface PhasedFluidProperties {

    /**
     * Heat energy per temperature unit per mass as a constant value. This is a
     * constant as per definition of the phased fluid domain.
     *
     * @return value in J/kg/K
     */
    public double getSpecificHeatCapacity();

    /**
     * Required heat energy to transfer from liquid to the vapour state.
     *
     * @return value in J/kg
     */
    public double getVaporizationHeatEnergy();

    /**
     * Saturation effort value (pressure) as function of temperature
     *
     * @param temperature in Kelvin
     * @return value in Pa (N/m^2)
     */
    public double getSaturationEffort(double temperature);

    /**
     * Saturation temperature function of effort value (pressure)
     *
     * @param effortValue in Pa (N/m^2)
     * @return value in Kelvin
     */
    public double getSaturationTemperature(double effortValue);

    /**
     * Depending on the effort (pressure) there is only a certain amount of heat
     * energy that can be stored in the liquid. Everthing above this will be
     * vaporized.
     *
     * @param effortValue Pressue in Pa (N/m^2)
     * @return Maximum heat capacity for liquid in J/kg
     */
    public double getLiquidHeatCapacity(double effortValue);

    
    public double getDensity(double heatEnergy, double effortValue);

    /**
     * Calculates the average density between two heat energies for given
     * effortValue. This is used in PhasedExpandingThermalVolumeHandler for some
     * nasty approach.
     *
     * @param heatEnergyStart
     * @param heatEnergyEnd
     * @param effortValue
     * @return
     */
    public double getAvgDensity(double heatEnergyStart, double heatEnergyEnd,
            double effortValue);
    
    /**
     * Density as funcion of pressure for liquid part.
     *
     * @param pressure
     * @return
     */
    
    public double getDensityLiquid(double pressure);

    /**
     * Density as funcion of pressure for vapour part.
     *
     * @param pressure
     * @return
     */
    public double getDensityVapor(double pressure);

    public double getVapourFraction(double heatEnergy, double effortValue);

    public double getTemperature(double heatEnergy, double effortValue);

}
