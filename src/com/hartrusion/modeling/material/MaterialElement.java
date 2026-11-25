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
import java.util.ArrayList;
import java.util.List;

/**
 * Base class for the material part of a network element, which transports the
 * material concentrations through the element along the flows that the network
 * solver calculates. An instance is attached to an existing AbstractElement as
 * a reference by the MaterialLayer, so that materials can be added to any
 * network without changing the element classes that were used to build it.
 *
 * <p>
 * The element calls prepareMaterialCalculation and doMaterialCalculation from
 * its own prepareCalculation and doCalculation methods, which makes the
 * concentrations being calculated by every solver that runs the elements
 * without the solvers knowing about materials. The concentration calculation
 * only reads flows and never changes efforts or flows of the network.
 *
 * <p>
 * The subclasses define how the concentration is calculated inside the
 * element. MaterialElementNoMass passes the concentration through,
 * MaterialElementMass mixes the inflow ideally with the content of the element
 * and MaterialOrigin provides fixed concentrations at the boundary of the
 * network.
 */
public abstract class MaterialElement {

    /**
     * The network element that this material element belongs to. Its flows on
     * the connected nodes define the concentration transport.
     */
    protected final AbstractElement element;

    /**
     * Material nodes of all nodes connected to the element, in the same order
     * as the element holds its nodes.
     */
    protected final MaterialNode[] materialNodes;

    /**
     * For each entry of materialNodes, the index under which the element is
     * registered on that node. It is determined once because the topology of
     * the network does not change after the material layer was added.
     */
    protected final int[] connectionIndex;

    /**
     * Names of all materials known to this element. The position of a name in
     * this list is the index of the material in all concentration arrays.
     */
    protected final List<String> materialNames = new ArrayList<>();

    /**
     * Creates the material element for an existing element. All nodes of the
     * element must already have their material node assigned.
     *
     * @param element Element to attach the material element to.
     */
    public MaterialElement(AbstractElement element) {
        this.element = element;
        int nodeCount = element.getNumberOfNodes();
        materialNodes = new MaterialNode[nodeCount];
        connectionIndex = new int[nodeCount];
        GeneralNode node;
        for (int idx = 0; idx < nodeCount; idx++) {
            node = element.getNode(idx);
            if (node.getMaterialNode() == null) {
                throw new ModelErrorException("Node " + node.toString()
                        + " has no material node assigned.");
            }
            materialNodes[idx] = node.getMaterialNode();
            connectionIndex[idx] = findConnectionIndex(node);
        }
    }

    /**
     * Returns the index under which the element is registered on a node, which
     * is the index the general node uses for its flow values.
     *
     * @param node Node connected to the element.
     * @return Connection index on the node.
     */
    private int findConnectionIndex(GeneralNode node) {
        for (int idx = 0; idx < node.getNumberOfElements(); idx++) {
            if (node.getElement(idx) == element) {
                return idx;
            }
        }
        throw new ModelErrorException("Element " + element.toString()
                + " is not registered on node " + node.toString());
    }

    /**
     * Adds a new material with a concentration of 0.0. Subclasses that hold
     * own concentration arrays extend them when this returns true.
     *
     * @param materialName Unique name of the material.
     * @return true if the material was added, false if it was already known.
     */
    public boolean addMaterial(String materialName) {
        if (materialNames.contains(materialName)) {
            return false;
        }
        materialNames.add(materialName);
        return true;
    }

    /**
     * Returns the index of a material in the concentration arrays.
     *
     * @param materialName Unique name of the material.
     * @return Index of the material.
     * @throws ModelErrorException if the material is not known.
     */
    public int getMaterialIndex(String materialName) {
        int idx = materialNames.indexOf(materialName);
        if (idx < 0) {
            throw new ModelErrorException("Material " + materialName
                    + " is not known to element " + element.toString());
        }
        return idx;
    }

    public int getMaterialCount() {
        return materialNames.size();
    }

    /**
     * Prepares the next calculation run. This is called from the
     * prepareCalculation method of the element, which may happen more than
     * once per run, so implementations must not change their state on
     * repeated calls. The updated states on the nodes are reset by the material
     * nodes themselves.
     */
    public abstract void prepareMaterialCalculation();

    /**
     * Calculates the concentrations inside the element and on the connected
     * material nodes as far as the known flows allow. This is called from the
     * doCalculation method of the element.
     *
     * @return true if something was calculated.
     */
    public boolean doMaterialCalculation() {
        boolean didSomething = calculateElementConcentrations();
        for (MaterialNode mn : materialNodes) {
            didSomething = mn.doCalculateConcentration() || didSomething;
        }
        return didSomething;
    }

    /**
     * Calculates the concentrations of the flows that leave the element and,
     * for elements with an own content, the concentration of the content.
     *
     * @return true if something was calculated.
     */
    protected abstract boolean calculateElementConcentrations();

    /**
     * Returns whether all concentrations of the element for the current run
     * are calculated, which is used for diagnostics only.
     *
     * @return true if the calculation is finished.
     */
    public boolean isMaterialCalculationFinished() {
        return allConcentrationsUpdated();
    }

    /**
     * Returns the flow from a connected node into the element, which is
     * positive if the flow goes into the element.
     *
     * @param idx Index of the node on the element.
     * @return Flow into the element.
     */
    protected double getInflow(int idx) {
        return materialNodes[idx].getNode().getFlow(element);
    }

    protected boolean allFlowsUpdated() {
        for (MaterialNode mn : materialNodes) {
            if (!mn.getNode().flowUpdated(element)) {
                return false;
            }
        }
        return true;
    }

    protected boolean allConcentrationsUpdated() {
        for (int idx = 0; idx < materialNodes.length; idx++) {
            if (!materialNodes[idx].isConcentrationUpdated(
                    connectionIndex[idx])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets the concentrations on the connection of a node, if they are not yet
     * updated in this run.
     *
     * @param idx Index of the node on the element.
     * @param values Concentration of each material.
     * @return true if the values were set.
     */
    protected boolean setOutflowConcentrations(int idx, double[] values) {
        if (materialNodes[idx].isConcentrationUpdated(connectionIndex[idx])) {
            return false;
        }
        materialNodes[idx].setConcentrations(connectionIndex[idx], values);
        return true;
    }

    /**
     * Marks the connection of a node to have no concentration, if it is not
     * yet updated in this run.
     *
     * @param idx Index of the node on the element.
     * @return true if the connection was marked.
     */
    protected boolean setOutflowNoConcentration(int idx) {
        if (materialNodes[idx].isConcentrationUpdated(connectionIndex[idx])) {
            return false;
        }
        materialNodes[idx].setNoConcentration(connectionIndex[idx]);
        return true;
    }
}
