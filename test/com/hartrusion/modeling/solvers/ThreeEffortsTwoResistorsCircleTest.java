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
 * and two resistors, as the TransferSubnet of the RBMK simulator generates it
 * four times in one run.
 * <pre>
 *
 *        nA      r1      nB    uMid    nC      r2      nD
 *         o----XXXXXX----o----(-|)----o----XXXXXX----o
 *         |                                          |
 *  uLeft (|)                                        (|) uRight
 *         |                                          |
 *         ---------------------o----------------------
 *                              | nGnd
 *                             _|_ gnd
 *
 * </pre> The grounded effort sources {@code uLeft} and {@code uRight} force
 * the outer nodes {@code nA} and {@code nD}, whereas the effort source
 * {@code uMid} floats between {@code nB} and {@code nC} with its positive
 * side at {@code nB}. As there is only one mesh, a single flow I runs through
 * all elements, which is defined as positive in the direction from
 * {@code nA} via {@code nB} and {@code nC} towards {@code nD}. Kirchhoff's
 * voltage law along the mesh gives
 * <pre>
 *     uLeft - I * r1 - uMid - I * r2 - uRight = 0
 *     I = (uLeft - uMid - uRight) / (r1 + r2)
 * </pre> and the node efforts follow with nB = uLeft - I * r1 and
 * nC = nB - uMid. The expected values of the tests were calculated by hand
 * with these equations, and the tests with the {@link NodalAnalysis} solver
 * confirm them inside the library before they are used to validate the
 * {@link ThreeEffortsTwoResistorsCircle} solver.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class ThreeEffortsTwoResistorsCircleTest {

    private static final double EFFORT_TOLERANCE = 1e-9;
    private static final double FLOW_TOLERANCE = 1e-12;

    // First circuit: uLeft = 8 V, uMid = 3 V, uRight = 2 V, r1 = 220 Ohm and
    // r2 = 330 Ohm, which results in I = 3 V / 550 Ohm.
    private static final double FIRST_LEFT_EFFORT = 8.0;
    private static final double FIRST_MID_EFFORT = 3.0;
    private static final double FIRST_RIGHT_EFFORT = 2.0;
    private static final double FIRST_R1 = 220;
    private static final double FIRST_R2 = 330;
    private static final double FIRST_FLOW = 0.005454545454545;
    private static final double FIRST_EFFORT_B = 6.8;
    private static final double FIRST_EFFORT_C = 3.8;

    // Second circuit with a negative flow: uLeft = 1.5 V, uMid = 2.5 V,
    // uRight = 9 V, r1 = 470 Ohm and r2 = 150 Ohm, which results in
    // I = -10 V / 620 Ohm.
    private static final double SECOND_LEFT_EFFORT = 1.5;
    private static final double SECOND_MID_EFFORT = 2.5;
    private static final double SECOND_RIGHT_EFFORT = 9.0;
    private static final double SECOND_R1 = 470;
    private static final double SECOND_R2 = 150;
    private static final double SECOND_FLOW = -0.016129032258065;
    private static final double SECOND_EFFORT_B = 9.080645161290;
    private static final double SECOND_EFFORT_C = 6.580645161290;

    // First circuit with bridged r1, which results in I = 3 V / 330 Ohm and
    // nB = nA.
    private static final double BRIDGED_FLOW = 0.009090909090909;
    private static final double BRIDGED_EFFORT_C = 5.0;

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public ThreeEffortsTwoResistorsCircleTest() {
    }

    /**
     * Holds all nodes and elements of the mesh shown in the class
     * description. The node and element lists are in the form a solver
     * receives them from the TransferSubnet.
     */
    private static class CircleNetwork {

        final List<GeneralNode> nodes = new ArrayList<>();
        final List<AbstractElement> elements = new ArrayList<>();

        final ClosedOrigin gnd;
        final GeneralNode nGnd, nA, nB, nC, nD;
        final EffortSource uLeft, uMid, uRight;
        final LinearDissipator r1, r2;

        /**
         * Builds the network.
         *
         * @param reversed false connects all elements as the TransferSubnet
         * of the RBMK simulator does, true connects every element the other
         * way round and negates the source values, which results in the same
         * circuit with reversed element directions.
         */
        CircleNetwork(double leftEffort, double midEffort, double rightEffort,
                double resistance1, double resistance2, boolean reversed) {
            double sign = 1.0;
            if (reversed) {
                sign = -1.0;
            }
            gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
            gnd.setName("Zero");
            nGnd = createNode("nGnd");
            gnd.connectTo(nGnd);
            elements.add(gnd);
            nA = createNode("nA");
            nB = createNode("nB");
            nC = createNode("nC");
            nD = createNode("nD");

            uLeft = new EffortSource(PhysicalDomain.ELECTRICAL);
            uLeft.setName("uLeft");
            uLeft.setEffort(sign * leftEffort);
            uMid = new EffortSource(PhysicalDomain.ELECTRICAL);
            uMid.setName("uMid");
            uMid.setEffort(sign * midEffort);
            uRight = new EffortSource(PhysicalDomain.ELECTRICAL);
            uRight.setName("uRight");
            uRight.setEffort(sign * rightEffort);
            r1 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r1.setName("r1");
            r1.setResistanceParameter(resistance1);
            r2 = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            r2.setName("r2");
            r2.setResistanceParameter(resistance2);

            // Same element order and orientation as in the subnet dumps of
            // the simulator.
            connect(r1, nB, nA, reversed);
            connect(uLeft, nGnd, nA, reversed);
            connect(uMid, nC, nB, reversed);
            connect(r2, nD, nC, reversed);
            connect(uRight, nGnd, nD, reversed);
        }

        private GeneralNode createNode(String name) {
            GeneralNode node = new GeneralNode(PhysicalDomain.ELECTRICAL);
            node.setName(name);
            nodes.add(node);
            return node;
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
         * provides the reference for the dedicated solver.
         */
        void solveWithNodalAnalysis() {
            assertTrue(NodalAnalysis.isSolvableByNodalAnalysis(nodes,
                    elements), "Nodal analysis does not accept circuit");
            NodalAnalysis solver = new NodalAnalysis();
            for (GeneralNode node : nodes) {
                solver.registerNode(node);
            }
            for (AbstractElement element : elements) {
                solver.registerElement(element);
            }
            solver.nodalAnalysisSetup();
            solver.prepareCalculation();
            solver.doCalculation();
        }

        /**
         * Creates the dedicated solver for the network, which must accept the
         * circuit.
         */
        ThreeEffortsTwoResistorsCircle createCircleSolver() {
            assertTrue(ThreeEffortsTwoResistorsCircle.isSolveableBySolver(
                    nodes, elements), "Solver does not accept circuit");
            try {
                return new ThreeEffortsTwoResistorsCircle(nodes, elements);
            } catch (WrongSolverException ex) {
                fail("Solver does not accept circuit");
                return null;
            }
        }

        /**
         * Runs one calculation cycle with the dedicated solver, which
         * includes resetting all elements as the TransferSubnet does.
         */
        void solveWithCircleSolver(ThreeEffortsTwoResistorsCircle solver) {
            for (AbstractElement element : elements) {
                element.prepareCalculation();
            }
            solver.solveNetwork();
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
         * through every element in the direction from nA towards nD.
         */
        void assertSolution(double effortA, double effortB, double effortC,
                double effortD, double meshFlow) {
            for (AbstractElement element : elements) {
                assertTrue(element.isCalculationFinished(),
                        "Not calculated: " + element);
            }
            assertEquals(nGnd.getEffort(), 0.0, EFFORT_TOLERANCE);
            assertEquals(nA.getEffort(), effortA, EFFORT_TOLERANCE, "nA");
            assertEquals(nB.getEffort(), effortB, EFFORT_TOLERANCE, "nB");
            assertEquals(nC.getEffort(), effortC, EFFORT_TOLERANCE, "nC");
            assertEquals(nD.getEffort(), effortD, EFFORT_TOLERANCE, "nD");
            assertEquals(getFlowFrom(uLeft, nGnd), meshFlow, FLOW_TOLERANCE,
                    "uLeft");
            assertEquals(getFlowFrom(r1, nA), meshFlow, FLOW_TOLERANCE, "r1");
            assertEquals(getFlowFrom(uMid, nB), meshFlow, FLOW_TOLERANCE,
                    "uMid");
            assertEquals(getFlowFrom(r2, nC), meshFlow, FLOW_TOLERANCE, "r2");
            assertEquals(getFlowFrom(uRight, nD), meshFlow, FLOW_TOLERANCE,
                    "uRight");
        }
    }

    private static CircleNetwork createFirstCircuit(boolean reversed) {
        return new CircleNetwork(FIRST_LEFT_EFFORT, FIRST_MID_EFFORT,
                FIRST_RIGHT_EFFORT, FIRST_R1, FIRST_R2, reversed);
    }

    private static CircleNetwork createSecondCircuit() {
        return new CircleNetwork(SECOND_LEFT_EFFORT, SECOND_MID_EFFORT,
                SECOND_RIGHT_EFFORT, SECOND_R1, SECOND_R2, false);
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
                FIRST_EFFORT_C, FIRST_RIGHT_EFFORT, FIRST_FLOW);
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
                SECOND_EFFORT_C, SECOND_RIGHT_EFFORT, SECOND_FLOW);
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
                FIRST_EFFORT_C, FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Solves the first circuit with the {@link ThreeEffortsTwoResistorsCircle}
     * solver.
     */
    @Test
    public void testFirstCircuitWithCircleSolver() {
        CircleNetwork network = createFirstCircuit(false);
        network.solveWithCircleSolver(network.createCircleSolver());
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_EFFORT_B,
                FIRST_EFFORT_C, FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Solves the second circuit with negative flow with the
     * {@link ThreeEffortsTwoResistorsCircle} solver.
     */
    @Test
    public void testSecondCircuitWithCircleSolver() {
        CircleNetwork network = createSecondCircuit();
        network.solveWithCircleSolver(network.createCircleSolver());
        network.assertSolution(SECOND_LEFT_EFFORT, SECOND_EFFORT_B,
                SECOND_EFFORT_C, SECOND_RIGHT_EFFORT, SECOND_FLOW);
    }

    /**
     * Solves the first circuit with reversed element directions with the
     * {@link ThreeEffortsTwoResistorsCircle} solver.
     */
    @Test
    public void testFirstCircuitReversedWithCircleSolver() {
        CircleNetwork network = createFirstCircuit(true);
        network.solveWithCircleSolver(network.createCircleSolver());
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_EFFORT_B,
                FIRST_EFFORT_C, FIRST_RIGHT_EFFORT, FIRST_FLOW);
    }

    /**
     * Runs two calculation cycles on two identical networks with the
     * dedicated solver and with the {@link NodalAnalysis} solver. The second
     * cycle changes all source efforts and opens r2, so that the solver must
     * handle changed values with the topology analysis of the first cycle and
     * must result in zero flow in the whole mesh.
     */
    @Test
    public void testChangingValuesAndOpenResistorAgainstNodalAnalysis() {
        CircleNetwork dedicated = createFirstCircuit(false);
        CircleNetwork reference = createFirstCircuit(false);
        ThreeEffortsTwoResistorsCircle solver = dedicated.createCircleSolver();

        dedicated.solveWithCircleSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);

        for (CircleNetwork network : new CircleNetwork[]{dedicated,
            reference}) {
            network.uLeft.setEffort(4.7);
            network.uMid.setEffort(-1.2);
            network.uRight.setEffort(6.8);
            network.r2.setOpenConnection();
        }
        dedicated.solveWithCircleSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);
        // Without flow, nB has the effort of nA and nC the effort of nD.
        dedicated.assertSolution(4.7, 4.7, 5.9, 6.8, 0.0);
    }

    /**
     * Solves the first circuit with bridged r1, for which the flow is only
     * limited by r2. The expected values follow from the equations of the
     * class description with r1 = 0.
     */
    @Test
    public void testBridgedResistorWithCircleSolver() {
        CircleNetwork network = createFirstCircuit(false);
        network.r1.setBridgedConnection();
        network.solveWithCircleSolver(network.createCircleSolver());
        network.assertSolution(FIRST_LEFT_EFFORT, FIRST_LEFT_EFFORT,
                BRIDGED_EFFORT_C, FIRST_RIGHT_EFFORT, BRIDGED_FLOW);
    }

    /**
     * Solves the first circuit with both resistors open. There is no flow,
     * and the floating nodes nB and nC get the effort of nA in front of the
     * open resistor r1 respectively the effort difference of uMid.
     */
    @Test
    public void testBothResistorsOpenWithCircleSolver() {
        CircleNetwork network = createFirstCircuit(false);
        network.r1.setOpenConnection();
        network.r2.setOpenConnection();
        network.solveWithCircleSolver(network.createCircleSolver());
        // The effort of nB comes from one of both outer nodes, depending on
        // the direction of the walk along the mesh, which is why only the
        // effort difference over uMid and the zero flow are checked.
        for (AbstractElement element : network.elements) {
            assertTrue(element.isCalculationFinished(),
                    "Not calculated: " + element);
        }
        assertEquals(network.nB.getEffort() - network.nC.getEffort(),
                FIRST_MID_EFFORT, EFFORT_TOLERANCE);
        assertEquals(network.uMid.getFlow(), 0.0, FLOW_TOLERANCE);
        assertEquals(network.uLeft.getFlow(), 0.0, FLOW_TOLERANCE);
    }

    /**
     * Checks that two bridged resistors, which short-circuit the effort
     * sources, are reported as a model error.
     */
    @Test(expectedExceptions = ModelErrorException.class)
    public void testBothResistorsBridgedThrows() {
        CircleNetwork network = createFirstCircuit(false);
        network.r1.setBridgedConnection();
        network.r2.setBridgedConnection();
        network.solveWithCircleSolver(network.createCircleSolver());
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
        extra.connectBetween(network.nB, network.nC);
        network.elements.add(extra);
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
                expected.nD.getEffort(),
                CircleNetwork.getFlowFrom(expected.r1, expected.nA));
    }
}
