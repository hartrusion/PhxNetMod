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

import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.exceptions.WrongSolverException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import java.util.ArrayList;
import java.util.List;

/**
 * Solves a certain network which is generated frequently when simulating with
 * the {@link TransferSubnet} solver. This provides a straight, fast calculation
 * for a chain of resistors that sit between two fixed effort sources, avoiding
 * the more complex and slower nodal analysis.
 * <pre>
 *
 *    n1    R1    na    R2    nb         Rn    n2
 *     o---XXXXX---o---XXXXX---o-- ... --XXXXX---o
 *     |                                        |
 *     |                                        |
 *    (|) U1                                   (I) U2
 *     |                                        |
 *     |                                        |
 *     --------------------o---------------------
 *                         |  nGnd
 *                        _|_ gnd
 *
 * </pre> The two outer nodes {@code n1} and {@code n2} are forced to fixed
 * effort values by the grounded effort sources {@code U1} and {@code U2}.
 * Between them sit {@code n} {@link LinearDissipator} elements in series (at
 * least one, with no upper limit). Solving this is trivial: propagate the fixed
 * efforts to {@code n1} and {@code n2}, sum up all resistances, compute the
 * single series flow and apply it to every resistor (honoring each resistor's
 * direction), then let each resistor apply Ohm's law to fill in the
 * intermediate node efforts.
 * <p>
 * Generated using Claude Opus 4.8
 *
 * @author Viktor Alexander Hartung
 */
public class DualEffortSeriesResistors implements DedicatedSolver {

    EffortSource u1 = null, u2 = null;
    ClosedOrigin gnd = null;
    GeneralNode nGnd, n1, n2;

    // The resistors in series order, starting from n1 and ending at n2.
    private final LinearDissipator[] resistors;
    // Direction flag per resistor. A resistor is "reversed" if its node with
    // index 0 is the one pointing towards n2 (downstream) instead of n1.
    private final boolean[] reversed;

    private final List<GeneralNode> nodes;
    private final List<AbstractElement> elements;

    /**
     * Generates a solver for the given network type, if it fails, an exception
     * is thrown.
     *
     * @param nodes
     * @param elements
     * @throws WrongSolverException
     */
    public DualEffortSeriesResistors(List<GeneralNode> nodes,
            List<AbstractElement> elements) throws WrongSolverException {
        List<LinearDissipator> resistorList = new ArrayList<>();

        // Try to find all required elements, abort if there is anything that
        // does not belong into this simple series network.
        for (AbstractElement e : elements) {
            switch (e.getElementType()) {
                case DISSIPATOR, OPEN, BRIDGED:
                    resistorList.add((LinearDissipator) e);
                    break;
                case EFFORTSOURCE:
                    if (u1 == null) {
                        u1 = (EffortSource) e;
                    } else if (u2 == null) {
                        u2 = (EffortSource) e;
                    } else {
                        throw new WrongSolverException();
                    }
                    break;
                case ORIGIN:
                    if (gnd == null && e instanceof ClosedOrigin) {
                        gnd = (ClosedOrigin) e;
                    } else {
                        throw new WrongSolverException();
                    }
                    break;
                default:
                    // A flow source or any other element type means this is not
                    // the network this solver was made for.
                    throw new WrongSolverException();
            }
        }
        // Check if everything mandatory is there. At least one resistor is
        // required, both effort sources and the ground.
        if (gnd == null || u1 == null || u2 == null || resistorList.isEmpty()) {
            throw new WrongSolverException();
        }
        nGnd = gnd.getNode(0); // get and check ground node
        // The ground node must connect exactly to gnd, u1 and u2.
        if (nGnd.getNumberOfElements() != 3) {
            throw new WrongSolverException();
        }
        // Find the two outer nodes and walk the resistor chain between them.
        // This may throw a couple of exceptions on malformed networks which all
        // mean that this is not the expected network type.
        List<LinearDissipator> ordered = new ArrayList<>();
        List<Boolean> reversedList = new ArrayList<>();
        try {
            n1 = u1.getOnlyOtherNode(nGnd);
            n2 = u2.getOnlyOtherNode(nGnd);
            // The outer nodes connect only to their effort source and the first
            // respectively last resistor of the chain.
            if (n1.getNumberOfElements() != 2 || n2.getNumberOfElements() != 2) {
                throw new WrongSolverException();
            }
            // Traverse the series chain from n1 towards n2. Every intermediate
            // node must have exactly two elements (the two adjacent resistors),
            // otherwise getOnlyOtherElement throws and we reject the network.
            GeneralNode node = n1;
            AbstractElement cameFrom = u1;
            while (node != n2) {
                if (ordered.size() > resistorList.size()) {
                    // More steps than resistors means we never reach n2 on a
                    // clean chain - reject to avoid looping on bad topologies.
                    throw new WrongSolverException();
                }
                AbstractElement next = node.getOnlyOtherElement(cameFrom);
                if (!(next instanceof LinearDissipator)) {
                    throw new WrongSolverException();
                }
                LinearDissipator r = (LinearDissipator) next;
                // node is the upstream (n1-side) node of this resistor. If it
                // is not at index 0, the resistor points the other way.
                reversedList.add(r.getNode(0) != node);
                ordered.add(r);
                cameFrom = r;
                node = r.getOnlyOtherNode(node);
            }
        } catch (NoFlowThroughException | ModelErrorException ex) {
            throw new WrongSolverException();
        }
        // The traversal must have visited every resistor exactly once. If not,
        // there are stray resistors that do not belong to the series chain.
        if (ordered.size() != resistorList.size()) {
            throw new WrongSolverException();
        }
        // Store the chain in plain arrays for the fastest possible solve.
        resistors = ordered.toArray(LinearDissipator[]::new);
        reversed = new boolean[reversedList.size()];
        for (int i = 0; i < reversed.length; i++) {
            reversed[i] = reversedList.get(i);
        }
        this.nodes = nodes;
        this.elements = elements;
    }

    @Override
    public void solveNetwork() {
        // Sum up all series resistances.
        double rSum = 0.0;
        for (LinearDissipator r : resistors) {
            rSum += r.getResistance();
        }

        // Apply the ground potential and propagate the fixed efforts of both
        // sources onto the outer nodes n1 and n2.
        gnd.doCalculation();
        u1.doCalculation();
        u2.doCalculation();

        // The whole chain carries a single series flow. Positive value means it
        // flows from n1 towards n2 (n1 being at the higher effort).
        double i = (n1.getEffort() - n2.getEffort()) / rSum;

        // Set that flow on every resistor, honoring its individual direction.
        for (int k = 0; k < resistors.length; k++) {
            if (reversed[k]) {
                resistors[k].setFlow(-i, true);
            } else {
                resistors[k].setFlow(i, true);
            }
        }

        // Let each resistor apply Ohms law to fill in the intermediate node
        // efforts. Starting from n1 this cascades down the chain.
        for (LinearDissipator r : resistors) {
            r.doCalculation();
        }
    }
}
