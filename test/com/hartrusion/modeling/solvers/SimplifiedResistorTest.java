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
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * This integration test will set up some different, simple looking networks and
 * check if the simplification process works well. These are basically easy to
 * debug integration tests, challenging only the SimplifiedResistor class and
 * not the solver itself. Unfortunately, this also uses the recursive simplifier
 * solver, but it will not trigger the star delta and no two-series calculation
 * is needed from LinearNetwork class.
 *
 * During development, there was some trouble getting different combinations of
 * directions from those resistors to work properly, this is why those tests
 * where written for all those different cases.
 *
 * @author Viktor Alexander Hartung
 */
public class SimplifiedResistorTest {

    RecursiveSimplifier instance;
    GeneralNode[] p;
    LinearDissipator[] r;
    EffortSource u;
    ClosedOrigin zero;
    
    @BeforeClass
    public static void setUpClass() throws Exception {
        // Remove all loggers
        java.util.logging.Logger root = java.util.logging.Logger.getLogger("");
        for (java.util.logging.Handler h : root.getHandlers()) {
            root.removeHandler(h);
        }
    }

    public SimplifiedResistorTest() {
        p = new GeneralNode[3];
        r = new LinearDissipator[2];
    }

    @BeforeMethod
    public void setUpMethod() throws Exception {
        instance = new RecursiveSimplifier();
        u = new EffortSource(PhysicalDomain.ELECTRICAL);
        zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);

        for (int idx = 0; idx < p.length; idx++) { // init
            p[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            p[idx].setName("p" + idx);
        }

        for (int idx = 0; idx < 2; idx++) { // init
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setResistanceParameter(400.0);
            r[idx].setName("R" + idx);
        }

        u.setEffort(16); // 16 Volts allow easy calculation
    }

    @AfterMethod
    public void tearDownMethod() throws Exception {
        instance = null;
        u = null;
        zero = null;

        for (int idx = 0; idx < p.length; idx++) { // init
            p[idx] = null;
        }

        for (int idx = 0; idx < 2; idx++) { // init
            r[idx] = null;
        }
    }

