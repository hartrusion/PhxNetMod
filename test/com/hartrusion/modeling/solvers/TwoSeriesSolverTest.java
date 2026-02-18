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
package com.hartrusion.modeling.solvers;

import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Specialized tests that trigger the TwoSeriesSolver class in the
 * RecursiveSimplifier solver class.
 *
 * @author Viktor Alexander Hartung
 */
public class TwoSeriesSolverTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public TwoSeriesSolverTest() {
    }

    /**
     * Tests the behavior of an open, not connected effort source. This is a
     * case that can happen if for example in a hydraulic network when a pump is
     * connected between two closed valves. The corresponding electronics
     * circuit is represented by resistors with elementType OPEN.
     */
    @Test
    public void testStandardBehaviour() {
        /*
         *  p1       U       p2     
         *   o------(-)------o    
         * 1 |            0  |    
         *   X               X  
         *   X R1            X R2  
         *   X 220 Ohms      X  180 Ohms
         * 0 |            1  |
         *   o---------------o 
         *   |       p0
         *  _|_ z
         *
         */
        LinearDissipator r1, r2;
        EffortSource u;
        ClosedOrigin z;
        GeneralNode p0, p1, p2;

        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setResistanceParameter(220);
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(180);
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("Voltage-Source");
        u.setEffort(12);
        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p0 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p0.setName("Port0");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p0);
        r1.connectTo(p0);
        r1.connectTo(p1);
        u.connectTo(p1);
        u.connectTo(p2);
        r2.connectTo(p2);
        r2.connectTo(p0);

        // A special solver is needed for this circuit. Usually this is
        // selected automatically by the solving network but we will add
        // it here manually, since no network solver is used.
        RecursiveSimplifier solver = new RecursiveSimplifier();
        solver.registerNode(p0);
        solver.registerNode(p1);
        solver.registerNode(p2);
        solver.registerElement(z);
        solver.registerElement(u);
        solver.registerElement(r1);
        solver.registerElement(r2);
        
        solver.recursiveSimplificationSetup(0);

        solver.prepareCalculation();
        solver.doRecursiveCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);

        assertEquals(r1.getFlow(), 0.03, 1e-8);
        assertEquals(r2.getFlow(), 0.03, 1e-8);
        assertEquals(u.getFlow(), 0.03, 1e-8);
    }

    @Test
    public void testOneOpenConnection() {
        /*
         *  p1       U       p2     
         *   o------(-)------o    
         * 1 |            0  |    
         *                   X  
         *     R1            X R2  
         *     inf Ohms      X  330 Ohms
         * 0 |            1  |
         *   o---------------o 
         *   |       p0
         *  _|_ z
         *
         */
        LinearDissipator r1, r2;
        EffortSource u;
        ClosedOrigin z;
        GeneralNode p0, p1, p2;

        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setOpenConnection();
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setResistanceParameter(330);
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("Voltage-Source");
        u.setEffort(13.2);
        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p0 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p0.setName("node0");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p0);
        r1.connectTo(p0);
        r1.connectTo(p1);
        u.connectTo(p1);
        u.connectTo(p2);
        r2.connectTo(p2);
        r2.connectTo(p0);

        // A special solver is needed for this circuit. Usually this is
        // selected automatically by the solving network but we will add
        // it here manually, since no network solver is used.
        RecursiveSimplifier solver = new RecursiveSimplifier();
        solver.registerNode(p0);
        solver.registerNode(p1);
        solver.registerNode(p2);
        solver.registerElement(z);
        solver.registerElement(u);
        solver.registerElement(r1);
        solver.registerElement(r2);
        
        solver.recursiveSimplificationSetup(0);

        solver.prepareCalculation();
        solver.doRecursiveCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);

        // no flow, nowhere
        assertEquals(r1.getFlow(), 0.0, 1e-8);
        assertEquals(r2.getFlow(), 0.0, 1e-8);
        assertEquals(u.getFlow(), 0.0, 1e-8);

        assertEquals(p1.getEffort(), -13.2, 1e-8);
        assertEquals(p2.getEffort(), 0, 1e-8);

        // Switch open and resistor side
        r1.setResistanceParameter(330);
        r2.setOpenConnection();

        // Recalculate
        solver.prepareCalculation();
        solver.doRecursiveCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);

        // no flow, nowhere
        assertEquals(r1.getFlow(), 0.0, 1e-8);
        assertEquals(r2.getFlow(), 0.0, 1e-8);
        assertEquals(u.getFlow(), 0.0, 1e-8);

        // effort value is on other node now
        assertEquals(p1.getEffort(), 0, 1e-8);
        assertEquals(p2.getEffort(), 13.2, 1e-8);
    }

    /**
     * Tests the behaviour of an open, not connected effort source. This is a
     * case that can happen if for example in a hydraulic network when a pump is
     * connected between two closed valves. The corresponding electronics
     * circuit is represented by resistors with elementType OPEN.
     */
    @Test
    public void testIsolatedEffortSource() {
        /*
         *  p1       U       p2     
         *   o------(-)------o    
         * 1 |            0  |    
         *                      
         *     R1              R2  
         *     inf Ohms        inf Ohms
         * 0 |            1  |
         *   o---------------o 
         *   |       p0
         *  _|_ z
         *
         */
        LinearDissipator r1, r2;
        EffortSource u;
        ClosedOrigin z;
        GeneralNode p0, p1, p2;

        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setOpenConnection();
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setOpenConnection();
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("Voltage-Source");
        u.setEffort(6);
        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p0 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p0.setName("node0");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p0);
        r1.connectTo(p0);
        r1.connectTo(p1);
        u.connectTo(p1);
        u.connectTo(p2);
        r2.connectTo(p2);
        r2.connectTo(p0);

        // A special solver is needed for this circuit. Usually this is
        // selected automatically by the solving network, but we will add
        // it here manually, since no network solver is used.
        RecursiveSimplifier solver = new RecursiveSimplifier();
        solver.registerNode(p0);
        solver.registerNode(p1);
        solver.registerNode(p2);
        solver.registerElement(z);
        solver.registerElement(u);
        solver.registerElement(r1);
        solver.registerElement(r2);
        
        solver.recursiveSimplificationSetup(0);

        solver.prepareCalculation();
        solver.doRecursiveCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);

        assertEquals(r1.getFlow(), 0.0, 1e-8);
        assertEquals(r2.getFlow(), 0.0, 1e-8);
        assertEquals(u.getFlow(), 0.0, 1e-8);

        // run a few iterations
        for (int idx = 0; idx < 50; idx++) {
            solver.prepareCalculation();
            solver.doRecursiveCalculation();
        }

        // it is expected for the nodes not to float away
        assertEquals(p1.getEffort(), 3.0, 10.0, "Source floating away");
        assertEquals(p1.getEffort(), 3.0, 10.0, "Source floating away");
    }

    /**
     * Tests the behaviour of an open, not connected effort source. This is a
     * case that can happen if for example in a hydraulic network when a pump is
     * connected between two closed valves. The corresponding electronics
     * circuit is represented by resistors with elementType OPEN.
     */
    @Test
    public void testBridgeAndShortcut() {
        /*
         *  p1       U       p2     
         *   o------(-)------o    
         * 1 |            0  |    
         *  |||                 
         *  ||| R1             R2  
         *  ||| 0 Ohms         inf Ohms
         * 0 |            1  |
         *   o---------------o 
         *   |       p0
         *  _|_ z
         *
         */
        LinearDissipator r1, r2;
        EffortSource u;
        ClosedOrigin z;
        GeneralNode p0, p1, p2;

        r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setName("R1");
        r1.setBridgedConnection();
        r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setName("R2");
        r2.setOpenConnection();
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("Voltage-Source");
        u.setEffort(6);
        z = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        z.setName("Zero");
        p0 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p0.setName("node0");
        p1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p1.setName("node1");
        p2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        p2.setName("node2");

        z.connectTo(p0);
        r1.connectTo(p0);
        r1.connectTo(p1);
        u.connectTo(p1);
        u.connectTo(p2);
        r2.connectTo(p2);
        r2.connectTo(p0);

        // A special solver is needed for this circuit. Usually this is
        // selected automatically by the solving network, but we will add
        // it here manually, since no network solver is used.
        RecursiveSimplifier solver = new RecursiveSimplifier();
        solver.registerNode(p0);
        solver.registerNode(p1);
        solver.registerNode(p2);
        solver.registerElement(z);
        solver.registerElement(u);
        solver.registerElement(r1);
        solver.registerElement(r2);
        
        solver.recursiveSimplificationSetup(0);

        solver.prepareCalculation();
        solver.doRecursiveCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);

        assertEquals(r1.getFlow(), 0.0, 1e-8);
        assertEquals(r2.getFlow(), 0.0, 1e-8);
        assertEquals(u.getFlow(), 0.0, 1e-8);
        assertEquals(p2.getEffort(), 6.0, 1e-8);
        assertEquals(p1.getEffort(), 0.0, 1e-8);

        // Switch elements open and shortcut connection
        r1.setOpenConnection();
        r2.setBridgedConnection();

        // Run test again
        solver.prepareCalculation();
        solver.doRecursiveCalculation();

        // calculation must be finished
        assertEquals(u.isCalculationFinished(), true);
        assertEquals(r1.isCalculationFinished(), true);
        assertEquals(r2.isCalculationFinished(), true);

        assertEquals(r1.getFlow(), 0.0, 1e-8);
        assertEquals(r2.getFlow(), 0.0, 1e-8);
        assertEquals(u.getFlow(), 0.0, 1e-8);
        // Source will draw open connection to negative effort
        assertEquals(p2.getEffort(), 0.0, 1e-8);
        assertEquals(p1.getEffort(), -6.0, 1e-8);

    }
}
