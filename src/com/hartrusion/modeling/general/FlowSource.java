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
 *
 * @author Viktor Alexander Hartungr
 */
public class FlowSource extends FlowThrough {

    private double flow = 0.02;

    /**
     * Setting the flow with the doCalculation method can result in the flow
     * source getting the flow set by itself. Usually it is not legal to set
     * flow on a flow source but in this case, the source itself ist the reason
     * for this to happen
     */
    protected boolean allowLooping;

    public FlowSource(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.FLOWSOURCE;
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        allowLooping = true;
        if (!nodes.get(0).flowUpdated(this)) {
            nodes.get(0).setFlow(flow, this, true);
            didSomething = true;
        }
        if (!nodes.get(1).flowUpdated(this)) {
            nodes.get(1).setFlow(-flow, this, true);
            didSomething = true;
        }
        allowLooping = false;
        return didSomething;
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        if (!allowLooping) {
            throw new ModelErrorException(
                    "Setting a flow on a flow source from a node is illegal.");
        }
    }

    @Override
    public void setFlow(double f, boolean update) {
        flow = f;
    }

    /**
     * Sets the flow value on this source, not calling any model updates. Used
     * to parametrize the model.
     *
     * @param f Flow value to set.
     */
    public void setFlow(double f) {
        flow = f;
    }

    @Override
    public double getFlow() {
        // always return the set flow, not the flow from nodes.
        return flow;
    }

    @Override
    public void setStepTime(double dt) {
        // this class does not use stepTime
    }

}
