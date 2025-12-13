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
 * Stores flow energy. While at first glance it sounds like this would just
 * store flow energy to release it later, it also acts as a flow limitation. Try
 * to imagine pushing a car, you will feel the force with your hand as you apply
 * force to move a big mass. While not being present too much in hydraulic
 * systems, this is a base class for mechanical systems which always consider
 * moving masses.
 * <p>
 * Supports 1 or 2 nodes where it will force its flow variables onto. More than
 * 2 nodes are not supported as this would require knowledge about how flow is
 * distributed.
 * <p>
 * Positive flow value on this element means: flow into node 0, out of node 1.
 * <p>
 * flow = tau * integral(effort)
 *
 * @author Viktor Alexander Hartung
 */
public class Inductance extends EnergyStorage {
    
    public Inductance(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.INDUCTANCE;
    }

    /**
     * Set initial flow. Flow can only be set using this method, it is not
     * allowed for connected conserving nodes to set the flow. As flow is the
     * state space variable for inductance, this will update the state space
     * variable. Use for initialization only or with extreme caution.
     *
     * @param f positive: flow into node 0 and out of node 1
     */
    public void setFlow(double f) {
        stateValue = f;
    }

    public double getFlow() {
        return stateValue;
    }

    @Override
    public void registerNode(GeneralNode n) {
        if (!nodes.contains(n) & nodes.size() >= 2) {
            throw new ModelErrorException(
                    "Cannot add a third node to Inductance.");
        } else {
            super.registerNode(n);
        }
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;

        // state value is forced upon connected nodes to let other elements
        // via nodes know about state value.
        if (!nodes.get(0).flowUpdated(this)) {
            nodes.get(0).setFlow(stateValue, this, true);
            didSomething = true;
        }

        // again if there is a second node connected. note the sign on
        // stateValue due to convention of flow direction.
        if (nodes.size() == 2) {
            if (!nodes.get(1).flowUpdated(this)) {
                nodes.get(1).setFlow(-stateValue, this, true);
                didSomething = true;
            }
        }

        // to calculate new delta value, total effort applied to this Element
        // must be known.
        boolean effortsUpdated = nodes.get(0).effortUpdated();
        if (nodes.size() == 2) {
            effortsUpdated = effortsUpdated && nodes.get(1).effortUpdated();
        }
        if (effortsUpdated && !deltaCalculated) {

            didSomething = true;
        }
        return didSomething;
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        // nothing will happen here :)
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        throw new ModelErrorException(
                "Force flow value on Capacitance is illegal.");
    }

}
