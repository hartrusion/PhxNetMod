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
 * Self capacitance is the ability to "just" store the effort energy. It will be
 * the same effort value on all nodes, and it will also enforce the effort value
 * to all those connected nodes.
 *
 * <p>
 * Behaviour: flow = C * dEffort/dt or effort = 1/C * int(flow) whereas in
 * general terms C is referred as the time constant tau for general elements.
 *
 * <p>
 * Flow that goes into this will sup up as effort and not leave the element. It
 * represents a hydraulic tank or a thermal mass.
 *
 * @author Viktor Alexander Hartung
 */
public class SelfCapacitance extends Capacitance {

    public SelfCapacitance(PhysicalDomain domain) {
        super(domain);
    }

    @Override
    public boolean doCalculation() {
        // state value is forced upon all connected nodes to let other elements
        // via nodes know about state value.
        return setEffortValueOnNodes(stateValue) || calculateNewDeltaValue();
    }

    /**
     * Set the Effort value which is the state value of a self capacitance to
     * all nodes. Is called from doCalculation, here it is made of a separate
     * method to have the possibility to override it.
     *
     * @param effort Value that will be set on all nodes
     * @return true if there were changes made by this method
     */
    protected boolean setEffortValueOnNodes(double effort) {
        boolean allEffortSet = true;
        for (GeneralNode n : nodes) {
            allEffortSet = allEffortSet && n.effortUpdated();
        }
        if (!allEffortSet) { // this should be false only once per cycle
            for (GeneralNode n : nodes) {
                n.setEffort(effort, this, true);
            }
            return true;
        }
        return false;
    }

    /**
     * Sums up the flow that goes into the element in one timestep. Is called
     * from doCalculation, here it is made of a separate method to have the
     * possibility to override it.
     *
     * @return true if there were changes made by this method
     */
    protected boolean calculateNewDeltaValue() {
        // to calculate new delta value, all flow values must have been updated
        boolean allFlowSet = true;
        double flowSum = 0;
        if (!deltaCalculated) {
            for (GeneralNode cp : nodes) {
                allFlowSet = allFlowSet && cp.flowUpdated(this);
                if (cp.flowUpdated(this)) {
                    flowSum = flowSum + cp.getFlow(this);
                }
            }
            if (allFlowSet) {
                delta = flowSum * stepTime; // delta is the flowed amount
                deltaCalculated = true;
                super.integrate(); // integrates
                return true;
            }
            return false;
        }
        return false;
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        throw new ModelErrorException(
                "Force effort value on self capacitance is illegal.");
    }

    @Override
    public void setFlow(double flow, GeneralNode source) {
        // nothing will happen here, this is totally fine.
    }
}
