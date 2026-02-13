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
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;
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

    private final List<GeneralNode> nodes = new ArrayList<>();
    private final List<AbstractElement> elements = new ArrayList<>();

    public void addElement(AbstractElement e) {
        if (!elements.contains(e)) {
            elements.add(e);
            for (int idx = 0; idx < e.getNumberOfNodes(); idx++) {
                if (!nodes.contains(e.getNode(idx))) {
                    nodes.add(e.getNode(idx));
                }
            }

        }
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
