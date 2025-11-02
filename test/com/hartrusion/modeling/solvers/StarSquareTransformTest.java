/*
 * The MIT License
 *
 * Copyright 2025 Viktor Alexander Hartung
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
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.*;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 *
 * @author viktor
 */
public class StarSquareTransformTest {

    public StarSquareTransformTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    /**
     * Tests the correct simplification for two bridged resistor elements if
     * they are next to each other. This looks like a V, the opposite elements
     * are not bridged. This test tests all possible combinations (four)
     */
    @Test
    public void testTwoBridgesVShaped() {
        // Commonly used for all subcases
        GeneralNode[] node = new GeneralNode[5];
        LinearDissipator[] r = new LinearDissipator[4];
        StarSquareTransform instance;

        // Setup a new star arragement with 4 resistors, set shortcuts and
        // check if the generated square arrangement contains the correct
        // values. Middle resistors will always be positive infintiy.
        // The remaining non-shorted resitors have specified values, it is
        // expected that they will be at the correct place.
        
        // V between 0 and 1 - idx 0
        prepareStarSetup(node, r);
        instance = new StarSquareTransform();
        instance.setupStar(node[4]);
        r[0].setBridgedConnection();
        r[1].setBridgedConnection();
        instance.calculateSquareResistorValues();
        assertEquals(instance.getSquareElement(4).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(5).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(0).getResistance(),
                0, 1e-12); // bridged
        assertEquals(instance.getSquareElement(1).getResistance(),
                120.0, 1e-12); // value from R2
        assertEquals(instance.getSquareElement(2).getResistance(),
                Double.POSITIVE_INFINITY); // open
        assertEquals(instance.getSquareElement(3).getResistance(),
                130.0, 1e-12); // value from r3
        
        // V between 1 and 2 - idx 1
        prepareStarSetup(node, r);
        instance = new StarSquareTransform();
        instance.setupStar(node[4]);
        r[1].setBridgedConnection();
        r[2].setBridgedConnection();
        instance.calculateSquareResistorValues();
        assertEquals(instance.getSquareElement(4).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(5).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(0).getResistance(),
                100, 1e-12); // value from R0
        assertEquals(instance.getSquareElement(1).getResistance(),
                0.0, 1e-12);
        assertEquals(instance.getSquareElement(2).getResistance(),
                130.0, 1e-12); // value from R3
        assertEquals(instance.getSquareElement(3).getResistance(),
                Double.POSITIVE_INFINITY);
        
        // V between 2 and 3 - idx 2
        prepareStarSetup(node, r);
        instance = new StarSquareTransform();
        instance.setupStar(node[4]);
        r[2].setBridgedConnection();
        r[3].setBridgedConnection();
        instance.calculateSquareResistorValues();
        assertEquals(instance.getSquareElement(4).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(5).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(0).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(1).getResistance(),
                110.0, 1e-12); // value from R1
        assertEquals(instance.getSquareElement(2).getResistance(),
                0, 1e-12);
        assertEquals(instance.getSquareElement(3).getResistance(),
                100, 1e-12); // value from R0 - fails
        
         // V between 3 and 0 - idx 3
        prepareStarSetup(node, r);
        instance = new StarSquareTransform();
        instance.setupStar(node[4]);
        r[3].setBridgedConnection();
        r[0].setBridgedConnection();
        instance.calculateSquareResistorValues();
        assertEquals(instance.getSquareElement(4).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(5).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(0).getResistance(),
                110.0, 1e-12); // value from R1
        assertEquals(instance.getSquareElement(1).getResistance(),
                Double.POSITIVE_INFINITY);
        assertEquals(instance.getSquareElement(2).getResistance(),
                120, 1e-12); // value from R2
        assertEquals(instance.getSquareElement(3).getResistance(),
                0, 1e-12);

    }

    /**
     * Helper function that generates a star setup template and writes it into
     * existing arrays.
     *
     * @param nodeArray
     * @param resistorArray
     */
    private void prepareStarSetup(GeneralNode[] nodeArray,
            LinearDissipator[] resistorArray) {
        for (int idx = 0; idx < nodeArray.length; idx++) { // init
            nodeArray[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            if (idx == 4) {
                nodeArray[idx].setName("StarNode");
            } else {
                nodeArray[idx].setName("node" + idx);
            }
        }

        for (int idx = 0; idx < resistorArray.length; idx++) { // init
            resistorArray[idx] = new LinearDissipator(
                    PhysicalDomain.ELECTRICAL);
            resistorArray[idx].setName("R" + idx);
            // r[0] will have 100 ohms, r[1] has 110 and so on per default
            resistorArray[idx].setResistanceParameter(
                    100.0 + 10.0 * (double) (idx));
        }

        resistorArray[0].connectBetween(nodeArray[0], nodeArray[4]);
        resistorArray[1].connectBetween(nodeArray[1], nodeArray[4]);
        resistorArray[2].connectBetween(nodeArray[2], nodeArray[4]);
        resistorArray[3].connectBetween(nodeArray[3], nodeArray[4]);

        assertEquals(StarSquareTransform.checkForStarNode(nodeArray[4]), true);
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
        RecursiveSimplifier solver = new RecursiveSimplifier();

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
            solver.registerNode(n);
        }
        for (LinearDissipator res : r) {
            solver.registerElement(res);
        }
        solver.registerElement(u);
        solver.registerElement(zero);

        // set up the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = solver.recursiveSimplificationSetup(0);

        solver.prepareRecursiveCalculation();

        // After method call, whole network must be in fully calculated state.
        solver.doRecursiveCalculation();
        assertTrue(solver.isCalculationFinished());
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
        RecursiveSimplifier solver = new RecursiveSimplifier();

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
            solver.registerNode(n);
        }

        // It is vital that the elements are getting connected in the exact
        // same order as here, otherwise the error will not be triggered!
        solver.registerElement(zero);
        solver.registerElement(r[0]);
        solver.registerElement(r[1]);
        solver.registerElement(u);
        for (int idx = 2; idx <= 11; idx++) {
            solver.registerElement(r[idx]);
        }

        // set up the solver, creating the layers
        int numberOfLayers;
        numberOfLayers = solver.recursiveSimplificationSetup(0);

        solver.prepareRecursiveCalculation();

        // After method call, whole network must be in fully calculated state.
        solver.doRecursiveCalculation();
        assertTrue(solver.isCalculationFinished());

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
