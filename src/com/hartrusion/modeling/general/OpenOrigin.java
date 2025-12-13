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
import com.hartrusion.modeling.exceptions.ModelErrorException;

/**
 * Sets a fixed effort value to one node, regardless of flow value. This acts as
 * coordinate system origin. It allows any flow in and out and just forces the
 * effort value, therefore it's called open origin. If the model is not a closed
 * loop, open origins will be used to allow a flow into or out of the model.
 * <p>
 * While it is possible and intended to have multiple origins, also with
 * multiple effort values, it is not intended to change the effort after the
 * model was set up. For changing effort values, an effort source must be used.
 *
 * @author Viktor Alexander Hartung
 */
public class OpenOrigin extends Origin {

    public OpenOrigin(PhysicalDomain domain) {
        super(domain);
        if (domain == PhysicalDomain.ELECTRICAL) {
            throw new ModelErrorException("No open origin may ever exist in "
                    + "electrical domain.");
        }
    }

    @Override
    public boolean doCalculation() {
        if (!nodes.get(0).effortUpdated()) {
            nodes.get(0).setEffort(effort, this, true);
            return true;
        }
        return false;
    }

    @Override
    public boolean isCalculationFinished() {
        // this just sets effort value to 1 node so just return if its done.
        return nodes.get(0).effortUpdated();
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        // Nothing will happen here basically. All flows are allowed.
    }

    /**
     * Returns weather the flow to or from this element is in an updated state
     * for this calculation run.
     *
     * @return true, if value was set.
     */
    public boolean flowUpdated() {
        return nodes.get(0).flowUpdated(this);
    }

    /**
     * Returns the current flow value into this origin.
     *
     * @return flow variable. Positive: Flow goes into this origin.
     */
    public double getFlow() {
        return nodes.get(0).getFlow(this);
    }
}
