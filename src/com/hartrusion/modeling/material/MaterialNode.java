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

import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.GeneralNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Holds the material concentrations on one node of an existing network, so
 * that the concentrations are transported along the flows that the network
 * solver calculates for the node. The class is the material counterpart of the
 * HeatNode, but instead of extending the node class it is attached to an
 * existing GeneralNode as a reference, which allows adding materials to any
 * network without changing the node classes that were used to build it.
 *
 * <p>
 * For each element connected to the general node, this object stores one
 * concentration value per material, using the same connection index as the
 * general node uses for its flows. A concentration on a connection describes
 * the concentration of the fluid that flows on this connection, regardless of
 * its direction. The concentrations of all inbound connections are mixed
 * ideally and the mixture is distributed onto all outbound connections, which
 * is exactly how the HeatNode handles the temperature.
 *
 * <p>
 * The material elements set the concentrations of the flows leaving their
 * elements into this node and read the concentrations of the flows going into
 * their elements from this node. Materials are identified by their index in the
 * arrays, which is the same on all material nodes and material elements of a
 * network because the MaterialLayer adds every material to all of them in the
 * same order.
 */
public class MaterialNode {

    /**
     * The general node of the network which this material node belongs to. Its
     * flows define the direction and the weight of the concentration mixing.
     */
    private final GeneralNode node;

    /**
     * Names of all materials known to this node. The position of a name in this
     * list is the index of the material in all concentration arrays.
     */
    private final List<String> materialNames = new ArrayList<>();

    /**
     * Concentration of each material on each connection, which is indexed as
     * [connection][material] with the connection index of the general node.
     */
    private double[][] concentration;

    /**
     * Marks that the concentrations of a connection were set during the
     * current calculation run, either with a value or as having no value.
     */
    private final boolean[] concentrationUpdated;

    /**
     * Marks that a connection has no concentration value in the current
     * calculation run because there is no flow on it, which is the same
     * concept as the no-temperature state of the HeatNode.
     */
    private final boolean[] noConcentration;

    /**
     * The concentrations that were last distributed from this node onto its
     * outbound connections. The value is kept while there is no flow so that
     * the node always provides a concentration to read.
     */
    private double[] mixedConcentration = new double[0];

    /**
     * Sum of the product of flow and concentration of all inbound connections,
     * which is kept as a field only to avoid an allocation on each run.
     */
    private double[] inboundSum = new double[0];

    /**
     * Creates the material node for an existing general node. The number of
     * connections is taken from the general node, so the network topology must
     * be complete when this object is created.
     *
     * @param node General node to attach the material node to.
     */
    public MaterialNode(GeneralNode node) {
        this.node = node;
        int connections = node.getNumberOfElements();
        concentration = new double[connections][0];
        concentrationUpdated = new boolean[connections];
        noConcentration = new boolean[connections];
    }

    /**
     * Adds a new material with a concentration of 0.0 by extending all arrays
     * by one value, which keeps the values of the already known materials. A
     * material name that is already known is ignored.
     *
     * @param materialName Unique name of the material.
     * @return true if the material was added, false if it was already known.
     */
    public boolean addMaterial(String materialName) {
        if (materialNames.contains(materialName)) {
            return false;
        }
        materialNames.add(materialName);
        int count = materialNames.size();
        for (int idx = 0; idx < concentration.length; idx++) {
            concentration[idx] = extendArray(concentration[idx], count);
        }
        mixedConcentration = extendArray(mixedConcentration, count);
        inboundSum = new double[count];
        return true;
    }

    /**
     * Returns a copy of the given array with the new length, filling the new
     * values with 0.0. It is used by all material classes whenever a material
     * is added.
     *
     * @param values Array to extend.
     * @param length New length of the array.
     * @return The extended array.
     */
    static double[] extendArray(double[] values, int length) {
        double[] extended = new double[length];
        System.arraycopy(values, 0, extended, 0,
                Math.min(values.length, length));
        return extended;
    }

    /**
     * Returns the index of a material in the concentration arrays.
     *
     * @param materialName Unique name of the material.
     * @return Index of the material.
     * @throws ModelErrorException if the material is not known to this node.
     */
    public int getMaterialIndex(String materialName) {
        int idx = materialNames.indexOf(materialName);
        if (idx < 0) {
            throw new ModelErrorException("Material " + materialName
                    + " is not known to node " + node.toString());
        }
        return idx;
    }

    public int getMaterialCount() {
        return materialNames.size();
    }

    public GeneralNode getNode() {
        return node;
    }

    /**
     * Resets the updated states of all connections, so that the concentrations
     * can be calculated again for the next run. This is called from the
     * prepareCalculation method of the general node.
     */
    public void prepareCalculation() {
        for (int idx = 0; idx < concentrationUpdated.length; idx++) {
            concentrationUpdated[idx] = false;
            noConcentration[idx] = false;
        }
    }

