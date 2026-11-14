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

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.exceptions.WrongSolverException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import java.util.ArrayList;
import java.util.List;

/**
 * Solves the network of several fuel cells that share one bus node, which is
 * the thermal model of the fuel elements in the RBMK simulator expressed as an
 * electrical circuit. The {@link TransferSubnet} generates this network for
 * every evaporator, because several fuel elements are assigned to one
 * evaporator, and solving it with the {@link NodalAnalysis} in its own thread
 * is far more expensive than the closed solution of this class.
 * <pre>
 *
 *                              nBus
 *     ---------o----------------o--------------------- ... ------o
 *              |                |                                |
 *              X rSimple        X rOuter[0]                      X rOuter[n-1]
 *              X                X                                X
 *              |                |   rInner[0]                    |
 *     nSimple  o     nCell[0]   o----XXXXXX----o nFuel[0]        o ...
 *              |                |              |                 |
 *     uSimple (|)     iHeat[0] (-)            (|) uFuel[0]      ...
 *              |                |              |                 |
 *     ---------o----------------o--------------o--------- ... ---o
 *              | nGnd
 *             _|_ gnd
 *
 * </pre> Each cell consists of an effort source {@code uFuel} (temperature of
 * the heat capacity of the fuel), a resistor {@code rInner} to the cell node, a
 * flow source {@code iHeat} (heat from the nuclear fission) into the cell node
 * and a resistor {@code rOuter} from the cell node to the bus. Additionally,
 * the bus can be connected to any number of simple branches, which consist of
 * an effort source and a resistor only, as the water side of the thermal model
 * (temperature of the water and thermal resistance of the heat transfer). The
 * network must contain at least one cell and at least two branches in total,
 * whereas the number of cells has no upper limit. All elements may be
 * connected in any direction.
 * <p>
 * The solution uses the Norton equivalent of each branch as seen from the bus.
 * A cell with the conductances {@code Ga} (outer) and {@code Gb} (inner), the
 * fuel node effort {@code UF} and the heat flow {@code IQ} is equivalent to a
 * conductance {@code Ga*Gb/(Ga+Gb)} with the source flow
 * {@code (UF*Gb + IQ)*Ga/(Ga+Gb)} into the bus, and a simple branch is
 * equivalent to its conductance with the source flow {@code UF*G}. The bus
 * effort follows from Millman's theorem as the sum of all source flows divided
 * by the sum of all conductances, and the effort of each cell node follows from
 * its node equation {@code (IQ + UF*Gb + UB*Ga)/(Ga+Gb)}. The solver only sets
 * these efforts, and all flows are then calculated by the elements themselves
 * through Ohm's law and Kirchhoff's current law of the nodes. This also covers
 * open resistors (conductance zero) and bridged resistors, whose flow results
 * from the current law because Ohm's law can not provide it.
 * <p>
 * The calculation runs in loops over arrays which are created once in the
 * constructor, so that no objects are created during the simulation.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class FuelCellSolver implements DedicatedSolver {

    /**
     * Number of elements on a cell node, which are the flow source and the
     * inner and outer resistor.
     */
    private static final int CELL_NODE_ELEMENTS = 3;

    /**
     * Number of elements on a forced node of a branch, which are the effort
     * source and the first resistor of the branch.
     */
    private static final int FORCED_NODE_ELEMENTS = 2;

    private final ClosedOrigin gnd;
    private final GeneralNode bus;

    // Simple branches: effort source with one resistor towards the bus. The
    // forced node is the node between the effort source and the resistor.
    private final LinearDissipator[] simpleResistors;
    private final GeneralNode[] simpleForcedNodes;

    // Cells: the forced node sits between uFuel and rInner, the cell node
    // between rInner, iHeat and rOuter.
    private final LinearDissipator[] cellInnerResistors;
    private final LinearDissipator[] cellOuterResistors;
    private final FlowSource[] cellFlowSources;
    private final GeneralNode[] cellForcedNodes;
    private final GeneralNode[] cellNodes;
    /**
     * True for each cell whose flow source has the cell node as node 0. A
     * positive flow then flows out of the cell node, so the flow into the
     * cell node is the negated flow value.
     */
    private final boolean[] cellFlowSourceReversed;

    private final List<GeneralNode> nodes;
    private final List<AbstractElement> elements;

    /**
     * Generates a solver for the given network. The network is analyzed and
     * all elements and nodes are assigned to their role in a branch. If the
     * network does not match the fuel cell constellation, an exception is
     * thrown and the solver can not be used.
     * <p>
     * The analysis starts at every effort source, because each branch has
     * exactly one, and follows the branch from the forced node towards the
     * bus. Elements of type NONE without nodes are ignored, because the
     * TransferSubnet keeps them as leftovers of replaced elements.
     *
     * @param nodes Full list of nodes of the network
     * @param elements Full list of elements of the network
     * @throws WrongSolverException if the network does not match.
     */
    public FuelCellSolver(List<GeneralNode> nodes,
            List<AbstractElement> elements) throws WrongSolverException {
        ClosedOrigin origin = null;
        List<EffortSource> effortSources = new ArrayList<>();
        int flowSourceCount = 0;
        int resistorCount = 0;
        int usedElementCount = 0;

        for (AbstractElement e : elements) {
            switch (e.getElementType()) {
                case DISSIPATOR, OPEN, BRIDGED:
                    if (!(e instanceof LinearDissipator)) {
                        throw new WrongSolverException();
                    }
                    resistorCount++;
                    break;
                case EFFORTSOURCE:
                    effortSources.add((EffortSource) e);
                    break;
                case FLOWSOURCE:
                    flowSourceCount++;
                    break;
                case ORIGIN:
                    if (origin != null || !(e instanceof ClosedOrigin)) {
                        throw new WrongSolverException();
                    }
                    origin = (ClosedOrigin) e;
                    break;
                case NONE:
                    if (e.getNumberOfNodes() != 0) {
                        throw new WrongSolverException();
                    }
                    continue; // unconnected leftover, not counted as used
                default:
                    throw new WrongSolverException();
            }
            usedElementCount++;
        }
        if (origin == null || effortSources.isEmpty()) {
            throw new WrongSolverException();
        }
        gnd = origin;
        GeneralNode ground = gnd.getNode(0);
        // The ground node connects the origin and all sources, nothing else.
        if (ground.getNumberOfElements()
                != 1 + effortSources.size() + flowSourceCount) {
            throw new WrongSolverException();
        }

        // Follow each branch from its effort source towards the bus. The
        // results are collected in lists first, because the number of cells
        // and simple branches is only known afterwards.
        List<LinearDissipator> simpleResistorList = new ArrayList<>();
        List<GeneralNode> simpleForcedNodeList = new ArrayList<>();
        List<LinearDissipator> innerResistorList = new ArrayList<>();
        List<LinearDissipator> outerResistorList = new ArrayList<>();
        List<FlowSource> flowSourceList = new ArrayList<>();
        List<GeneralNode> cellForcedNodeList = new ArrayList<>();
        List<GeneralNode> cellNodeList = new ArrayList<>();
        GeneralNode busCandidate = null;
        try {
            for (EffortSource source : effortSources) {
                GeneralNode forcedNode = source.getOnlyOtherNode(ground);
                if (forcedNode == ground || forcedNode.getNumberOfElements()
                        != FORCED_NODE_ELEMENTS) {
                    throw new WrongSolverException();
                }
                AbstractElement firstElement
                        = forcedNode.getOnlyOtherElement(source);
                if (!LinearNetwork.isElementResistorType(firstElement)) {
                    throw new WrongSolverException();
                }
                LinearDissipator firstResistor = (LinearDissipator) firstElement;
                GeneralNode nextNode = firstResistor.getOnlyOtherNode(forcedNode);
                if (nextNode == ground) {
                    throw new WrongSolverException();
                }
                FlowSource heatSource = findCellFlowSource(nextNode, ground);
                GeneralNode branchEnd;
                if (heatSource == null) {
                    // Simple branch, the resistor ends at the bus.
                    simpleResistorList.add(firstResistor);
                    simpleForcedNodeList.add(forcedNode);
                    branchEnd = nextNode;
                } else {
                    // Cell, the third element on the cell node must be the
                    // outer resistor towards the bus.
                    LinearDissipator outerResistor = null;
                    for (int idx = 0; idx < CELL_NODE_ELEMENTS; idx++) {
                        AbstractElement e = nextNode.getElement(idx);
                        if (e != firstResistor && e != heatSource) {
                            if (!LinearNetwork.isElementResistorType(e)) {
                                throw new WrongSolverException();
                            }
                            outerResistor = (LinearDissipator) e;
                        }
                    }
                    if (outerResistor == null) {
                        throw new WrongSolverException();
                    }
                    innerResistorList.add(firstResistor);
                    outerResistorList.add(outerResistor);
                    flowSourceList.add(heatSource);
                    cellForcedNodeList.add(forcedNode);
                    cellNodeList.add(nextNode);
                    branchEnd = outerResistor.getOnlyOtherNode(nextNode);
                }
                // All branches must end at the same bus node.
                if (busCandidate == null) {
                    busCandidate = branchEnd;
                } else if (busCandidate != branchEnd) {
                    throw new WrongSolverException();
                }
            }
        } catch (NoFlowThroughException | ModelErrorException ex) {
            throw new WrongSolverException();
        }

        int simpleCount = simpleResistorList.size();
        int cellCount = cellNodeList.size();
        // At least one cell and two branches, the bus connects only the
        // branches, every flow source belongs to a cell, and there are no
        // further elements than the ones found in the branches.
        if (cellCount < 1 || simpleCount + cellCount < 2
                || busCandidate == ground
                || busCandidate.getNumberOfElements() != simpleCount + cellCount
                || flowSourceCount != cellCount
                || resistorCount != simpleCount + 2 * cellCount
                || usedElementCount != 1 + 2 * simpleCount + 4 * cellCount) {
            throw new WrongSolverException();
        }
        bus = busCandidate;

        // Store everything in plain arrays for the fastest possible solve.
        simpleResistors = simpleResistorList.toArray(LinearDissipator[]::new);
        simpleForcedNodes = simpleForcedNodeList.toArray(GeneralNode[]::new);
        cellInnerResistors = innerResistorList.toArray(LinearDissipator[]::new);
        cellOuterResistors = outerResistorList.toArray(LinearDissipator[]::new);
        cellFlowSources = flowSourceList.toArray(FlowSource[]::new);
        cellForcedNodes = cellForcedNodeList.toArray(GeneralNode[]::new);
        cellNodes = cellNodeList.toArray(GeneralNode[]::new);
        cellFlowSourceReversed = new boolean[cellCount];
        for (int k = 0; k < cellCount; k++) {
            cellFlowSourceReversed[k] = cellFlowSources[k].getNode(0)
                    == cellNodes[k];
        }
        this.nodes = nodes;
        this.elements = elements;
    }

    /**
     * Returns the flow source if the given node is a cell node, which has
     * exactly three elements of which one is a flow source to ground. Returns
     * null if the node is no cell node.
     */
    private static FlowSource findCellFlowSource(GeneralNode node,
            GeneralNode ground) throws NoFlowThroughException {
        if (node.getNumberOfElements() != CELL_NODE_ELEMENTS) {
            return null;
        }
        FlowSource found = null;
        for (int idx = 0; idx < CELL_NODE_ELEMENTS; idx++) {
            AbstractElement e = node.getElement(idx);
            if (e.getElementType() == ElementType.FLOWSOURCE
                    && e.getOnlyOtherNode(node) == ground) {
                if (found != null) {
                    return null; // two flow sources, not a cell
                }
                found = (FlowSource) e;
            }
        }
        return found;
    }

    /**
     * Checks if the given network matches the constellation this solver was
     * made for. The check constructs a temporary instance, so that the
     * topology analysis only exists once in the constructor.
     *
     * @param nodes Full list of nodes of the network
     * @param elements Full list of elements of the network
     * @return true if the network can be solved by this solver.
     */
    public static boolean isSolveableBySolver(List<GeneralNode> nodes,
            List<AbstractElement> elements) {
        try {
            new FuelCellSolver(nodes, elements);
            return true;
        } catch (WrongSolverException ex) {
            return false;
        }
    }

    @Override
    public void solveNetwork() {
        // The origin sets the ground effort, which the effort sources pass on
        // to all forced nodes. The flow sources must set their flows before
        // any other flow is known on a cell node, otherwise the current law of
        // the cell node would try to set the flow on the flow source.
        gnd.doCalculation();
        for (FlowSource heatSource : cellFlowSources) {
            heatSource.doCalculation();
        }

        // Sum up the Norton equivalents of all branches as seen from the bus.
        // A bridged branch forces the bus effort directly instead.
        double conductanceSum = 0.0;
        double sourceFlowSum = 0.0;
        boolean busForced = false;
        double forcedBusEffort = 0.0;
        for (int j = 0; j < simpleResistors.length; j++) {
            double forcedEffort = simpleForcedNodes[j].getEffort();
            if (simpleResistors[j].getElementType() == ElementType.BRIDGED) {
                busForced = true;
                forcedBusEffort = forcedEffort;
            } else {
                double conductance = simpleResistors[j].getConductance();
                conductanceSum += conductance;
                sourceFlowSum += conductance * forcedEffort;
            }
        }
        for (int k = 0; k < cellNodes.length; k++) {
            double forcedEffort = cellForcedNodes[k].getEffort();
            double heatFlow = getHeatFlowIntoCell(k);
            boolean innerBridged = cellInnerResistors[k].getElementType()
                    == ElementType.BRIDGED;
            boolean outerBridged = cellOuterResistors[k].getElementType()
                    == ElementType.BRIDGED;
            if (innerBridged && outerBridged) {
                busForced = true;
                forcedBusEffort = forcedEffort;
            } else if (outerBridged) {
                // Cell node is the bus, heat flows directly into the bus.
                double innerConductance = cellInnerResistors[k].getConductance();
                conductanceSum += innerConductance;
                sourceFlowSum += innerConductance * forcedEffort + heatFlow;
            } else if (innerBridged) {
                // Cell node is forced, the heat flows into the fuel source.
                double outerConductance = cellOuterResistors[k].getConductance();
                conductanceSum += outerConductance;
                sourceFlowSum += outerConductance * forcedEffort;
            } else {
                double innerConductance = cellInnerResistors[k].getConductance();
                double outerConductance = cellOuterResistors[k].getConductance();
                double cellConductance = innerConductance + outerConductance;
                if (cellConductance > 0.0) {
                    conductanceSum += innerConductance * outerConductance
                            / cellConductance;
                    sourceFlowSum += (innerConductance * forcedEffort
                            + heatFlow) * outerConductance / cellConductance;
                }
            }
        }

        double busEffort;
        if (busForced) {
            busEffort = forcedBusEffort;
        } else if (conductanceSum > 0.0) {
            busEffort = sourceFlowSum / conductanceSum;
        } else {
            // All branches are open, the bus is floating without any flow.
            // Its effort is defined as the effort of the first cell.
            busEffort = cellForcedNodes[0].getEffort();
        }

        // Setting the efforts with update lets the elements calculate all
        // flows with Ohm's law and the current law of the nodes.
        if (!bus.effortUpdated()) {
            bus.setEffort(busEffort, null, true);
        }
        for (int k = 0; k < cellNodes.length; k++) {
            if (!cellNodes[k].effortUpdated()) {
                cellNodes[k].setEffort(calculateCellEffort(k, busEffort),
                        null, true);
            }
        }
    }

    /**
     * Returns the heat flow of a cell in the direction into the cell node,
     * independent of the direction in which the flow source is connected.
     */
    private double getHeatFlowIntoCell(int k) {
        if (cellFlowSourceReversed[k]) {
            return -cellFlowSources[k].getFlow();
        }
        return cellFlowSources[k].getFlow();
    }

    /**
     * Calculates the effort of a cell node from its node equation, once the
     * bus effort is known. A bridged resistor connects the cell node directly
     * to its fuel node or to the bus.
     */
    private double calculateCellEffort(int k, double busEffort) {
        double forcedEffort = cellForcedNodes[k].getEffort();
        if (cellInnerResistors[k].getElementType() == ElementType.BRIDGED) {
            return forcedEffort;
        }
        if (cellOuterResistors[k].getElementType() == ElementType.BRIDGED) {
            return busEffort;
        }
        double innerConductance = cellInnerResistors[k].getConductance();
        double outerConductance = cellOuterResistors[k].getConductance();
        double cellConductance = innerConductance + outerConductance;
        if (cellConductance <= 0.0) {
            // Both resistors are open, the heat flow has no path. The cell
            // node is undefined and set to the fuel effort.
            return forcedEffort;
        }
        return (getHeatFlowIntoCell(k) + innerConductance * forcedEffort
                + outerConductance * busEffort) / cellConductance;
    }
}
