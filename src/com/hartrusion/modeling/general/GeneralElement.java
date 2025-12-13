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
package com.hartrusion.modeling.general;

/**
 * Interface for connecting generalized elements with nodes. Nodes will call
 * those methods to let elements know that effort or flow value has been
 * updated. This might result in the element to pass through this information or
 * do nothing at all, depending on what kind of element it is. This is the way
 * the effort and flow information are passed through the model as far as
 * possible.
 * <p>
 * Calling the methods will only pass on information but not trigger any
 * calculation at all. Calculations will be done using the CalculationStep
 * interface.
 * <p>
 * It is meant that methods of this interface are getting called by the node
 * instances.
 *
 * @author Viktor Alexander Hartung
 */
public interface GeneralElement {

    /**
     * Set the effort from a connected node.This method is meant to perform a
     * set-only thing, they will eventually pass through this element and
     * continue setting to other elements, but they will never trigger a
     * calculation. As effort will be saved in nodes, it might not have any
     * consequence.
     *
     * @param effort Effort set from node to this element
     * @param source GeneralNode calling the function to identify source
     */
    public void setEffort(double effort, GeneralNode source);

    /**
     * Set the flow from a connected node. As flow has a direction, it is a
     * signed value. This method is meant to perform a set-only thing, they will
     * eventually pass through this element and continue setting to other
     * elements, but they will never trigger a calculation.
     *
     * @param flow positive: flows into this element and comes from node.
     * negative: flows out of this element towards node.
     * @param source GeneralNode calling the function to identify source
     */
    public void setFlow(double flow, GeneralNode source);
}
