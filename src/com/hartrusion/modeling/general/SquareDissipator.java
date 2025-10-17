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
import static java.lang.Math.pow;
import static java.lang.Math.sqrt;
import com.hartrusion.modeling.PhysicalDomain;

/**
 * Dissipates energy by transforming it to excess heat. Requires exactly two
 * nodes to be used. Does not store energy and therefore has no state space
 * variable. Allows different effort values on each node and has same flow value
 * on both nodes.
 *
 * <p>
 * DO NOT USE! IT'S NOT WORKING! Behaves horribly for discrete time steps.
 *
 * <p>
 * Square formula: effort = flow^2 * zeta;
 *
 * <p>
 * As the effort rises with positive flow the direction has to be into this
 * element for positive flow values.
 *
 * @author Viktor Alexander Hartung
 */
public class SquareDissipator extends FlowThrough {

    protected double zeta; // main parameter

    public SquareDissipator(PhysicalDomain physicalDomain) {
        super(physicalDomain);
        elementType = ElementType.DISSIPATOR;
        zeta = 0.01; // prevent div/0
    }

    /**
     * Set zeta coefficient. effort = flow^2 * zeta;
     *
     * @param z zeta
     */
    public void setZetaParameter(double z) {
        this.zeta = z;
    }

    /**
     * Calculates and sets zeta for two given values of effort and flow. This is
     * a shortcut, instead of using setZetaParameter, there can just be two
     * values be set, like the flow and pressure drop of a fully open valve on
     * full power.
     *
     * <p>
     * effort = flow^2 * zeta;
     *
     * <p>
     * flow = sqrt(effort) 
     *
     * @param effort value of operation, must be a positive value.
     * @param flow value of operation, must be a positive value
     */
    public void setOperatingPoint(double effort, double flow) {
        this.zeta = effort / pow(flow,2);
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        double deltaEffort;
        double myFlow;

        if (nodes.size() != 2) {
            throw new UnsupportedOperationException(
                    "Dissipator must have exactly two nodes for calculation");
        } else if (isCalculationFinished()) {
            return false; // nothing to do.
        } else {
            // same as for linear resistor, 2 of 3 node values must be known
            // while efforts will be used as delta (therefore 3, not 4).
            if (nodes.get(0).flowUpdated(this)
                    && nodes.get(0).effortUpdated()
                    && !nodes.get(1).effortUpdated()) {
                // case1: flow and effort on node 0 known
                myFlow = nodes.get(0).getFlow(this);
                if (myFlow > 0) {
                    deltaEffort = pow(myFlow, 2) * zeta;
                } else if (myFlow < 0) { // keep sign of flow, even with square
                    deltaEffort = -pow(myFlow, 2) * zeta;
                } else {
                    deltaEffort = 0;
                }
                nodes.get(1).setEffort(nodes.get(0).getEffort() - deltaEffort, this, true);
                didSomething = true;
            } else if (nodes.get(0).flowUpdated(this)
                    && nodes.get(1).effortUpdated()
                    && !nodes.get(0).effortUpdated()) {
                // case2: flow and effort on node 1 known
                myFlow = nodes.get(1).getFlow(this);
                if (myFlow > 0) {
                    deltaEffort = pow(myFlow, 2) * zeta;
                } else if (myFlow < 0) { // keep sign of flow, even with square
                    deltaEffort = -pow(myFlow, 2) * zeta;
                } else {
                    deltaEffort = 0;
                }
                nodes.get(0).setEffort(nodes.get(1).getEffort() - deltaEffort, this, true);
                didSomething = true;
            } else if (nodes.get(0).effortUpdated()
                    && nodes.get(1).effortUpdated()
                    && !nodes.get(0).flowUpdated(this)) {
                // case 3: both efforts are given, flow is missing
                // effort / zeta = flow ^2
                // sqrt(effort/zeta) = flow
                // but ^2 and sqrt only for positive values.
                deltaEffort = nodes.get(1).getEffort()
                        - nodes.get(0).getEffort();
                myFlow = zeta / deltaEffort; // this is flow^2
                if (myFlow > 0) {
                    myFlow = sqrt(myFlow);
                } else if (myFlow < 0) { // keep sign of flow, even with square
                    myFlow = -sqrt(-myFlow);
                } else {
                    myFlow = 0;
                }
                nodes.get(0).setFlow(-myFlow, this, true);
                nodes.get(1).setFlow(myFlow, this, true);
                didSomething = true;
            }
        }

        return didSomething;
    }
    
    @Override
    public void setStepTime(double dt) {
        // this class does not use stepTime
    }
    
    
}
