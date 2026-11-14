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
import com.hartrusion.modeling.general.GeneralNode;
import java.util.List;

/**
 * Solves a closed ring of effort sources and resistors in series which is
 * lifted to a higher effort by a grounded effort source on one of its nodes,
 * as the {@link TransferSubnet} generates it in the RBMK simulator.
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
 * </pre> The effort source {@code uLift} forces the ring node, and because
 * the ring is only connected to the rest of the network at this single node,
 * no flow can run through {@code uLift}. The ring itself is a single mesh, so
 * that Kirchhoff's voltage law along the ring gives its flow as the sum of the
 * source efforts in the ring divided by the sum of the ring resistances. The
 * ring is described and solved by {@link SingleMesh}, which walks along the
 * ring from the ring node and stores the direction of every element, so that
 * the solver neither depends on the number and arrangement of the elements in
 * the ring nor on the direction in which they are connected.
 * <p>
 * The network is recognized by the ground node, which must only connect the
 * closed origin and the effort source {@code uLift}, and by the ring, which
 * must start and end at the other node of {@code uLift}, consist of effort
 * sources and at least one resistor and contain all remaining elements of the
 * network. In the network of the simulator, the ring consists of one effort
 * source and four resistors.
 * <p>
 * The solver sets the effort of the ring node with the ground and
 * {@code uLift}, sets the calculated flow on all resistors of the ring and
 * leaves the remaining node efforts to the elements, which propagate them
 * from the ring node along the ring with the known flow. The zero flow of
 * {@code uLift} follows from the current law of the ring node. An open
 * resistor results in zero flow in the whole ring, whereas bridged resistors
 * only short-circuit the effort sources of the ring, which has no solution
 * and is reported as a {@link ModelErrorException}.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class FloatingEffortRing implements DedicatedSolver {

    /**
     * Number of elements on the ground node, which are the origin and the
     * effort source that lifts the ring.
     */
    private static final int GROUND_NODE_ELEMENTS = 2;

    /**
     * Number of used elements outside of the ring, which are the origin and
     * the effort source that lifts the ring.
     */
    private static final int ELEMENTS_OUTSIDE_RING = 2;

    private final ClosedOrigin gnd;

    /**
     * Effort source between the ground node and the ring node, which carries
     * no flow.
     */
    private final EffortSource liftSource;

    private final SingleMesh ring;

    /**
     * Generates a solver for the given network and throws an exception if the
     * network does not consist of a closed origin, an effort source from the
     * ground node to the ring node and a single ring that starts and ends at
     * the ring node.
     *
     * @param nodes Full list of nodes of the network
     * @param elements Full list of elements of the network
     * @throws WrongSolverException if the network does not match
     */
    public FloatingEffortRing(List<GeneralNode> nodes,
            List<AbstractElement> elements) throws WrongSolverException {
        gnd = SingleMesh.findClosedOrigin(elements);
        GeneralNode ground = gnd.getNode(0);
        if (ground.getNumberOfElements() != GROUND_NODE_ELEMENTS) {
            throw new WrongSolverException();
        }
        GeneralNode ringNode;
        try {
            AbstractElement lift = ground.getOnlyOtherElement(gnd);
            if (lift.getElementType() != ElementType.EFFORTSOURCE
                    || !elements.contains(lift)) {
                throw new WrongSolverException();
            }
            liftSource = (EffortSource) lift;
            ringNode = liftSource.getOnlyOtherNode(ground);
        } catch (NoFlowThroughException | ModelErrorException ex) {
            throw new WrongSolverException();
        }
        if (ringNode == ground) {
            throw new WrongSolverException();
        }
        ring = new SingleMesh(ringNode, liftSource, elements);
        if (SingleMesh.countConnectedElements(elements)
                != ELEMENTS_OUTSIDE_RING + ring.getNumberOfElements()) {
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
            new FloatingEffortRing(nodes, elements);
            return true;
        } catch (WrongSolverException ex) {
            return false;
        }
    }

    @Override
    public void solveNetwork() {
        gnd.doCalculation();
        liftSource.doCalculation();
        ring.solveMesh();
    }
}
