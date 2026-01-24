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

/**
 * Raises effort between two nodes. If the set effort is positive, the effort on
 * node 1 will be higher than on node 0 by that value. This is according to the
 * convention that positive flow also goes in on node 0 and out of node 1.
 * <p>
 * Must be connected to two nodes!
 *
 * @author Viktor Alexander Hartung
 */
public class EffortSource extends FlowThrough {

    private double effort = 10.0;

    /**
     * Usually, an effort source can and will set its effort value to the
     * network immediately. However, if the effort value itself is not known at
     * the beginning, this variable will force to wait for an effort value to be
     * set. This is needed on connected systems where the source gets its value
     * from another network component externally.
     */
    private boolean effortUpdated = true;

    public EffortSource(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.EFFORTSOURCE;
    }

    /**
     * Set effort value for this effort source. Effort value of node 1 will be
     * higher than node 0 by the set value.
     *
     * @param e value to set
     */
    public void setEffort(double e) {
        if (!Double.isFinite(effort)) {
            throw new IllegalArgumentException(
                    "Non-finite effort value (NaN or inf) provided.");
        }
        effort = e;
        effortUpdated = true;
    }

    /**
     * Will force the effort source to wait until an effort value was set with
     * setEffort(double e) method. Otherwise, the effort source will set its
     * effort automatically on firing doCalculation or if an effort value is set
     * on one of both nodes. Usually needed for connected networks with
     * different domain where the effort source serves as a connection element
     * which gets its value from a different network that has to be solved
     * first.
     */
    public void resetEffortUpdated() {
        effortUpdated = false;
    }

    /**
     * Get the set effort for this effort source. Effort value of node 1 will be
     * higher than node 0 by the set value.
     *
     * @return effective effort value
     */
    public double getEffort() {
        return effort;
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        if (!effortUpdated) { // value not yet known
            return; // wait
        }
        if (nodes.indexOf(source) == 0) {
            if (!nodes.get(1).effortUpdated()) { // avoid loops etc
                nodes.get(1).setEffort(effort + this.effort, this, true);
            }
        } else if (nodes.indexOf(source) == 1) {
            if (!nodes.get(0).effortUpdated()) {
                nodes.get(0).setEffort(effort - this.effort, this, true);
            }
        }
    }

    @Override
    public boolean doCalculation() {
        if (!effortUpdated) { // value not yet known
            return false; // wait
        }
        // check if 1 of 2 efforts is updated, then update second node.
        // should not be necessary as the element always passes through as
        // soon as one effort is known. 
        if (nodes.get(0).effortUpdated()
                && !nodes.get(1).effortUpdated()) {
            nodes.get(1).setEffort(nodes.get(0).getEffort() + effort,
                    this, true);
            return true;
        } else if (!nodes.get(0).effortUpdated()
                && nodes.get(1).effortUpdated()) {
            nodes.get(0).setEffort(nodes.get(1).getEffort() - effort,
                    this, true);
            return true;
        }
        return false;
    }

    @Override
    public void setStepTime(double dt) {
        // this class does not use step time
    }

}
