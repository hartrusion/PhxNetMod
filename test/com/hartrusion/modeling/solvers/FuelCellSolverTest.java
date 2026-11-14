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
import com.hartrusion.modeling.exceptions.WrongSolverException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
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
 * Tests the solution of the fuel cell network, which is the thermal model of
 * the fuel elements in the RBMK simulator expressed as an electrical circuit.
 * <p>
 * In the thermal model, the effort source {@code uWater} represents the water
 * temperature and {@code rWater} the thermal resistance of the heat transfer
 * to the water. Each cell represents one fuel element, whereby its effort
 * source {@code uFuel} is the temperature of the heat capacity and its flow
 * source {@code iHeat} the heat from the nuclear fission. Several fuel
 * elements are assigned to one evaporator, which is why many cells share the
 * same bus node. The tests use values of electrical engineering instead
 * (voltages up to 10 V, currents of some milliamperes and resistances between
 * 150 and 470 Ohm), so that the circuit can be read like an exercise.
 * <pre>
 *
 *                              nBus
 *     ---------o----------------o--------------------- ... ------o
 *              |                |                                |
 *              X rWater         X rOuter[0]                      X rOuter[n-1]
 *              X                X                                X
 *              |                |   rInner[0]                    |
 *       nWater o     nCell[0]   o----XXXXXX----o nFuel[0]        o ...
 *              |                |              |                 |
 *      uWater (|)     iHeat[0] (-)            (|) uFuel[0]      ...
 *              |                |              |                 |
 *     ---------o----------------o--------------o--------- ... ---o
 *              | nGnd
 *             _|_ gnd
 *
 * </pre> The network is described by a parameter table with one row per
 * cell, which holds the effort of {@code uFuel}, the flow of {@code iHeat}
 * and the resistances of {@code rInner} and {@code rOuter}. The elements are
 * connected with the same node orientation as the TransferSubnet of the RBMK
 * simulator generates them.
 * <p>
 * The expected values were computed independently with a nodal analysis
 * matrix in numpy (probe script outside of this repository), and the tests
 * with the {@link NodalAnalysis} solver confirm them inside the library
 * before they are used to validate the {@link FuelCellSolver}.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class FuelCellSolverTest {

    private static final double EFFORT_TOLERANCE = 1e-6;
    private static final double FLOW_TOLERANCE = 1e-9;

    // Column indices of the cell parameter tables.
    private static final int COLUMN_FUEL_EFFORT = 0;
    private static final int COLUMN_HEAT_FLOW = 1;
    private static final int COLUMN_INNER_RESISTANCE = 2;
    private static final int COLUMN_OUTER_RESISTANCE = 3;

    /**
     * Eight cells, as they occur 40 times in one run of the RBMK simulator.
     * Columns: uFuel / V, iHeat / A, rInner / Ohm, rOuter / Ohm.
     */
    private static final double[][] EIGHT_CELLS = {
        {6.0, 0.012, 330, 220},
        {7.5, 0.015, 470, 270},
        {5.2, 0.008, 390, 180},
        {8.0, 0.020, 270, 330},
        {4.7, 0.005, 220, 150},
        {9.1, 0.018, 470, 390},
        {6.8, 0.010, 330, 270},
        {5.6, 0.014, 390, 220}};
    private static final double EIGHT_CELLS_WATER_EFFORT = 2.0;
    private static final double EIGHT_CELLS_WATER_RESISTANCE = 100;

    // Expected solution of the eight cell network.
    private static final double EIGHT_CELLS_BUS_EFFORT = 6.975810542;
    // Flow through rWater from nWater towards nBus.
    private static final double EIGHT_CELLS_WATER_FLOW = -0.049758105421;
    private static final double[] EIGHT_CELLS_CELL_EFFORTS = {
        8.169486325, 9.739366155, 7.400291424, 10.509114744,
        6.499130593, 11.775617389, 8.381695798, 8.448796904};
    // Flows through rInner from nFuel towards nCell.
    private static final double[] EIGHT_CELLS_INNER_FLOWS = {
        -0.006574200986, -0.004764608841, -0.005641772881, -0.009293017570,
        -0.008177866330, -0.005692802956, -0.004793017570, -0.007304607446};
    // Flows through rOuter from nCell towards nBus.
    private static final double[] EIGHT_CELLS_OUTER_FLOWS = {
        0.005425799014, 0.010235391159, 0.002358227119, 0.010706982430,
        -0.003177866330, 0.012307197044, 0.005206982430, 0.006695392554};

    /**
     * Seven cells, as they occur 8 times in one run of the RBMK simulator.
     * Columns: uFuel / V, iHeat / A, rInner / Ohm, rOuter / Ohm.
     */
    private static final double[][] SEVEN_CELLS = {
        {5.0, 0.010, 270, 180},
        {6.2, 0.016, 330, 220},
        {7.3, 0.009, 470, 330},
        {4.4, 0.012, 220, 150},
        {8.2, 0.019, 390, 270},
        {5.9, 0.007, 330, 390},
        {6.6, 0.013, 270, 220}};
    private static final double SEVEN_CELLS_WATER_EFFORT = 1.5;
    private static final double SEVEN_CELLS_WATER_RESISTANCE = 150;

    // Expected solution of the seven cell network.
    private static final double SEVEN_CELLS_BUS_EFFORT = 6.993771901;
    private static final double SEVEN_CELLS_WATER_FLOW = -0.036625146005;
    private static final double[] SEVEN_CELLS_CELL_EFFORTS = {
        7.276263140, 8.788263140, 8.864965992, 7.012513022,
        10.518592487, 7.652562121, 8.392894721};
    private static final double[] SEVEN_CELLS_INNER_FLOWS = {
        -0.008430604224, -0.007843221638, -0.003329714876, -0.011875059191,
        -0.005945108941, -0.005310794307, -0.006640350818};
    private static final double[] SEVEN_CELLS_OUTER_FLOWS = {
        0.001569395776, 0.008156778362, 0.005670285124, 0.000124940809,
        0.013054891059, 0.001689205693, 0.006359649182};

    @BeforeClass
    public static void setUpClass() throws Exception {
        // Keep Log out clean during test run
        SimpleLogOut.configureLoggingWarningsOnly();
    }

    public FuelCellSolverTest() {
    }

    /**
     * Holds all nodes and elements of one fuel cell network, which is built
     * from a parameter table as shown in the class description. The node and
     * element lists are in the form a solver receives them from the
     * TransferSubnet.
     */
    private static class FuelCellNetwork {

        final List<GeneralNode> nodes = new ArrayList<>();
        final List<AbstractElement> elements = new ArrayList<>();

        final ClosedOrigin gnd;
        final GeneralNode nGnd, nWater, nBus;
        final EffortSource uWater;
        final LinearDissipator rWater;

        final EffortSource[] uFuel;
        final FlowSource[] iHeat;
        final LinearDissipator[] rInner, rOuter;
        final GeneralNode[] nFuel, nCell;

        /**
         * Builds the network.
         *
         * @param waterEffort Effort of uWater.
         * @param waterResistance Resistance of rWater.
         * @param cells Parameter table with one row per cell.
         * @param reversed false connects all elements as the TransferSubnet
         * of the RBMK simulator does, true connects every element the other
         * way round and negates the source values, which results in the same
         * circuit with reversed element directions.
         */
        FuelCellNetwork(double waterEffort, double waterResistance,
                double[][] cells, boolean reversed) {
            double sign = 1.0;
            if (reversed) {
                sign = -1.0;
            }

            gnd = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
            gnd.setName("Zero");
            nGnd = createNode("nGnd");
            gnd.connectTo(nGnd);
            elements.add(gnd);

            // Water side: uWater forces nWater, rWater leads to the bus.
            nWater = createNode("nWater");
            nBus = createNode("nBus");
            uWater = new EffortSource(PhysicalDomain.ELECTRICAL);
            uWater.setName("uWater");
            uWater.setEffort(sign * waterEffort);
            connect(uWater, nGnd, nWater, reversed);
            rWater = new LinearDissipator(PhysicalDomain.ELECTRICAL);
            rWater.setName("rWater");
            rWater.setResistanceParameter(waterResistance);
            connect(rWater, nWater, nBus, reversed);

            uFuel = new EffortSource[cells.length];
            iHeat = new FlowSource[cells.length];
            rInner = new LinearDissipator[cells.length];
            rOuter = new LinearDissipator[cells.length];
            nFuel = new GeneralNode[cells.length];
            nCell = new GeneralNode[cells.length];
            for (int k = 0; k < cells.length; k++) {
                nFuel[k] = createNode("nFuel" + k);
                nCell[k] = createNode("nCell" + k);

                uFuel[k] = new EffortSource(PhysicalDomain.ELECTRICAL);
                uFuel[k].setName("uFuel" + k);
                uFuel[k].setEffort(sign * cells[k][COLUMN_FUEL_EFFORT]);
                iHeat[k] = new FlowSource(PhysicalDomain.ELECTRICAL);
                iHeat[k].setName("iHeat" + k);
                iHeat[k].setFlow(sign * cells[k][COLUMN_HEAT_FLOW]);
                rInner[k] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
                rInner[k].setName("rInner" + k);
                rInner[k].setResistanceParameter(
                        cells[k][COLUMN_INNER_RESISTANCE]);
                rOuter[k] = new LinearDissipator(PhysicalDomain.ELECTRICAL);
                rOuter[k].setName("rOuter" + k);
                rOuter[k].setResistanceParameter(
                        cells[k][COLUMN_OUTER_RESISTANCE]);

                // Same orientation as in the subnet dumps of the simulator.
                connect(rOuter[k], nCell[k], nBus, reversed);
                connect(iHeat[k], nGnd, nCell[k], reversed);
                connect(uFuel[k], nGnd, nFuel[k], reversed);
                connect(rInner[k], nFuel[k], nCell[k], reversed);
            }
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
        FuelCellSolver createFuelCellSolver() {
            assertTrue(FuelCellSolver.isSolveableBySolver(nodes, elements),
                    "FuelCellSolver does not accept circuit");
            try {
                return new FuelCellSolver(nodes, elements);
            } catch (WrongSolverException ex) {
                fail("FuelCellSolver does not accept circuit");
                return null;
            }
        }

        /**
         * Runs one calculation cycle with the dedicated solver.
         */
        void solveWithFuelCellSolver(FuelCellSolver solver) {
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
         * Checks that every element is fully calculated and compares the
         * efforts of the bus and the cell nodes as well as the flows of all
         * resistors and effort sources with the expected solution.
         */
        void assertSolution(double busEffort, double waterFlow,
                double[] cellEfforts, double[] innerFlows,
                double[] outerFlows) {
            for (AbstractElement element : elements) {
                assertTrue(element.isCalculationFinished(),
                        "Not calculated: " + element);
            }
            assertEquals(nGnd.getEffort(), 0.0, EFFORT_TOLERANCE);
            assertEquals(nBus.getEffort(), busEffort, EFFORT_TOLERANCE);
            assertEquals(getFlowFrom(rWater, nWater), waterFlow,
                    FLOW_TOLERANCE);
            // The flow through uWater from ground leaves nWater via rWater.
            assertEquals(getFlowFrom(uWater, nGnd), waterFlow, FLOW_TOLERANCE);
            for (int k = 0; k < nCell.length; k++) {
                assertEquals(nCell[k].getEffort(), cellEfforts[k],
                        EFFORT_TOLERANCE, "nCell" + k);
                assertEquals(getFlowFrom(rInner[k], nFuel[k]), innerFlows[k],
                        FLOW_TOLERANCE, "rInner" + k);
                assertEquals(getFlowFrom(rOuter[k], nCell[k]), outerFlows[k],
                        FLOW_TOLERANCE, "rOuter" + k);
                assertEquals(getFlowFrom(uFuel[k], nGnd), innerFlows[k],
                        FLOW_TOLERANCE, "uFuel" + k);
            }
        }
    }

    /**
     * Confirms the expected solution of the eight cell network with the
     * general {@link NodalAnalysis} solver.
     */
    @Test
    public void testEightCellsWithNodalAnalysis() {
        FuelCellNetwork network = new FuelCellNetwork(EIGHT_CELLS_WATER_EFFORT,
                EIGHT_CELLS_WATER_RESISTANCE, EIGHT_CELLS, false);
        network.solveWithNodalAnalysis();
        network.assertSolution(EIGHT_CELLS_BUS_EFFORT, EIGHT_CELLS_WATER_FLOW,
                EIGHT_CELLS_CELL_EFFORTS, EIGHT_CELLS_INNER_FLOWS,
                EIGHT_CELLS_OUTER_FLOWS);
    }

    /**
     * Confirms the expected solution of the seven cell network with the
     * general {@link NodalAnalysis} solver.
     */
    @Test
    public void testSevenCellsWithNodalAnalysis() {
        FuelCellNetwork network = new FuelCellNetwork(SEVEN_CELLS_WATER_EFFORT,
                SEVEN_CELLS_WATER_RESISTANCE, SEVEN_CELLS, false);
        network.solveWithNodalAnalysis();
        network.assertSolution(SEVEN_CELLS_BUS_EFFORT, SEVEN_CELLS_WATER_FLOW,
                SEVEN_CELLS_CELL_EFFORTS, SEVEN_CELLS_INNER_FLOWS,
                SEVEN_CELLS_OUTER_FLOWS);
    }

    /**
     * Confirms that the seven cell network with all elements connected the
     * other way round and negated source values is the same circuit, which
     * makes the expected values of the seven cell network also valid for the
     * reversed test of the dedicated solver.
     */
    @Test
    public void testSevenCellsReversedWithNodalAnalysis() {
        FuelCellNetwork network = new FuelCellNetwork(SEVEN_CELLS_WATER_EFFORT,
                SEVEN_CELLS_WATER_RESISTANCE, SEVEN_CELLS, true);
        network.solveWithNodalAnalysis();
        network.assertSolution(SEVEN_CELLS_BUS_EFFORT, SEVEN_CELLS_WATER_FLOW,
                SEVEN_CELLS_CELL_EFFORTS, SEVEN_CELLS_INNER_FLOWS,
                SEVEN_CELLS_OUTER_FLOWS);
    }

    /**
     * Solves the eight cell network with the {@link FuelCellSolver}.
     */
    @Test
    public void testEightCellsWithFuelCellSolver() {
        FuelCellNetwork network = new FuelCellNetwork(EIGHT_CELLS_WATER_EFFORT,
                EIGHT_CELLS_WATER_RESISTANCE, EIGHT_CELLS, false);
        network.solveWithFuelCellSolver(network.createFuelCellSolver());
        network.assertSolution(EIGHT_CELLS_BUS_EFFORT, EIGHT_CELLS_WATER_FLOW,
                EIGHT_CELLS_CELL_EFFORTS, EIGHT_CELLS_INNER_FLOWS,
                EIGHT_CELLS_OUTER_FLOWS);
    }

    /**
     * Solves the seven cell network with the {@link FuelCellSolver}.
     */
    @Test
    public void testSevenCellsWithFuelCellSolver() {
        FuelCellNetwork network = new FuelCellNetwork(SEVEN_CELLS_WATER_EFFORT,
                SEVEN_CELLS_WATER_RESISTANCE, SEVEN_CELLS, false);
        network.solveWithFuelCellSolver(network.createFuelCellSolver());
        network.assertSolution(SEVEN_CELLS_BUS_EFFORT, SEVEN_CELLS_WATER_FLOW,
                SEVEN_CELLS_CELL_EFFORTS, SEVEN_CELLS_INNER_FLOWS,
                SEVEN_CELLS_OUTER_FLOWS);
    }

    /**
     * Solves the seven cell network with every element connected the other
     * way round, which must give the same solution because the solver has to
     * handle any element direction.
     */
    @Test
    public void testSevenCellsReversedWithFuelCellSolver() {
        FuelCellNetwork network = new FuelCellNetwork(SEVEN_CELLS_WATER_EFFORT,
                SEVEN_CELLS_WATER_RESISTANCE, SEVEN_CELLS, true);
        network.solveWithFuelCellSolver(network.createFuelCellSolver());
        network.assertSolution(SEVEN_CELLS_BUS_EFFORT, SEVEN_CELLS_WATER_FLOW,
                SEVEN_CELLS_CELL_EFFORTS, SEVEN_CELLS_INNER_FLOWS,
                SEVEN_CELLS_OUTER_FLOWS);
    }

    /**
     * Compares the {@link FuelCellSolver} with the {@link NodalAnalysis} on
     * two identical eight cell networks over two calculation cycles. Between
     * the cycles, the heat flows and the fuel temperatures change as in a
     * running simulation, and the outer resistor of the third cell becomes an
     * open connection, so that its cell is separated from the bus and its heat
     * can only flow back into the fuel source.
     */
    @Test
    public void testChangingValuesAndOpenResistorAgainstNodalAnalysis() {
        FuelCellNetwork dedicated = new FuelCellNetwork(
                EIGHT_CELLS_WATER_EFFORT, EIGHT_CELLS_WATER_RESISTANCE,
                EIGHT_CELLS, false);
        FuelCellNetwork reference = new FuelCellNetwork(
                EIGHT_CELLS_WATER_EFFORT, EIGHT_CELLS_WATER_RESISTANCE,
                EIGHT_CELLS, false);
        FuelCellSolver solver = dedicated.createFuelCellSolver();

        // First cycle with the original values.
        dedicated.solveWithFuelCellSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);

        // Second cycle with changed values on both networks.
        for (FuelCellNetwork network : new FuelCellNetwork[]{dedicated,
            reference}) {
            for (int k = 0; k < network.iHeat.length; k++) {
                network.iHeat[k].setFlow(0.004 + 0.002 * k);
                network.uFuel[k].setEffort(3.0 + 0.5 * k);
            }
            network.rOuter[2].setOpenConnection();
        }
        dedicated.solveWithFuelCellSolver(solver);
        reference.solveWithNodalAnalysis();
        assertSameSolution(dedicated, reference);
        // No flow through the open resistor, all heat of cell 2 flows back.
        assertEquals(FuelCellNetwork.getFlowFrom(dedicated.rOuter[2],
                dedicated.nCell[2]), 0.0, FLOW_TOLERANCE);
        assertEquals(FuelCellNetwork.getFlowFrom(dedicated.rInner[2],
                dedicated.nFuel[2]), -0.008, FLOW_TOLERANCE);
    }

    /**
     * Compares bus and cell node efforts as well as all resistor flows of two
     * identical networks that were solved with different solvers.
     */
    private static void assertSameSolution(FuelCellNetwork actual,
            FuelCellNetwork expected) {
        assertEquals(actual.nBus.getEffort(), expected.nBus.getEffort(),
                EFFORT_TOLERANCE);
        assertEquals(actual.rWater.getFlow(), expected.rWater.getFlow(),
                FLOW_TOLERANCE);
        for (int k = 0; k < actual.nCell.length; k++) {
            assertEquals(actual.nCell[k].getEffort(),
                    expected.nCell[k].getEffort(), EFFORT_TOLERANCE,
                    "nCell" + k);
            assertEquals(actual.rInner[k].getFlow(),
                    expected.rInner[k].getFlow(), FLOW_TOLERANCE,
                    "rInner" + k);
            assertEquals(actual.rOuter[k].getFlow(),
                    expected.rOuter[k].getFlow(), FLOW_TOLERANCE,
                    "rOuter" + k);
            assertTrue(actual.uFuel[k].isCalculationFinished());
        }
    }

    /**
     * Checks that the {@link FuelCellSolver} rejects a network which differs
     * from the fuel cell constellation by an additional resistor between two
     * cell nodes, so that the TransferSubnet falls back to a general solver.
     */
    @Test
    public void testRejectsCrossConnectedCells() {
        FuelCellNetwork network = new FuelCellNetwork(SEVEN_CELLS_WATER_EFFORT,
                SEVEN_CELLS_WATER_RESISTANCE, SEVEN_CELLS, false);
        LinearDissipator rCross = new LinearDissipator(
                PhysicalDomain.ELECTRICAL);
        rCross.setName("rCross");
        rCross.setResistanceParameter(100);
        rCross.connectTo(network.nCell[0]);
        rCross.connectTo(network.nCell[1]);
        network.elements.add(rCross);
        assertFalse(FuelCellSolver.isSolveableBySolver(network.nodes,
                network.elements));
    }
}
