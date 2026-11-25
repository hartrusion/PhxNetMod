/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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
package com.hartrusion.modeling.material;

import com.hartrusion.modeling.general.AbstractElement;

/**
 * Material part of an element at the boundary of a network, like an origin or
 * an enforcer, which provides fixed concentrations for the flows that enter
 * the network through it. Flows that leave the network through the element do
 * not change these concentrations. The concentrations are 0.0 when a material
 * is added and have to be defined with setConcentration.
 */
public class MaterialOrigin extends MaterialElement {

    /**
     * Fixed concentration of each material for all flows leaving the element
     * into the network.
     */
    private double[] concentration = new double[0];

    public MaterialOrigin(AbstractElement element) {
        super(element);
    }

    @Override
    public boolean addMaterial(String materialName) {
        if (!super.addMaterial(materialName)) {
            return false;
        }
        concentration = MaterialNode.extendArray(concentration,
                getMaterialCount());
        return true;
    }

    @Override
    public void prepareMaterialCalculation() {
        // The concentrations are fixed, there is no state to update.
    }

    /**
     * Sets the fixed concentrations on all connections with a flow leaving the
     * element. Connections with a flow into the element get their value from
     * the node mixing.
     *
     * @return true if something was calculated.
     */
    @Override
    protected boolean calculateElementConcentrations() {
        boolean didSomething = false;
        double flow;
        for (int idx = 0; idx < materialNodes.length; idx++) {
            if (!materialNodes[idx].getNode().flowUpdated(element)) {
                continue;
            }
            flow = getInflow(idx);
            if (flow == 0.0) {
                didSomething = setOutflowNoConcentration(idx) || didSomething;
            } else if (flow < 0.0) {
                didSomething = setOutflowConcentrations(idx, concentration)
                        || didSomething;
            }
        }
        return didSomething;
    }

    /**
     * Defines the concentration of a material that enters the network through
     * this element.
     *
     * @param materialName Unique name of the material.
     * @param value Concentration value.
     */
    public void setConcentration(String materialName, double value) {
        concentration[getMaterialIndex(materialName)] = value;
    }

    public double getConcentration(String materialName) {
        return concentration[getMaterialIndex(materialName)];
    }
}
