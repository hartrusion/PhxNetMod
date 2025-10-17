/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/UnitTests/EmptyTestNGTest.java to edit this template
 */
package com.hartrusion.modeling.thermal;

import com.hartrusion.modeling.heatfluid.HeatThermalExchanger;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.heatfluid.HeatFlowSource;
import com.hartrusion.modeling.heatfluid.HeatOrigin;
import com.hartrusion.modeling.heatfluid.HeatNode;
import com.hartrusion.modeling.solvers.SimpleIterator;
import static org.testng.Assert.*;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * This integration test models a heat exchanger from literature and checks the
 * results. Its basically an oil cooler with very low flow, it will take very
 * long to change its temperature due to the high mass and the low flows. It
 * starts in stationary state.
 *
 * <pre>
 *                                            v  water in 0.1385 kg/s 10 °C
 *                                          |   |
 *               ---------------------------+   +-----
 *    hot oil in |                                   |
 * 120 °C ------|+-----------------------------------+|------ oil out 33.8 °C
 *   ->                                                        ->
 *        ------|+-----------------------------------+|------
 *               |                                   |
 *               --------+   +------------------------
 *                       |   |
 *                         v    water out 43 °C
 * </pre>
 *
 * @author Viktor Alexander Hartung
 */
public class ThermalLogGradientResistanceNGTest {

    ThermalLogGradientResistance instance;
    HeatThermalExchanger oilSide;
    HeatThermalExchanger waterSide;
    HeatOrigin oilSource, oilSink, waterSource, waterSink;
    HeatNode oilNode, oilIn, oilOut, waterNode, waterIn, waterOut;
    GeneralNode thOilNode, thWaterNode;
    HeatFlowSource oilPump, waterPump;

    SimpleIterator solver;

    public ThermalLogGradientResistanceNGTest() {
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new ThermalLogGradientResistance();
        solver = new SimpleIterator();

        oilSide = new HeatThermalExchanger();
        oilSide.setName("Oil-Pipe");
        waterSide = new HeatThermalExchanger();
        waterSide.setName("Water-Chamber");
        oilSource = new HeatOrigin();
        oilPump = new HeatFlowSource();
        oilSink = new HeatOrigin();
        waterSource = new HeatOrigin();
        waterPump = new HeatFlowSource();
        waterSink = new HeatOrigin();
        oilIn = new HeatNode();
        oilNode = new HeatNode();
        oilOut = new HeatNode();
        waterIn = new HeatNode();
        waterNode = new HeatNode();
        waterOut = new HeatNode();
        thOilNode = new GeneralNode(PhysicalDomain.THERMAL);
        thWaterNode = new GeneralNode(PhysicalDomain.THERMAL);

        oilSource.connectTo(oilNode);
        oilPump.connectTo(oilNode);
        oilPump.connectTo(oilIn);
        oilSide.connectTo(oilIn);
        oilSide.connectTo(oilOut);
        oilSink.connectTo(oilOut);

        waterSource.connectTo(waterNode);
        waterPump.connectTo(waterNode);
        waterPump.connectTo(waterIn);
        waterSide.connectTo(waterIn);
        waterSide.connectTo(waterOut);
        waterSink.connectTo(waterOut);

        oilSide.initComponent();
        waterSide.initComponent();

        // Add the nodes to the thermal origin that is linked to the heat
        // exchanger.
        oilSide.getInnerThermalEffortSource().connectTo(thOilNode);
        waterSide.getInnerThermalEffortSource().connectTo(thWaterNode);

        // Connect the non-linear resistance element between both effort sources
        instance.connectTo(thOilNode);
        instance.connectTo(thWaterNode);

        // Add everything to a solver, we use the stupid iterative thing here.
        solver.addElement(oilSource);
        solver.addElement(oilPump);
        solver.addElement(oilSide);
        solver.addElement(oilSink);
        solver.addElement(waterSource);
        solver.addElement(waterPump);
        solver.addElement(waterSide);
        solver.addElement(waterSink);
        solver.addElement(instance);
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
    }

    /**
     * Test of prepareCalculation method, of class ThermalLogGradientResistance.
     */
    @Test
    public void testStepResponses() {
        double stepTime = 25; // Its a super slow system, 25 Seconds (!) is okay
        int steps = (int) (10000.0 / stepTime); // simulate 10000 seconds

        // Make the connections to the remote heat handlers
        instance.initHeatHandler(oilSide);
        instance.initHeatHandler(waterSide);

        oilSide.setStepTime(stepTime);
        waterSide.setStepTime(stepTime);

        // Set initial conditions from example
        oilPump.setFlow(0.138888888889); // 500 kg/h as kg/s
        waterPump.setFlow(0.1385); // 498.6 kg/h as kg/s
        oilSide.getHeatHandler().setInnerThermalMass(75);
        oilSide.getHeatHandler().setInitialTemperature(273.15 + 33.8);
        oilSide.setSpecificHeatCapacity(1600);
        oilSource.setOriginTemperature(273.15 + 120);
        waterSide.getHeatHandler().setInnerThermalMass(100);
        waterSide.getHeatHandler().setInitialTemperature(273.15 + 43.0);
        waterSource.setOriginTemperature(273.15 + 10);

        for (int cycle = 0; cycle < steps; cycle++) {
            solver.prepareCalculation();
            solver.doCalculation();

            // Check Temperature Values at t=900s
            if (cycle == (int) (950.0 / stepTime)) {
                assertEquals(oilSide.getHeatHandler().getTemperature(),
                        306.7, 0.5);
                assertEquals(waterSide.getHeatHandler().getTemperature(),
                        316.2, 0.5);
            }
            // At t=1000s, make an incease in oil flow by 10 %
            if (cycle == (int) (1000.0 / stepTime)) {
                oilPump.setFlow(0.152777777778); // increase by 10 %
            }
            if (cycle == (int) (4950.0 / stepTime)) {
                assertEquals(oilSide.getHeatHandler().getTemperature(),
                        310.6, 0.5);
                assertEquals(waterSide.getHeatHandler().getTemperature(),
                        317.8, 0.5);
            }
            if (cycle == (int) (5000.0 / stepTime)) { // 5000 Secods
                // increase oil inlet temperature to 125 deg C
                oilSource.setOriginTemperature(273.15 + 125);
            }
            if (cycle == (int) (9950.0 / stepTime)) {
                assertEquals(oilSide.getHeatHandler().getTemperature(),
                        311.9, 0.5);
                assertEquals(waterSide.getHeatHandler().getTemperature(),
                        319.4, 0.5);
            }
        }
    }

}
