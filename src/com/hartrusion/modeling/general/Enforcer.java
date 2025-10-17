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

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;

/**
 * An absolute source which forces both flow and effort to the connected node.
 * This element is outside the normal electronic like circuit and is not
 * intended to be used for simple physical modeling. However, the steam domain
 * does require such elements to exist. It also can be used for testing purposes
 * to test single elements without setting up a whole network.
 *
 * @author Viktor Alexander Hartung
 */
public class Enforcer extends AbstractElement {

    protected double effort;
    protected double flow;

    public Enforcer(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.ENFORCER;
        effort = 0; // init
    }

    @Override
    public void registerNode(GeneralNode n) {
        if (!nodes.isEmpty() & !nodes.contains(n)) {
            throw new ModelErrorException(
                    "Enforcer only supports one node");
        } else {
            super.registerNode(n); // considers also if its already added
        }
    }

    /**
     * Set fixed effort value which will be forced to connected node. This can
     * only be set using this method, it is not allowed for connected conserving
     * nodes to set the effort.
     *
     * @param e Enforced effort value
     */
    public void setEffort(double e) {
        effort = e;
    }

    /**
     * Returns the effort value that this Enforcer is forcing on the node.
     *
     * @return effort value
     */
    public double getEffort() {
        return effort;
    }

    /**
     * Set fixed effort value which will be forced to connected node. This can
     * only be set using this method, it is not allowed for connected conserving
     * nodes to set the effort.
     *
     * <p>
     * Contrary to conventions between elements and nodes, a positive flow will
     * flow out of this element (as this is what this element is supposed to
     * do).
     *
     * @param f Enforced flow value.
     */
    public void setFlow(double f) {
        flow = f;
    }

    /**
     * Returns the flow value that this Enforcer is forcing on the node.
     *
     * <p>
     * Contrary to conventions between elements and nodes, a positive flow will
     * flow out of this element (as this is what this element is supposed to
     * do).
     *
     * @return flow value. Positive: Goes into enforcer, negative: Flows to
     * node.
     */
    public double getFlow() {
        return flow;
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        throw new ModelErrorException(
                "Force effort value on enforcer is illegal.");
    }

    @Override
    public void setStepTime(double dt) {
        // this class does not use stepTime
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        // Deep dive fact: Flow has to be updated first. Otherwise, setting the
        // effort might fire back a set-operation on this element by the method
        // call from here before the set-flag ist updated.
        if (!nodes.get(0).flowUpdated(this)) {
            nodes.get(0).setFlow(-flow, this, true); // reverse: positive leaves
            didSomething = true;
        }
        if (!nodes.get(0).effortUpdated()) {
            nodes.get(0).setEffort(effort, this, true);
            didSomething = true;
        }
        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // this just sets effort value to 1 node so just return if its done.
        return nodes.get(0).effortUpdated() && nodes.get(0).flowUpdated(this);
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        throw new ModelErrorException(
                "Force flow value on a enforcer is illegal.");
    }

}
