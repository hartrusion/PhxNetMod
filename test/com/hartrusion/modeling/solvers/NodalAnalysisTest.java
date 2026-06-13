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

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.util.SimpleLogOut;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Integration test for the class NodalAnalysis. It uses the same example
 * networks as the SuperPositionTest so the results can be compared directly
 * with the (independently obtained) superposition results.
 * <p>
 * AI generated using Claude Opus 4.8
 *
 * @author Viktor Alexander Hartung
 */
public class NodalAnalysisTest {

    @BeforeClass
    public static void setUpClass() throws Exception {
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public NodalAnalysisTest() {
    }

    /**
     * Two real voltage sources and three resistors. Both effort sources are
     * real voltage sources: U0 is in series with R0 and U1 is in series with
     * R1. Identical to SuperPositionTest.testBasicNetwork.
     *
     * <pre>
     * node1       R0         node2      R1
     *   o-------XXXXXX--------o-------XXXXXX--------o node3
     *   |      200 Ohms       |       50 Ohms      |
     *   |                     X                     |
     *  (|) U0                 X R2                 (|) U1
     *   |  15 V               X  100 Ohms          |  10 V
     *   |                     |                     |
     *   ----------------------o----------------------
     *                         |  node 0
     *                        _|_ zero
     * </pre>
     */
    @Test
    public void testBasicNetwork() {
        NodalAnalysis instance = new NodalAnalysis();

        GeneralNode[] node = new GeneralNode[4];
        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        LinearDissipator[] r = new LinearDissipator[3];
        for (int idx = 0; idx < r.length; idx++) {
            r[idx] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r[idx].setName("R" + idx);
        }
        r[0].setResistanceParameter(200);
        r[1].setResistanceParameter(50);
        r[2].setResistanceParameter(100);

        EffortSource[] u = new EffortSource[2];
        u[0] = new EffortSource(PhysicalDomain.ELECTRICAL);
        u[0].setName("U0");
        u[1] = new EffortSource(PhysicalDomain.ELECTRICAL);
        u[1].setName("U1");
        u[0].setEffort(15);
        u[1].setEffort(10);

        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        zero.setName("Gnd");

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

        List<GeneralNode> nodeList = new ArrayList<>();
        List<AbstractElement> elementList = new ArrayList<>();
        for (GeneralNode n : node) {
            nodeList.add(n);
        }
        for (LinearDissipator res : r) {
            elementList.add(res);
        }
        elementList.add(u[0]);
        elementList.add(u[1]);
        elementList.add(zero);

        // The network has to be detected as solvable.
        assertTrue(NodalAnalysis.isSolvableByNodalAnalysis(nodeList, elementList),
                "Network should be solvable by nodal analysis.");

        for (GeneralNode n : nodeList) {
            instance.registerNode(n);
        }
        for (AbstractElement e : elementList) {
            instance.registerElement(e);
        }

        instance.nodalAnalysisSetup();
        instance.prepareCalculation();
        instance.doCalculation();

        assertTrue(instance.isCalculationFinished(),
                "No full solution was provided.");

        // flow on R2 must be 0.0785 ampere (same value as superposition)
        assertEquals(r[2].getFlow(), 0.0785, 0.0001,
                "Calculation result on resistor R2 is wrong.");
    }

    /**
     * The 2010 summer term exam network with two current sources and one real
     * voltage source (U in series with R3). Identical topology and expected
     * results to SuperPositionTest.testSummerTermExam.
     *
     * <pre>
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
     * </pre>
     */
    @Test
    public void testSummerTermExam() {
        NodalAnalysis instance = new NodalAnalysis();

        GeneralNode[] node = new GeneralNode[4];
        for (int idx = 0; idx < node.length; idx++) {
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

        instance.nodalAnalysisSetup();
        instance.prepareCalculation();
        instance.doCalculation();

        assertTrue(instance.isCalculationFinished(),
                "No full solution was provided.");

        // Same expected values as the superposition solver.
        assertEquals(r1.getFlow(), -3.669, 0.01, "Wrong flow on R1");
        assertEquals(r3.getFlow(), 0.512, 0.01, "Wrong flow on R3");
        assertEquals(u.getFlow(), 0.512, 0.01, "Wrong flow on U");
    }

    /**
     * Verifies that the static solvability check rejects an ideal voltage
     * source, that is an effort source which is not in series with a single
     * resistor.
     *
     * <pre>
     *     node1     R0     node2
     *   ----o------XXXX------o----
     *   |   |                |   |
     *  (|)U |               (|)U1|   (U directly across node0/node1 -> ideal)
     *   |   |                    |
     *   o node0 -----------------
     *  _|_ zero
     * </pre>
     */
    @Test
    public void testIdealSourceRejected() {
        // node1 is connected to U, R0 and a second resistor, so U has no
        // dedicated series resistor and is therefore an ideal source.
        GeneralNode node0 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        GeneralNode node1 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        GeneralNode node2 = new GeneralNode(PhysicalDomain.ELECTRICAL);
        node0.setName("node0");
        node1.setName("node1");
        node2.setName("node2");

        LinearDissipator r0 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r0.setResistanceParameter(100);
        r0.setName("R0");
        LinearDissipator r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r1.setResistanceParameter(100);
        r1.setName("R1");

        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setEffort(10);
        u.setName("U");

        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        zero.setName("Gnd");

        zero.connectTo(node0);
        u.connectTo(node0);
        u.connectTo(node1);
        r0.connectTo(node1);
        r0.connectTo(node2);
        r1.connectTo(node1);  // makes node1 degree 3 -> U is ideal
        r1.connectTo(node2);

        List<GeneralNode> nodeList = new ArrayList<>();
        nodeList.add(node0);
        nodeList.add(node1);
        nodeList.add(node2);
        List<AbstractElement> elementList = new ArrayList<>();
        elementList.add(r0);
        elementList.add(r1);
        elementList.add(u);
        elementList.add(zero);

        assertFalse(
                NodalAnalysis.isSolvableByNodalAnalysis(nodeList, elementList),
                "Ideal voltage source must be rejected.");
    }

    /**
     * Two effort sources that share a single resistor between them. Each source
     * would, seen on its own, regard that one resistor as its series resistor,
     * so the resistor has to be split into two halves during the conversion.
     * The network must still be solvable and yield the correct result.
     *
     * <pre>
     *   node1        R 120 Ohms        node2
     *     o---------XXXXXXXXX----------o
     *     |                            |
     *    (|) U 5 V                    (|) Uc 2 V
     *     |                            |
     *     o-------------o--------------o
     *                   | node0
     *                  _|_ zero
     * </pre>
     */
    @Test
    public void testSharedSeriesResistor() {
        NodalAnalysis instance = new NodalAnalysis();

        GeneralNode[] node = new GeneralNode[3];
        for (int idx = 0; idx < node.length; idx++) {
            node[idx] = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node[idx].setName("node" + idx);
        }

        LinearDissipator r = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        r.setResistanceParameter(120);
        r.setName("R");

        EffortSource u = new EffortSource(PhysicalDomain.ELECTRICAL);
        u.setEffort(5);
        u.setName("U");
        EffortSource uc = new EffortSource(PhysicalDomain.ELECTRICAL);
        uc.setEffort(2);
        uc.setName("Uc");

        ClosedOrigin zero = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
        zero.setName("Gnd");

        // Build the network: both sources go from ground to their node, the
        // single resistor connects the two source nodes.
        zero.connectTo(node[0]);
        u.connectTo(node[0]);
        u.connectTo(node[1]);
        uc.connectTo(node[0]);
        uc.connectTo(node[2]);
        r.connectTo(node[1]);
        r.connectTo(node[2]);

        List<GeneralNode> nodeList = new ArrayList<>();
        List<AbstractElement> elementList = new ArrayList<>();
        for (GeneralNode n : node) {
            nodeList.add(n);
        }
        elementList.add(r);
        elementList.add(u);
        elementList.add(uc);
        elementList.add(zero);

        // Despite the shared resistor the network has to be solvable.
        assertTrue(NodalAnalysis.isSolvableByNodalAnalysis(nodeList, elementList),
                "Shared resistor network should be solvable by nodal analysis.");

        for (GeneralNode n : nodeList) {
            instance.registerNode(n);
        }
        for (AbstractElement e : elementList) {
            instance.registerElement(e);
        }

        instance.nodalAnalysisSetup();
        instance.prepareCalculation();
        instance.doCalculation();

        assertTrue(instance.isCalculationFinished(),
                "No full solution was provided.");

        // V(node1) = 5, V(node2) = 2, current through R = (5 - 2) / 120.
        assertEquals(node[1].getEffort(), 5.0, 1e-9, "Wrong effort on node1");
        assertEquals(node[2].getEffort(), 2.0, 1e-9, "Wrong effort on node2");
        assertEquals(r.getFlow(), (5.0 - 2.0) / 120.0, 1e-9,
                "Wrong flow on the shared resistor R");
    }
}
