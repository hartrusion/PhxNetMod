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
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;

/**
 * Will pass flow through the element. Acts as abstract base class for
 * dissipator class or other elements with two nodes and no flow storage
 * capability.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class FlowThrough extends AbstractElement {

    public FlowThrough(PhysicalDomain domain) {
        super(domain);
    }

    @Override
    public void registerNode(GeneralNode n) {
        if (!nodes.contains(n) & nodes.size() >= 2) {
            throw new ModelErrorException(
                    "Cannot add a third node to flow-through elements");
        } else {
            super.registerNode(n);
        }
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        // any effort set is allowed, so nothing happens here.
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        // if flow is set, just pass it through this Element.
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        if (flow == 0.0 && Double.compare(flow, 0.0) != 0) {
            // prevent a negative zero flow. It's not bad but its extra work.
            if (!nodes.get(idx).flowUpdated(this)) {
                nodes.get(idx).setFlow(0.0, this, true);
            }
        } else {
            if (!nodes.get(idx).flowUpdated(this)) {
                // what comes IN, goes OUT
                nodes.get(idx).setFlow(-flow, this, true);
            }
        }
    }

    /**
     * Sets a known flow which is then used for further calculations. The sign
     * is according to the flow of super class FlowThrough, meaning positive
     * flow value on this element means: flow into node 0, out of node 1.
     *
     * <p>
     * This is used to provide information to the model for next steps of
     * calculation.
     *
     * @param flow value to set
     * @param update true if the set operation shall trigger possible
     * calculations and update as many things as possible. Set to false to only
     * transfer values without triggering any update process.
     */
    public void setFlow(double flow, boolean update) {
        nodes.get(0).setFlow(flow, this, update);
        nodes.get(1).setFlow(-flow, this, update);
    }

    public boolean flowUpdated() {
        return nodes.get(0).flowUpdated(this);
    }

    /**
     * Returns flow through this two-node element. Positive flow value on this
     * element means: flow into node 0, out of node 1.
     *
     * @return flow value
     */
    public double getFlow() {
        if (elementType == ElementType.OPEN) {
            return 0.0;
        }
        if (!nodes.get(0).flowUpdated(this)) {
            throw new CalculationException("Requested flow but it is not in "
                    + "required updated state.");
        }
        return nodes.get(0).getFlow(this);
    }

    /**
     * Modifies element type. Used primarily for network analysis, this allows
     * to set a different element type like an open connection or a shortcut to
     * this specific element.
     *
     * @param t
     */
    public void setElementType(ElementType t) {
        this.elementType = t;
    }

}
