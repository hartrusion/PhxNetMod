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

import com.hartrusion.modeling.PhysicalDomain;

/**
 * Mutual capacitances store effort energy relative between two nodes. A popular
 * example is the electronic condensator which has two connections. It will
 * never enforce an effort value but rather force an effort difference between
 * the connected nodes.
 *
 * <p>
 * Behaviour: flow = C * dEffort/dt or effort = 1/C * int(flow) whereas in
 * general terms C is refered as the time constant tau for general elements.
 *
 * <p>
 * Flow that goes into this element leaves the element on the other node.
 *
 * <p>
 * The sign is according to the flow of super class FlowThrough, meaning
 * positive flow value on this element means: flow into node 0, out of node 1,
 * therefore positive effort means that effort on node 0 is higher than on node
 * 1. This is only a reference to the internal effort value, if the element is
 * reversed in a circuit, it will behave exactly as before.
 *
 * @author Viktor Alexander Hartung
 */
public class MutualCapacitance extends Capacitance {
    
    MutualCapacitance(PhysicalDomain domain) {
        super(domain);
    }

    @Override
    public boolean doCalculation() {
        // as soon as flow is known, a delta value can be calculated.
        if (!deltaCalculated && nodes.get(0).flowUpdated(this)) {
            delta = nodes.get(0).getFlow(this) * stepTime;
            deltaCalculated = true;
            super.integrate(); // integrates
            return true;
        }
        return false;
    }

    /**
     * A mutual capacitance has the characteristics that all the flow goes
     * through and the integration of the flow will result in effort storage.
     *
     * @param flow
     */
    public void setFlow(double flow) {
        nodes.get(0).setFlow(flow, this, true);
        nodes.get(1).setFlow(-flow, this, true);
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        if (nodes.indexOf(source) == 0
                && !nodes.get(1).effortUpdated()) {
            nodes.get(1).setEffort(nodes.get(0).getEffort() - stateValue);
        } else if (nodes.indexOf(source) == 1
                && !nodes.get(0).effortUpdated()) {
            nodes.get(0).setEffort(nodes.get(1).getEffort() + stateValue);
        }
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        // if flow is set, just pass it through this Element.
        int idx = nodes.indexOf(source);
        idx = -idx + 1; // switch 0 and 1
        if (flow == -0.0) {
            // prevent a negative zero flow. Its not bad but its extra work.
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

}
