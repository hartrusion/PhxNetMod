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

import com.hartrusion.modeling.initial.AbstractInitialCondition;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.PhysicalDomain;

/**
 * A most basic element that all elements extend to. Holds the basic ability to
 * register nodes.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class AbstractElement implements CalculationStep,
        GeneralElement {

    protected List<GeneralNode> nodes = new ArrayList<>();

    /**
     * Used to identify network analysis behaviour. At first hand, most elements
     * could also be checked by casting them to their proper types but resistors
     * can be considered to be of three different types. It also makes it easier
     * just to get what kind of element it actually is.
     */
    protected ElementType elementType;

    protected PhysicalDomain physicalDomain;

    protected String elementName; // unique identifier

    /**
     * Link to another element that has a coupled connection to this one. Mostly
     * this is for heat exchangers or similar, as they connect the networks.
     * Used for the solving algorithms traverse algorithms.
     */
    private AbstractElement coupledElement = null;

    private AbstractElement() {
        // force the use of the parametrized constructor.
    }

    /**
     * Constructs an object which is derived from this one. The constructor is
     * public to allow usage in other packages like the Steam package, if it's
     * not noted public, it can only be used by general elements.
     *
     * @param domain PhysicalDomain
     */
    public AbstractElement(PhysicalDomain domain) {
        physicalDomain = domain;
    }

    /**
     * Sets a unique name for this element. The name can be returned by using
     * the toString function of java.lang.object.
     * 
     * @param name Unique name of the element.
     */
    public void setName(String name) {
        elementName = name;
    }

    @Override
    public String toString() {
        if (elementName == null) {
            return super.toString();
        } else {
            return elementName;
        }
    }

    /**
     * Returns the physical domain this element was created to. The physical
     * domain must be identical for all connected nodes and elements and serves
     * as a validation for the model itself.
     *
     * @return PhysicalDomain
     */
    public PhysicalDomain getPhysicalDomain() {
        return physicalDomain;
    }

    /**
     * Registers a node with this element. Used to set up the dynamic model.
     *
     * <p>
     * Note that this does <b>not</b> do the registration on the nodes side. Use
     * the connect method for a more quick setup.
     *
     * @param n Node connected to this element
     */
    public void registerNode(GeneralNode n) {
        if (n == null) {
            throw new ModelErrorException("Provided node is null");
        }
        if (!nodes.contains(n)) {
            if (n.getPhysicalDomain() != physicalDomain
                    && physicalDomain != PhysicalDomain.MULTIDOMAIN) {
                throw new ModelErrorException("Node and element must have the"
                        + " same physical domain!");
            }
            nodes.add(n);
        } else {
            throw new ModelErrorException(
                    "Node already registered with element.");
        }
    }

    public void connectTo(GeneralNode n) {
        registerNode(n);
        n.registerElement(this);
    }

    public void connectToVia(AbstractElement e, GeneralNode n) {
        registerNode(n);
        n.registerElement(this);
        n.registerElement(e);
        e.registerNode(n);
    }

    public void connectBetween(GeneralNode n1, GeneralNode n2) {
        n1.registerElement(this);
        registerNode(n1);
        n2.registerElement(this);
        registerNode(n2);
    }

    /**
     * Replaces a registered node with another node. This is used for model
     * manipulation, mostly by superposition solvers. Not intended to be used
     * for building and configuring dynamic models.
     *
     * @param node This node will be removed
     * @param replacement The new node which will be used
     */
    public void replaceNode(GeneralNode node, GeneralNode replacement) {
        if (!nodes.contains(node)) {
            throw new ModelErrorException(
                    "Node does not exist, therefore it cant be replaced.");
        } else {
            nodes.set(nodes.indexOf(node), replacement);
        }
    }

    /**
     * Returns node by index.
     *
     * @param index Requested index
     * @return GeneralNode according to index
     */
    public GeneralNode getNode(int index) {
        if (index == 1 && nodes.size() == 1) {
            return null;
        }
        return nodes.get(index);
    }

    /**
     * Returns index of node
     *
     * @param n General Node
     * @return Index
     */
    public int getIndexOfNode(GeneralNode n) {
        return nodes.indexOf(n);
    }

    /**
     * Returns the other node if possible.If a node is provided, this will
     * return the other node of this element. Works only if there are only two
     * registered nodes, will throw an exception if there is a different number
     * of nodes connected.
     *
     * @param n Node
     * @return The only other node
     * @throws NoFlowThroughException if there are more than 2 nodes
     */
    public GeneralNode getOnlyOtherNode(GeneralNode n)
            throws NoFlowThroughException {
        if (nodes.size() != 2) {
            throw new ModelErrorException(
                    "Can only return the only other node if there are "
                    + "exactly two nodes registered.");
        }
        int idx = nodes.indexOf(n);
        if (idx == -1) {
            throw new ModelErrorException(
                    "Provided node is not connected to element");
        }
        idx = -idx + 1; // switch 0 and 1
        return nodes.get(idx);
    }

    public int getNumberOfNodes() {
        return nodes.size();
    }

    @Override
    public void prepareCalculation() {
        for (GeneralNode n : nodes) {
            n.prepareCalculation();
        }
    }

    /**
     * Calculations are complete if all state space variables connected to this
     * block are updated.Calling this function checks weather all connected
     * nodes have updated state space variables.
     *
     * @return true if all nodes connected to this Element have updated values.
     */
    @Override
    public boolean isCalculationFinished() {
        boolean valuesKnown = true;
        for (GeneralNode n : nodes) {
            valuesKnown = valuesKnown && n.effortUpdated();
            valuesKnown = valuesKnown && n.flowUpdated(this);
        }
        return valuesKnown;
    }

    /**
     * Get specified type or behaviour of element. While this can also be done
     * with casting to derivative types, this is more used in network analysis
     * to determine how a certain component should be handled when doing super
     * position solving.
     *
     * @return Element Type
     */
    public ElementType getElementType() {
        return elementType;
    }

    /**
     * To be called to check the name of any set initial condition. This will
     * compare the names and ensures that only elements with the same name as
     * the IC will be able to load the IC.
     *
     * @param ic Initial condition object
     */
    protected void checkInitialConditionName(AbstractInitialCondition ic) {
        if (elementName != null) {
            if (!ic.getElementName().equals(elementName)) {
                throw new UnsupportedOperationException("Element name mismatch"
                        + " on provided InitialCondition object");
            }
        }
    }

    /**
     * Determine weather an element has a linear behaviour so it can be solved
     * with the provided linear solvers. Method is intended to be overridden by
     * non-linear elements and return false to identify nonlinear behaviour.
     *
     * @return true for linear elements
     */
    public boolean isLinear() {
        return true;
    }

    /**
     * Gets a reference to a coupled element if there is any. This does not have
     * any effect on the element itself but represents a connection to another
     * network, for example, a heat exchanger volume has a thermal network
     * connected to it and this coupled element can be a reference between those
     * tho domains. Its not required for the network to work but for the solver
     * to be able to see that there is something else he has to take care of.
     *
     * @return coupledElement
     */
    public AbstractElement getCoupledElement() {
        return coupledElement;
    }

    /**
     * Saves a reference to a coupled element. This does not have any effect on
     * the element itself but represents a connection to another network, for
     * example, a heat exchanger volume has a thermal network connected to it
     * and this coupled element can be a reference between those tho domains.
     * It's not required for the network to work but for the solver to be
     * able to see that there is something else he has to take care of.
     *
     * @param coupledElement An element that has some connection with this one.
     */
    public void setCoupledElement(AbstractElement coupledElement) {
        this.coupledElement = coupledElement;
        // Also make the connection the other way so both elements know
        // each other
        if (coupledElement.getCoupledElement() == null) {
            coupledElement.setCoupledElement(this);
        }
    }
}
