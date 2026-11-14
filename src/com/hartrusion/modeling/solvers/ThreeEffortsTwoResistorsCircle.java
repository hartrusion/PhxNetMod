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
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import java.util.List;

/**
 * Solves a single mesh of three effort sources and two resistors which is
 * closed over a ground node, as the {@link TransferSubnet} generates it
 * several times in the RBMK simulator.
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
 * </pre> Because the network consists of only one mesh, a single flow runs
 * through all five elements, so that Kirchhoff's voltage law along the mesh
 * directly gives this flow as the sum of all source efforts divided by the sum
 * of both resistances. The solver does not depend on the arrangement that is
 * shown above, since it walks along the mesh from the ground node and only
 * requires that every node except the ground node connects exactly two
 * elements of the mesh. The direction in which each element is connected is
 * stored during this walk, so that the signs of the source efforts and the
 * resistor flows are applied correctly in every cycle.
 * <p>
 * The solver sets the calculated flow on both resistors and leaves the node
 * efforts to the elements, which propagate them from the ground node along
 * the mesh with the known flows. This also covers bridged resistors, whereas
 * an open resistor results in zero flow in the whole mesh. If both resistors
 * are open, the nodes between them are not connected to the ground node and
 * their efforts are undefined, which is why the solver then assigns the
 * effort of the node in front of the first open resistor to the node behind
 * it, as if no effort difference existed across that resistor. Two bridged
 * resistors short-circuit the effort sources and have no solution, which is
 * reported as a {@link ModelErrorException}.
 * <p>
 * The node and element arrays are created once in the constructor, so that
 * {@link #solveNetwork()} only runs over plain arrays and creates no objects.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class ThreeEffortsTwoResistorsCircle implements DedicatedSolver {

    private static final int EFFORT_SOURCES = 3;
    private static final int RESISTORS = 2;
    private static final int MESH_ELEMENTS = EFFORT_SOURCES + RESISTORS;

    /**
     * Number of elements on the ground node, which are the origin and the
     * first and last element of the mesh.
     */
    private static final int GROUND_NODE_ELEMENTS = 3;

    /**
     * Number of elements on every other node of the mesh, which are the two
     * neighboring elements of the mesh.
     */
    private static final int MESH_NODE_ELEMENTS = 2;

    private final ClosedOrigin gnd;

    /**
     * All five elements in the order of the walk along the mesh, starting and
     * ending at the ground node. The order allows the efforts to cascade from
     * the ground node through the mesh when the elements are calculated.
     */
    private final FlowThrough[] meshElements;

    private final EffortSource[] sources;

    /**
     * True if the walk along the mesh passes the effort source from its node
     * 0 to its node 1, which means that its effort adds to the mesh effort.
     */
    private final boolean[] sourceAlongMesh;

    private final LinearDissipator[] resistors;

    /**
     * True if the walk along the mesh passes the resistor from its node 0 to
     * its node 1, which means that a positive mesh flow is a positive flow on
     * the resistor.
     */
    private final boolean[] resistorAlongMesh;

    /**
     * Node of each resistor which the walk along the mesh reaches first, used
     * to transfer an effort across an open resistor.
     */
    private final GeneralNode[] resistorEntryNodes;

    /**
     * Node of each resistor which the walk along the mesh reaches last.
     */
    private final GeneralNode[] resistorExitNodes;

    /**
     * Generates a solver for the given network and throws an exception if the
     * network does not consist of exactly one closed origin, three effort
     * sources and two resistors which form a single mesh over the ground node.
     * Elements of type NONE without nodes are ignored, because the
     * TransferSubnet keeps them as leftovers of replaced elements.
     *
     * @param nodes Full list of nodes of the network
     * @param elements Full list of elements of the network
     * @throws WrongSolverException if the network does not match
     */
    public ThreeEffortsTwoResistorsCircle(List<GeneralNode> nodes,
            List<AbstractElement> elements) throws WrongSolverException {
        ClosedOrigin origin = null;
        int sourceCount = 0;
        int resistorCount = 0;
        for (AbstractElement e : elements) {
            switch (e.getElementType()) {
                case ORIGIN:
                    if (origin != null || !(e instanceof ClosedOrigin)) {
                        throw new WrongSolverException();
                    }
                    origin = (ClosedOrigin) e;
                    break;
                case EFFORTSOURCE:
                    sourceCount++;
                    break;
                case DISSIPATOR, OPEN, BRIDGED:
                    if (!LinearNetwork.isElementResistorType(e)) {
                        throw new WrongSolverException();
                    }
                    resistorCount++;
                    break;
                case NONE:
                    if (e.getNumberOfNodes() != 0) {
                        throw new WrongSolverException();
                    }
                    break;
                default:
                    throw new WrongSolverException();
            }
        }
        if (origin == null || sourceCount != EFFORT_SOURCES
                || resistorCount != RESISTORS) {
            throw new WrongSolverException();
        }
        gnd = origin;
        GeneralNode ground = gnd.getNode(0);
        if (ground.getNumberOfElements() != GROUND_NODE_ELEMENTS) {
            throw new WrongSolverException();
        }

        meshElements = new FlowThrough[MESH_ELEMENTS];
        sources = new EffortSource[EFFORT_SOURCES];
        sourceAlongMesh = new boolean[EFFORT_SOURCES];
        resistors = new LinearDissipator[RESISTORS];
        resistorAlongMesh = new boolean[RESISTORS];
        resistorEntryNodes = new GeneralNode[RESISTORS];
        resistorExitNodes = new GeneralNode[RESISTORS];

        // Walk along the mesh, starting with the first element on the ground
        // node that is not the origin, until the walk returns to the ground
        // node. Every element must belong to the given element list, so that
        // the walk does not leave the subnet.
        AbstractElement element = ground.getElement(0);
        if (element == gnd) {
            element = ground.getElement(1);
        }
        GeneralNode node = ground;
        int sourceIndex = 0;
        int resistorIndex = 0;
        boolean meshClosed = false;
        try {
            for (int k = 0; k < MESH_ELEMENTS; k++) {
                if (!(element instanceof FlowThrough)
                        || !elements.contains(element)) {
                    throw new WrongSolverException();
                }
                GeneralNode nextNode = element.getOnlyOtherNode(node);
                boolean alongMesh = element.getNode(0) == node;
                meshElements[k] = (FlowThrough) element;
                if (element.getElementType() == ElementType.EFFORTSOURCE) {
                    sources[sourceIndex] = (EffortSource) element;
                    sourceAlongMesh[sourceIndex] = alongMesh;
                    sourceIndex++;
                } else {
                    resistors[resistorIndex] = (LinearDissipator) element;
                    resistorAlongMesh[resistorIndex] = alongMesh;
                    resistorEntryNodes[resistorIndex] = node;
                    resistorExitNodes[resistorIndex] = nextNode;
                    resistorIndex++;
                }
                if (nextNode == ground) {
                    // The mesh must close with its fifth element.
                    if (k != MESH_ELEMENTS - 1) {
                        throw new WrongSolverException();
                    }
                    meshClosed = true;
                    break;
                }
                if (nextNode.getNumberOfElements() != MESH_NODE_ELEMENTS) {
                    throw new WrongSolverException();
                }
                element = nextNode.getOnlyOtherElement(element);
                node = nextNode;
            }
        } catch (NoFlowThroughException | ModelErrorException ex) {
            throw new WrongSolverException();
        }
        // After five elements, the walk must have arrived at the ground node.
        // Otherwise the five elements do not form a single mesh.
        if (!meshClosed) {
            throw new WrongSolverException();
        }
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
            new ThreeEffortsTwoResistorsCircle(nodes, elements);
            return true;
        } catch (WrongSolverException ex) {
            return false;
        }
    }

    @Override
    public void solveNetwork() {
        // Kirchhoff's voltage law along the mesh: the efforts of all sources
        // in walk direction equal the effort drops over both resistors.
        double meshEffort = 0.0;
        for (int k = 0; k < sources.length; k++) {
            if (sourceAlongMesh[k]) {
                meshEffort += sources[k].getEffort();
            } else {
                meshEffort -= sources[k].getEffort();
            }
        }
        double meshResistance = 0.0;
        boolean meshOpen = false;
        for (LinearDissipator r : resistors) {
            if (r.getElementType() == ElementType.OPEN) {
                meshOpen = true;
            } else {
                meshResistance += r.getResistance();
            }
        }
        double meshFlow = 0.0;
        if (!meshOpen) {
            if (meshResistance == 0.0) {
                throw new ModelErrorException("Both resistors of the mesh "
                        + "are bridged, which short-circuits the effort "
                        + "sources.");
            }
            meshFlow = meshEffort / meshResistance;
        }

        gnd.doCalculation();
        for (int k = 0; k < resistors.length; k++) {
            if (resistorAlongMesh[k]) {
                resistors[k].setFlow(meshFlow, true);
            } else {
                resistors[k].setFlow(-meshFlow, true);
            }
        }
        // Let the elements cascade the efforts from the ground node along the
        // mesh, which is done in walk order to reach every node in one pass.
        for (FlowThrough e : meshElements) {
            e.doCalculation();
        }
        // With both resistors open, the nodes between them are still unknown
        // and get the effort of the node in front of the first open resistor.
        for (int k = 0; k < resistors.length; k++) {
            if (resistorEntryNodes[k].effortUpdated()
                    && !resistorExitNodes[k].effortUpdated()) {
                resistorExitNodes[k].setEffort(
                        resistorEntryNodes[k].getEffort(), null, true);
                for (FlowThrough e : meshElements) {
                    e.doCalculation();
                }
                break;
            }
        }
    }
}
