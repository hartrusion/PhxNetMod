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
 * @author Viktor Alexander Hartung
 */
public abstract class Origin extends AbstractElement {

    protected double effort;

    public Origin(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.ORIGIN;
        effort = 0; // init
    }

    @Override
    public void registerNode(GeneralNode p) {
        if (!nodes.isEmpty() & !nodes.contains(p)) {
            throw new ModelErrorException(
                    "Origin only supports one node");
        } else {
            super.registerNode(p); // considers also if its already added
        }
    }

    /**
     * Set fixed effort value which will be forced to connected node. This can
     * only be set using this method, it is not allowed for connected conserving
     * nodes to set the effort.
     *
     * @param e
     */
    public void setEffort(double e) {
        effort = e;
    }

    /**
     * Returns the effort value that this Origin is forcing on the node.
     *
     * @return effort value
     */
    public double getEffort() {
        return effort;
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        throw new ModelErrorException(
                "Force effort value on Origin is illegal.");
    }

    @Override
    public void setStepTime(double dt) {
        // this class does not use steptime
    }
}
