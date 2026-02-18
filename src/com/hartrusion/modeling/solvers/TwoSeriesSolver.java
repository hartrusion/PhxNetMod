/*
 * The MIT License
 *
 * Copyright 2025 Viktor Alexander Hartung.
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

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.ElementType;
import static com.hartrusion.modeling.ElementType.BRIDGED;
import static com.hartrusion.modeling.ElementType.DISSIPATOR;
import static com.hartrusion.modeling.ElementType.EFFORTSOURCE;
import static com.hartrusion.modeling.ElementType.FLOWSOURCE;
import static com.hartrusion.modeling.ElementType.OPEN;
import static com.hartrusion.modeling.ElementType.ORIGIN;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.ClosedOrigin;

/**
 * Performs calculation for the network if it consists of two resistors and one
 * source with an origin between resistors. This is a special case which will
 * happen if a network is simplified by using parallel and series
 * simplification. If an origin element is between two resistors, they will not
 * be further simplified because they are not strictly in series.
 */
public class TwoSeriesSolver {

    private List<GeneralNode> nodes;
    private List<AbstractElement> elements;

    private LinearDissipator resistorX = null;
    private LinearDissipator resistorY = null;
    private ClosedOrigin orig = null;
    private AbstractElement source = null;
    private ElementType sourceType;
    private GeneralNode middleNode = null;
    private GeneralNode nodeX;
    private GeneralNode nodeY;

    private int resistors = 0;
    private int sources = 0;
    private int origins = 0;

    /**
     * Initializes the solver with a given list of nodes and elements. It will
     * return true if everything is fine, otherwise FALSE (the solver cant be
     * used then)
     *
     * @param nodes
     * @param elements
     * @return
     */
    public boolean init(List<GeneralNode> nodes,
            List<AbstractElement> elements) {
        this.nodes = nodes;
        this.elements = elements;

        if (elements.size() != 4) {
            return false; // this is the most general depedency
        }
        // count number of elements
        for (AbstractElement e : elements) {
            switch (e.getElementType()) {
                case DISSIPATOR:
                case OPEN:
                case BRIDGED:
                    resistors++;
                    break;
                case FLOWSOURCE:
                case EFFORTSOURCE:
                    sources++;
                    break;
                case ORIGIN:
                    origins++;
                    break;
            }
        }
        if (resistors != 2 || sources != 1 || origins != 1) {
            return false; // wrong element combination
        }
        // there must be one node that has three connections, this is the
        // node between the resistors with the origin.
        for (GeneralNode n : nodes) {
            if (n.getNumberOfElements() == 3) {
                double mResistors = 0;
                double mSources = 0;
                double mOrigins = 0;
                for (int idx = 0; idx < 3; idx++) {
                    switch (n.getElement(idx).getElementType()) {
                        case DISSIPATOR:
                        case OPEN:
                        case BRIDGED:
                            mResistors++;
                            break;
                        case FLOWSOURCE:
                        case EFFORTSOURCE:
                            mSources++;
                            break;
                        case ORIGIN:
                            mOrigins++;
                            break;
                    }
                }
                middleNode = n; // remember node, will be used later
                // Check the exact number of elements on the middle node
                if (mResistors != 2 || mSources != 0 || mOrigins != 1) {
                    return false; // wrong combination on 3-element-node
                }
            }
        }

        sourceType = ElementType.NONE;
        for (AbstractElement e : elements) {
            if (isElementResistorType(e)) {
                // there are 2 resistors, assign them to X and Y
                if (resistorX == null) {
                    resistorX = (LinearDissipator) e;
                } else {
                    resistorY = (LinearDissipator) e;
                }
            } else if (e.getElementType() == ElementType.ORIGIN) {
                orig = (ClosedOrigin) e;
            } else if (e.getElementType() == ElementType.EFFORTSOURCE) {
                source = e;
                sourceType = ElementType.EFFORTSOURCE;
            } else if (e.getElementType() == ElementType.FLOWSOURCE) {
                source = e;
                sourceType = ElementType.FLOWSOURCE;
            }
        }
        // Check if everything was assigned
        if (orig == null || resistorX == null || middleNode == null
                || resistorY == null || source == null) {
            return false; // prevent nullpointer, supress warning message...
        }
        // Assign the nodes
        try {
            nodeX = resistorX.getOnlyOtherNode(middleNode);
        } catch (NoFlowThroughException ex) {
            throw new ModelErrorException("Expected flow through element has "
                    + "inconsistent number of nodes.");
        }
        try {
            nodeY = resistorY.getOnlyOtherNode(middleNode);
        } catch (NoFlowThroughException ex) {
            throw new ModelErrorException("Expected flow through element has "
                    + "inconsistent number of nodes.");
        }
        return true;
    }

