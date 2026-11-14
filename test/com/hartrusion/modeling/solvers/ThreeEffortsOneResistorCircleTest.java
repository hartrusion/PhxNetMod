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
 * Tests the solution of a single mesh which consists of three effort sources
 * and one resistor, as the TransferSubnet of the RBMK simulator generates it
 * twice in one run.
 * <pre>
 *
 *        nA    uMid    nB      r1      nC
 *         o----(-|)----o----XXXXXX----o
 *         |                           |
 *  uLeft (|)                         (|) uRight
 *         |                           |
 *         -------------o---------------
 *                      | nGnd
 *                     _|_ gnd
 *
 * </pre> The grounded effort sources {@code uLeft} and {@code uRight} force
 * the outer nodes {@code nA} and {@code nC}, whereas the effort source
 * {@code uMid} floats between {@code nA} and {@code nB} with its positive
 * side at {@code nA}. As there is only one mesh, a single flow I runs through
 * all elements, which is defined as positive in the direction from
 * {@code nA} via {@code nB} towards {@code nC}. Kirchhoff's voltage law along
 * the mesh gives
 * <pre>
 *     uLeft - uMid - I * r1 - uRight = 0
 *     I = (uLeft - uMid - uRight) / r1
 * </pre> and the effort of the floating node follows with nB = uLeft - uMid.
 * The expected values of the tests were calculated by hand with these
 * equations, and the tests with the {@link NodalAnalysis} solver confirm them
 * inside the library before they are used to validate the
 * {@link ThreeEffortsOneResistorCircle} solver.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class ThreeEffortsOneResistorCircleTest {

    private static final double EFFORT_TOLERANCE = 1e-9;
    private static final double FLOW_TOLERANCE = 1e-12;

    // First circuit: uLeft = 9 V, uMid = 2.5 V, uRight = 4 V and r1 = 270
    // Ohm, which results in I = 2.5 V / 270 Ohm.
    private static final double FIRST_LEFT_EFFORT = 9.0;
    private static final double FIRST_MID_EFFORT = 2.5;
    private static final double FIRST_RIGHT_EFFORT = 4.0;
    private static final double FIRST_R1 = 270;
    private static final double FIRST_FLOW = 0.009259259259259;
    private static final double FIRST_EFFORT_B = 6.5;

    // Second circuit with a negative flow: uLeft = 1 V, uMid = 3 V,
    // uRight = 6.4 V and r1 = 150 Ohm, which results in
    // I = -8.4 V / 150 Ohm.
    private static final double SECOND_LEFT_EFFORT = 1.0;
    private static final double SECOND_MID_EFFORT = 3.0;
    private static final double SECOND_RIGHT_EFFORT = 6.4;
    private static final double SECOND_R1 = 150;
    private static final double SECOND_FLOW = -0.056;
    private static final double SECOND_EFFORT_B = -2.0;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public ThreeEffortsOneResistorCircleTest() {
    }

    /**
     * Holds all nodes and elements of the mesh shown in the class
     * description. The node and element lists are in the form a solver
     * receives them from the TransferSubnet.
     */
    private static class CircleNetwork {

        final List<GeneralNode> nodes = new ArrayList<>();
        final List<AbstractElement> elements = new ArrayList<>();

        /**
         * Effort sources between the ground node and a forced node.
         */
        final List<AbstractElement> groundedSources;

        final ClosedOrigin gnd;
        final GeneralNode nGnd, nA, nB, nC;
        final EffortSource uLeft, uMid, uRight;
        final LinearDissipator r1;

        /**
         * Builds the network.
         *
         * @param reversed false connects all elements as the TransferSubnet
         * of the RBMK simulator does, true connects every element the other
         * way round and negates the source values, which results in the same
         * circuit with reversed element directions.
         */
        CircleNetwork(double leftEffort, double midEffort, double rightEffort,
                double resistance1, boolean reversed) {
            double sign = 1.0;
            if (reversed) {
                sign = -1.0;
            }
            gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
            gnd.setName("Zero");
            nGnd = createNode("nGnd");
            gnd.connectTo(nGnd);
            nA = createNode("nA");
            nB = createNode("nB");
            nC = createNode("nC");
            uLeft = createSource("uLeft", sign * leftEffort);
            uMid = createSource("uMid", sign * midEffort);
            uRight = createSource("uRight", sign * rightEffort);
            r1 = createResistor("r1", resistance1);

            // Same element order and orientation as in the subnet dumps of
            // the simulator.
            connect(uMid, nB, nA, reversed);
            connect(uLeft, nGnd, nA, reversed);
            connect(r1, nC, nB, reversed);
            connect(uRight, nGnd, nC, reversed);
            elements.add(gnd);
            groundedSources = List.of(uLeft, uRight);
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
         * efforts match the expected solution and that the mesh flow runs
         * through every element in the direction from nA towards nC.
         */
        void assertSolution(double effortA, double effortB, double effortC,
                double meshFlow) {
            assertAllCalculated();
            assertEquals(nGnd.getEffort(), 0.0, EFFORT_TOLERANCE);
            assertEquals(nA.getEffort(), effortA, EFFORT_TOLERANCE, "nA");
            assertEquals(nB.getEffort(), effortB, EFFORT_TOLERANCE, "nB");
            assertEquals(nC.getEffort(), effortC, EFFORT_TOLERANCE, "nC");
            assertEquals(getFlowFrom(uLeft, nGnd), meshFlow, FLOW_TOLERANCE,
                    "uLeft");
            assertEquals(getFlowFrom(uMid, nA), meshFlow, FLOW_TOLERANCE,
                    "uMid");
            assertEquals(getFlowFrom(r1, nB), meshFlow, FLOW_TOLERANCE, "r1");
            assertEquals(getFlowFrom(uRight, nC), meshFlow, FLOW_TOLERANCE,
                    "uRight");
        }
    }

    private static CircleNetwork createFirstCircuit(boolean reversed) {
        return new CircleNetwork(FIRST_LEFT_EFFORT, FIRST_MID_EFFORT,
                FIRST_RIGHT_EFFORT, FIRST_R1, reversed);
    }

    private static CircleNetwork createSecondCircuit() {
        return new CircleNetwork(SECOND_LEFT_EFFORT, SECOND_MID_EFFORT,
                SECOND_RIGHT_EFFORT, SECOND_R1, false);
    }

    /**
     * Confirms the hand calculated solution of the first circuit with the
     * general {@link NodalAnalysis} solver.
     */
    @Test
    public void testFirstCircuitWithNodalAnalysis() {
        CircleNetwork network = createFirstCircuit(false);
        network.solveWithNodalAnalysis();
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_EFFORT_B,
                FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Confirms the hand calculated solution of the second circuit, in which
     * the flow runs against the defined direction, with the general
     * {@link NodalAnalysis} solver.
     */
    @Test
    public void testSecondCircuitWithNodalAnalysis() {
        CircleNetwork network = createSecondCircuit();
        network.solveWithNodalAnalysis();
        network.assertSolution(SECOND_LEFT_EFFORT, SECOND_EFFORT_B,
                SECOND_RIGHT_EFFORT, SECOND_FLOW);
    }

    /**
     * Confirms with the general {@link NodalAnalysis} solver that the first
     * circuit with reversed element directions has the same solution.
     */
    @Test
    public void testFirstCircuitReversedWithNodalAnalysis() {
        CircleNetwork network = createFirstCircuit(true);
        network.solveWithNodalAnalysis();
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_EFFORT_B,
                FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Creates the dedicated solver for the network, which must accept the
     * circuit.
     */
    private static ThreeEffortsOneResistorCircle createCircleSolver(
            CircleNetwork network) {
        assertTrue(ThreeEffortsOneResistorCircle.isSolveableBySolver(
                network.nodes, network.elements),
                "Solver does not accept circuit");
        try {
            return new ThreeEffortsOneResistorCircle(network.nodes,
                    network.elements);
        } catch (WrongSolverException ex) {
            fail("Solver does not accept circuit");
            return null;
        }
    }

    /**
     * Solves the first circuit with the {@link ThreeEffortsOneResistorCircle}
     * solver.
     */
    @Test
    public void testFirstCircuitWithCircleSolver() {
        CircleNetwork network = createFirstCircuit(false);
        network.solveWithDedicatedSolver(createCircleSolver(network));
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_EFFORT_B,
                FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Solves the second circuit with negative flow with the
     * {@link ThreeEffortsOneResistorCircle} solver.
     */
    @Test
    public void testSecondCircuitWithCircleSolver() {
        CircleNetwork network = createSecondCircuit();
        network.solveWithDedicatedSolver(createCircleSolver(network));
        network.assertSolution(SECOND_LEFT_EFFORT, SECOND_EFFORT_B,
                SECOND_RIGHT_EFFORT, SECOND_FLOW);
    }

    /**
     * Solves the first circuit with reversed element directions with the
     * {@link ThreeEffortsOneResistorCircle} solver.
     */
    @Test
    public void testFirstCircuitReversedWithCircleSolver() {
        CircleNetwork network = createFirstCircuit(true);
        network.solveWithDedicatedSolver(createCircleSolver(network));
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_EFFORT_B,
                FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Runs two calculation cycles on two identical networks with the
     * dedicated solver and with the {@link NodalAnalysis} solver. The second
     * cycle changes all source efforts and opens r1, so that the solver must
     * handle changed values with the topology analysis of the first cycle and
     * must result in zero flow in the whole mesh.
     */
    @Test
    public void testChangingValuesAndOpenResistorAgainstNodalAnalysis() {
        CircleNetwork dedicated = createFirstCircuit(false);
        CircleNetwork reference = createFirstCircuit(false);
        ThreeEffortsOneResistorCircle solver = createCircleSolver(dedicated);

        dedicated.solveWithDedicatedSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);

        for (CircleNetwork network : new CircleNetwork[]{dedicated,
            reference}) {
            network.uLeft.setEffort(5.6);
            network.uMid.setEffort(-0.8);
            network.uRight.setEffort(3.3);
            network.r1.setOpenConnection();
        }
        dedicated.solveWithDedicatedSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);
        // Without flow, nB is uMid below nA.
        dedicated.assertSolution(5.6, 6.4, 3.3, 0.0);
    }

    /**
     * Checks that a bridged resistor, which short-circuits the effort
     * sources, is reported as a model error.
     */
    @Test(expectedExceptions = ModelErrorException.class)
    public void testBridgedResistorThrows() {
        CircleNetwork network = createFirstCircuit(false);
        network.r1.setBridgedConnection();
        network.solveWithDedicatedSolver(createCircleSolver(network));
    }

    /**
     * Checks that the solver rejects a network which differs from the single
     * mesh by an additional resistor parallel to uMid, so that the
     * TransferSubnet falls back to a general solver.
     */
    @Test
    public void testRejectsAdditionalResistor() {
        CircleNetwork network = createFirstCircuit(false);
        LinearDissipator extra = new LinearDissipator(PhysicalDomain.ELECTRICAL);
        extra.setResistanceParameter(FIRST_R1);
        extra.connectBetween(network.nA, network.nB);
        network.elements.add(extra);
        assertFalse(ThreeEffortsOneResistorCircle.isSolveableBySolver(
                network.nodes, network.elements));
    }

    /**
     * Checks that this solver and the solver for two resistors do not accept
     * the network of each other, because both are selected by the
     * TransferSubnet.
     */
    @Test
    public void testDoesNotOverlapWithTwoResistorsCircle() {
        CircleNetwork network = createFirstCircuit(false);
        assertFalse(ThreeEffortsTwoResistorsCircle.isSolveableBySolver(
                network.nodes, network.elements));
    }

    /**
     * Compares all node efforts and the flows of all elements of two
     * identical networks that were solved with different solvers.
     */
    private static void assertSameSolution(CircleNetwork actual,
            CircleNetwork expected) {
        actual.assertSolution(expected.nA.getEffort(),
                expected.nB.getEffort(), expected.nC.getEffort(),
                CircleNetwork.getFlowFrom(expected.r1, expected.nB));
    }
}
