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

import com.hartrusion.modeling.phasedfluid.PhasedPropertiesWater;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedPropertiesWaterNGTest {

    PhasedPropertiesWater instance;

    public PhasedPropertiesWaterNGTest() {
    }

    @BeforeClass
    public void setUpClass() throws Exception {
        instance = new PhasedPropertiesWater();
    }

    @AfterClass
    public void tearDownClass() throws Exception {
        instance = null;
    }

    /**
     * Test of getSaturationEffort method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetSaturationEffort() {
        System.out.println("getSaturationEffort");
        // 0 degree celsius = 0 Pa
        assertEquals(instance.getSaturationEffort(273.15), 0.0, 1.0);
        // 100 degree celsius = 1e5 Pa with some error
        assertEquals(instance.getSaturationEffort(373.15), 1e5, 2e4);
        // 275.5 degree celsius = 60e5 Pa with some error
        assertEquals(instance.getSaturationEffort(549.0), 60e5, 5e5);
    }

    /**
     * Test of getSaturationTemperature method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetSaturationTemperature() {
        System.out.println("getSaturationTemperature");
        // 1 bar must be 100 Degree celsius
        assertEquals(instance.getSaturationTemperature(1e5), 373.15, 5.0);
        // 60 bar must be around 275.5 degree celsius
        assertEquals(instance.getSaturationTemperature(60e5), 549.0, 5.0);
    }

    /**
     * Test of getVapourFraction method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetVapourFraction() {
        System.out.println("getVapourFraction");
        // Heat energy for 95 Deg Celsius without evaporation
        double liquidHeatEnergy = (273.5 + 95)
                * instance.getSpecificHeatCapacity();
        // 95 degrees celsius with 1 bar ambient pressure:
        assertEquals(instance.getVapourFraction(liquidHeatEnergy, 1e5),
                0.0, 0.0);
        // Add the vaporization energy at 100 deg celsius, expect X = 1.0
        liquidHeatEnergy = (273.5 + 100.5)
                * instance.getSpecificHeatCapacity()
                + instance.getVaporizationHeatEnergy();
        assertEquals(instance.getVapourFraction(liquidHeatEnergy, 1e5),
                1.0, 0.0);
        // Half the vapour energy should be X = 0.5
        liquidHeatEnergy = (273.5 + 100)
                * instance.getSpecificHeatCapacity()
                + instance.getVaporizationHeatEnergy() / 2;
        assertEquals(instance.getVapourFraction(liquidHeatEnergy, 1e5),
                0.50, 0.01);
        // Increase pressure for same energy, X will be less
        assertEquals(instance.getVapourFraction(liquidHeatEnergy, 5e5),
                0.40, 0.02);

    }

    /**
     * Test of getDensity method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetDensity() {
        System.out.println("getDensity");
        // Heat energy for 20 Deg Celsius without evaporation
        double liquidHeatEnergy = (273.5 + 20)
                * instance.getSpecificHeatCapacity();
        // Water with 20 deg and 1 bar, must be around 1000 kg per m^3
        assertEquals(instance.getDensity(liquidHeatEnergy, 1e5),
                970, 50);
        // Fully evaporate: Expect something between 0 and 2
        liquidHeatEnergy = (273.5 + 100.5)
                * instance.getSpecificHeatCapacity()
                + instance.getVaporizationHeatEnergy();
        assertEquals(instance.getDensity(liquidHeatEnergy, 1e5),
                1.0, 1.0);
        // Increase pressure to 5 bar, result will be around 100
        assertEquals(instance.getDensity(liquidHeatEnergy, 5e5),
                100.0, 10.0);
    }

    /**
     * Test of getAvgDensity method, of class PhasedPropertiesWater.
     */
    @Test
    public void testGetAvgDensity() {
        System.out.println("getAvgDensity");

        // Room temperature
        double heatEnergyRoom = (273.5 + 20)
                * instance.getSpecificHeatCapacity();

        // With vaporization energy at 100 deg celsius for X=0.7 at 1 bar
        double heatEnergyPartialSteam = (273.5 + 100.5)
                * instance.getSpecificHeatCapacity()
                + instance.getVaporizationHeatEnergy() * 0.7;
        
        // Before evaporation
        double heatEnergyBoilingStarts = (273.5 + 97)
                * instance.getSpecificHeatCapacity();
        
        assertEquals(instance.getAvgDensity(
                heatEnergyRoom, heatEnergyBoilingStarts, 1e5),
                950.0, 50.0); // 921.287
        
        assertEquals(instance.getAvgDensity(
                heatEnergyBoilingStarts, heatEnergyPartialSteam, 1e5),
                600.0, 100.0); // 625.69
    }
}
