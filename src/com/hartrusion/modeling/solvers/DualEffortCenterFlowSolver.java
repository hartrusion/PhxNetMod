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
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import java.util.List;

/**
 * Solves a certain network which is generated when simulating the RBMK fuel
 * temperature. This will provide a straight, fast calculation for each fuel
 * cell instead of having to set up a more complex, slower solver.
 * <pre>
 *
 *    n1     R1    nI     R2     n2
 *     o---XXXXXX---o---XXXXXX---o
 *     |            |            |
 *     |            |            |
 *    (|) U1       (-) Iq       (I) U2
 *     |            |            |
 *     |            |            |
 *     -------------o-------------
 *                  |  nGnd
 *                 _|_ gnd
 *
 * </pre> While this could easily be solved with the existing methods, the main
 * issue here is that we need to have this solved a few hundred times so this
 * class will provide the fastest, straight forward solution for it.
 *
 * @author Viktor Alexander Hartung
 */
public class DualEffortCenterFlowSolver implements DedicatedSolver {

    EffortSource u1 = null, u2 = null;
    FlowSource iQ = null;
    LinearDissipator r1 = null, r2 = null;
    ClosedOrigin gnd = null;
    GeneralNode nGnd, n1, nI, n2;
    // Describes direction. r1 and r2 have ni as node index 1.
    boolean iQReversed, r1Reversed, r2Reversed;

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
    public DualEffortCenterFlowSolver(List<GeneralNode> nodes,
            List<AbstractElement> elements) throws WrongSolverException {
        LinearDissipator rx = null, ry = null;

        // Try to find all required elements, abort if there are any additional.
        for (AbstractElement e : elements) {
            switch (e.getElementType()) {
                case DISSIPATOR, OPEN, BRIDGED:
                    if (rx == null) {
                        rx = (LinearDissipator) e;
                    } else if (ry == null) {
                        ry = (LinearDissipator) e;
                    } else {
                        throw new WrongSolverException();
                    }
                    break;
                case FLOWSOURCE:
                    if (iQ == null) {
                        iQ = (FlowSource) e;
                    } else {
                        throw new WrongSolverException();
                    }
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
                    if (e instanceof ClosedOrigin) {
                        gnd = (ClosedOrigin) e;
                    }
                    break;
            }
        }
        // Check if everything is there
        if (gnd == null || u1 == null || u2 == null || iQ == null
                || rx == null || ry == null) {
            throw new WrongSolverException();
        }
        nGnd = gnd.getNode(0); // get and check ground node
        if (nGnd.getNumberOfElements() != 4) {
            throw new WrongSolverException();
        }
        // Try to find and assign the nodes on the other side. Also write the
        // variable which sets the direction of the element as this also could
        // throw some exceptions.
        try {
            n1 = u1.getOnlyOtherNode(nGnd);
            n2 = u2.getOnlyOtherNode(nGnd);
            nI = iQ.getOnlyOtherNode(nGnd);
            // Assign the resistors, they should be connected at n1 and n2.
            r1 = (LinearDissipator) n1.getOnlyOtherElement(u1);
            r2 = (LinearDissipator) n2.getOnlyOtherElement(u2);
            // Non-reverse: R goes from U to center
            r1Reversed = r1.getNode(0) == nI;
            r2Reversed = r2.getNode(0) == nI;
            iQReversed = iQ.getNode(0) == nI;
        } catch (NoFlowThroughException | ModelErrorException ex) {
            throw new WrongSolverException();
        }
        // Check the node connections amount - if that is fine, we have exactly
        // that network which we can solve with this thing.
        if (n1.getNumberOfElements() != 2
                || n2.getNumberOfElements() != 2
                || nI.getNumberOfElements() != 3) {
            throw new WrongSolverException();
        }
        this.nodes = nodes;
        this.elements = elements;
    }

    @Override
    public void solveNetwork() {
        // Resistance values for both resistors in parallel and in series
        double rSum, rSplit;
        rSum = r1.getResistance() + r2.getResistance();
        rSplit = 1 / (r1.getConductance() + r2.getConductance());
      
        // Apply Ground potential and voltages by the voltage sources.
        gnd.doCalculation();
        u1.doCalculation();
        u2.doCalculation();

        // This is a bit of a superposition solution here. We calculate both
        // outer voltage sources first and remove the current source, the R are
        // in series then. Beware of the directions!
        double i1 = n1.getEffort() / rSum;
        double i2 = n2.getEffort() / rSum;
        // Voltage on the center current source
        double uQ;
        if (iQReversed) {
            uQ = -rSplit * iQ.getFlow();
        } else {
            uQ = rSplit * iQ.getFlow();
        }
        // current from current source flows from nI to n1 and nI to n2.
        double iQ1 = uQ * r1.getConductance();
        
        // Set the flow from the current source first, otherwise the update 
        // call below will cause an illgal set by that solution.
        iQ.doCalculation();
        
        // Perform manual superposition on the resistors, this will also
        // update the flow on the voltage sources 
        if (r1Reversed) {
            r1.setFlow(i2 - i1 + iQ1, true);
        } else {
            r1.setFlow(i1 - i2 - iQ1, true);
        }
        if (r2Reversed) {
            r2.setFlow(i1 - i2 + iQ1, true);
        } else {
            r2.setFlow(i2 - i1 - iQ1, true);
        }
        r1.doCalculation(); // make ohms law to apply voltage to nI
    }
}
