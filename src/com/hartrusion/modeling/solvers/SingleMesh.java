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
import java.util.ArrayList;
import java.util.List;

/**
 * Describes a single mesh of effort sources and resistors in series, which is
 * closed over one mesh node, and calculates the flow of that mesh. This class
 * is the common part of the dedicated solvers
 * {@link ThreeEffortsTwoResistorsCircle}, {@link ThreeEffortsOneResistorCircle}
 * and {@link FloatingEffortRing}, which only differ in how the mesh node gets
 * its effort and in the number of elements that they accept in the mesh.
 * <p>
 * The constructor walks along the mesh, beginning at the mesh node with the
 * first element that is not the entry element, which is the element that
 * connects the mesh node to the rest of the network. Every other node of the
 * mesh must connect exactly two elements of the mesh, and the walk must return
 * to the mesh node. The direction in which the walk passes each element is
 * stored, because the elements can be connected in any direction, so that the
 * signs of the source efforts and the resistor flows are applied correctly in
 * every cycle. Because a single mesh carries only one flow, Kirchhoff's
 * voltage law gives this flow as the sum of the source efforts in walk
 * direction divided by the sum of all resistances.
 * <p>
 * The node and element arrays are created once in the constructor, so that
 * {@link #solveMesh()} only runs over plain arrays and creates no objects.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
final class SingleMesh {

    /**
     * Number of elements on the mesh node, which are the entry element and the
     * first and last element of the mesh.
     */
    private static final int MESH_NODE_ELEMENTS = 3;

    /**
     * Number of elements on every other node of the mesh, which are the two
     * neighboring elements of the mesh.
     */
    private static final int INNER_NODE_ELEMENTS = 2;

    /**
     * All elements of the mesh in the order of the walk, starting and ending
     * at the mesh node. The order allows the efforts to cascade from the mesh
     * node through the mesh when the elements are calculated.
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
     * Walks along the mesh and throws an exception if the elements that start
     * at the mesh node do not form a single mesh of effort sources and at
     * least one resistor, which are all part of the given element list.
     *
     * @param meshNode Node at which the mesh is closed.
     * @param entryElement Element on the mesh node that is not part of the
     * mesh, for example the origin.
     * @param elements Full list of elements of the network.
     * @throws WrongSolverException if there is no such mesh.
     */
    SingleMesh(GeneralNode meshNode, AbstractElement entryElement,
            List<AbstractElement> elements) throws WrongSolverException {
        if (meshNode.getNumberOfElements() != MESH_NODE_ELEMENTS) {
            throw new WrongSolverException();
        }
        List<FlowThrough> elementList = new ArrayList<>();
        List<EffortSource> sourceList = new ArrayList<>();
        List<Boolean> sourceDirections = new ArrayList<>();
        List<LinearDissipator> resistorList = new ArrayList<>();
        List<Boolean> resistorDirections = new ArrayList<>();
        List<GeneralNode> entryNodes = new ArrayList<>();
        List<GeneralNode> exitNodes = new ArrayList<>();

        AbstractElement element = meshNode.getElement(0);
        if (element == entryElement) {
            element = meshNode.getElement(1);
        }
        GeneralNode node = meshNode;
        boolean meshClosed = false;
        try {
            // Each element can only be passed once, so the walk can not take
            // more steps than there are elements.
            for (int step = 0; step < elements.size(); step++) {
                if (element == entryElement
                        || !(element instanceof FlowThrough)
                        || !elements.contains(element)) {
                    throw new WrongSolverException();
                }
                GeneralNode nextNode = element.getOnlyOtherNode(node);
                boolean alongMesh = element.getNode(0) == node;
                elementList.add((FlowThrough) element);
                if (element.getElementType() == ElementType.EFFORTSOURCE) {
                    sourceList.add((EffortSource) element);
                    sourceDirections.add(alongMesh);
                } else if (LinearNetwork.isElementResistorType(element)) {
                    resistorList.add((LinearDissipator) element);
                    resistorDirections.add(alongMesh);
                    entryNodes.add(node);
                    exitNodes.add(nextNode);
                } else {
                    throw new WrongSolverException();
                }
                if (nextNode == meshNode) {
                    meshClosed = true;
                    break;
                }
                if (nextNode.getNumberOfElements() != INNER_NODE_ELEMENTS) {
                    throw new WrongSolverException();
                }
                element = nextNode.getOnlyOtherElement(element);
                node = nextNode;
            }
        } catch (NoFlowThroughException | ModelErrorException ex) {
            throw new WrongSolverException();
        }
        // A mesh without resistors would short-circuit its effort sources.
        if (!meshClosed || resistorList.isEmpty()) {
            throw new WrongSolverException();
        }

        meshElements = elementList.toArray(FlowThrough[]::new);
        sources = sourceList.toArray(EffortSource[]::new);
        sourceAlongMesh = new boolean[sources.length];
        for (int k = 0; k < sources.length; k++) {
            sourceAlongMesh[k] = sourceDirections.get(k);
        }
        resistors = resistorList.toArray(LinearDissipator[]::new);
        resistorAlongMesh = new boolean[resistors.length];
        for (int k = 0; k < resistors.length; k++) {
            resistorAlongMesh[k] = resistorDirections.get(k);
        }
        resistorEntryNodes = entryNodes.toArray(GeneralNode[]::new);
        resistorExitNodes = exitNodes.toArray(GeneralNode[]::new);
    }

    int getNumberOfEffortSources() {
        return sources.length;
    }

    int getNumberOfResistors() {
        return resistors.length;
    }

    int getNumberOfElements() {
        return meshElements.length;
    }

    /**
     * Calculates the mesh flow, sets it on all resistors and lets the elements
     * cascade the efforts from the mesh node along the mesh, which requires
     * that the effort of the mesh node was already set by the caller.
     * <p>
     * An open resistor results in zero flow in the whole mesh. If more than
     * one resistor is open, the nodes between them are not connected to the
     * mesh node and their efforts are undefined, which is why the effort of
     * the node in front of an open resistor is then assigned to the node
     * behind it, as if no effort difference existed across that resistor.
     * Bridged resistors only are a short circuit of the effort sources, which
     * has no solution and is reported as a {@link ModelErrorException}.
     */
    void solveMesh() {
        // Kirchhoff's voltage law along the mesh: the efforts of all sources
        // in walk direction equal the effort drops over all resistors.
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
                throw new ModelErrorException("All resistors of the mesh "
                        + "are bridged, which short-circuits the effort "
                        + "sources.");
            }
            meshFlow = meshEffort / meshResistance;
        }

        for (int k = 0; k < resistors.length; k++) {
            if (resistorAlongMesh[k]) {
                resistors[k].setFlow(meshFlow, true);
            } else {
                resistors[k].setFlow(-meshFlow, true);
            }
        }
        // Let the elements cascade the efforts from the mesh node along the
        // mesh, which is done in walk order to reach every node in one pass.
        for (FlowThrough e : meshElements) {
            e.doCalculation();
        }
        if (!meshOpen) {
            return;
        }
        // Nodes between open resistors are still unknown. Going in walk order,
        // each transfer fills the part of the mesh up to the next open
        // resistor, so that one pass reaches all nodes.
        for (int k = 0; k < resistors.length; k++) {
            if (resistorEntryNodes[k].effortUpdated()
                    && !resistorExitNodes[k].effortUpdated()) {
                resistorExitNodes[k].setEffort(
                        resistorEntryNodes[k].getEffort(), null, true);
                for (FlowThrough e : meshElements) {
                    e.doCalculation();
                }
            }
        }
    }

    /**
     * Returns the only closed origin of the given elements and throws an
     * exception if there is none, more than one or an origin of another type.
     * Used by the solvers built on this class to find their ground node.
     *
     * @param elements Full list of elements of the network.
     * @return The closed origin.
     * @throws WrongSolverException if there is not exactly one closed origin.
     */
    static ClosedOrigin findClosedOrigin(List<AbstractElement> elements)
            throws WrongSolverException {
        ClosedOrigin origin = null;
        for (AbstractElement e : elements) {
            if (e.getElementType() == ElementType.ORIGIN) {
                if (origin != null || !(e instanceof ClosedOrigin)) {
                    throw new WrongSolverException();
                }
                origin = (ClosedOrigin) e;
            }
        }
        if (origin == null) {
            throw new WrongSolverException();
        }
        return origin;
    }

    /**
     * Counts the elements of the network that are connected to nodes. Elements
     * of type NONE without nodes are not counted, because the TransferSubnet
     * keeps them as leftovers of replaced elements. The solvers built on this
     * class compare this number with the elements they use, so that a network
     * with additional elements is rejected.
     *
     * @param elements Full list of elements of the network.
     * @return Number of connected elements.
     */
    static int countConnectedElements(List<AbstractElement> elements) {
        int count = 0;
        for (AbstractElement e : elements) {
            if (e.getElementType() != ElementType.NONE
                    || e.getNumberOfNodes() != 0) {
                count++;
            }
        }
        return count;
    }
}
