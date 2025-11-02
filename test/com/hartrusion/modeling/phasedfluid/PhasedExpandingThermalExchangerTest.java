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

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.solvers.SimpleIterator;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * Tests the behaviour of the phased expanding thermal exchanger. This element
 * has some special behaviour regarding its flow and is triggering some special
 * cases in the solvers already.
 * <p>
 * This test tests all scenarios by enforcing a flow rate through the element.
 * No solver is used, instead a method will invoke the calculation runs in a
 * specific order that is also expected to happen like this if a solver would
 * call it but this allows easier debugging here.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedExpandingThermalExchangerTest {

    PhasedExpandingThermalExchanger instance;
    PhasedOrigin origin, sink;
    PhasedFlowSource flowSource;
    PhasedEffortSource effortSource;
    PhasedNode nodeOrigin, nodeIn, nodeOut, nodeSink;

    GeneralNode thNodeInstance, thNodeOrigin;
    OpenOrigin thOrigin;
    FlowSource thForceFlow;

    SimpleIterator thSolver;

    public PhasedExpandingThermalExchangerTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {

    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        PhasedFluidProperties fp = new PhasedPropertiesWater();

        instance = new PhasedExpandingThermalExchanger(fp);

        nodeOrigin = new PhasedNode();
        nodeIn = new PhasedNode();
        nodeOut = new PhasedNode();
        nodeSink = new PhasedNode();
        flowSource = new PhasedFlowSource();
        effortSource = new PhasedEffortSource();
        origin = new PhasedOrigin();
        sink = new PhasedOrigin();

        // On node 0 of instance a flow will be induced, so we connect a flow
        // source. node 1 will have an effort induced which will be set on
        // all nodes backwards through instance as instance does not have an
        // effort drop. As the effort is the pressure of the phased fluid, we
        // need to put a certain value on it.
        // From origin with flow source into instance:
        origin.connectToVia(flowSource, nodeOrigin);
        flowSource.connectToVia(instance, nodeIn);

        // Pressure built up from other side:
        sink.connectToVia(effortSource, nodeSink);
        effortSource.connectToVia(instance, nodeOut);

        // Thermal part, simply force a thermal flow here.
        thNodeInstance = new GeneralNode(PhysicalDomain.THERMAL);
        thNodeOrigin = new GeneralNode(PhysicalDomain.THERMAL);
        thOrigin = new OpenOrigin(PhysicalDomain.THERMAL);
        thForceFlow = new FlowSource(PhysicalDomain.THERMAL);

        thOrigin.connectToVia(thForceFlow, thNodeOrigin);
        thForceFlow.connectTo(thNodeInstance);

        instance.initComponent();
        instance.getInnerThermalEffortSource().connectTo(thNodeInstance);

        // Use a simple iterator to solve the thermal part
        thSolver = new SimpleIterator();
        thSolver.addElement(instance.getInnerThermalOrigin());
        thSolver.addElement(thOrigin);
        thSolver.addElement(instance.getInnerThermalEffortSource());
        thSolver.addElement(thForceFlow);
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        instance = null;
        nodeOrigin = null;
        nodeIn = null;
        nodeOut = null;
        nodeSink = null;
        flowSource = null;
        effortSource = null;
        origin = null;
        sink = null;
        thNodeInstance = null;
        thNodeOrigin = null;
        thOrigin = null;
        thForceFlow = null;
        thSolver = null;
    }

    /**
     * Tests the idle or no-flow behaviour. if nothing goes in and no thermal
     * enrgy is transfered, the out flow of the element must also be 0,0.
     */
    @Test
    public void testNoFlow() {
        origin.setOriginHeatEnergy(1252230); // default value
        flowSource.setFlow(0.0); // no fluid flow
        thForceFlow.setFlow(0.0); // no thermal flow
        effortSource.setEffort(1e5); // ambient pressure
        instance.setInitialState(1.0, 1e5, 298.15, 298.15);

        run();

        // Expect nothing to happen:
        assertEquals(nodeOut.getFlow(instance), 0.0, 1e-5);
    }

    /**
     * No thermal energy transfer with same initial conditions. If there is no
     * thermal energy transfered and water with 25 deg Celsius is inside and
     * water with 25 deg celsius is flowing in, it must have 25 deg celsius out
     * (or the heat energy representing the 25 which is 1252230) and the same
     * flow out as it has in.
     */
    @Test
    public void testFlushThrough() {
        origin.setOriginHeatEnergy(1252230); // default value
        flowSource.setFlow(10.0); // 10 kg/s in flow
        thForceFlow.setFlow(0.0); // no thermal flow
        effortSource.setEffort(1e5); // ambient pressure
        instance.setInitialState(1.0, 1e5, 298.15, 298.15);

        run();

        // Expect exactly 10 kg/s going out of the element
        assertEquals(nodeOut.getFlow(instance), -10.0, 1e-5);
        // Heat energy must be the same as from initial conditions
        assertEquals(nodeOut.getHeatEnergy(), 1252230, 1e-2);
    }
    
    @Test
    public void testHeatUp() {
        origin.setOriginHeatEnergy(1252230); // default value
        flowSource.setFlow(10.0); // 10 kg/s in flow
        thForceFlow.setFlow(0.0); // no thermal flow
        effortSource.setEffort(1e5); // ambient pressure
        instance.setInitialState(1.0, 1e5, 298.15, 298.15);

        run();

        fail("The test case is a prototype.");
    }

    private void run() {
        thSolver.prepareCalculation();
        origin.prepareCalculation();
        flowSource.prepareCalculation();
        instance.prepareCalculation();
        sink.prepareCalculation();

        // It must be possible to solve the thermal system.
        thSolver.doCalculation();
        assertEquals(thSolver.isCalculationFinished(), true,
                "Thermal subsystem is not in calculated state.");
        // Call the doCalculation method in the direction of the availability
        // of the solution to provide solution for the exp thermal exchanger.
        sink.doCalculation(); // sets effort = 0 to sink node
        effortSource.doCalculation(); // sets the effort to nodeOut
        instance.doCalculation(); // sets effort to nodeIn where flowSource is
        origin.doCalculation(); // sets effort to the other side of flowSc.
        flowSource.doCalculation(); // sets flow to nodes
        origin.doCalculation(); // sets the heat energy value over the flow
        flowSource.doCalculation(); // sets the heat energy value over the flow

        instance.doCalculation(); // main calculation or first reverse-flow step

        // In case of reverse-flow, this will set the requested in heat props,
        // otherwise this will finish the calculation:
        effortSource.doCalculation(); // sets the effort to nodeOut
        sink.doCalculation();

        instance.doCalculation(); // main calculation for reverse flow
        effortSource.doCalculation(); // sets flow finally
        sink.doCalculation();

        // Check that all auxiliary elements are fully calculated
        assertEquals(origin.isCalculationFinished(), true,
                "Phased origin is not in calculated state.");
        assertEquals(flowSource.isCalculationFinished(), true,
                "Flow source is not in calculated state.");
        assertEquals(sink.isCalculationFinished(), true,
                "The end of the test model is not in calculated state.");

        // Now, if all prerequisites are met, the Element which we test here
        // must be in calculated state also.
        assertEquals(instance.isCalculationFinished(), true,
                "Element not in calculated state.");
    }

}
