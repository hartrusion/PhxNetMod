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
package com.hartrusion.modeling.assemblies;

import com.hartrusion.modeling.heatfluid.HeatFlowSource;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.heatfluid.HeatOrigin;
import com.hartrusion.modeling.heatfluid.HeatThermalExchanger;
import com.hartrusion.modeling.phasedfluid.PhasedClosedSteamedReservoir;
import com.hartrusion.modeling.phasedfluid.PhasedFlowSource;
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
 * Integration test for Phased Condenser Assembly. Builds a small model and
 * performs some checks on it.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedCondenserTest {

    /* primary_in        instance
    *  o-------------
    *  |            |
    *  |     prim -----    o---------XXXXX----
    *  |  condens |   |    |                 |        secondary_out
    *  |          |   | = (|)                |            o--------
    *  | flowIn   |   |    |                 |            |      _|_
    * (-)         -----    |                 o-----       |       hz1
    *  |      prim. |      ----              |    |     -----
    *  o pn1  inner o          |             |    |     |   |  secondary
    * _|_     node  |          |             |   (|) =  |   |  side
    * pz1         -----    o---------XXXXX----    |     |   |
    *             |   |    |   |                  |     -----
    *     primary |   | = (|)  |    ---------------       |
    *   reservoir |   |    |   |    |                     |
    *             -----    |   |    |                     o secondary_in
    *               |      ----o----                      |
    *   primaryOut  o          |                         (-) heatFluidFlow
    *               |         _|_                         |
    *     flowOut  (-)       thermal                      o hn
    *               |        origin                       |
    *           pn2 o                                    _|_ hz2
    *               |                                        
    *              _|_
    *              pz2
     */
    private PhasedCondenser instance;

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

    public PhasedCondenserTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new PhasedCondenser(water);
        // Use build in methods to set up the components
        instance.initGenerateNodes();
        instance.initName("TestCondenserInstance");
        // 1 m² base area, 20 kg steam in condensing area,
        // 200 kg water in heat exchanger side, 
        // kTimesA is 425 k = 85 W/m^2/K, A = 5 m²
        // 0.5 m level for low, 1.5 m level for high.
        instance.initCharacteristic(1.0, 20, 200, 420, 0.5, 1.5);

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
        pz1.setOriginHeatEnergy(300 * water.getSpecificHeatCapacity());
        pz2.setOriginHeatEnergy(300 * water.getSpecificHeatCapacity());
        // 300 * c = 1.260.000
        hz2.setOriginTemperature(300);

        instance.initConditions(300, 300, 1.0);
        flowIn.setFlow(0.0);
        flowOut.setFlow(0.0);
        heatFluidFlow.setFlow(0);
        
        for (int idx = 0; idx < 1; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();
        }
        
        assertEquals(
                instance.getSecondarySide().getHeatHandler().getTemperature(),
                300, 1.0);
        assertEquals(instance.getPrimarySideReservoir().getTemperature(),
                300, 1.0);
 
    }

}
