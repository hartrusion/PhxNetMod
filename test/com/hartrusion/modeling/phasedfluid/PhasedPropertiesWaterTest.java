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

import static org.testng.Assert.*;
import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedPropertiesWaterTest {
    public PhasedPropertiesWaterTest() {
    }

    /**
     * Test of getSaturationEffort method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetSaturationEffort() {
        // 0 degree Celsius = 0 Pa
        assertEquals(Water.INSTANCE.getSaturationEffort(273.15), 0.0, 1.0);
        // 100 degree Celsius = 1e5 Pa with some error
        assertEquals(Water.INSTANCE.getSaturationEffort(373.15), 1e5, 2e4);
        // 275.5 degree Celsius = 60e5 Pa with some error
        assertEquals(Water.INSTANCE.getSaturationEffort(549.0), 60e5, 5e5);
    }

    /**
     * Test of getSaturationTemperature method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetSaturationTemperature() {
        // 1 bar must be 100 Degree Celsius
        assertEquals(Water.INSTANCE.getSaturationTemperature(1e5), 373.15, 5.0);
        // 60 bar must be around 275.5 degree Celsius
        assertEquals(Water.INSTANCE.getSaturationTemperature(60e5), 549.0, 5.0);
    }

    /**
     * Test of getVapourFraction method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetVapourFraction() {
        // Heat energy for 95 Deg Celsius without evaporation
        double liquidHeatEnergy = (273.5 + 95)
                * Water.INSTANCE.getSpecificHeatCapacity();
        // 95 degrees Celsius with 1 bar ambient pressure:
        assertEquals(Water.INSTANCE.getVapourFraction(liquidHeatEnergy, 1e5),
                0.0, 0.0);
        // Add the vaporization energy at 100 deg Celsius, expect X = 1.0
        liquidHeatEnergy = (273.5 + 100.5)
                * Water.INSTANCE.getSpecificHeatCapacity()
                + Water.INSTANCE.getVaporizationHeatEnergy();
        assertEquals(Water.INSTANCE.getVapourFraction(liquidHeatEnergy, 1e5),
                1.0, 0.0);
        // Half the vapour energy should be X = 0.5
        liquidHeatEnergy = (273.5 + 100)
                * Water.INSTANCE.getSpecificHeatCapacity()
                + Water.INSTANCE.getVaporizationHeatEnergy() / 2;
        assertEquals(Water.INSTANCE.getVapourFraction(liquidHeatEnergy, 1e5),
                0.50, 0.01);
        // Increase pressure for same energy, X will be less
        assertEquals(Water.INSTANCE.getVapourFraction(liquidHeatEnergy, 5e5),
                0.40, 0.02);

    }

    /**
     * Test of getDensity method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetDensity() {
        // Heat energy for 20 Deg Celsius without evaporation
        double liquidHeatEnergy = (273.5 + 20)
                * Water.INSTANCE.getSpecificHeatCapacity();
        // Water with 20 deg and 1 bar, must be around 1000 kg per m^3
        assertEquals(Water.INSTANCE.getDensity(liquidHeatEnergy, 1e5),
                970, 50);
        // Fully evaporate: Expect something between 0 and 2
        liquidHeatEnergy = (273.5 + 100.5)
                * Water.INSTANCE.getSpecificHeatCapacity()
                + Water.INSTANCE.getVaporizationHeatEnergy();
        assertEquals(Water.INSTANCE.getDensity(liquidHeatEnergy, 1e5),
                1.0, 1.0);
        // Increase pressure to 5 bar, result will be around 100
        assertEquals(Water.INSTANCE.getDensity(liquidHeatEnergy, 5e5),
                100.0, 10.0);
    }

    /**
     * Test of getAvgDensity method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetAvgDensity() {
        // Room temperature
        double heatEnergyRoom = (273.5 + 20)
                * Water.INSTANCE.getSpecificHeatCapacity();

        // With vaporization energy at 100 deg Celsius for X=0.7 at 1 bar
        double heatEnergyPartialSteam = (273.5 + 100.5)
                * Water.INSTANCE.getSpecificHeatCapacity()
                + Water.INSTANCE.getVaporizationHeatEnergy() * 0.7;
        
        // Before evaporation
        double heatEnergyBoilingStarts = (273.5 + 97)
                * Water.INSTANCE.getSpecificHeatCapacity();
        
        assertEquals(Water.INSTANCE.getAvgDensity(
                heatEnergyRoom, heatEnergyBoilingStarts, 1e5),
                950.0, 50.0); // 921.287
        
        assertEquals(Water.INSTANCE.getAvgDensity(
                heatEnergyBoilingStarts, heatEnergyPartialSteam, 1e5),
                600.0, 100.0); // 625.69
    }
}
