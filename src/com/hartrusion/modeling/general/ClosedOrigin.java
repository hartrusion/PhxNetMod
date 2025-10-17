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
 * Sets a fixed effort value to a node while not allowing any flow through the
 * origin. This is a strict effort set as a coordinate system zero which is used
 * for electronics circuits. For obvious reasons, it can hardly exist with a
 * SelfCapacitance in one network, as a self capacitance has to get its stored
 * flow from somewhere. This element does not allow this.
 *
 * <p>
 * While it is possible and intended to have multiple origins, also with
 * multiple effort values, it is not intended to change the effort after the
 * model was set up. For changing effort values, an effort source must be used.
 *
 * @author Viktor Alexander Hartung
 */
public class ClosedOrigin extends Origin {
    
    public ClosedOrigin(PhysicalDomain domain) {
        super(domain);
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        // Deep dive fact: Flow has to be updated first. Otherwise, setting the
        // effort might fire back a set-operation on this element by the method
        // call from here before the set-flag ist updated.
        if (!nodes.get(0).flowUpdated(this)) {
            nodes.get(0).setFlow(0.0, this, true);
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
        // this just sets effort value to 1 node so just return if it's done.
        return nodes.get(0).effortUpdated() && nodes.get(0).flowUpdated(this);
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        throw new ModelErrorException(
                "Force flow value on a closed origin is illegal.");
    }
}
