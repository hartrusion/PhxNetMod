/*
 * The MIT License
 *
 * Copyright 2026 viktor.
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
import com.hartrusion.modeling.phasedfluid.PhasedClosedSteamedReservoir;
import com.hartrusion.modeling.phasedfluid.PhasedFlowSource;
import com.hartrusion.modeling.phasedfluid.PhasedNoMassExchangerElement;
import com.hartrusion.modeling.phasedfluid.PhasedNode;
import com.hartrusion.modeling.phasedfluid.PhasedOrigin;
import com.hartrusion.modeling.phasedfluid.PhasedPropertiesWater;
import com.hartrusion.modeling.phasedfluid.PhasedThermalExchanger;
import com.hartrusion.modeling.solvers.DomainAnalogySolver;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author viktor
 */
public class PhasedSuperheaterTest {

    private PhasedSuperheater instance;

    private PhasedPropertiesWater water = new PhasedPropertiesWater();

    private PhasedFlowSource flowIn, flowOut;
    private PhasedNode pn1, pn2;
    private PhasedOrigin pz1, pz2;

    private PhasedFlowSource secPhasedFlow;
    private PhasedNode pnSec;
    private PhasedOrigin secOrig1, secOrig2;

    private DomainAnalogySolver solver;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public PhasedSuperheaterTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new PhasedSuperheater(water);
        // Use build in methods to set up the components
        instance.initGenerateNodes();
        instance.initName("TestCondenserInstance");
        // 1 m² base area, 20 kg steam in condensing area,
        instance.initCharacteristic(1.0, 200, 2e4, 1e5);

        // Generate additonal model elements
        flowIn = new PhasedFlowSource();
        flowOut = new PhasedFlowSource();
        pn1 = new PhasedNode();
        pn2 = new PhasedNode();
        pz1 = new PhasedOrigin();
        pz2 = new PhasedOrigin();
        secPhasedFlow = new PhasedFlowSource();
        pnSec = new PhasedNode();
        secOrig1 = new PhasedOrigin();
        secOrig2 = new PhasedOrigin();

        flowIn.setName("FlowIn");
        flowOut.setName("FlowOut");
        pn1.setName("pn1");
        pn2.setName("pn2");
        pz1.setName("pz1");
        pz2.setName("pz2");
        secPhasedFlow.setName("HeatFluidFlow");
        pnSec.setName("hn");
        secOrig1.setName("hz1");
        secOrig2.setName("hz2");

        // connect elements like in schematic
        pz2.connectToVia(flowOut, pn2);
        flowOut.connectTo(
                instance.getPhasedNode(PhasedCondenser.PRIMARY_OUT));
        pz1.connectToVia(flowIn, pn1);
        flowIn.connectTo(
                instance.getPhasedNode(PhasedCondenser.PRIMARY_IN));
        secOrig2.connectToVia(secPhasedFlow, pnSec);
        secPhasedFlow.connectTo(
                instance.getPhasedNode(PhasedCondenser.SECONDARY_IN));
        secOrig1.connectTo(
                instance.getPhasedNode(PhasedCondenser.SECONDARY_OUT));

        // Setup solver
        solver = new DomainAnalogySolver();
        solver.addNetwork(pnSec);
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
        secPhasedFlow = null;
        pnSec = null;
        secOrig1 = null;
        secOrig2 = null;
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
        secOrig1.setOriginHeatEnergy(heatEnergy);
        secOrig2.setOriginHeatEnergy(heatEnergy);
        
        // pressurize secondary loop
        secOrig1.setEffort(5e6);
        secOrig1.setEffort(5e6);

        instance.initConditions(temperature, heatEnergy, 1.0, 5e6);
        flowIn.setFlow(0.0);
        flowOut.setFlow(0.0);
        secPhasedFlow.setFlow(0);

        for (int idx = 0; idx < 10; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();

            // There must be no flow in primary side
            assertEquals(instance.getPhasedNode(PhasedCondenser.PRIMARY_INNER)
                    .getFlow(0), 0.0, 1e-8);
        }

        // There must be no changes to the inner energy.
        assertEquals(instance.getSecondarySideReservoir().getPhasedHandler()
                .getHeatEnergy(), heatEnergy, 1e-8);

        assertEquals(instance.getPrimarySideReservoir().getTemperature(),
                temperature, 1e-8);

    }

   // @Test // no automated test here, just to mess around here.
    public void testFailingAuxCondenser() {
        // Those parameters are from the rbmk sims auxiliary condenser 
        instance.initCharacteristic(4.0, 500, 5e5, 1e5);

        double temperaturePrimary = 273.15 + 140; // 413.5 - 140 °C
        double temperatureSecondary = 293.15; // 20 °C
        double heatEnergy = temperaturePrimary * water.getSpecificHeatCapacity() + water.getVaporizationHeatEnergy();

        pz1.setOriginHeatEnergy(heatEnergy);
        pz2.setOriginHeatEnergy(heatEnergy); // optional, this is the out-origin
        //hz2.setOriginTemperature(temperatureSecondary);

        // Set flow through both primary and secondary sides
        instance.initConditions(temperatureSecondary, temperatureSecondary, 0.2, 5e5);
        flowIn.setFlow(30.0);
        flowOut.setFlow(-30.0);
        secPhasedFlow.setFlow(600); // fully opened coolant loop: 600 kg/s

        for (int idx = 0; idx < 100; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();
//            System.out.println("Pri: Res. Level:"
//                    + String.format("%.2f", instance.getPrimarySideReservoir().getFillHeight() * 100) // cm
//                    + ", Res Temp: "
//                    + String.format("%.2f", instance.getPrimarySideReservoir().getTemperature() - 273.15)
//                    + ", Mid-Node: "
//                    + String.format("%.2f", instance.getPhasedNode(PhasedCondenserNoMass.PRIMARY_INNER).getHeatEnergy() / water.getSpecificHeatCapacity() - 273.15)
//                    + ", 2nd: Res-Loop "
//                    + String.format("%.2f", instance.getSecondarySideReservoir().getHeatHandler().getTemperature() - 273.15)
//                    + ", Mid-Node: "
//                    + String.format("%.2f", instance.getHeatNode(PhasedCondenserNoMass.SECONDARY_INNER).getTemperature() - 273.15)
//                    + ", Top Out: "
//                    + String.format("%.2f", instance.getHeatNode(PhasedCondenserNoMass.SECONDARY_OUT).getTemperature() - 273.15));
        }
    }
}