    /**
     * Sets the concentrations on a connection, which is called by the material
     * element of the connected element for a flow that leaves the element into
     * this node. If the node has exactly two connections, the values are passed
     * to the other connection instantly, as there is nothing to mix, which is
     * the same shortcut as the HeatNode uses.
     *
     * @param connection Connection index as used by the general node.
     * @param values Concentration of each material.
     */
    public void setConcentrations(int connection, double[] values) {
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new CalculationException("Tried to set a non-finite "
                        + "concentration value on node " + node.toString());
            }
        }
        System.arraycopy(values, 0, concentration[connection], 0,
                values.length);
        concentrationUpdated[connection] = true;
        noConcentration[connection] = false;
        if (concentration.length == 2) {
            // The value is the only inbound one, so it is also the mixture.
            System.arraycopy(values, 0, mixedConcentration, 0, values.length);
            int other = 1 - connection;
            if (!concentrationUpdated[other]) {
                System.arraycopy(values, 0, concentration[other], 0,
                        values.length);
                concentrationUpdated[other] = true;
                noConcentration[other] = false;
            }
        }
    }

    /**
     * Marks a connection to have no concentration value because there is no
     * flow on it, which is passed instantly to the other connection if the
     * node has exactly two connections.
     *
     * @param connection Connection index as used by the general node.
     */
    public void setNoConcentration(int connection) {
        concentrationUpdated[connection] = true;
        noConcentration[connection] = true;
        if (concentration.length == 2) {
            int other = 1 - connection;
            if (!concentrationUpdated[other]) {
                concentrationUpdated[other] = true;
                noConcentration[other] = true;
            }
        }
    }

    public boolean isConcentrationUpdated(int connection) {
        return concentrationUpdated[connection];
    }

    /**
     * Returns whether a connection has no concentration value in the current
     * run, which is the case if there is no flow on that connection.
     *
     * @param connection Connection index as used by the general node.
     * @return true if there is no concentration value.
     */
    public boolean hasNoConcentration(int connection) {
        return noConcentration[connection];
    }

    /**
     * Returns the concentration of a material on a connection. The value is
     * only meaningful if the connection is updated and has a concentration.
     *
     * @param connection Connection index as used by the general node.
     * @param material Index of the material.
     * @return Concentration value.
     */
    public double getConcentration(int connection, int material) {
        return concentration[connection][material];
    }

    /**
     * Returns the concentration of a material that was last distributed from
     * this node, which stays available even if there is currently no flow.
     *
     * @param materialName Unique name of the material.
     * @return Mixed concentration of the node.
     */
    public double getConcentration(String materialName) {
        return mixedConcentration[getMaterialIndex(materialName)];
    }

    /**
     * Mixes the concentrations of all inbound connections ideally, weighted by
     * their flows, and sets the mixture on all outbound connections. This is
     * only possible after all flows on the general node are known and all
     * inbound concentrations are updated. If all flows are zero, all connections
     * that are not yet updated are marked to have no concentration. Inbound
     * connections with no concentration are skipped, as they can only occur
     * with flows that are practically zero.
     *
     * <p>
     * This is called by the material elements of all connected elements, as it
     * is unknown which of them will provide the last missing value.
     *
     * @return true if something was calculated.
     */
    public boolean doCalculateConcentration() {
        if (!node.allFlowsUpdated()) {
            return false;
        }
        boolean allConnectionsUpdated = true;
        boolean allFlowsZero = true;
        double flow;
        for (int idx = 0; idx < concentration.length; idx++) {
            flow = node.getFlow(idx);
            // The node stores flows within its tolerance as exact 0.0.
            allFlowsZero = allFlowsZero && flow == 0.0;
            allConnectionsUpdated = allConnectionsUpdated
                    && concentrationUpdated[idx];
        }
        if (allConnectionsUpdated) {
            return false; // nothing left to do on this node
        }
        boolean didSomething = false;
        if (allFlowsZero) {
            for (int idx = 0; idx < concentration.length; idx++) {
                if (!concentrationUpdated[idx]) {
                    concentrationUpdated[idx] = true;
                    noConcentration[idx] = true;
                    didSomething = true;
                }
            }
            return didSomething;
        }
        double inFlow = 0.0;
        Arrays.fill(inboundSum, 0.0);
        for (int idx = 0; idx < concentration.length; idx++) {
            flow = node.getFlow(idx);
            if (flow >= 0.0) {
                continue; // a negative flow is the one going into the node
            }
            if (!concentrationUpdated[idx]) {
                return false; // mixing is only possible with all inbounds
            }
            if (noConcentration[idx]) {
                continue;
            }
            inFlow = inFlow - flow;
            for (int mdx = 0; mdx < inboundSum.length; mdx++) {
                inboundSum[mdx] = inboundSum[mdx]
                        - flow * concentration[idx][mdx];
            }
        }
        if (inFlow > 0.0) {
            for (int mdx = 0; mdx < inboundSum.length; mdx++) {
                mixedConcentration[mdx] = inboundSum[mdx] / inFlow;
            }
        }
        for (int idx = 0; idx < concentration.length; idx++) {
            if (concentrationUpdated[idx]) {
                continue;
            }
            if (inFlow > 0.0) {
                System.arraycopy(mixedConcentration, 0, concentration[idx], 0,
                        mixedConcentration.length);
            } else {
                noConcentration[idx] = true;
            }
            concentrationUpdated[idx] = true;
            didSomething = true;
        }
        return didSomething;
    }
}
