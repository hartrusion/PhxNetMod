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
package com.hartrusion.modeling.solvers;

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * Describes a network of linear components for network analysis.
 *
 * A network is a set of elements, each element is connected to two nodes. nodes
 * holds a list of all used nodes, while arrays node0 and node1 hold the
 * indizies for each element. For example, if element.get(3) is connected to
 * nodes.get(4) and nodes.get(6) those indizies will be saved as node0[3] = 4
 * and node1[3] = 6.
 *
 * Elements of such a network are limited to two nodes per element. This is due
 * to the purpose of this class to describe linear resistor networks for
 * electric circuits and provide some means to solve them. This is done to
 * provide those methods of electric circuits for solving the more general
 * modeling issues, especially calculating flows and effort values over large
 * networks of elements.
 *
 * It is preferred not to create objects by this class as it is not known if
 * those objects will be of a more extended type in the application or not. This
 * class will only work with the general type elements and describe a network on
 * this level. This can be further extended into a network solver to provide a
 * solution for a network analysis problem, e.g. a piping network with multiple
 * pumps. The network analysis will provide flow and effort values which will be
 * accesible by the derived elements.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class LinearNetwork {

    /**
     * Initial size for arrays. Smaller arrays will not get resized
     */
    protected static final int INIT_ARRAY_SIZE = 10;

    private String networkName;

    protected List<GeneralNode> nodes = new ArrayList<>();

    protected List<AbstractElement> elements = new ArrayList<>();

    /**
     * If not changed by derived and overridden methods, all elements are added
     * to this instance of an iterative solver, providing methods to solve or
     * prepare all elements and nodes.
     */
    protected SimpleIterator iterativeSolver = new SimpleIterator();

    /**
     * Instance to solve a two-elements-in-series situation which can occur when
     * simplifying networks by series and parallel simplification. This is
     * handled by this special solver to provide a solution even if resistors
     * are open or shorted and have that origin node between them.
     */
    private final TwoSeriesSolver twoSeriesSolver = new TwoSeriesSolver();

    /**
     * Index of node 0 from element. The array index represents the index of the
     * element in elements list. The array value at that index represents the
     * index in nodes arraylist of node index 0 of nodes arraylist.
     * node0idx[elements.indexof(e)] = nodes.indexof(e.getnode(0));
     */
    protected int[] node0idx;
    /**
     * Index of node 1 from element. The array index represents the index of the
     * element in elements list. The array value at that index represents the
     * index in nodes arraylist of node index 1 of nodes arraylist.
     * node0idx[elements.indexof(e)] = nodes.indexof(e.getNode(1));
     */
    protected int[] node1idx;

    private int numberOfNodes = 0, numberOfElements = 0, numberOfOrigins = 0;

    public LinearNetwork() {
        node0idx = new int[INIT_ARRAY_SIZE];
        node1idx = new int[INIT_ARRAY_SIZE];
    }

    /**
     * Prepares all added elements and nodes for the next calculation iteration.
     */
    public void prepareCalculation() {
        iterativeSolver.prepareCalculation();
    }

    /**
     * Calls calculation on all elements of this network until no further
     * calculation is possible.
     */
    public void doCalculation() {
        iterativeSolver.doCalculation();
        if (!isCalculationFinished()) {
            twoSeriesSolver.solve(); // try this
        }
    }

    /**
     * Checks if all elements assigned to this network are fully calculated.
     *
     * @return true, if everything was calculated.
     */
    public boolean isCalculationFinished() {
        return iterativeSolver.isCalculationFinished();
    }

    /**
     * Registers node in this network. The node will be added to the list of
     * nodes at the next free position inside the arraylist. This is usually the
     * first step of configuring such a network.
     *
     * @param p
     */
    public void registerNode(GeneralNode p) {
        if (!nodes.contains(p)) {
            nodes.add(p);
            numberOfNodes++;
        } else {
            throw new UnsupportedOperationException(
                    "Node already added to network");
        }
    }

    /**
     * Register element inside network.Requires element to be connected to node
     * alrealdy, also requires the nodes to be present already in both node
     * lists, so registerNode(GeneralNode p) has to be used to add all nodes
     * before calling this. Each element will be checked wether the nodes are
     * also part of the network. This ensures that no ports will be missing in
     * network.
     *
     * <p>
     * Corresponding indizies of nodes will be saved in array fields for further
     * analysis of network.
     *
     * <p>
     * This method also performs additional checks which need to be passed to be
     * able to add all corresponding elements to the network.
     *
     * <p>
     * Elements also get added to a list for running doCalculation and
     * prepareCalculation on all added elements.
     *
     * @param e
     */
    public void registerElement(AbstractElement e) {
        int idx;
        if (!elements.contains(e)) {
            // keep track of node0/node1 array size
            if (node0idx.length <= elements.size()) {
                int[] node2idx = new int[node0idx.length + 1];
                System.arraycopy(node0idx, 0, node2idx, 0, node0idx.length);
                node0idx = node2idx;
                int[] node3idx = new int[node1idx.length + 1];
                System.arraycopy(node1idx, 0, node3idx, 0, node1idx.length);
                node1idx = node3idx;
            }

            if (e.getElementType() == ElementType.CAPACTIANCE
                    || e.getElementType() == ElementType.INDUCTANCE) {
                throw new UnsupportedOperationException("Energy Storage "
                        + "elements not yet supported in linear network.");
            }
            if (e.getElementType() == ElementType.NONE) {
                // allow dummy placeholder
                elements.add(e);
                node0idx[elements.indexOf(e)] = -1;
                node1idx[elements.indexOf(e)] = -1;
                numberOfElements++;
                return;
            }
            // Node 0 has to be in network. Get index of node 0 in nodes-List
            // of this network:
            if (e.getNumberOfNodes() > 2) {
                throw new ModelErrorException("Element has more than two nodes "
                        + " but 2 was expected here.");
            }
            if ((e.getElementType() == ElementType.DISSIPATOR
                    || e.getElementType() == ElementType.OPEN
                    || e.getElementType() == ElementType.BRIDGED
                    || e.getElementType() == ElementType.EFFORTSOURCE
                    || e.getElementType() == ElementType.FLOWSOURCE)
                    && e.getNumberOfNodes() != 2) {
                throw new ModelErrorException("Element of this type "
                        + "must have exactly 2 registered nodes.");
            }
            idx = this.nodes.indexOf(e.getNode(0));
            if (idx < 0) {
                throw new ModelErrorException(
                        "Registered element node 0 not registered in network");
            } else { // node exists in list:
                elements.add(e); // add element to list...
                // and save node index in node list according to element index.
                node0idx[elements.indexOf(e)] = idx;
                // do the same for node 1 if node 1 is registered.
                if (e.getNumberOfNodes() == 2) {
                    idx = this.nodes.indexOf(e.getNode(1));
                    if (idx < 0) {
                        throw new ModelErrorException(
                                "Registered element node 1 not "
                                + "registered in network");
                    } else { // node exists in list:
                        // save index
                        node1idx[elements.indexOf(e)] = idx;
                    }
                } else {
                    node1idx[elements.indexOf(e)] = -1;
                }
                numberOfElements++;
                if (e.getElementType() != null) {
                    iterativeSolver.addElement(e);
                    twoSeriesSolver.addElement(e);
                }
            }
        } else {
            throw new ModelErrorException(
                    "Element already added to network");
        }
    }

    /**
     * Returns a specific parent element, identified by its index. This may be
     * used by the superposition solver to read the calculated values, which
     * were provided by the child network and its solution, back to the original
     * elements.
     *
     * @param index Index of element, implies that the order in which the
     * elements were added here was correct
     * @return AbstractElement
     */
    public AbstractElement getElement(int index) {
        return elements.get(index);
    }

    /**
     * Check if an element is of a type that can be seen as a resistor.
     *
     * Shortcuts or open connections will stay in the model, as these can be
     * just temporarily behave like this and the overall calculation does work
     * for those extreme conditions also.
     *
     * @param e element to check
     * @return true: it is a resistor or similar
     */
    protected static boolean isElementResistorType(AbstractElement e) {
        return e.getElementType() == ElementType.DISSIPATOR
                || e.getElementType() == ElementType.OPEN
                || e.getElementType() == ElementType.BRIDGED;
    }

    /**
     * Sets a unique name for this network. The name can be returned by using
     * the toString function of java.lang.object.
     *
     * <p>
     * Note that an @-sign is not allowed as the hashCode will be appended to
     * the set name with the @-sign afterwards.
     *
     * @param name
     */
    public void setName(String name) {;
        networkName = name;
    }

    @Override
    public String toString() {
        if (networkName == null) {
            return super.toString();
        } else {
            return networkName;
        }
    }
}
