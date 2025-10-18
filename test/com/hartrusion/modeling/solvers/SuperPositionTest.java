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
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;

/**
 * Integration test for class SuperPosition. This also serves as a documentation
 * of how to create a model and use the superposition solver to solve the model.
 *
 * <p>
 * The superposition solver also uses Overlay, RecursiveSimplifier and
 * SimplifiedResistor classes and requires them to work properly.
 *
 * @author Viktor Alexander Hartung
 */
public class SuperPositionTest {
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // Remove all loggers
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
    }

    public SuperPositionTest() {
    }

    /**
     * Integration test for a simple network with two sources and three
     * resistors. This is the most basic variant of a network requiring
     * superposition solving.
     *
     */
    @Test
    public void testBasicNetwork() {
        System.out.println("basicNetwork");
        SuperPosition instance = new SuperPosition();
//        SuperPosition.setThreadPool(
//                java.util.concurrent.Executors.newFixedThreadPool(
//                        Runtime.getRuntime().availableProcessors()));

        /* This test will create a network with two effort sources like
         * following:
         * node1       R0         node2      R1
         *   o-------XXXXXX--------o-------XXXXXX--------o node3
         *   |      200 Ohms       |       50 Ohms       |
         *   |                     X                     |
         *  (|) U0                 X R2                 (|) U1
         *   |  15 V               X  100 Ohms           |  10 V
         *   |                     |                     |
         *   ----------------------o----------------------
         *                         |  node 0
         *                        _|_ zero 
         * 
         * This network can be solved using superposition. The expected current
         * which flows through R2 is 0.0785 amps. This result requires all other
         * calculations to be correct and therefore other checks are obsolte.
         */
        // Set up network components
        GeneralNode[] node;
        LinearDissipator[] r; // declare
        EffortSource[] u;
        ClosedOrigin zero;

        node = new GeneralNode[4]; // allocate
        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        r = new LinearDissipator[3]; // allocate
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(100);
            r[idx].setName("R" + idx);
        }

        u = new EffortSource[2];
        u[0] = new EffortSource(PhysicalDomain.ELECTRICAL);
        u[0].setName("U0");
        u[1] = new EffortSource(PhysicalDomain.ELECTRICAL);
        u[1].setName("U1");

        zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        zero.setName("Gnd");

        // add some values
        u[0].setEffort(15);
        u[1].setEffort(10);

        r[0].setResistanceParameter(200);
        r[1].setResistanceParameter(50);
        r[2].setResistanceParameter(100);

        // Build the network
        zero.connectTo(node[0]);
        u[0].connectTo(node[0]);
        u[0].connectTo(node[1]);
        r[0].connectTo(node[1]);
        r[0].connectTo(node[2]);
        r[1].connectTo(node[2]);
        r[2].connectTo(node[2]);
        r[2].connectTo(node[0]);
        r[1].connectTo(node[3]);
        u[1].connectTo(node[0]);
        u[1].connectTo(node[3]);

        // register ports
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }

        // register elements
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u[0]);
        instance.registerElement(u[1]);
        instance.registerElement(zero);

        // Call layer generation
        int layers;
        layers = instance.superPositionSetup();
        assertEquals(layers, 2, "Expected 2 layers.");

        // Call Prepare Calculation
        instance.prepareCalculation();

        // Call doCalculation
        instance.doCalculation();

        // flow on R2 must be 0.0785 ampere
        assertEquals(r[2].getFlow(), 0.0785, 0.0001,
                "Calulation result on resistor is wrong.");
    }

    /**
     * Integration test for a more complicated network which was provided in
     * summer term test exam in 2010 duing my studies. It is a rather simple
     * network which has both flow and effort sources, therefore requiring the
     * class "Overlay" to work properly.
     *
     * Not yet fully supported.
     *
     */
    @Test
    public void testSummerTermExam() {
        System.out.println("summerTermExam");
        SuperPosition instance = new SuperPosition();

        /* This test will create a network with two effort sources like
         * following:
         *                I4         R4 10 Ohms
         *            ----->--------XXXXXX-----------
         *            |                              |
         *     node1  |      U  12 V     R3          | node3
         *   ---------o-------(-)->--o--XXXXXX-------o---------
         *   |        |          node2  20 Ohms      |        |
         *   |^ Iq1   X  R1                      R2  X        |^ Iq2
         *  (-) 4 A   X  25 Ohms             40 Ohms X       (-) 2 A
         *   |        X                              X        |
         *   |        |                              |        |
         *   ---------o----------------------------------------
         *            |  node 0
         *           _|_ zero 
         *
         * Unfortunately, there was no solution provided and no solving was
         * necessary as the exam was only about nodal analysis and getting the
         * node-voltage equation matix.
         */
        GeneralNode[] node = new GeneralNode[4]; // allocate
        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        LinearDissipator r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setResistanceParameter(25);
        r1.setName("R1");
        LinearDissipator r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r2.setResistanceParameter(40);
        r2.setName("R2");
        LinearDissipator r3 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r3.setResistanceParameter(20);
        r3.setName("R3");
        LinearDissipator r4 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r4.setResistanceParameter(10);
        r4.setName("R4");

        FlowSource iq1 = new FlowSource(PhysicalDomain.ELECTRICAL);
        iq1.setFlow(4);
        iq1.setName("Iq1");
        FlowSource iq2 = new FlowSource(PhysicalDomain.ELECTRICAL);
        iq2.setFlow(2);
        iq2.setName("Iq2");

        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("U");
        u.setEffort(12);

        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        zero.setName("Ground");

        // Build network
        zero.connectTo(node[0]);
        iq1.connectTo(node[0]);
        r1.connectTo(node[0]);
        r2.connectTo(node[0]);
        iq2.connectTo(node[0]);
        iq1.connectTo(node[1]);
        r1.connectTo(node[1]);
        r4.connectTo(node[1]);
        u.connectTo(node[1]);
        u.connectTo(node[2]);
        r3.connectTo(node[2]);
        r3.connectTo(node[3]);
        r4.connectTo(node[3]);
        r2.connectTo(node[3]);
        iq2.connectTo(node[3]);

        // register ports
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }

        instance.registerElement(zero);
        instance.registerElement(iq1);
        instance.registerElement(iq2);
        instance.registerElement(r1);
        instance.registerElement(r2);
        instance.registerElement(r3);
        instance.registerElement(r4);
        instance.registerElement(u);

        // Call layer generation
        int layers;
        layers = instance.superPositionSetup();

        // There must be one layer for each source
        assertEquals(layers, 3, "Wrong number of created layers");

        instance.prepareCalculation();
        instance.doCalculation();

        assertTrue(instance.isCalculationFinished(),
                "No full solution was provided.");

        // Assuming that the calculations manually on paper are correct, we 
        // should find the values here (they were not correct on first try...):
        assertEquals(r1.getFlow(), -3.669, 0.01, "Wrong flow on R1");
        assertEquals(r3.getFlow(), 0.512, 0.01, "Wrong flow on R3");
        assertEquals(u.getFlow(), 0.512, 0.01, "Wrong flow on u");
    }

    /**
     * This rather simple model will represent a typical modeled situation of
     * hydraulic pumps, it happened that some of the
     */
    @Test
    public void testEffortSourcesWithOpenConnections() {
        System.out.println("effortSourcesWithOpenConnections");
        SuperPosition instance = new SuperPosition();
        /*              n5      R4       n6
            ------------o-----XXXXXX-----o
            |           |                |       
            X           X                | 
            X R1        X R3             | 
            X           X                |      
            |           |                |           
            o n2        o n4             |
            |           |                |
           (|) U0      (|) U1           (|) U2
            |           |                |
            o n1        o n3             |
            |           |                |
            X           X                |
            X R0        X R2             |
            X           X                |
            |           |                |
            ------------o-----------------
                     n0 |
                   z   _|_
         */
        ClosedOrigin gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("gnd");

        GeneralNode[] n = new GeneralNode[7]; // allocate
        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            n[idx].setName("n" + idx);
        }

        LinearDissipator[] r = new LinearDissipator[5]; // allocate
        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(470);
            r[idx].setName("R" + idx);
        }

        EffortSource[] u = new EffortSource[3]; // allocate
        for (int idx = 0; idx < u.length; idx++) { // init
            u[idx] = new EffortSource(PhysicalDomain.ELECTRICAL);
            u[idx].setName("U" + idx);
            u[idx].setEffort(12);
        }

        gnd.connectTo(n[0]);
        r[0].connectTo(n[0]);
        r[2].connectTo(n[0]);
        u[2].connectTo(n[0]);
        r[0].connectTo(n[1]);
        u[0].connectTo(n[1]);
        u[0].connectTo(n[2]);
        r[1].connectTo(n[2]);
        r[2].connectTo(n[3]);
        u[1].connectTo(n[3]);
        u[1].connectTo(n[4]);
        r[3].connectTo(n[4]);
        r[1].connectTo(n[5]);
        r[3].connectTo(n[5]);
        r[4].connectTo(n[5]);
        r[4].connectTo(n[6]);
        u[2].connectTo(n[6]);

        for (GeneralNode p : n) {
            instance.registerNode(p);
        }
        instance.registerElement(gnd);
        for (LinearDissipator e : r) {
            instance.registerElement(e);
        }
        for (EffortSource e : u) {
            instance.registerElement(e);
        }

        instance.superPositionSetup();

        r[2].setOpenConnection();
        r[3].setOpenConnection();
        r[4].setOpenConnection();
        instance.prepareCalculation();
        instance.doCalculation();

        assertEquals(u[1].getFlow(), 0.0, 1e-12, "Flow between open elements");

    }
}