    /**
     * This is a very basic test with two serial resistors. They are all in a
     * desired order, however, order of elements and ports are not mandatory.
     */
    @Test
    public void testBasicSeriesResistors() {
        System.out.println("basicSeriesResistor");
        /*
         *          p1  
         *   --------o--------   
         *   |            0  |  <-- this number is the index of the port.
         *   |               X   
         *   |               X R0  
         *   |               X  400 Ohms
         * 1 |            1  |
            (|)              o p2
         * 0 |            0  |
         *   |               X   
         *   |               X R1  
         *   |               X  400 Ohms
         *   |            1  |
         *   o---------------- 
         *   |       p0
         *  _|_ z
         *
         */

        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[1]);
        r[0].connectTo(p[2]);
        r[1].connectTo(p[2]);
        r[1].connectTo(p[0]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.02, 0.000001);
    }

    /**
     * Based on test basicSeriesResistor, this has a different order how the
     * elements are connected to each other. However, results must be the same.
     */
    @Test
    public void testFullReverseOrderSeriesResistors() {
        System.out.println("fullReverseOrderSeriesResistor");
        /*
         *          p1  
         *   --------o--------  
         *   |            1  |
         *   |               X   
         *   |               X R0  
         *   |               X  400 Ohms
         * 1 |            0  |
            (|)              o p2
         * 0 |            1  |
         *   |               X   
         *   |               X R1  
         *   |               X  400 Ohms
         *   |            0  |
         *   o---------------- 
         *   |       p0
         *  _|_ z
         *
         */

        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[2]);
        r[0].connectTo(p[1]);
        r[1].connectTo(p[0]);
        r[1].connectTo(p[2]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.02, 0.000001);
    }

    /**
     * Based on test basicSeriesResistor, this has a different order how the
     * elements are connected to each other. However, results must be the same.
     */
    @Test
    public void testFlowFacesMiddleSeriesResistors() {
        System.out.println("flowFacesMiddleSeriesResistors");
        /*
         *          p1  
         *   --------o--------  
         *   |            0  |
         *   |               X   
         *   |               X R0  
         *   |               X  400 Ohms
         * 1 |            1  |
            (|)              o p2
         * 0 |            1  |
         *   |               X   
         *   |               X R1  
         *   |               X  400 Ohms
         *   |            0  |
         *   o---------------- 
         *   |       p0
         *  _|_ z
         *
         */

        // Connect elements to create the netwok described - note the
        // differences to the basic test!
        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[1]);
        r[0].connectTo(p[2]);
        r[1].connectTo(p[0]);
        r[1].connectTo(p[2]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.02, 0.000001);
    }

    /**
     * Based on test basicSeriesResistor, this has a different order how the
     * elements are connected to each other. However, results must be the same.
     */
    @Test
    public void testFlowFacesOuterSeriesResistors() {
        System.out.println("flowFacesOuterSeriesResistors");
        /*
         *          p1  
         *   --------o--------         < ----   parentPorts[1]
         *   |       port[1] |                        
         *   |               X   
         *   |               X R0              parentResistor[1]
         *   |               X  400 Ohms
         * 1 |       port[0] |                        
            (|)              o p2
         * 0 |       port[0] |                         
         *   |               X                       
         *   |               X R1              parentResistor[0]
         *   |               X  400 Ohms               
         *   |       port[1] |  
         *   o----------------         < ----   parentPorts[0]
         *   |       p0
         *  _|_ z
         *
         */

        // Connect elements to create the netwok described - note the
        // differences to the basic test!
        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[2]);
        r[0].connectTo(p[1]);
        r[1].connectTo(p[2]);
        r[1].connectTo(p[0]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.02, 0.000001);
    }

    /**
     * This is a very basic test with two parallel resistors. They are all in a
     * desired order, however, order of elements and ports are not mandatory.
     */
    @Test
    public void testBasicParallelResistors() {
        System.out.println("basicSeriesResistor");
        /*
         *                  p1  
         *   ----------------o---------------
         *   |            0  |           0  |
         * 1 |               X              X
         *  (|)              X R0           X R1
         * 0 |               X 400 Ohms     X 400 Ohms
         *   |            1  |           1  |
         *   o---------------o --------------
         *   |              p0
         *  _|_ z
         *
         */

        // Connect elements to create the netwok described
        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[1]);
        r[0].connectTo(p[0]);
        r[1].connectTo(p[1]);
        r[1].connectTo(p[0]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.08, 0.000001);
    }

    /**
     * Two parallel resistors but their order is reversed, the flow is from 1 to
     * 0. This must not be an issue.
     */
    @Test
    public void testReverseParallelResistors() {
        System.out.println("reverseSeriesResistor");
        /*
         *                  p1  
         *   ----------------o---------------
         *   |            1  |           1  |
         * 1 |               X              X
         *  (|)              X R0           X R1
         * 0 |               X 400 Ohms     X 400 Ohms
         *   |            0  |           0  |
         *   o---------------o --------------
         *   |              p0
         *  _|_ z
         *
         */

        // Connect elements to create the netwok described
        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[0]);
        r[0].connectTo(p[1]);
        r[1].connectTo(p[0]);
        r[1].connectTo(p[1]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.08, 0.000001);
    }

    /**
     * Two parallel resistors but their order is reversed. They seem to form a
     * loop but that must never, under any circumstance, make any difference.
     */
    @Test
    public void testMixedOrderParallelResistors() {
        System.out.println("mixedOrderParallelResistors");
        /*
         *                  p1  
         *   ----------------o---------------
         *   |            0  |           1  |
         * 1 |               X              X
         *  (|)              X R0           X R1
         * 0 |               X 400 Ohms     X 400 Ohms
         *   |            1  |           0  |
         *   o---------------o --------------
         *   |              p0
         *  _|_ z
         *
         */

        // Connect elements to create the netwok described
        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[1]);
        r[0].connectTo(p[0]);
        r[1].connectTo(p[0]);
        r[1].connectTo(p[1]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.08, 0.000001);
    }

    /**
     * Two parallel resistors but their order is reversed. They seem to form a
     * loop but that must never, under any circumstance, make any difference.
     * Same as test testMixedOrderParallelResistors, just the other way round.
     */
    @Test
    public void testMixedReverseOrderParallelResistors() {
        System.out.println("mixedReversedOrderParallelResistors");
        /*
         *                  p1  
         *   ----------------o---------------
         *   |            1  |           0  |
         * 1 |               X              X
         *  (|)              X R0           X R1
         * 0 |               X 400 Ohms     X 400 Ohms
         *   |            0  |           1  |
         *   o---------------o --------------
         *   |              p0
         *  _|_ z
         *
         */

        // Connect elements to create the netwok described
        zero.connectTo(p[0]);
        u.connectTo(p[0]);
        u.connectTo(p[1]);
        r[0].connectTo(p[0]);
        r[0].connectTo(p[1]);
        r[1].connectTo(p[1]);
        r[1].connectTo(p[0]);

        setupAndRunSolver();

        assertEquals(u.getFlow(), 0.08, 0.000001);
    }

    /**
     * This will do common steps to have the RecursiveSimplifier setup
     * everything and run calculations.
     */
    private void setupAndRunSolver() {
        // Register ports - fails with exeption if not possible
        for (GeneralNode n : p) {
            // the parallels will have one less port
            if (n.getNumberOfElements() >= 1) {
                instance.registerNode(n);
            }
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

        // This example must be finished with 1 layer.
        assertEquals(numberOfLayers, 1, "Wrong number of layers created.");

        instance.prepareRecursiveCalculation();
        instance.doRecursiveCalculation();
        assertTrue(instance.isCalculationFinished());
    }
    
    
    /**
     * Now with an open and closed connection, usually this is impossible but
     * having a bridged 0 V source should be possible. It was a problem using
     * this in another test.
     */
//    @Test
//    public void testMixedOrderParallelOpenAndBridged() {
//        System.out.println("mixedOrderParallelOpenAndBridged");
//        /*
//         *  p1               p2  
//         *   o----XXXXXX-----o---------------
//         *   |            1  |           0  |
//         * 1 |              ---             I
//         *  (|)                R0           I R1
//         * 0 |              ---inf Ohms     I 0 Ohm
//         *   |            0  |           1  |
//         *   o---------------o --------------
//         *   |              p0
//         *  _|_ z
//         *
//         */
//        
//        r[0].setOpenConnection();
//        r[1]
//
//        // Connect elements to create the netwok described
//        zero.connectTo(p[0]);
//        u.connectTo(p[0]);
//        u.connectTo(p[1]);
//        r[0].connectTo(p[0]);
//        r[0].connectTo(p[1]);
//        r[1].connectTo(p[1]);
//        r[1].connectTo(p[0]);
//
//        setupAndRunSolver();
//
//        assertEquals(u.getFlow(), 0.08, 0.000001);
//    }
}
