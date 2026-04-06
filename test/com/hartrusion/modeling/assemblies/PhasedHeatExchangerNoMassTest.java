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
import com.hartrusion.modeling.heatfluid.HeatNoMassEnergyExchangerResistance;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.heatfluid.HeatOrigin;
import com.hartrusion.modeling.phasedfluid.PhasedFlowSource;
import com.hartrusion.modeling.phasedfluid.PhasedNode;
import com.hartrusion.modeling.phasedfluid.PhasedOrigin;
import com.hartrusion.modeling.phasedfluid.PhasedPropertiesWater;
import com.hartrusion.modeling.phasedfluid.PhasedSimpleFlowResistance;
import com.hartrusion.modeling.solvers.DomainAnalogySolver;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Dedicated test for the no-mass-thermal exchanger between phased and heat
 * domain. This was generated as there was an issue that has to be investigated
 * in the power plant simulation.
 * <p>
 * The test builds the following structure:
 * <pre>
 *  ---------------          -----------------------
 *  |             |          |                    _|_
 *  |  primary_in o          o secondary_out     heDrainOrig
 *  |             |          |
 *  |  primary   ---        ---
 *  |  Side      | |        | |
 * (-)           | |========| |  secondarySide
 *  | phFlow     | |        | |
 *  |            ---        ---                   The used resistor phR is
 *  |             | primary  |                    generating the back-pressure
 *  o phSrcN      o _out     o secondary_in       according to the set flow.
 *  |             |          |
 * _|_            X          |                    The heat domain does not
 * phSrcOrig      X phR     (-) heFlow            need a specific pressure.
 *                X          |
 *                |          |
 *    phDrainNode o          o heSrcN
 *               _|_        _|_
 *          phDrainOrig   heSrcOrig
 * </pre>
 * <p>
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedHeatExchangerNoMassTest {

    private PhasedHeatExchangerNoMass instance;
    private PhasedNode phSrcN, phDrainNode;
    private HeatNode heSrcN;
    private PhasedSimpleFlowResistance phR;
    private PhasedFlowSource phFlow;
    private HeatFlowSource heFlow;
    private PhasedOrigin phSrcOrig, phDrainOrig;
    private HeatOrigin heSrcOrig, heDrainOrig;

    private final PhasedPropertiesWater water = new PhasedPropertiesWater();

    private DomainAnalogySolver solver;

    public PhasedHeatExchangerNoMassTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        phSrcN = new PhasedNode();
        phSrcN.setName("phSrcN");
        phDrainNode = new PhasedNode();
        phDrainNode.setName("phDrainNode");
        heSrcN = new HeatNode();
        heSrcN.setName("heSrcN");
        phR = new PhasedSimpleFlowResistance();
        phR.setName("phR");
        phFlow = new PhasedFlowSource();
        phFlow.setName("phFlow");
        heFlow = new HeatFlowSource();
        heFlow.setName("heFlow");
        phSrcOrig = new PhasedOrigin();
        phSrcOrig.setName("phSrcOrig");
        phDrainOrig = new PhasedOrigin();
        phDrainOrig.setName("phDrainOrig");
        heSrcOrig = new HeatOrigin();
        heSrcOrig.setName("heSrcOrig");
        heDrainOrig = new HeatOrigin();
        heDrainOrig.setName("heDrainOrig");

        instance = new PhasedHeatExchangerNoMass(water);
        instance.initName("instance");

        // Build the model
        instance.initGenerateNodes();

        // primary flow path:
        phSrcOrig.connectTo(phSrcN);
        phFlow.connectBetween(phSrcN,
                instance.getPhasedNode(PhasedHeatExchangerNoMass.PRIMARY_IN));
        phR.connectBetween(
                instance.getPhasedNode(PhasedHeatExchangerNoMass.PRIMARY_OUT),
                phDrainNode);
        phDrainOrig.connectTo(phDrainNode);

        // Secondary flow path:
        heSrcOrig.connectTo(heSrcN);
        heFlow.connectBetween(heSrcN,
                instance.getHeatNode(PhasedHeatExchangerNoMass.SECONDARY_IN));
        heDrainOrig.connectTo(
                instance.getHeatNode(PhasedHeatExchangerNoMass.SECONDARY_OUT));

        solver = new DomainAnalogySolver();
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        phSrcN = null;
        phDrainNode = null;
        heSrcN = null;
        phR = null;
        phFlow = null;
        heFlow = null;
        phSrcOrig = null;
        phDrainOrig = null;
        heSrcOrig = null;
        heDrainOrig = null;
        instance = null;
        solver = null;
    }

    @Test
    public void testGetSecondarySide() {

        solver.addNetwork(phDrainNode);

        double phasedMassFlow = 87; // kg/s
        double phasedPressure = 1e5; // Pa - 1 bar
        double phasedInTemp = 294.25; // Kelvin
        double heatInTemp = 295.15; // Kelvin
        double heatMassFlow = 44000; // kg/s

        phR.setResistanceParameter(phasedMassFlow / phasedPressure);
        phFlow.setFlow(phasedMassFlow);
        phSrcOrig.setOriginHeatEnergy( // 1,235,850
                phasedInTemp * water.getSpecificHeatCapacity());
        heFlow.setFlow(heatMassFlow);
        heSrcOrig.setOriginTemperature(heatInTemp);

        // Solve network with one iteration, only static values here.
        solver.prepareCalculation();
        solver.doCalculation();
        
        // Problem
        double phasedOutEnergy = 
                instance.getPhasedNode(PhasedHeatExchangerNoMass.PRIMARY_OUT)
                        .getHeatEnergy();

        double phasedOutTemp = water.getTemperature(phasedOutEnergy,
                instance.getPhasedNode(PhasedHeatExchangerNoMass.PRIMARY_OUT)
                        .getEffort());
        
        
    }

}
