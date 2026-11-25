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

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.MassedElement;
import com.hartrusion.modeling.steam.SteamIsobaricIsochoricEvaporator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Adds material concentrations to an existing network and provides access to
 * them. A material is any scalar property of the fluid that mixes ideally,
 * like a conductivity or an impurity, and it is identified by a unique name.
 *
 * <p>
 * Adding a material searches the whole network that is connected to a given
 * node and attaches a MaterialNode to each node and a MaterialElement to each
 * element, which are then calculated together with the network by the
 * prepareCalculation and doCalculation calls of the nodes and elements. The
 * search follows only the node connections, it does not follow coupled
 * elements, so the material does not pass through heat exchangers into the
 * other side. Elements that connect nodes of different physical domains, like
 * the converters between heat fluid and phased fluid, are passed as they
 * transport the same fluid.
 *
 * <p>
 * The type of the material element is chosen once from the type of the
 * element: Origins and enforcers become a MaterialOrigin, elements with an own
 * fluid content become a MaterialElementMass and all other elements become a
 * MaterialElementNoMass. A material must be added after the topology of the
 * network is complete.
 */
public final class MaterialLayer {

    private MaterialLayer() {
        // Only static methods are provided.
    }

    /**
     * Adds a material to the whole network that is connected to the given
     * node. Nodes and elements that do not have a material object yet get a
     * new one, already existing material objects just get the new material.
     * Adding an already known material has no effect.
     *
     * @param materialName Unique name of the material.
     * @param anyNode Any node of the network.
     */
    public static void add(String materialName, GeneralNode anyNode) {
        List<GeneralNode> nodes = new ArrayList<>();
        List<AbstractElement> elements = new ArrayList<>();
        collectNetwork(anyNode, nodes, elements);
        // All material nodes must exist before any material element is
        // created, as the material element references the material nodes.
        for (GeneralNode n : nodes) {
            if (n.getMaterialNode() == null) {
                n.setMaterialNode(new MaterialNode(n));
            }
            n.getMaterialNode().addMaterial(materialName);
        }
        for (AbstractElement e : elements) {
            if (e.getMaterialElement() == null) {
                e.setMaterialElement(createMaterialElement(e));
            }
            e.getMaterialElement().addMaterial(materialName);
        }
    }

    /**
     * Sets the time step on all mass material elements of the network that is
     * connected to the given node. This must be the same time step that the
     * network itself is calculated with.
     *
     * @param anyNode Any node of the network.
     * @param dt Time step in seconds.
     */
    public static void setStepTime(GeneralNode anyNode, double dt) {
        List<GeneralNode> nodes = new ArrayList<>();
        List<AbstractElement> elements = new ArrayList<>();
        collectNetwork(anyNode, nodes, elements);
        for (AbstractElement e : elements) {
            if (e.getMaterialElement() instanceof MaterialElementMass mem) {
                mem.setStepTime(dt);
            }
        }
    }

    /**
     * Defines the concentration of a material that enters the network through
     * an origin or enforcer element.
     *
     * @param origin Element with a MaterialOrigin assigned.
     * @param materialName Unique name of the material.
     * @param value Concentration value.
     */
    public static void setOriginConcentration(AbstractElement origin,
            String materialName, double value) {
        if (!(origin.getMaterialElement() instanceof MaterialOrigin mo)) {
            throw new ModelErrorException("Element " + origin.toString()
                    + " has no material origin assigned.");
        }
        mo.setConcentration(materialName, value);
    }

    /**
     * Sets the concentration of a material in the content of an element with
     * an own fluid content, which is used to define initial conditions.
     *
     * @param element Element with a MaterialElementMass assigned.
     * @param materialName Unique name of the material.
     * @param value Concentration value.
     */
    public static void setInitialConcentration(AbstractElement element,
            String materialName, double value) {
        if (!(element.getMaterialElement() instanceof MaterialElementMass mem)) {
            throw new ModelErrorException("Element " + element.toString()
                    + " has no material mass element assigned.");
        }
        mem.setConcentration(materialName, value);
    }

    /**
     * Returns the concentration of a material that was last distributed from a
     * node.
     *
     * @param node Node with a MaterialNode assigned.
     * @param materialName Unique name of the material.
     * @return Concentration value.
     */
    public static double getConcentration(GeneralNode node,
            String materialName) {
        if (node.getMaterialNode() == null) {
            throw new ModelErrorException("Node " + node.toString()
                    + " has no material node assigned.");
        }
        return node.getMaterialNode().getConcentration(materialName);
    }

    /**
     * Collects all nodes and elements of the network that is connected to the
     * start node by following the node connections of the elements, without
     * following coupled elements.
     *
     * @param start Node to start the search from.
     * @param nodes List that receives all found nodes.
     * @param elements List that receives all found elements.
     */
    private static void collectNetwork(GeneralNode start,
            List<GeneralNode> nodes, List<AbstractElement> elements) {
        Set<GeneralNode> visitedNodes
                = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<AbstractElement> visitedElements
                = Collections.newSetFromMap(new IdentityHashMap<>());
        Deque<GeneralNode> queue = new ArrayDeque<>();
        queue.add(start);
        visitedNodes.add(start);
        GeneralNode node;
        AbstractElement element;
        GeneralNode next;
        while (!queue.isEmpty()) {
            node = queue.poll();
            nodes.add(node);
            for (int idx = 0; idx < node.getNumberOfElements(); idx++) {
                element = node.getElement(idx);
                if (!visitedElements.add(element)) {
                    continue;
                }
                elements.add(element);
                for (int jdx = 0; jdx < element.getNumberOfNodes(); jdx++) {
                    next = element.getNode(jdx);
                    if (visitedNodes.add(next)) {
                        queue.add(next);
                    }
                }
            }
        }
    }

    /**
     * Creates the material element that matches the behaviour of an element.
     * Origins and enforcers are the boundaries of the network and provide fixed
     * concentrations. Capacitances and the elements that implement the
     * MassedElement interface have an own fluid content that the inflow is
     * mixed with.
     * All other elements pass the concentration through.
     *
     * @param element Element to create the material element for.
     * @return New material element.
     */
    private static MaterialElement createMaterialElement(
            AbstractElement element) {
        ElementType type = element.getElementType();
        if (type == ElementType.ORIGIN || type == ElementType.ENFORCER) {
            return new MaterialOrigin(element);
        }
        // The steam evaporator has a fluid content, but its mass can not be
        // set freely, so it does not implement the MassedElement interface.
        if (type == ElementType.CAPACTIANCE
                || element instanceof MassedElement
                || element instanceof SteamIsobaricIsochoricEvaporator) {
            return new MaterialElementMass(element);
        }
        return new MaterialElementNoMass(element);
    }
}
