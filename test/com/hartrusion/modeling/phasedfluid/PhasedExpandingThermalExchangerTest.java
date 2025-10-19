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
    PhasedNode nodeOrigin, nodeIn, nodeOut;

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
        flowSource = new PhasedFlowSource();
        origin = new PhasedOrigin();
        sink = new PhasedOrigin();

        origin.connectToVia(flowSource, nodeOrigin);
        flowSource.connectToVia(instance, nodeIn);
        instance.connectToVia(sink, nodeOut);

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
        flowSource = null;
        origin = null;
        sink = null;
        thNodeInstance = null;
        thNodeOrigin = null;
        thOrigin = null;
        thForceFlow = null;
        thSolver = null;
    }

    @Test
    public void testNoFlow() {
        System.out.println("noFlow");
        flowSource.setFlow(0.0);
        run();

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
        // of the solution.
        origin.doCalculation();
        flowSource.doCalculation();
        instance.doCalculation();
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
