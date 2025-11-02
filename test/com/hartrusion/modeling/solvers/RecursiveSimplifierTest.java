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
import org.testng.annotations.Test;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.ClosedOrigin;
import org.testng.annotations.BeforeClass;

/**
 * Integration tests for recursive simplifier.
 *
 * @author Viktor Alexander Hartung
 */
public class RecursiveSimplifierTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Remove all loggers
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        //for (java.util.logging.Handler h : root.getHandlers()) {
        //    root.removeHandler(h);
        //}
    }

    public RecursiveSimplifierTest() {
    }

    /**
     * Integration test for a network with some parallel and series resistors
     * and a flow source. The parallel resistors must be simplified to one
     * resistor which then can be simplified with others in series.
     */
    @Test
    public void testBasicParallelSeriesNetwork() {
        System.out.println("basicParallelSeriesNetwork");
        /*
         * Example: A network consisting of 8 resistors, a voltage source and an 
         * origin shall be simplified. Each node and element exists as its own 
         * object, the objects are registered and all are known to this network.
         *
         * Note that there is an origin placed between two resistors. This will 
         * prevent a full simplification later, if you use origins, try to
         * connect all resistors or sources to one port and use only one origin 
         * instead of multiple ones. The resistors will NOT be detected as
         * series!
         *
         *   node3
         *     o-------XXXXXX--------o  node 4
         *     |         R1          |
         *     X                     X                  For test case: all 
         *     X R0                  X R2               resistors have 300 ohms
         *     X                     X                  value. This allows R3,
         *     | node 2              |                  R4 and R5 to be easily
         *     |             --------o--------  node5   simplified to 100 Ohms.
         *    (|) u (src)    |       |       |
         *     |             X       X       X
         *     | node 1      X R3    X R4    X  R5
         *     X             X       X       X
         *     X  R7         |       |       |
         *     X             --------o--------
         *     |      R6             |
         *     o----XXXXXXX----------o  node6
         *     | node 0
         *    _|_  origin
         *
         * The simplified network will look like this. It has fewer ports and
         * elements so there is an assignment between all those indexes which
         * is basically what this class will also organize.
         * 
         *   .-------XXXXXX--------.
         *   |        R0-2         |          All resistors in series will be:
         *   |     substElem[0]    |          R_total = R0-2 + R3-5 + R6 + R7
         *   |      -> 900 Ohms    |          R_total = 1600 Ohms
         *   | childPort[2]        |          I = U / R_total = 16 V / 1600 Ohms
         *   o node 2              |          I = 0.01 Ampere
         *   |                     o node5 - childPort[3]
         *  (|) u (src)            |
         *   |                     X 
         *   | node 1              X R3-5 - substElem[1]
         *   X                     X  -> 100 Ohms
         *   X  R7  both           | 
         *   X      300 Ohms       |
         *   |      R6             |
         *   o----XXXXXXX----------o  node 6 - childPort[4]
         *   | node 0 - childPort[0]
         *  _|_  origin
         *
         * R6 is represented by element[6] and will be childElement[0]. 
         * Therefore, elementOfChildelement[0] = 6 and
         * childElementOfElement[6] = 0.
         *
         * The next recursion will further simplify R0-2, R3-5 and R6 as series 
         * and create the last possible iteration. As R7 and R6 are split by a 
         * node which has an origin port, they can not be simplified as one 
         * resistor. However, this is believed to be a very common case and as 
         * its no problem to solve this by hand, there is a method 
         * twoSerialResistorsCalculation in superclass LinearNetwork which was
         * written to solve exactly this common issue. It will be invoked if 
         * there is no manual calculation possible.
         * 
         */
        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[7];
        LinearDissipator[] r = new LinearDissipator[8];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < 8; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(300.0);
            r[idx].setName("R" + idx);
        }

        u.setEffort(16); // 16 Volts allow easy calculation

        // Connect elements to create the network described
        zero.connectTo(node[0]);
        r[7].connectTo(node[0]);
        r[7].connectTo(node[1]);
        u.connectTo(node[1]);
        u.connectTo(node[2]);
        r[0].connectTo(node[2]);
        r[0].connectTo(node[3]);
        r[1].connectTo(node[3]);
        r[1].connectTo(node[4]);
        r[2].connectTo(node[4]);
        r[2].connectTo(node[5]);
        r[3].connectTo(node[5]);
        r[4].connectTo(node[5]);
        r[5].connectTo(node[5]);
        r[3].connectTo(node[6]);
        r[4].connectTo(node[6]);
        r[5].connectTo(node[6]);
        r[6].connectTo(node[6]);
        r[6].connectTo(node[0]);

        // Register ports - fails with exception if not possible
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }

        // Register elements - fails with exception if not possible
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        // setup the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = instance.recursiveSimplificationSetup(0);

        // it is expected to have 2 child layers for this example.
        assertEquals(numberOfLayers, 2, "Wrong number of layers created.");

        instance.prepareRecursiveCalculation();

        // After method call, whole network must be in fully calculated state.
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

        // The flow on each resistor in series must be 0.01 ampere
        assertEquals(r[1].getFlow(), 0.01, 0.000001,
                "Calulation result on resistor is wrong.");
        assertEquals(r[6].getFlow(), 0.01, 0.000001,
                "Calulation result on resistor is wrong.");
        assertEquals(r[7].getFlow(), 0.01, 0.000001,
                "Calulation result on resistor is wrong.");

        // Now lets change all resistors from 300 Ohms to 30 Ohms.
        for (int idx = 0; idx < 8; idx++) {
            r[idx].setResistanceParameter(30);
        }
        // transfer all new resistances through model...
        instance.prepareRecursiveCalculation();

        // ... and call calculation again, check that it works with that
        // one call
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

        // The flow is now expected to be 10x higher. This is also a test that
        // cyclic change of values will work.
        assertEquals(r[1].getFlow(), 0.1, 0.000001,
                "Calulation result on resistor is wrong.");

        // Make R1 an open connection. The flow in the whole network is now
        // expected to be zero.
        r[1].setOpenConnection();
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

        // The flow is now expected to be zero.
        assertEquals(r[0].getFlow(), 0, 0.000001,
                "Calulation result on resistor is wrong.");
        assertEquals(r[1].getFlow(), 0, 0.000001,
                "Calulation result on resistor is wrong.");
    }

    /**
     * Another test that occurred during the chernobyl simulation, this is a
     * small part of a network that did not provide a full solution. It is
     * however obvious why there is no solution, as R2 is floating alone.
     */
    @Test
    public void testFloatingSeriesResistor() {
        System.out.println("floatingSeriesResistor");
        /*              R0
         *  0  o-----[      ]-----o  2
         *     |                  |
         *     |                  X
         *    (|)                 X R1
         *     |                  X 
         *     |                  |
         *  1  o-----[      ]-----o  3
         *     |        R2
         *    _|_
         *
         */

        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[4];
        LinearDissipator[] r = new LinearDissipator[3];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(470.0);
            r[idx].setName("R" + idx);
        }
        r[0].setOpenConnection();
        r[2].setOpenConnection();

        // Connect elements - note that we do not use node[0] first
        zero.connectTo(node[1]);
        u.connectTo(node[1]);
        r[2].connectTo(node[1]);
        u.connectTo(node[0]);
        r[0].connectTo(node[0]);
        r[0].connectTo(node[2]);
        r[1].connectTo(node[2]);
        r[1].connectTo(node[3]);
        r[2].connectTo(node[3]);

        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        instance.recursiveSimplificationSetup(0);

        // First iteration:
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());
        // Prepare and do a second iteration;
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "Second iteration failed");
    }

    /**
     * During steam functionality tests a layer of a superposition solver formed
     * a kind of floating island ring which lead to a solver error. This type of
     * network will therefore be tested here to somehow work. To solve it, a
     * detection of such island rings had to be implemented.
     */
    @Test
    public void testRingIsle() {
        System.out.println("ringIsle");
        /* 
         *   4 o------XXXXXX------o------XXXXXX------.
         *     |        R3        3        R2        |
         *     X                                     |
         *     X  R4                                 o 2
         *     X                                     |
         *     |        R0        1        R1        |
         *  0  o------XXXXXX------o------XXXXXX------.
         *     | 
         *     |
         *    (|) 
         *     | 
         *     |
         *  5  o 
         *     | 
         *    _|_
         *
         */

        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[6];
        LinearDissipator[] r = new LinearDissipator[5];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(470.0);
            r[idx].setName("R" + idx);
        }
        // r[1].setOpenConnection(); <- this works easier as its detected.

        // connect somehow similar like in failed example
        zero.connectTo(node[5]);
        u.connectTo(node[5]);
        r[0].connectTo(node[0]);
        r[0].connectTo(node[1]);
        r[1].connectTo(node[1]);
        r[1].connectTo(node[2]);
        r[2].connectTo(node[2]);
        r[2].connectTo(node[3]);
        r[3].connectTo(node[3]);
        r[3].connectTo(node[4]);
        r[4].connectTo(node[4]);
        r[4].connectTo(node[0]);
        u.connectTo(node[0]);

        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        instance.recursiveSimplificationSetup(0);

        // First iteration:
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());
        assertTrue(instance.isCalculationFinished(), "First iteration failed");
        // Prepare and do a second iteration;
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "Second iteration failed");
    }

    /**
     * Another case which came up in the chornobyl development from the main
     * circulation pumps network. This can be solved quite easy by just ignoring
     * the R4, R7 and R8 values, making some series-parallel-simplifications
     * afterward. However, at the time of writing this, the solver is yet not
     * able to solve it so it is
     */
    @Test
    public void testNetworkWithLoop() {
        System.out.println("networkWithLoop");
        /*
         *                          n0    [4]            [8]
         *     .---------------------o---XXXXX---.  .---XXXXX---o n5
         *     |                     |     R2    |  |     R6    |
         *     X                     X           |  |           X
         * [1] X R0              [6] X R4        |  |        R7 X [9]
         *     X                     X           |  |           X
         *     |               R1    |     R5    |  |     R3    |
         *  n1 o---(-)---o---XXXXX---o---XXXXX---oooo---XXXXX---o
         *     |   [2]  n2    [3]   n3    [7]     n6     [5]    n4
         *    _|_ [0]
         *
         */
        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[7];
        LinearDissipator[] r = new LinearDissipator[8];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(470.0);
            r[idx].setName("R" + idx);
        }

        // connect somehow similar like in failed example
        zero.connectTo(node[1]);
        u.connectTo(node[1]);
        u.connectTo(node[2]);
        r[0].connectTo(node[1]);
        r[0].connectTo(node[0]);
        r[1].connectTo(node[2]);
        r[1].connectTo(node[3]);
        r[4].connectTo(node[0]);
        r[4].connectTo(node[3]);
        r[2].connectTo(node[0]);
        r[2].connectTo(node[6]);
        r[5].connectTo(node[3]);
        r[5].connectTo(node[6]);
        r[6].connectTo(node[6]);
        r[6].connectTo(node[5]);
        r[3].connectTo(node[6]);
        r[3].connectTo(node[4]);
        r[7].connectTo(node[5]);
        r[7].connectTo(node[4]);

        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        instance.recursiveSimplificationSetup(0);

        // First iteration:
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "First iteration failed");
        // Prepare and do a second iteration;
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "Second iteration failed");
    }

    /**
     * And another test situation that has to be debugged as it occurred in the
     * main circulation pump circuit. The solver did not provide a proper
     * solution, even if it's clear by just looking at the circuit that there
     * are two loops that will have zero flow.
     */
    @Test
    public void testTwoLoopsWithSource() {
        System.out.println("twoLoopsWithSource");
        /*
         *            .-----------------------------------------------.
         *            |              n4              n5               |
         *         n3 o-----[---]-----o-----XXXXX-----o-----XXXXX-----.
         *            |      R3              R4               R5
         *            |
         *           (|) u
         *            |
         *            |n0    R0       n1     R1       n2      R2
         *   .--------o-----[---]-----o-----XXXXX-----o-----[   ]-----.
         *   |        |                                               |
         *  _|_zero   .-----------------------------------------------.
         */

        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[6];
        LinearDissipator[] r = new LinearDissipator[6];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(470.0);
            r[idx].setName("R" + idx);
        }

        // Register exactly as it was connected in debugged model as only
        // this seems to trigger the problem.
        zero.registerNode(node[5]);
        node[5].registerElement(zero);
        u.registerNode(node[5]);
        node[5].registerElement(u);
        u.registerNode(node[0]);
        node[0].registerElement(u);
        // upper loop:
        r[3].registerNode(node[0]);
        node[0].registerElement(r[3]);
        r[3].registerNode(node[1]);
        node[1].registerElement(r[3]);
        r[4].registerNode(node[1]);
        node[1].registerElement(r[4]);
        r[5].registerNode(node[2]);
        node[2].registerElement(r[5]);
        r[5].setBridgedConnection();
        r[4].registerNode(node[2]);
        node[2].registerElement(r[4]);
        r[5].registerNode(node[0]);
        node[0].registerElement(r[5]);
        // lower loop:
        node[3].registerElement(r[0]);
        r[0].registerNode(node[3]);
        node[3].registerElement(r[1]);
        r[1].registerNode(node[3]);
        node[4].registerElement(r[2]);
        r[2].registerNode(node[4]);
        node[4].registerElement(r[1]);
        r[1].registerNode(node[4]);
        r[1].setBridgedConnection();
        r[0].registerNode(node[5]);
        node[5].registerElement(r[0]);
        r[2].registerNode(node[5]);
        node[5].registerElement(r[2]);
        r[2].setBridgedConnection();

        // Ports are in same order as array:
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        // Different list order for the elements, as debugged:
        instance.registerElement(zero);
        instance.registerElement(u);
        instance.registerElement(r[3]);
        instance.registerElement(r[0]);
        instance.registerElement(r[5]); // [4]
        instance.registerElement(r[2]); // [5]
        instance.registerElement(r[4]); // [6]
        instance.registerElement(r[1]); // [6]

        instance.recursiveSimplificationSetup(0);

        // First iteration:
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "First iteration failed");
        // Prepare and do a second iteration;
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "Second iteration failed");

    }

    /**
     * A rather easy looking thing that failed to solve in another attempt of
     * solving the rbmk network. It's characterized by having a bridged and an
     * open resistor being used to simply connect a resistor to a source. While
     * not being complicated, it triggered some validation check during the
     * calculation run, it failed to have the same effort on the bridged nodes.
     */
    @Test
    public void testOpenBridgedResistor() {
        System.out.println("openBridgedResistor");
        /*
         *          1      1   r1  0      1
         *         o--------[-----]--------o       note that we have index 
         *     n0  o 2     0   r3  1     2 o n1    numbers here for nodes and
         *         o--------[     ]--------o       element node lists.
         *         |0                      | 0
         *         |                       | 0
         *         |1                      X
         *        (|)  u                   X  r0
         *         |0  111198 V            X  4000 Ohms
         *         |                       | 1
         *        1| 2     1   r2  0     1 | 0
         *         o--------[-----]--------o
         *     n3  o 3     0   r4  1     2 o n2
         *         o--------[     ]--------o
         *        0|
         *        _|_ 
         */

        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[4];
        LinearDissipator[] r = new LinearDissipator[5];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }

        r[0].setResistanceParameter(4000);
        r[1].setBridgedConnection();
        r[2].setBridgedConnection();
        r[3].setOpenConnection();
        r[4].setOpenConnection();
        u.setEffort(111198);

        // Connect exactly with same orders as described
        node[3].registerElement(zero);
        zero.registerNode(node[3]);
        node[3].registerElement(u);
        u.registerNode(node[3]);
        node[0].registerElement(u);
        u.registerNode(node[0]);
        node[1].registerElement(r[0]);
        r[0].registerNode(node[1]);
        node[2].registerElement(r[0]);
        r[0].registerNode(node[2]);
        node[1].registerElement(r[1]);
        r[1].registerNode(node[1]);
        node[0].registerElement(r[1]);
        r[1].registerNode(node[0]);
        node[0].registerElement(r[3]);
        r[3].registerNode(node[0]);
        node[1].registerElement(r[3]);
        r[3].registerNode(node[1]);
        node[2].registerElement(r[2]);
        r[2].registerNode(node[2]);
        node[3].registerElement(r[2]);
        r[2].registerNode(node[3]);
        node[3].registerElement(r[4]);
        r[4].registerNode(node[3]);
        node[2].registerElement(r[4]);
        r[4].registerNode(node[2]);

        // Register with same order as in described example!
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        instance.registerElement(zero);
        instance.registerElement(u);
        for (LinearDissipator r1 : r) {
            instance.registerElement(r1);
        }

        instance.recursiveSimplificationSetup(0);

        // First iteration:
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "First iteration failed");
        // Prepare and do a second iteration;
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished(), "Second iteration failed");
    }

    /**
     * This situation has three parallel resistors connected to one node and if
     * you would merge them, a dead end resistor will be the result. But somehow
     * it ended up triggering the star delta solver which is some not intended
     * behaviour and an internal check will be triggered.
     */
    @Test
    public void testDeadEndStar() {
        System.out.println("deadEndStar");
        /*
         *            r1           n3    r4
         *  --------[   ]----------o---XXXXX----      n3 is a node that has all
         *  |                      o     r3    |      those elements connected.
         *  |                      o---XXXXX---o n0
         *  |    u           r2    o     r0    |
         *  o----(-)----o---XXXXX--o---[   ]----
         *  |n1        n2          n3
         * _|_
         *
         */
        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[4];
        LinearDissipator[] r = new LinearDissipator[5];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }

        u.setEffort(10);
        r[0].setOpenConnection();
        r[1].setOpenConnection();
        r[2].setResistanceParameter(165811);
        r[3].setResistanceParameter(2e5);
        r[4].setResistanceParameter(2e5);

        // Connect but with the same order as in network, maybe that will do
        // some thing to trigger the error (even though it should not do that!)
        zero.connectTo(node[1]);
        u.connectBetween(node[1], node[2]);
        r[0].connectBetween(node[0], node[3]);
        r[1].connectBetween(node[3], node[1]);
        r[2].connectBetween(node[2], node[3]);
        r[3].connectBetween(node[3], node[0]);
        r[4].connectBetween(node[3], node[0]);

        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        // setup the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = instance.recursiveSimplificationSetup(0);

        instance.prepareRecursiveCalculation();

        // After method call, whole network must be in fully calculated state.
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

    }

    /**
     * Occurred during solving of the more large chronobyl model, somehow this
     * network could not be further solved. This kind of network is hard to draw
     * in the comments so we won't have a nice sketch here. It contains four
     * nodes that have 4 connections and no further simplification is possible.
     * This required the StarSquareTransform class to be implemented and serves
     * as a test for using the StarSquareTransform.
     */
    @Test
    public void testIndecipherableMayhem() {
        System.out.println("indecipherableMayhem");
        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[7];
        LinearDissipator[] r = new LinearDissipator[12];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }

        // Connect as listed in debugger with the exact same connections, 
        // the register method is therefore used instead of the connect.
        // But it turned out this does not matter.
        // Using connect methods instead of register gives same failure:
        zero.connectTo(node[0]);
        r[0].connectBetween(node[2], node[4]);
        r[1].connectBetween(node[6], node[5]);
        r[2].connectBetween(node[5], node[1]);
        u.connectBetween(node[0], node[6]);
        r[3].connectBetween(node[1], node[4]);
        r[4].connectBetween(node[4], node[3]);
        r[5].connectBetween(node[3], node[5]);
        r[6].connectBetween(node[5], node[4]);
        r[7].connectBetween(node[2], node[3]);
        r[8].connectBetween(node[0], node[3]);
        r[9].connectBetween(node[0], node[1]);
        r[10].connectBetween(node[1], node[2]);
        r[11].connectBetween(node[0], node[2]);

        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        // set up the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = instance.recursiveSimplificationSetup(0);

        instance.prepareRecursiveCalculation();

        // After method call, whole network must be in fully calculated state.
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());
    }

    /**
     * Another issue in the chernobyl simulation. Two of the feed pumps do crash
     * the simulation with almost infinity flow rate, they also trigger one
     * warning of the LinearDissipator class that there would be different
     * effort values on a bridged element. This value is very high (in the
     * number of thousands) and this test case investigates the network which
     * provoked this error.
     * <p>
     * r[8] to r[11] are SimplifiedResistor classes that are represented as
     * resistors in this network layer.
     * <p>
     * The network is basically a total mess of open connections and there must
     * be no flow.
     */
    @Test
    public void testComplexFeedwaterDetail() {
        System.out.println("complexFeedwaterDetail");
        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node = new GeneralNode[7];
        LinearDissipator[] r = new LinearDissipator[12];
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        for (int idx = 0; idx < r.length; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }

        // Connect as listed in debugger with the exact same connections, 
        // the register method is therefore used instead of the connect.
        node[0].registerElement(r[5]);
        node[0].registerElement(r[7]);
        node[0].registerElement(r[8]);
        node[0].registerElement(r[10]);
        node[1].registerElement(r[4]);
        node[1].registerElement(r[6]);
        node[1].registerElement(r[9]);
        node[1].registerElement(r[10]);
        node[2].registerElement(r[2]);
        node[2].registerElement(r[3]);
        node[2].registerElement(r[6]);
        node[2].registerElement(r[8]);
        node[2].registerElement(r[11]);
        node[3].registerElement(r[1]);
        node[3].registerElement(r[7]);
        node[3].registerElement(r[9]);
        node[3].registerElement(r[11]);
        node[4].registerElement(r[0]);
        node[4].registerElement(r[1]);
        node[4].registerElement(r[3]);
        node[4].registerElement(r[4]);
        node[4].registerElement(r[5]);
        node[5].registerElement(r[0]);
        node[5].registerElement(u);
        node[6].registerElement(zero);
        node[6].registerElement(u);
        node[6].registerElement(r[2]);

        zero.registerNode(node[6]);
        r[0].registerNode(node[5]);
        r[0].registerNode(node[4]);
        r[1].registerNode(node[4]);
        r[1].registerNode(node[3]);
        u.registerNode(node[6]);
        u.registerNode(node[5]);
        r[2].registerNode(node[6]);
        r[2].registerNode(node[2]);
        r[3].registerNode(node[2]);
        r[3].registerNode(node[4]);
        r[4].registerNode(node[4]);
        r[4].registerNode(node[1]);
        r[5].registerNode(node[0]);
        r[5].registerNode(node[4]);
        r[6].registerNode(node[1]);
        r[6].registerNode(node[2]);
        r[7].registerNode(node[3]);
        r[7].registerNode(node[0]);
        r[8].registerNode(node[0]);
        r[8].registerNode(node[2]);
        r[9].registerNode(node[3]);
        r[9].registerNode(node[1]);
        r[10].registerNode(node[0]);
        r[10].registerNode(node[1]);
        r[11].registerNode(node[2]);
        r[11].registerNode(node[3]);

        // Set Values from breakpoint
        r[0].setResistanceParameter(14814.814814814816);
        r[1].setOpenConnection();
        u.setEffort(1600000.0);
        r[2].setResistanceParameter(222.22222222222223);
        r[3].setOpenConnection();
        r[4].setOpenConnection();
        r[5].setOpenConnection();
        r[6].setOpenConnection();
        r[7].setOpenConnection();
        r[8].setBridgedConnection();
        r[9].setOpenConnection();
        r[10].setBridgedConnection();
        r[11].setOpenConnection();

        for (GeneralNode n : node) {
            instance.registerNode(n);
        }
        
        // It is vital that the elements are getting connected in the exact
        // same order as here, otherwise the error will not be triggered!
        instance.registerElement(zero);
        instance.registerElement(r[0]);
        instance.registerElement(r[1]);
        instance.registerElement(u);
        for (int idx = 2; idx <= 11; idx++) {
            instance.registerElement(r[idx]);
        }
         
        // set up the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = instance.recursiveSimplificationSetup(0);

        instance.prepareRecursiveCalculation();

        // After method call, whole network must be in fully calculated state.
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

        // Those are all connected to zero
        assertEquals(node[6].getEffort(), 0.0, 1e-12);
        assertEquals(node[2].getEffort(), 0.0, 1e-12);
        assertEquals(node[0].getEffort(), 0.0, 1e-12);
        assertEquals(node[1].getEffort(), 0.0, 1e-12);

        assertEquals(node[4].getEffort(), 1600000.0, 1e-12);
        
        // Node 3 is free-fluating
        
        for (int idx = 0; idx < r.length; idx++) { // init
            assertEquals(r[idx].getFlow(), 0.0, 1e-12);
        }
    }
}
