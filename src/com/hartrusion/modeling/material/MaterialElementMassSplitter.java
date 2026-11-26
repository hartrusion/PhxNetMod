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

import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;
import java.util.Arrays;

/**
 * Material part of an element with an own content that separates its content
 * into outflows with different concentrations, like a steam drum, where the
 * steam leaves the drum with a much lower concentration of dissolved materials
 * than the water inside. It is assigned by the MaterialLayer to the
 * PhasedClosedSteamedReservoir instead of a MaterialElementMass.
 *
 * <p>
 * For each node and material, an outflow limit can be defined, which is the
 * highest concentration that a flow leaving the element through this node can
 * have. If the content has a higher concentration, the flow leaves with the
 * limit and the remaining material stays inside the element, which enriches
 * the content over time. Without a defined limit, a flow leaves with the
 * concentration of the content like with the MaterialElementMass.
 */
public class MaterialElementMassSplitter extends MaterialElementMass {

    /**
     * Highest concentration of each material for a flow leaving the element,
     * indexed as [node][material] with the node index of the element. A value
     * of positive infinity means that there is no limit.
     */
    private final double[][] outflowLimit;

    /**
     * Concentration of each material of the flow leaving the element through
     * each node, indexed as [node][material]. It is kept as a field only to
     * avoid an allocation on each run.
     */
    private final double[][] limitedConcentration;

    public MaterialElementMassSplitter(AbstractElement element) {
        super(element);
        outflowLimit = new double[materialNodes.length][0];
        limitedConcentration = new double[materialNodes.length][0];
    }

    @Override
    public boolean addMaterial(String materialName) {
        if (!super.addMaterial(materialName)) {
            return false;
        }
        int count = getMaterialCount();
        for (int idx = 0; idx < outflowLimit.length; idx++) {
            outflowLimit[idx] = Arrays.copyOf(outflowLimit[idx], count);
            outflowLimit[idx][count - 1] = Double.POSITIVE_INFINITY;
            limitedConcentration[idx] = new double[count];
        }
        return true;
    }

    /**
     * Defines the highest concentration of a material for the flow that
     * leaves the element through the given node. Setting the limit to
     * positive infinity removes the limit.
     *
     * @param node Node of the element, like the steam outlet of a drum.
     * @param materialName Unique name of the material.
     * @param limit Highest concentration of the outflow.
     */
    public void setOutflowLimit(GeneralNode node, String materialName,
            double limit) {
        outflowLimit[getNodeIndex(node)][getMaterialIndex(materialName)]
                = limit;
    }

    public double getOutflowLimit(GeneralNode node, String materialName) {
        return outflowLimit[getNodeIndex(node)][getMaterialIndex(
                materialName)];
    }

    /**
     * Returns the index of a node on the element.
     *
     * @param node Node connected to the element.
     * @return Index of the node.
     * @throws ModelErrorException if the node is not connected.
     */
    private int getNodeIndex(GeneralNode node) {
        for (int idx = 0; idx < materialNodes.length; idx++) {
            if (materialNodes[idx].getNode() == node) {
                return idx;
            }
        }
        throw new ModelErrorException("Node " + node.toString()
                + " is not connected to element " + element.toString());
    }

    /**
     * Returns the concentrations of the content, limited by the outflow
     * limits of the node.
     *
     * @param idx Index of the node on the element.
     * @return Concentration of each material of the outflow.
     */
    @Override
    protected double[] getOutflowConcentrations(int idx) {
        double[] content = super.getOutflowConcentrations(idx);
        for (int mdx = 0; mdx < content.length; mdx++) {
            limitedConcentration[idx][mdx] = Math.min(content[mdx],
                    outflowLimit[idx][mdx]);
        }
        return limitedConcentration[idx];
    }
}
