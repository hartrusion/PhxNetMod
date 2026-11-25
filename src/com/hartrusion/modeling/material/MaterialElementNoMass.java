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
 * Material part of an element without an own content, like a flow resistance,
 * a valve or a pump, which passes the concentrations through without any
 * delay. If more than one flow goes into the element, the inflows are mixed
 * ideally and the mixture leaves the element on all outbound connections. This
 * is the same calculation as the HeatSimpleHandler does for the temperature.
 */
public class MaterialElementNoMass extends MaterialElement {

    /**
     * Concentrations of the mixed inflow, which is kept as a field only to
     * avoid an allocation on each run.
     */
    private double[] mixedConcentration = new double[0];

    public MaterialElementNoMass(AbstractElement element) {
        super(element);
    }

    @Override
    public boolean addMaterial(String materialName) {
        if (!super.addMaterial(materialName)) {
            return false;
        }
        mixedConcentration = new double[getMaterialCount()];
        return true;
    }

    @Override
    public void prepareMaterialCalculation() {
        // There is no state inside the element.
    }

    /**
     * Mixes all inflows ideally and sets the mixture on all connections that do
     * not have an inflow. If there is no flow at all or no inflow with a
     * concentration, the remaining connections are marked to have no
     * concentration.
     *
     * @return true if something was calculated.
     */
    @Override
    protected boolean calculateElementConcentrations() {
        if (!allFlowsUpdated() || allConcentrationsUpdated()) {
            return false;
        }
        double flow;
        double inflow = 0.0;
        for (int mdx = 0; mdx < mixedConcentration.length; mdx++) {
            mixedConcentration[mdx] = 0.0;
        }
        for (int idx = 0; idx < materialNodes.length; idx++) {
            flow = getInflow(idx);
            if (flow <= 0.0) {
                continue; // zero flow or flow leaving the element
            }
            if (!materialNodes[idx].isConcentrationUpdated(
                    connectionIndex[idx])) {
                return false; // mixing is only possible with all inflows
            }
            if (materialNodes[idx].hasNoConcentration(connectionIndex[idx])) {
                continue; // practically zero flow
            }
            inflow = inflow + flow;
            for (int mdx = 0; mdx < mixedConcentration.length; mdx++) {
                mixedConcentration[mdx] = mixedConcentration[mdx] + flow
                        * materialNodes[idx].getConcentration(
                                connectionIndex[idx], mdx);
            }
        }
        boolean didSomething = false;
        if (inflow > 0.0) {
            for (int mdx = 0; mdx < mixedConcentration.length; mdx++) {
                mixedConcentration[mdx] = mixedConcentration[mdx] / inflow;
            }
            for (int idx = 0; idx < materialNodes.length; idx++) {
                if (getInflow(idx) > 0.0) {
                    continue;
                }
                didSomething = setOutflowConcentrations(idx,
                        mixedConcentration) || didSomething;
            }
        } else {
            for (int idx = 0; idx < materialNodes.length; idx++) {
                didSomething = setOutflowNoConcentration(idx) || didSomething;
            }
        }
        return didSomething;
    }
}