    public boolean solve() {
        double resistance, effort, flow;

        boolean open = resistorX.getElementType() == ElementType.OPEN
                || resistorY.getElementType() == ElementType.OPEN;
        boolean bothOpen = resistorX.getElementType() == ElementType.OPEN
                && resistorY.getElementType() == ElementType.OPEN;
        boolean shortcut = resistorX.getElementType() == ElementType.BRIDGED
                && resistorY.getElementType() == ElementType.BRIDGED;

        // there are some special cases which need to be adressed first.
        if (open && sourceType == ElementType.FLOWSOURCE) {
            throw new UnsupportedOperationException("Model error: Flow "
                    + "source must not be in series with open connection.");
        } else if (shortcut && sourceType == ElementType.EFFORTSOURCE) {
            throw new UnsupportedOperationException("Model error: Effort "
                    + "can never be shortened with shortcut.");
        }
        // The network actually looks like this:
        //
        //  nodeX
        //    o---------XXXXXXXX---------.
        //    |         resistorX        |
        //    | (1)                      |
        //    |                          |
        //   (|)  source                 |
        //    |                          |
        //    | (0)                      |
        //    |                          |
        //    o---------XXXXXXXX---------o middleNode
        //  nodeY       resistorY        |
        //                              _|_ orig
        //
        orig.doCalculation(); // this will cause effort update on middle node.

        // if one resistor is of type "open connection", there will be 
        // no flow and that's the network solution.
        if (open && sourceType == ElementType.EFFORTSOURCE) {

            middleNode.setFlow(0.0, orig, true);
            middleNode.setFlow(0.0, resistorX, true);
            middleNode.setFlow(0.0, resistorY, true);

            resistorX.setFlow(0.0, middleNode);
            resistorY.setFlow(0.0, middleNode);

            source.setFlow(0.0, nodeY);
            if (bothOpen) {
                // this is a nondefnied case. The source is now floating so
                // there is no theroretical solution. It will be defined to
                // use the previous value from both nodes again.
                // nodeX.setEffort(nodeX.getEffort());
                // nodeY.setEffort(nodeY.getEffort());
                // this definition collides with the fact that no nonupdated
                // efforts must be used. therefore, we will set nodeY, which
                // is node 0 of the source, to a value of 0.0 and call the
                // doCalculation method on the source to calc the value on the
                // other node.
                nodeY.setEffort(0.0);
                source.doCalculation();
            }
            // doCalculation will propagate all known values from here on.
            resistorX.doCalculation();
            resistorY.doCalculation();

            return true;
        }
        if (sourceType == ElementType.FLOWSOURCE) {
            throw new UnsupportedOperationException("NOT YET IMPLEMENTED!");
        }

        // we do not know where nodeX and Y are connected to the source, this is
        // why they are named X and Y here, not with numbers. We assume that we
        // have positive voltage from X to Y, therefore X shall be connected to
        // index 1 of effort source. if not, we need to put negative sign.
        if (nodeX == source.getNode(1)) {
            effort = ((EffortSource) source).getEffort();
        } else {
            effort = -((EffortSource) source).getEffort();
        }

        // calculate total resistance:
        resistance = resistorX.getResistance() + resistorY.getResistance();

        // now we calculate flow from nodeX to nodeY in that direction using
        // ohms law I = U/R
        flow = effort / resistance;

        // set flow to all elements. This will cause the resistors to update
        // and calculate the efforts.
        resistorX.setFlow(flow, nodeX);
        resistorY.setFlow(-flow, nodeY);
        source.setFlow(flow, nodeY);

        resistorX.doCalculation();
        resistorY.doCalculation();

        // model should be fully updated by now.
        return true;
    }

    /**
     * Check if an element is of a type that can be seen as a resistor.
     *
     * Shortcuts or open connections will stay in the model, as these can be
     * just temporarily behave like this and the overall calculation does work
     * for those extreme conditions also.
     *
     * @param e element to check
     * @return true: it is a resistor or similar
     */
    protected static boolean isElementResistorType(AbstractElement e) {
        return e.getElementType() == ElementType.DISSIPATOR
                || e.getElementType() == ElementType.OPEN
                || e.getElementType() == ElementType.BRIDGED;
    }
}
