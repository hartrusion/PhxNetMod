/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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
package com.hartrusion.modeling.assemblies;

import com.hartrusion.modeling.heatfluid.HeatFlowSource;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.heatfluid.HeatOrigin;
import com.hartrusion.modeling.phasedfluid.PhasedFlowSource;
import com.hartrusion.modeling.phasedfluid.PhasedNode;
import com.hartrusion.modeling.phasedfluid.PhasedOrigin;
import com.hartrusion.modeling.phasedfluid.PhasedPropertiesWater;
import com.hartrusion.modeling.solvers.DomainAnalogySolver;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedCondenserNoMassTest {
    private PhasedCondenserNoMass instance;

    private PhasedPropertiesWater water = new PhasedPropertiesWater();

    private PhasedFlowSource flowIn, flowOut;
    private PhasedNode pn1, pn2;
    private PhasedOrigin pz1, pz2;

    private HeatFlowSource heatFluidFlow;
    private HeatNode hn;
    private HeatOrigin hz1, hz2;

    private DomainAnalogySolver solver;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public PhasedCondenserNoMassTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new PhasedCondenserNoMass(water);
        // Use build in methods to set up the components
        instance.initGenerateNodes();
        instance.initName("TestCondenserInstance");
        // 1 m² base area, 20 kg steam in condensing area,
        instance.initCharacteristic(1.0, 200, 2e4, 1e5, 4.0);

        // Generate additonal model elements
        flowIn = new PhasedFlowSource();
        flowOut = new PhasedFlowSource();
        pn1 = new PhasedNode();
        pn2 = new PhasedNode();
        pz1 = new PhasedOrigin();
        pz2 = new PhasedOrigin();
        heatFluidFlow = new HeatFlowSource();
        hn = new HeatNode();
        hz1 = new HeatOrigin();
        hz2 = new HeatOrigin();

        flowIn.setName("FlowIn");
        flowOut.setName("FlowOut");
        pn1.setName("pn1");
        pn2.setName("pn2");
        pz1.setName("pz1");
        pz2.setName("pz2");
        heatFluidFlow.setName("HeatFluidFlow");
        hn.setName("hn");
        hz1.setName("hz1");
        hz2.setName("hz2");

        // connect elements like in schematic
        pz2.connectToVia(flowOut, pn2);
        flowOut.connectTo(
                instance.getPhasedNode(PhasedCondenser.PRIMARY_OUT));
        pz1.connectToVia(flowIn, pn1);
        flowIn.connectTo(
                instance.getPhasedNode(PhasedCondenser.PRIMARY_IN));
        hz2.connectToVia(heatFluidFlow, hn);
        heatFluidFlow.connectTo(
                instance.getHeatNode(PhasedCondenser.SECONDARY_IN));
        hz1.connectTo(
                instance.getHeatNode(PhasedCondenser.SECONDARY_OUT));

        // Setup solver
        solver = new DomainAnalogySolver();
        solver.addNetwork(hn);
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        // clear all references 
        instance = null;
        flowIn = null;
        flowOut = null;
        pn1 = null;
        pn2 = null;
        pz1 = null;
        pz2 = null;
        heatFluidFlow = null;
        hn = null;
        hz1 = null;
        hz2 = null;
    }

    /**
     * Test of nothing. If all initial conditions are correct, no flow should be
     * anywhere. Sets all temperatures to 300 Kelvin and runs the calculations a
     * few times, after that, temperatures must still be 300. Of course it is
     * also expected for the calculation fo be in fully completed state each
     * time.
     */
    @Test
    public void testZeroFlow() {
        double temperature = 300;
        // 300 * c = 1.260.000
        double heatEnergy = temperature * water.getSpecificHeatCapacity();

        pz1.setOriginHeatEnergy(heatEnergy);
        pz2.setOriginHeatEnergy(heatEnergy);
        hz2.setOriginTemperature(temperature);

        instance.initConditions(temperature, temperature, 1.0);
        flowIn.setFlow(0.0);
        flowOut.setFlow(0.0);
        heatFluidFlow.setFlow(0);

        for (int idx = 0; idx < 10; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();

            // There must be no flow in primary side
            assertEquals(instance.getPhasedNode(PhasedCondenser.PRIMARY_INNER)
                    .getFlow(0), 0.0, 1e-8);
        }

        // There must be no changes to the inner temperature and energy.
        assertEquals(instance.getSecondarySideReservoir().getHeatHandler()
                .getTemperature(), temperature, 1e-8);

        assertEquals(instance.getPrimarySideReservoir().getTemperature(),
                temperature, 1e-8);

    }
    
    // @Test // no automated test here, just to mess around here.
    public void testFailingAuxCondenser() {
        // Those parameters are from the rbmk sims auxiliary condenser 
        instance.initCharacteristic(4.0, 500, 5e5, 1e5, 4.0);
        
        double temperaturePrimary = 273.15 + 140; // 413.5 - 140 °C
        double temperatureSecondary = 293.15; // 20 °C
        double heatEnergy = temperaturePrimary * water.getSpecificHeatCapacity() + water.getVaporizationHeatEnergy();

        pz1.setOriginHeatEnergy(heatEnergy);
        pz2.setOriginHeatEnergy(heatEnergy); // optional, this is the out-origin
        hz2.setOriginTemperature(temperatureSecondary);

        // Set flow through both primary and secondary sides5
        instance.initConditions(temperatureSecondary, temperatureSecondary, 0.2);
        flowIn.setFlow(30.0);
        flowOut.setFlow(30.0);
        heatFluidFlow.setFlow(600); // fully opened coolant loop: 600 kg/s

        for (int idx = 0; idx < 10; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();
            System.out.println("Pri Reservoir: "
                    + String.format("%.2f", instance.getPrimarySideReservoir().getTemperature() - 273.15)
                    + ", Pri Mid-Node: "
                    + String.format("%.2f", instance.getPhasedNode(PhasedCondenserNoMass.PRIMARY_INNER).getHeatEnergy() / water.getSpecificHeatCapacity() - 273.15)
                    + ", 2nd Reservoir: "
                    + String.format("%.2f", instance.getSecondarySideReservoir().getHeatHandler().getTemperature() - 273.15)
                    + ", 2nd Mid-Node: "
                    + String.format("%.2f", instance.getHeatNode(PhasedCondenserNoMass.SECONDARY_INNER).getTemperature() - 273.15));
        }
    }
}
