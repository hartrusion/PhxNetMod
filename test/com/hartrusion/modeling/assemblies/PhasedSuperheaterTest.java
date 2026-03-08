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
 * Connects flow sources to the primary and secondary side with the secondary
 * side having a resistor for simulating pressure level. This allows to test the
 * superheater element like it would behave in the model.
 * <pre>
 *                                      This resistor will create a pressure
 *        priInFlowSource               linear to the set secondary flow:
 *     o------(|)----->---
 *    _|_                |                  secOutFlowPressure
 * priOrigSource         |                o-----XXXXXXXX-->---o secOrigDrainNode
 *            PRIMARY_IN |                |  SECONDARY_OUT    |
 *         o==============================|=========o        _|_
 *         |             |                |         |   secOrigDrain
 *         |  saturated [|]======XXX=====[|]        |
 *         |  phased     | noMassThermal  ^         |
 *         |  fluid      V exhchangers    |         |
 *         |                              |         |
 *         |------------------------------|---------|   Properties of the fluid
 *         |          mass based       [ sec ]      |   will be set to the open
 *         |         thermal exchange  [mass ]      |   origins which serve as
 *         |                              |         |   a boundry.
 *         o==============================|=========o
 *    PRIMARY_OUT V                       ^  SECONDARY_IN
 *                |                       |                     This part is the
 * priCondensate  |                       |                     HP turbine out
 * OutFlowSource (-)                     (-)  secPhasedFlow     that gets super-
 *                |                       |                     heated.
 *   priDrainNode o                       o   secOrigSourceNode
 *               _|_                     _|_  secOrigSource
 *         priOrigCondensateDrain
 * </pre>
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedSuperheaterTest {

    private PhasedSuperheater instance;

    private PhasedPropertiesWater water = new PhasedPropertiesWater();

    private PhasedFlowSource priInFlowSource, priCondensateOutFlowSource;
    private PhasedNode priSourceNode, priDrainNode;
    private PhasedOrigin priOrigSource, priOrigCondensateDrain;

    private PhasedFlowSource secPhasedFlow;
    private PhasedNode secOrigSourceNode, secOrigDrainNode;
    private PhasedOrigin secOrigDrain, secOrigSource;
    private PhasedSimpleFlowResistance secOutFlowPressure;

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
        priInFlowSource = new PhasedFlowSource();
        priCondensateOutFlowSource = new PhasedFlowSource();
        priSourceNode = new PhasedNode();
        priDrainNode = new PhasedNode();
        priOrigSource = new PhasedOrigin();
        priOrigCondensateDrain = new PhasedOrigin();
        secPhasedFlow = new PhasedFlowSource();
        secOrigSourceNode = new PhasedNode();
        secOrigSource = new PhasedOrigin();
        secOrigDrainNode = new PhasedNode();
        secOrigDrain = new PhasedOrigin();
        secOutFlowPressure = new PhasedSimpleFlowResistance();

        priInFlowSource.setName("PriInFlowSource");
        priCondensateOutFlowSource.setName("PriCondensateFlowOut");
        priSourceNode.setName("PriSourceNode");
        priDrainNode.setName("PriDrainNode");
        priOrigSource.setName("PriOrigSource");
        priOrigCondensateDrain.setName("PriOrigCondensateDrain");
        secPhasedFlow.setName("HeatFluidFlow");
        secOrigSourceNode.setName("SecOriginSourceNode");
        secOrigSource.setName("SecOriginSource");
        secOrigDrainNode.setName("SecOriginDrainNode");
        secOrigDrain.setName("SecOriginDrain");
        secOutFlowPressure.setName("secOutFlowPressure");

        // connect elements
        priOrigSource.connectToVia(priInFlowSource, priSourceNode);
        priInFlowSource.connectTo(
                instance.getPhasedNode(PhasedCondenser.PRIMARY_IN));
        priOrigCondensateDrain.connectToVia(priCondensateOutFlowSource,
                priDrainNode);
        priCondensateOutFlowSource.connectTo(
                instance.getPhasedNode(PhasedCondenser.PRIMARY_OUT));
        secOrigSource.connectToVia(secPhasedFlow, secOrigSourceNode);
        secPhasedFlow.connectTo(
                instance.getPhasedNode(PhasedCondenser.SECONDARY_IN));
        secOutFlowPressure.connectBetween(
                instance.getPhasedNode(PhasedCondenser.SECONDARY_OUT),
                secOrigDrainNode);
        secOrigDrain.connectTo(secOrigDrainNode);

        // Setup solver
        solver = new DomainAnalogySolver();
        solver.addNetwork(secOrigSourceNode);
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        // clear all references 
        instance = null;
        priInFlowSource = null;
        priCondensateOutFlowSource = null;
        priSourceNode = null;
        priDrainNode = null;
        priOrigSource = null;
        priOrigCondensateDrain = null;
        secPhasedFlow = null;
        secOrigSourceNode = null;
        secOrigDrain = null;
        secOrigDrainNode = null;
        secOrigSource = null;
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

        priOrigSource.setOriginHeatEnergy(heatEnergy);
        priOrigCondensateDrain.setOriginHeatEnergy(heatEnergy);
        secOrigDrain.setOriginHeatEnergy(heatEnergy);
        secOrigSource.setOriginHeatEnergy(heatEnergy);

        // pressurize secondary loop
        secOrigDrain.setEffort(5e6);
        secOrigDrain.setEffort(5e6);

        instance.initConditions(temperature, heatEnergy, 1.0, 5e6);
        priInFlowSource.setFlow(0.0);
        priCondensateOutFlowSource.setFlow(0.0);
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

    /**
     * Uses the conditions of the chernobyl RBMK simulator and allows testing
     * the class for that designed purpose. This case here showed an unexpected
     * behavior with the temperature in the condenser rising above any physical
     * accepted level. The condition is a way too hot condensate that should be
     * cooled down by the steam from the HP turbine.
     */
    @Test
    public void chernobylTurbine() {
        // Conditions after starting up turbine:
        // Primary in: Goes into nomassexchanger
        // 12.87 kg/s, 4439777, 812387 Pa
        // Primary Condenser conditions: 1856301 heatEnergy, 13095 kg (h=60 cm)
        // Secondary in: Goes into reservoir loop
        // 238.85 kg/s, 3337775, 86979 Pa
        // Secondary Reservoir loop condition: 2989426 heatEnergy

        // same configuration as in simulator, no ambient pressure
        // changed: Obviously there is some serious issue if the thermal 
        // conductivity kTimesA gets higher than 1e6 
        instance.initCharacteristic(25.0, 200, 5e5, 0.0);

        // Initial conditions: A temperature for saturation is usually given
        // but here a pressure is used, so we calculate saturation temp:
        instance.initConditions(water.getSaturationTemperature(812387),
                2989426, 0.6, 2989426);

        // Primary in flow (the pressure is obtained from the saturated steam)
        priOrigSource.setOriginHeatEnergy(4439777);
        priInFlowSource.setFlow(12.87);

        // Secondary in flow:
        secOrigSource.setOriginHeatEnergy(3337775);
        secPhasedFlow.setFlow(238.85);
        // Calculate a flow resistance that will generate the desired pressure
        // on the phased fluid in the secondary flow part:
        secOutFlowPressure.setResistanceParameter(86979 / 238.85);

        // To test: The temperature of the resercoir needs to go down each 
        // cycle as it is beeing cooled. Init with high value for first cycle.
        double oldReservoirTemperature = 10000;

        for (int idx = 0; idx < 100; idx++) {
            solver.prepareCalculation();
            solver.doCalculation();

            double primaryInTemperature = water.getTemperature(
                    instance.getPhasedNode(PhasedSuperheater.PRIMARY_IN)
                            .getHeatEnergy(),
                    instance.getPhasedNode(PhasedSuperheater.PRIMARY_IN)
                            .getEffort()) - 273.15;
            double secondaryOutTemperature = water.getTemperature(
                    instance.getPhasedNode(PhasedSuperheater.SECONDARY_OUT)
                            .getHeatEnergy(),
                    instance.getPhasedNode(PhasedSuperheater.SECONDARY_OUT)
                            .getEffort()) - 273.15;

            // Check that the reservoir actually strictly cools down. The
            // Initial state seems buggy.
            if (idx > 1) {
                assertEquals(oldReservoirTemperature
                        > instance.getPrimarySideReservoir().getTemperature(),
                        true, "Reservoir must cool down each cycle.");
            }
            // save for next cycle
            oldReservoirTemperature
                    = instance.getPrimarySideReservoir().getTemperature();
        }
    }

}
