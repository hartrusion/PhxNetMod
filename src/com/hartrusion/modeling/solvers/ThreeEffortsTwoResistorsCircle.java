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

import com.hartrusion.modeling.exceptions.WrongSolverException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.GeneralNode;
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
 * through all elements, so that Kirchhoff's voltage law along the mesh
 * directly gives this flow as the sum of all source efforts divided by the sum of both resistances.
 * The mesh itself is described and solved by {@link SingleMesh}, which walks
 * along the mesh from the ground node and stores the direction of every
 * element, so that the solver does not depend on the arrangement and the
 * element directions that are shown above. This class only adds the ground
 * node as the start of the mesh and requires exactly three effort sources and two resistors
 * in the mesh, and no further elements in the network.
 * <p>
 * The solver sets the calculated flow on both resistors and leaves the node
 * efforts to the elements, which propagate them from the ground node along
 * the mesh with the known flow. This also covers bridged resistors,
 * whereas an open resistor results in zero flow in the whole mesh. If both
 * resistors are open, the nodes between them are not connected to the ground
 * node, and they get the effort of the node in front of the first open
 * resistor. Two bridged resistors short-circuit the effort sources and have
 * no solution, which is reported as a ModelErrorException.
 * <p>
 * LLM code generated with Claude Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public class ThreeEffortsTwoResistorsCircle implements DedicatedSolver {

    private static final int EFFORT_SOURCES = 3;
    private static final int RESISTORS = 2;

    /**
     * Number of used elements, which are the origin and the elements of the
     * mesh.
     */
    private static final int USED_ELEMENTS = 1 + EFFORT_SOURCES + RESISTORS;

    private final ClosedOrigin gnd;

    private final SingleMesh mesh;

    /**
     * Generates a solver for the given network and throws an exception if the
     * network does not consist of exactly one closed origin, three effort
     * sources and two resistors which form a single mesh over the ground node.
     *
     * @param nodes Full list of nodes of the network
     * @param elements Full list of elements of the network
     * @throws WrongSolverException if the network does not match
     */
    public ThreeEffortsTwoResistorsCircle(List<GeneralNode> nodes,
            List<AbstractElement> elements) throws WrongSolverException {
        if (SingleMesh.countConnectedElements(elements) != USED_ELEMENTS) {
            throw new WrongSolverException();
        }
        gnd = SingleMesh.findClosedOrigin(elements);
        mesh = new SingleMesh(gnd.getNode(0), gnd, elements);
        if (mesh.getNumberOfEffortSources() != EFFORT_SOURCES
                || mesh.getNumberOfResistors() != RESISTORS) {
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
        gnd.doCalculation();
        mesh.solveMesh();
    }
}
