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
import static org.testng.Assert.*;

import org.testng.annotations.Test;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class StarDeltaTransformTest {

    public StarDeltaTransformTest() {
    }

    /**
     * Simple bridge network with load. This so called wheatstone bridge with a
     * resistor as a load cannot be simplified using parallels and series
     * simplification. It either requires either mesh or nodal analysis or some
     * equations which will be solved.
     *
     * <p>
     * The recursive simplifier will apply a star delta transform to solve it,
     * making series an paralell simplifications possible after that transform.
     * This integration test will therefore require the star delta transform to
     * work properly.
     */
    @Test
    public void testWheatstoneBridge() {
        System.out.println("wheatstoneBridge");
        /*
         *               node 1
         *     -----------o-------------           Resistance between node 1
         *     |          |            |           and node 0 is expected to
         *     |          X 40 Ohms    X 55 Ohms   be 50.8 Ohms.
         *     |          X R1         X R2        Expected flow on U is 
         *     |          X            X           therefore I = U/R = 0.197 A
         *     |          |     R0     |
         *  U (|)   node2 o---XXXXXX---o node3
         * 10V |          |    45 Ohms |
         *     |          X 60 Ohms    X 50 Ohms
         *     |          X R3         X R4
         *     |          X            X
         *     |          |            |
         *     -----------o-------------
         *                | node0
         *               _|_
         */

        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node;
        LinearDissipator[] r; // declare
        EffortSource u;
        ClosedOrigin zero;

        node = new GeneralNode[4]; // allocate
        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        r = new LinearDissipator[5]; // allocate
        for (int idx = 0; idx < 5; idx++) {
            r[idx]  = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }
        
        r[0].setResistanceParameter(45);
        r[1].setResistanceParameter(40);
        r[2].setResistanceParameter(55);
        r[3].setResistanceParameter(60);
        r[4].setResistanceParameter(50);


        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setEffort(10);
        zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        // Connect everything
        u.connectTo(node[0]);
        zero.connectTo(node[0]);
        u.connectTo(node[1]);
        r[1].connectTo(node[1]);
        r[2].connectTo(node[1]);
        r[1].connectTo(node[2]);
        r[2].connectTo(node[3]);
        r[0].connectTo(node[2]);
        r[0].connectTo(node[3]);
        r[3].connectTo(node[2]);
        r[4].connectTo(node[3]);
        r[3].connectTo(node[0]);
        r[4].connectTo(node[0]);

        // Register ports - fails with exeption if not possible
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }

        // Register elements - fails with exeption if not possible
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        // setup the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = instance.recursiveSimplificationSetup(0);
        assertEquals(numberOfLayers, 4, "Wrong number of layers created.");

        instance.prepareRecursiveCalculation();

        // After method call, whole network must be in fully caculated state.
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

        // Check expected flow value through whole circuit to
        assertEquals(u.getFlow(), 0.197, 0.001,
                "Calulation result on resistor is wrong.");
    }

    /**
     * The weatstone example above is quite easy to solve and all ports will be
     * considered in the star delta transform. However, adding another port and
     * resistor will make the integration of star delta much more complicated.
     *
     * <p>
     * This tests will not only require the star delta transform to work proper,
     * it also tests the integration between different simplifications more
     * intense. The star delta algorithm will solve the network in a totally
     * different way than the wheatstone bridge itself.
     *
     */
    @Test
    public void testBridgeWithRealSource() {
        System.out.println("bridgeWithRealSource");
        /*
         *               node 1
         *       -----------o-------------           Resistance between node 1
         *       |          |            |           and node 2 is expected to
         *       X 35 Ohms  X 40 Ohms    X 55 Ohms   be 85.8 Ohms.
         *       X R5       X R1         X R2        Expected flow on U is 
         *       X          X            X           therefore I = U/R = 0.117 A
         *       |          |     R0     |
         * node4 o    node2 o---XXXXXX---o node3     The example didnt include
         *       |          |    45 Ohms |           R6 first, therefore numbers
         *       |          X 60 Ohms    X 50 Ohms   and names of elements and
         *  U   (|)         X R3         X R4        ports are not sorted.
         *  10 V |          X            X
         *       |          |            |
         *       -----------o-------------
         *                  | node0
         *                 _|_
         */

        RecursiveSimplifier instance = new RecursiveSimplifier();

        // Prepare network elements
        GeneralNode[] node;
        LinearDissipator[] r; // declare
        EffortSource u;
        ClosedOrigin zero;

        node = new GeneralNode[5]; // allocate
        for (int idx = 0; idx < node.length; idx++) { // init
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        r = new LinearDissipator[6]; // allocate
        for (int idx = 0; idx < 6; idx++) {
            r[idx]  = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }

        r[0].setResistanceParameter(45);
        r[1].setResistanceParameter(40);
        r[2].setResistanceParameter(55);
        r[3].setResistanceParameter(60);
        r[4].setResistanceParameter(50);
        r[5].setResistanceParameter(35);


        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setEffort(10);
        zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        // Connect everything
        u.connectTo(node[0]);
        zero.connectTo(node[0]);
        u.connectTo(node[4]);
        r[5].connectTo(node[4]);
        r[5].connectTo(node[1]);
        r[1].connectTo(node[1]);
        r[2].connectTo(node[1]);
        r[1].connectTo(node[2]);
        r[2].connectTo(node[3]);
        r[0].connectTo(node[2]);
        r[0].connectTo(node[3]);
        r[3].connectTo(node[2]);
        r[4].connectTo(node[3]);
        r[3].connectTo(node[0]);
        r[4].connectTo(node[0]);

        // Register ports - fails with exeption if not possible
        for (GeneralNode n : node) {
            instance.registerNode(n);
        }

        // Register elements - fails with exeption if not possible
        for (LinearDissipator res : r) {
            instance.registerElement(res);
        }
        instance.registerElement(u);
        instance.registerElement(zero);

        // setup the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = instance.recursiveSimplificationSetup(0);
        assertEquals(numberOfLayers, 6, "Wrong number of layers created.");

        instance.prepareRecursiveCalculation();

        // After method call, whole network must be in fully caculated state.
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());

        // Check expected flow value through whole circuit to validate
        // calculation results
        assertEquals(u.getFlow(), 0.117, 0.001,
                "Calulation result on resistor is wrong.");
    }

    /**
     * Another case from chernobyl which could not be solved. This circuit was
     * occuring with those values somewhere deep in all those layers and failed
     * to solve, therefore it ended up as a test case here.
     * 
     * There is an if (openElements == 1) in calculateStarValuesFromDelta in
     * class StarDeltaTransform which is required to work properly to provide
     * a network solution. At the time of writing this test, the method was
     * not yet finished but it turned out it was unnecessary. However, this test
     * is kept as it tests something providing a result.
     */
    @Test
    public void testStarWithOneOpenConnection() {
        System.out.println("starWithOneOpenConnection");
        /*                  o2
         *    ------------[   ]-------------
         *    |   8.83e8   n2      R       |
         * n1 o----XXXXX----o----XXXXX-----o n3
         *    |    wtV2     |    66666,67  |
         *    |            (|) u=0         |
         *    |  3424182.9  |              |
         *    -----XXXXX----o----[   ]------
         *         wtV1     |n0    o1
         *                 _|_ gnd
         */
        RecursiveSimplifier instance = new RecursiveSimplifier();

        GeneralNode[] n = new GeneralNode[4];
        for (int idx = 0; idx < n.length; idx++) { // init
            n[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            n[idx].setName("n" + idx);
        }
        LinearDissipator o1 = 
                new LinearDissipator(PhysicalDomain.ELECTRICAL, false);
        o1.setName("o1");
        LinearDissipator o2 = 
                new LinearDissipator(PhysicalDomain.ELECTRICAL, false);
        o2.setName("o2");
        LinearDissipator wtV1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        wtV1.setResistanceParameter(3424182.9);
        wtV1.setName("wtV1");
        LinearDissipator wtV2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        wtV2.setResistanceParameter(8.83e8);
        wtV2.setName("wtV2");
        LinearDissipator R = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        R.setResistanceParameter(66666.67);
        R.setName("R");
        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setName("u");
        u.setEffort(0);
        ClosedOrigin gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        gnd.setName("GND");
        
        // All elements will be connected exactly as occured in the debugged
        // case, sometime the connection order made differences (what it should
        // never do)
        gnd.connectTo(n[0]);
        u.connectTo(n[0]);
        wtV2.connectTo(n[1]);
        o2.connectTo(n[1]);
        wtV1.connectTo(n[1]);
        o1.connectTo(n[0]);
        wtV1.connectTo(n[0]);
        wtV2.connectTo(n[2]);
        u.connectTo(n[2]);
        R.connectTo(n[2]);
        R.connectTo(n[3]);
        o1.connectTo( n[3]);
        o2.connectTo(n[3]);
        
        // Same goes for adding to network, keep the exact order
        for (int idx = 0; idx < n.length; idx++) {
           instance.registerNode(n[idx]);
        }
        instance.registerElement(gnd);
        instance.registerElement(wtV2);
        instance.registerElement(u);
        instance.registerElement(R);
        instance.registerElement(o1);
        instance.registerElement(o2);
        instance.registerElement(wtV1);
        
        instance.recursiveSimplificationSetup(0);
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        
        // After calling the calculation, model must be in full calulated
        // state, this was not the case and test replicated it sucessfully.
        assertTrue(instance.isCalculationFinished());
        // All efforts must be zero:
        assertEquals(n[1].getEffort(), 0.0, 1e-12, "Effort not as expected");
        assertEquals(n[2].getEffort(), 0.0, 1e-12, "Effort not as expected");
        assertEquals(n[3].getEffort(), 0.0, 1e-12, "Effort not as expected");
        
        // Change voltage on source and check the calculated flow
        // for an additional validation of the calculation.
        
        // I = U / R and R is the sum of wtV2 and wtV1 as others can be ignored
        double expectedFlow = 1e9 / (8.83e8 + 3424182.9);
        u.setEffort(1e9);
        
        // Run calculation again
        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        
        assertTrue(instance.isCalculationFinished());
        assertEquals(u.getFlow(), expectedFlow, 1e-8, "Flow value invalid.");
        
        assertEquals(n[3].getEffort(), 1e9, 1e-12, "Node floating?");
    }
}
