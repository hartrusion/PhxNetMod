/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.WrongSolverException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.util.SimpleLogOut;
import java.util.ArrayList;
import java.util.List;
import static org.testng.Assert.*;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests the solution of a closed ring of one effort source and four resistors
 * in series, which is lifted to a higher effort by a grounded effort source on
 * one of its nodes, as the TransferSubnet of the RBMK simulator generates it
 * twice in one run.
 * <pre>
 *
 *            nRing    r1      n1    uRing    n2
 *    -----------o----XXXXXX----o----(-|)----o
 *    |          |                           |
 *    |          X r4                        X r2
 *    |          X                           X
 *   (|) uLift   |     r3      n3            |
 *    |       n4 o----XXXXXX----o-------------
 *    |
 *    o nGnd
 *   _|_ gnd
 *
 * </pre> The effort source {@code uLift} forces the ring node {@code nRing}
 * and carries no flow, because the ring is only connected at this single
 * node. The effort source {@code uRing} has its positive side at {@code n1}.
 * The ring flow I is defined as positive in the direction from {@code nRing}
 * via {@code n1}, {@code n2}, {@code n3} and {@code n4} back to
 * {@code nRing}, and Kirchhoff's voltage law along the ring gives
 * <pre>
 *     - I * r1 - uRing - I * r2 - I * r3 - I * r4 = 0
 *     I = - uRing / (r1 + r2 + r3 + r4)
 * </pre> The node efforts follow from nRing = uLift along the ring, for
 * example with n1 = nRing - I * r1 and n2 = n1 - uRing. The expected values of
 * the tests were calculated by hand with these equations, and the tests with
 * the {@link NodalAnalysis} solver confirm them inside the library before they
 * are used to validate the {@link FloatingEffortRing} solver.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class FloatingEffortRingTest {

    private static final double EFFORT_TOLERANCE = 1e-9;
    private static final double FLOW_TOLERANCE = 1e-12;

    // Ring circuit: uLift = 5 V, uRing = 9 V, r1 = 100 Ohm, r2 = 220 Ohm,
    // r3 = 330 Ohm and r4 = 470 Ohm, which results in I = -9 V / 1120 Ohm.
    private static final double LIFT_EFFORT = 5.0;
    private static final double RING_EFFORT = 9.0;
    private static final double R1 = 100;
    private static final double R2 = 220;
    private static final double R3 = 330;
    private static final double R4 = 470;
    private static final double RING_FLOW = -0.008035714285714;
    private static final double EFFORT_1 = 5.803571428571;
    private static final double EFFORT_2 = -3.196428571429;
    private static final double EFFORT_3 = -1.428571428571;
    private static final double EFFORT_4 = 1.223214285714;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public FloatingEffortRingTest() {
    }

    /**
     * Holds all nodes and elements of the ring network shown in the class
     * description. The node and element lists are in the form a solver
     * receives them from the TransferSubnet.
     */
    private static class RingNetwork {

        final List<GeneralNode> nodes = new ArrayList<>();
        final List<AbstractElement> elements = new ArrayList<>();

        /**
         * Effort sources between the ground node and a forced node.
         */
        final List<AbstractElement> groundedSources;

        final ClosedOrigin gnd;
        final GeneralNode nGnd, nRing, n1, n2, n3, n4;
        final EffortSource uLift, uRing;
        final LinearDissipator r1, r2, r3, r4;

        /**
         * Builds the network.
         *
         * @param reversed false connects all elements as the TransferSubnet
         * of the RBMK simulator does, true connects every element the other
         * way round and negates the source values, which results in the same
         * circuit with reversed element directions.
         */
        RingNetwork(boolean reversed) {
            double sign = 1.0;
            if (reversed) {
                sign = -1.0;
            }
            gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
            gnd.setName("Zero");
            nGnd = createNode("nGnd");
            gnd.connectTo(nGnd);
            nRing = createNode("nRing");
            n1 = createNode("n1");
            n2 = createNode("n2");
            n3 = createNode("n3");
            n4 = createNode("n4");
            uLift = createSource("uLift", sign * LIFT_EFFORT);
            uRing = createSource("uRing", sign * RING_EFFORT);
            r1 = createResistor("r1", R1);
            r2 = createResistor("r2", R2);
            r3 = createResistor("r3", R3);
            r4 = createResistor("r4", R4);

            // Same element order and orientation as in the subnet dumps of
            // the simulator.
            connect(r2, n2, n3, reversed);
            connect(uRing, n2, n1, reversed);
            connect(r3, n3, n4, reversed);
            connect(r1, nRing, n1, reversed);
            connect(r4, nRing, n4, reversed);
            connect(uLift, nGnd, nRing, reversed);
            elements.add(gnd);
            groundedSources = List.of(uLift);
        }

        private GeneralNode createNode(String name) {
            GeneralNode node = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node.setName(name);
            nodes.add(node);
            return node;
        }

        private EffortSource createSource(String name, double effort) {
            EffortSource source = new EffortSource(PhysicalDomain.ELECTRICAL);
            source.setName(name);
            source.setEffort(effort);
            return source;
        }

        private LinearDissipator createResistor(String name,
                double resistance) {
            LinearDissipator r = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r.setName(name);
            r.setResistanceParameter(resistance);
            return r;
        }

        /**
         * Connects an element from node0 to node1, or the other way round if
         * reversed is set. The connection order defines node index 0 and 1
         * on the element, which defines the direction of the element.
         */
        private void connect(AbstractElement element, GeneralNode node0,
                GeneralNode node1, boolean reversed) {
            if (reversed) {
                element.connectTo(node1);
                element.connectTo(node0);
            } else {
                element.connectTo(node0);
                element.connectTo(node1);
            }
            elements.add(element);
        }

        /**
         * Solves the network with the general nodal analysis solver, which
         * provides the reference for the dedicated solver. The grounded
         * effort sources are registered as forced nodes, as the
         * TransferSubnet does it for its boundary sources, because the nodal
         * analysis does not accept effort sources without series resistor
         * otherwise.
         */
        void solveWithNodalAnalysis() {
            assertTrue(NodalAnalysis.isSolvableByNodalAnalysis(nodes,
                    elements, groundedSources),
                    "Nodal analysis does not accept circuit");
            NodalAnalysis solver = new NodalAnalysis();
            for (GeneralNode node : nodes) {
                solver.registerNode(node);
            }
            for (AbstractElement element : elements) {
                solver.registerElement(element);
            }
            for (AbstractElement source : groundedSources) {
                GeneralNode forcedNode = source.getNode(1);
                if (forcedNode == nGnd) {
                    forcedNode = source.getNode(0);
                }
                solver.forceNodeEffort(forcedNode, (EffortSource) source);
            }
            solver.nodalAnalysisSetup();
            solver.prepareCalculation();
            solver.doCalculation();
        }

        /**
         * Runs one calculation cycle with a dedicated solver, which includes
         * resetting all elements as the TransferSubnet does.
         */
        void solveWithDedicatedSolver(DedicatedSolver solver) {
            for (AbstractElement element : elements) {
                element.prepareCalculation();
            }
            solver.solveNetwork();
        }

        void assertAllCalculated() {
            for (AbstractElement element : elements) {
                assertTrue(element.isCalculationFinished(),
                        "Not calculated: " + element);
            }
        }

        /**
         * Returns the flow through a two-node element in the direction from
         * the given node to the other node, independent of the orientation in
         * which the element was connected.
         */
        static double getFlowFrom(FlowThrough element, GeneralNode from) {
            if (element.getNode(0) == from) {
                return element.getFlow();
            }
            return -element.getFlow();
        }

        /**
         * Checks that every element is fully calculated, that all node
         * efforts match the expected solution, that the ring flow runs
         * through every ring element in the direction from nRing via n1
         * towards n4 and that uLift carries no flow.
         */
        void assertSolution(double effortRing, double effort1, double effort2,
                double effort3, double effort4, double ringFlow) {
            assertAllCalculated();
            assertEquals(nGnd.getEffort(), 0.0, EFFORT_TOLERANCE);
            assertEquals(nRing.getEffort(), effortRing, EFFORT_TOLERANCE,
                    "nRing");
            assertEquals(n1.getEffort(), effort1, EFFORT_TOLERANCE, "n1");
            assertEquals(n2.getEffort(), effort2, EFFORT_TOLERANCE, "n2");
            assertEquals(n3.getEffort(), effort3, EFFORT_TOLERANCE, "n3");
            assertEquals(n4.getEffort(), effort4, EFFORT_TOLERANCE, "n4");
            assertEquals(getFlowFrom(uLift, nGnd), 0.0, FLOW_TOLERANCE,
                    "uLift");
            assertEquals(getFlowFrom(r1, nRing), ringFlow, FLOW_TOLERANCE,
                    "r1");
            assertEquals(getFlowFrom(uRing, n1), ringFlow, FLOW_TOLERANCE,
                    "uRing");
            assertEquals(getFlowFrom(r2, n2), ringFlow, FLOW_TOLERANCE, "r2");
            assertEquals(getFlowFrom(r3, n3), ringFlow, FLOW_TOLERANCE, "r3");
            assertEquals(getFlowFrom(r4, n4), ringFlow, FLOW_TOLERANCE, "r4");
        }
    }

    /**
     * Confirms the hand calculated solution of the ring with the general
     * {@link NodalAnalysis} solver.
     */
    @Test
    public void testRingWithNodalAnalysis() {
        RingNetwork network = new RingNetwork(false);
        network.solveWithNodalAnalysis();
        network.assertSolution(LIFT_EFFORT, EFFORT_1, EFFORT_2, EFFORT_3,
                EFFORT_4, RING_FLOW);
    }

    /**
     * Confirms with the general {@link NodalAnalysis} solver that the ring
     * with reversed element directions has the same solution.
     */
    @Test
    public void testRingReversedWithNodalAnalysis() {
        RingNetwork network = new RingNetwork(true);
        network.solveWithNodalAnalysis();
        network.assertSolution(LIFT_EFFORT, EFFORT_1, EFFORT_2, EFFORT_3,
                EFFORT_4, RING_FLOW);
    }

    /**
     * Creates the dedicated solver for the network, which must accept the
     * circuit.
     */
    private static FloatingEffortRing createRingSolver(RingNetwork network) {
        assertTrue(FloatingEffortRing.isSolveableBySolver(network.nodes,
                network.elements), "Solver does not accept circuit");
        try {
            return new FloatingEffortRing(network.nodes, network.elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
            return null;
        }
    }

    /**
     * Solves the ring with the {@link FloatingEffortRing} solver.
     */
    @Test
    public void testRingWithRingSolver() {
        RingNetwork network = new RingNetwork(false);
        network.solveWithDedicatedSolver(createRingSolver(network));
        network.assertSolution(LIFT_EFFORT, EFFORT_1, EFFORT_2, EFFORT_3,
                EFFORT_4, RING_FLOW);
    }

    /**
     * Solves the ring with reversed element directions with the
     * {@link FloatingEffortRing} solver.
     */
    @Test
    public void testRingReversedWithRingSolver() {
        RingNetwork network = new RingNetwork(true);
        network.solveWithDedicatedSolver(createRingSolver(network));
        network.assertSolution(LIFT_EFFORT, EFFORT_1, EFFORT_2, EFFORT_3,
                EFFORT_4, RING_FLOW);
    }

    /**
     * Runs two calculation cycles on two identical networks with the
     * dedicated solver and with the {@link NodalAnalysis} solver. The second
     * cycle changes both source efforts and opens r3, so that the solver must
     * handle changed values with the topology analysis of the first cycle and
     * must result in zero flow in the whole ring.
     */
    @Test
    public void testChangingValuesAndOpenResistorAgainstNodalAnalysis() {
        RingNetwork dedicated = new RingNetwork(false);
        RingNetwork reference = new RingNetwork(false);
        FloatingEffortRing solver = createRingSolver(dedicated);

        dedicated.solveWithDedicatedSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);

        for (RingNetwork network : new RingNetwork[]{dedicated, reference}) {
            network.uLift.setEffort(7.5);
            network.uRing.setEffort(-2.2);
            network.r3.setOpenConnection();
        }
        dedicated.solveWithDedicatedSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);
        // Without flow, n1 and n4 have the ring node effort, whereas n2 and
        // n3 differ from it by the effort of uRing.
        dedicated.assertSolution(7.5, 7.5, 9.7, 9.7, 7.5, 0.0);
    }

    /**
     * Solves the ring with the two open resistors r1 and r3. There is no
     * flow, and the nodes n1 and n2 between them are not connected to the
     * ring node, so that only their effort difference over uRing and the zero
     * flow are checked.
     */
    @Test
    public void testTwoOpenResistorsWithRingSolver() {
        RingNetwork network = new RingNetwork(false);
        network.r1.setOpenConnection();
        network.r3.setOpenConnection();
        network.solveWithDedicatedSolver(createRingSolver(network));
        network.assertAllCalculated();
        assertEquals(network.n1.getEffort() - network.n2.getEffort(),
                RING_EFFORT, EFFORT_TOLERANCE);
        assertEquals(network.n4.getEffort(), LIFT_EFFORT, EFFORT_TOLERANCE);
        assertEquals(network.uRing.getFlow(), 0.0, FLOW_TOLERANCE);
        assertEquals(network.uLift.getFlow(), 0.0, FLOW_TOLERANCE);
    }

    /**
     * Checks that the solver rejects a ring with an additional resistor
     * between n1 and n3, which splits the ring into two meshes, so that the
     * TransferSubnet falls back to a general solver.
     */
    @Test
    public void testRejectsAdditionalResistor() {
        RingNetwork network = new RingNetwork(false);
        LinearDissipator extra = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        extra.setResistanceParameter(R1);
        extra.connectBetween(network.n1, network.n3);
        network.elements.add(extra);
        assertFalse(FloatingEffortRing.isSolveableBySolver(network.nodes,
                network.elements));
    }

    /**
     * Compares all node efforts and the flows of all elements of two
     * identical networks that were solved with different solvers.
     */
    private static void assertSameSolution(RingNetwork actual,
            RingNetwork expected) {
        actual.assertSolution(expected.nRing.getEffort(),
                expected.n1.getEffort(), expected.n2.getEffort(),
                expected.n3.getEffort(), expected.n4.getEffort(),
                RingNetwork.getFlowFrom(expected.r1, expected.nRing));
    }
}
