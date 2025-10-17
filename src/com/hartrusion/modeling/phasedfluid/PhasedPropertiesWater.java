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

import com.hartrusion.modeling.steam.SteamTable;

/**
 * Very limited steam properties class that can be used instead of the real
 * steam table if only saturated pressure and temperature is used. Will use a
 * very very basic formula for calculation but is, on the other hand, very fast.
 *
 * <p>
 * The intended use is NOT to provide acurate values but to provide somehing
 * that looks like it would be steam for the operator.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedPropertiesWater implements
        PhasedFluidProperties, SteamTable {

    @Override
    public double getSpecificHeatCapacity() {
        return 4200;
    }

    @Override
    public double getVaporizationHeatEnergy() {
        return 2100000;
    }

    @Override
    public double getSaturationEffort(double temperature) {
        return 1e5 * Math.pow((temperature - 273.5) / 100.0, 4.0);
    }

    @Override
    public double getSaturationTemperature(double effortValue) {
        // Temperature = 4th root of pressure times 100 with
        // pressure in bar and temperature in °C
        return 100.0 * Math.pow(effortValue * 1e-5, 0.25) + 273.5;
    }

    @Override
    public double getLiquidHeatCapacity(double effortValue) {
        return getSaturationTemperature(effortValue)
                * getSpecificHeatCapacity();
    }

    @Override
    public double getVapourFraction(double heatEnergy, double effortValue) {
        double liquidCapacity = getLiquidHeatCapacity(effortValue);
        double vapourEnergy;
        if (heatEnergy <= liquidCapacity) {
            return 0.0; // full liquid
        }
        vapourEnergy = getVaporizationHeatEnergy();
        if (heatEnergy >= liquidCapacity + vapourEnergy) {
            return 1.0; // full steam
        }
        // reaced here: only a part is vapourized, return the fraction as 
        // fraction of specific energy.
        return (heatEnergy - liquidCapacity) / vapourEnergy;
    }

    @Override
    public double getDensity(double heatEnergy, double effortValue) {
        double vapourFraction = getVapourFraction(heatEnergy, effortValue);
        if (vapourFraction <= 0.0) {
            return getDensityLiquid(effortValue);
        }
        if (vapourFraction >= 1.0) {
            return getDensityVapor(effortValue);
        }
        // interpolate between X=0 and X=1
        return (1.0 - vapourFraction) * getDensityLiquid(effortValue)
                + vapourFraction * getDensityVapor(effortValue);
    }

    @Override
    public double getAvgDensity(double heatEnergyStart, double heatEnergyEnd,
            double effortValue) {
        if (heatEnergyStart == heatEnergyEnd) {
            return getDensity(heatEnergyStart, effortValue);
        } else if (heatEnergyStart > heatEnergyEnd) {
            // reverse inputs if start has the higher value.
            double reverse = heatEnergyStart;
            heatEnergyStart = heatEnergyEnd;
            heatEnergyEnd = reverse;
        }
        // Point where the fluid is exactly at X=0 and starts to evaporate.
        double heatEnergySaturatedFluid
                = getLiquidHeatCapacity(effortValue);
        double heatEnergySaturatedGas
                = heatEnergySaturatedFluid + getVaporizationHeatEnergy();

        // Calculate integral rho over heatEnergy. Graph looks like this:
        //
        //     ^
        // rho |
        //     |---------o
        //     |           o
        //     |             o
        //     |               o
        //     |                 o---------------
        //     |
        //     o---------|-------|------------------>
        //        heSatFluid  heSatGas           heatEnergy
        //
        // The graph will be different for each effortValue but we have one
        // constant effort value.
        double integral = 0.0;

        // Left part with constant fluid density
        if (heatEnergyStart < heatEnergySaturatedFluid) {
            if (heatEnergyEnd >= heatEnergySaturatedFluid) {
                integral = (heatEnergySaturatedFluid - heatEnergyStart)
                        * getDensityLiquid(effortValue);
            } else {
                integral = (heatEnergyEnd - heatEnergyStart)
                        * getDensityLiquid(effortValue);
            }
        }
        // Evaporation part in the middle, this is a straight line downwards.
        if (heatEnergyStart < heatEnergySaturatedGas
                && heatEnergyEnd > heatEnergySaturatedFluid) {
            double x1, x2, y1, y2;
            if (heatEnergyStart < heatEnergySaturatedFluid) {
                x1 = heatEnergySaturatedFluid;
                y1 = getDensityLiquid(effortValue);
            } else {
                x1 = heatEnergyStart;
                y1 = getDensity(heatEnergyStart, effortValue);
            }
            if (heatEnergyEnd >= heatEnergySaturatedGas) {
                x2 = heatEnergySaturatedGas;
                y2 = getDensityVapor(effortValue);
            } else {
                x2 = heatEnergyEnd;
                y2 = getDensity(heatEnergyEnd, effortValue);
            }
            integral = integral + 
                    (x2-x1) * y2 // square below 
                    + (x2-x1) * (y1-y2) * 0.5; // triangle part
                    
        }
        if (heatEnergyEnd > heatEnergySaturatedGas) {
            throw new UnsupportedOperationException("Todo, not yet implemented");
        }
        
        // calculate average
        return integral / (heatEnergyEnd - heatEnergyStart);
    }

    @Override
    public double getTemperature(double heatEnergy, double effortValue) {
        double liquidCapacity = getLiquidHeatCapacity(effortValue);
        double specHeatCap = getSpecificHeatCapacity();
        double vapourEnergy;
        if (heatEnergy <= liquidCapacity) {
            return heatEnergy / specHeatCap; // liquid state
        }
        vapourEnergy = getVaporizationHeatEnergy();
        if (heatEnergy >= liquidCapacity + vapourEnergy) {
            // Full steam state: evaporization does not count for temperature
            // rise so subtract this from the energy value.
            return (heatEnergy - vapourEnergy) / specHeatCap;
        }
        // Liquid/Vapour mixture: Until evaporation is complete the temperature
        // stays at the evaporation temperature.
        return liquidCapacity / specHeatCap;
    }

    @Override
    public double getDensityLiquid(double pressure) {
        if (pressure <= 1e5) {
            return 958.6368896760329;
        }
        // Linear for p=1e5 with 958.63 and p=1e7 with 688.41
        // Values taken from IF97
        return -2.72955107660472e-5 * pressure + 961.366440752638;
    }

    @Override
    public double getDensityVapor(double pressure) {
        if (pressure <= 1e5) {
            return 0.5903109235445781;
        }
        // Linear for p=1e5 with 0.590 and p=1e7 with 55.4521
        return 5.54159701208284e-6 * pressure + 0.0361512223362946;
    }

    // Also provide those properties poorly for steam property:
    @Override
    public double get(String function, double arg) {
        switch (function) {
            case "pSat_T" -> {
                return getSaturationEffort(arg);
            }
            case "TSat_p" -> {
                return getSaturationTemperature(arg);
            }
        }
        throw new UnsupportedOperationException("Unknown function");
    }

    @Override
    public double get(String function, double arg1, double arg2) {
        switch (function) {
            case "rho_Tx" -> {
                // use saturation pressure and next case.
                return get("rho_px", getSaturationEffort(arg1), arg2);
            }
            case "rho_px" -> {
                if (arg2 <= 0.0) {
                    return getDensityLiquid(arg1);
                }
                if (arg2 >= 1.0) {
                    return getDensityVapor(arg1);
                }
                // interpolate between X=0 and X=1
                return (1.0 - arg2) * getDensityLiquid(arg1) + arg2 * getDensityVapor(arg1);
            }


        }
        throw new UnsupportedOperationException("Unknown function");
    }
}
