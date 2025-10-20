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
import com.hartrusion.modeling.general.CalculationStep;
import com.hartrusion.modeling.general.AbstractElement;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class SimpleIterator {

    List<CalculationStep> calc = new ArrayList<>();
    int numberOfOrigins = 0;

    /**
     * Highest index in list calc that is not of a resistor type.
     */
    int lastNonResistanceIndex = 0;

    /**
     * Adds an element to the list of elements which shall recceive a solution
     * by this solver. Origins and sources will be automatically priorized so
     * there is no need to consider an order of adding for those.
     *
     * @param e Element as AbstractElement to add
     */
    public void addElement(AbstractElement e) {
        // when solving it makes sense to start from origin and
        // sources to push calculated values in a decent manner
        // through the model. Therefore add the sources first.
        switch (e.getElementType()) {
            case ORIGIN:
            case ENFORCER:
                calc.add(0, e);
                lastNonResistanceIndex = calc.size() - 1;
                numberOfOrigins++;
                break;
            case EFFORTSOURCE:
            case FLOWSOURCE:
                // place sources after origin(s)
                calc.add(numberOfOrigins, e);
                lastNonResistanceIndex = calc.size() - 1;
                break;
            default:
                calc.add(e);
        }
    }

    /**
     * Prepares all added elements and nodes for the next calculation iteration.
     */
    public void prepareCalculation() {
        for (CalculationStep calcStep : calc) {
            calcStep.prepareCalculation();
        }
    }

    /**
     * Invokes doCalculation on origins, effort and flow sources only. This will
     * be used in RecursiveSimplifier to prepare the network top-down before
     * calculating it from bottom to up. The recursive calculation will invoke
     * setEffort/setFlow methods on the parents network elements, a result might
     * be that a resistor may try to set a variable that is intended to be
     * forced by a source element. This would result in an exception, as forcing
     * variables to a source is illegal. To handle this, we just force variables
     * from the sources and origins first.
     */
    public void doCalculationOnEnforcerElements() {
        boolean didSomething;
        int iterations = 0;
        while (true) {
            didSomething = false;
            for (int idx = 0; idx <= lastNonResistanceIndex; idx++) {
                didSomething = calc.get(idx).doCalculation() || didSomething;
            }
            iterations++;
            if (iterations >= 1000) {
                throw new UnsupportedOperationException("Endless iterations?");
            }
            if (!didSomething) {
                break; // give up if nothing more happens.
            }
        }
    }
    
    /**
     * Calls calculation on all elements of this network until no further
     * calculation is possible.
     */
    public void doCalculation() {
        boolean didSomething;
        int iterations = 0;
        while (true) {
            didSomething = false;
            for (CalculationStep calcStep : calc) {
                didSomething = calcStep.doCalculation() || didSomething;
            }
            iterations++;
            if (iterations >= 1000) {
                throw new UnsupportedOperationException("Endless iterations?");
            }
            if (!didSomething) {
                break; // give up if nothing more happens.
            }
        }
    }

    /**
     * Checks if all elements assigned to this network are fully calculated.
     *
     * @return true, if everything was calculated.
     */
    public boolean isCalculationFinished() {
        for (CalculationStep calcStep : calc) {
            if (!calcStep.isCalculationFinished()) {
                return false;
            }
        }
        return true;
    }

    public boolean containsElement(AbstractElement element) {
        if (element instanceof CalculationStep) {
            return calc.contains((CalculationStep) element);
        }
        return false;
    }

}
