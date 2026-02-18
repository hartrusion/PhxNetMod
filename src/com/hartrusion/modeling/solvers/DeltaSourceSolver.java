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
import static com.hartrusion.modeling.ElementType.BRIDGED;
import static com.hartrusion.modeling.ElementType.DISSIPATOR;
import static com.hartrusion.modeling.ElementType.EFFORTSOURCE;
import static com.hartrusion.modeling.ElementType.FLOWSOURCE;
import static com.hartrusion.modeling.ElementType.OPEN;
import static com.hartrusion.modeling.ElementType.ORIGIN;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import java.util.ArrayList;
import java.util.List;

/**
 * Solves a specific arrangement of source, resistors and an origin. This is
 * needed when not using the Overlay simplification with the SuperPosition
 * solver.
 * <p>
 * This class is designed to solve a very specific case: There are three
 * resistors between three nodes (delta arrangement), with one source parallel
 * to one of the resistors and an origin on the node which is on the opposing
 * side of the source. This arrangement can not be simplified further with the
 * existing means of any of the simplifications available and has to be solved
 * manually.
 *
 * @author Viktor Alexander Hartung
 */
public class DeltaSourceSolver {

    private List<GeneralNode> nodes;
    private List<AbstractElement> elements;

    private final SimpleIterator lastIterator = new SimpleIterator();

    private int resistors = 0;
    private int sources = 0;
    private int origins = 0;
    private LinearDissipator resistorX = null;
    private LinearDissipator resistorY = null;
    private LinearDissipator resistorZ = null;
    private ClosedOrigin orig = null;
    private EffortSource source = null;
    private GeneralNode middleNode = null;
    private GeneralNode nodeX;
    private GeneralNode nodeY;

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
        
        if (elements.size() != 5) {
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
                    source = (EffortSource) e;
                    break;
                case ORIGIN:
                    origins++;
                    orig = (ClosedOrigin) e;
                    break;
            }
        }
        if (resistors != 3 || sources != 1 || origins != 1) {
            return false; // wrong element combination
        }

        // try to identify all three nodes and the connecting elements.
        // The network looks like this:
        // 
        //        0      source           1 <- node index on source
        //        .----------(--)---------.
        //        |                       |
        //  nodeX o---------XXXXXX--------o nodeY
        //        |       resistorZ       |
        //        |       middleNode      |
        //        .---XXXXX---o---XXXXX---.
        //         resistorX  |   resistorY
        //                   _|_
        //                   orig
        middleNode = orig.getNode(0);
        nodeX = source.getNode(0);
        nodeY = source.getNode(1);
        resistorZ = findElementBetween(nodeX, nodeY);
        resistorX = findElementBetween(nodeX, middleNode);
        resistorY = findElementBetween(nodeY, middleNode);

        if (resistorZ == null || resistorX == null || resistorY == null) {
            return false; // unable to find the exact layout that was expected.
        }

        return true;
    }

    public boolean solve() {
        double resistance, flow, effortX, effortY;

        // special case: set effort to 0.0
        if (Math.abs(source.getEffort()) < 1e-12) {
            nodeX.setEffort(orig.getEffort());
            nodeY.setEffort(orig.getEffort());
            middleNode.setEffort(orig.getEffort());
            resistorX.setFlow(0.0, nodeX);
            resistorY.setFlow(0.0, nodeY);
            resistorZ.setFlow(0.0, nodeX);
            lastIterator.doCalculation();
            return true;
        }

        // unsolveable case 1: resistor Z shorted but source has effort
        if (Math.abs(source.getEffort()) > 1e-12
                && resistorZ.getElementType() == ElementType.BRIDGED) {
            throw new CalculationException("Illegal bridged element parallel "
                    + "to Effort source detected.");
        }

        // illegal case 2: resistors X and Y are shorted but source has effort
        if (Math.abs(source.getEffort()) > 1e-12
                && resistorX.getElementType() == ElementType.BRIDGED
                && resistorY.getElementType() == ElementType.BRIDGED) {
            throw new CalculationException("Illegal bridged path over 2 "
                    + "parallel to an Effort source detected.");
        }

        // Another special case: both lower (X and Y) elements open. It does not
        // matter what value resistorZ has as the iterative call will solve 
        // this.
        if (resistorX.getElementType() == ElementType.OPEN
                && resistorY.getElementType() == ElementType.OPEN) {
            nodeX.setEffort(orig.getEffort()); // set one effort here
            lastIterator.doCalculation(); // do the rest.
            return true;
        }

        // resistorX open: This forces nodeY to the origin effort and no flow
        // through both X and Y resistors.
        if (resistorX.getElementType() == ElementType.OPEN) {
            nodeY.setEffort(orig.getEffort()); // force effort potential here
            resistorX.setFlow(0.0, nodeX);
            resistorY.setFlow(0.0, nodeY);
            lastIterator.doCalculation(); // do the rest.
            return true;
        }

        // same for X resistor
        if (resistorY.getElementType() == ElementType.OPEN) {
            nodeX.setEffort(orig.getEffort()); // force effort potential here
            resistorX.setFlow(0.0, nodeX);
            resistorY.setFlow(0.0, nodeY);
            lastIterator.doCalculation(); // do the rest.
            return true;
        }

        // The end: now we have a flow through the X and Y part, we will 
        // manually calculate this. Add series resistances (its okay if one 
        // is a shortcut here). Now comes some common ohms law and circuit
        // calculations.
        resistance = resistorX.getResistance() + resistorY.getResistance();
        flow = source.getEffort() / resistance;

        // Either set the effort of one node if there is a shortcut or use
        // the unloaded voltage divider formula.
        if (resistorX.getElementType() == ElementType.BRIDGED) {
            nodeX.setEffort(orig.getEffort());
        } else if (resistorY.getElementType() == ElementType.BRIDGED) {
            nodeY.setEffort(orig.getEffort());
        } else {
            effortX = resistorX.getResistance() * flow;
            effortY = resistorY.getResistance() * flow;
            // we can do this as the sign of the effort and flow is defined.
            nodeX.setEffort(orig.getEffort() - effortX);
            nodeY.setEffort(orig.getEffort() + effortY);
        }
        // Now the iterator can do its job. We do not set flow here as we do 
        // not know the direction in which the resistors are placed in.
        lastIterator.doCalculation();
        return true;
    }

    /**
     * Finds the Resistor which is connected between two given ndoes in the
     * elements list.
     *
     * @param n1
     * @param n2
     * @return
     */
    private LinearDissipator findElementBetween(
            GeneralNode n1, GeneralNode n2) {
        for (AbstractElement e : elements) {
            if (!isElementResistorType(e)) {
                continue;
            }
            if (n1.isElementRegistered(e)
                    && n2.isElementRegistered(e)) {
                return (LinearDissipator) e;
            }
        }
        return null;
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
