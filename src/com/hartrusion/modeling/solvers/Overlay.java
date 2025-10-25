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

import java.util.logging.Level;
import java.util.logging.Logger;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.ClosedOrigin;

/**
 * Represents one layer for superposition solver. The great advantage of
 * superposition comes from the first simplification when elements can be
 * simplified by shortcut or leaving them away. This Overlay will exactly do
 * this step for one layer and pass the network for further simplification to a
 * different algorithm. This algorithm is fixed and will be the
 * RecursiveSimplifier, but may be changed later. Its no matter to the user.
 *
 * <p>
 * The replacement of shorcuts and open connections will not be done for all
 * elements, this is why the registerElement has two additional arguments to
 * sppecify which elements has to be replaced. LinearDissipators which will
 * represent a valve for example can change their behaviour between steps,
 * therefore they will be kept as open connections and wont be replaced by
 * mergin the nodes.
 *
 * <p>
 * The superposition layer will be added to this network (elements and nodes
 * lists). This class then holds childNodes and childElements, which form a
 * simplified form of the network missing the elements that were replaced by the
 * superposition solver.
 *
 * <p>
 * Element connection order will be kept, meaning the direction of flow will be
 * the same for parent and child elements. However, the order of how nodes have
 * the elements registered will not be the same. This is not a problem as the
 * results will be transferred per element, not per node, but might be good to
 * know for debugging.
 *
 * @author Viktor Alexander Hartung
 */
public class Overlay extends ChildNetwork {

    private static final Logger LOGGER = Logger.getLogger(
            Overlay.class.getName());

    /**
     * Marks the element on [index] of elements list which was an effort source
     * and is no longer a soure in this network. It was replaced with a shortcut
     * in parent network and this overlay will try to optimize this.
     */
    private boolean[] wasEffortSource;

    /**
     * Marks the element on [index] of elements list which was a flow source and
     * is no longer a soure in this network. It was replaced with an open
     * connection in parent network and this overlay will try to optimize this.
     */
    private boolean[] wasFlowSource;

    /**
     * nodes.get(groundNodeIdx) is the node connected to the ground Origin. It
     * is the node connected to the remaining sources connection 0.
     */
    private int groundNodeIdx;

    /**
     * Holds information on which node of element is merged with another node of
     * parent network. Index is the node index of the parent network, value is
     * the node which this index is merged to. This is a result of shortcuts
     * from replaced effort sources. Note that this is refering to the parent
     * network so it has to be considered when creating child networks!
     */
    private int[] nodeMergedWith;

    /**
     * True for elements.get(index) if the element is bridged by having a
     * replaced effort source parallel to this element or a whole chain of
     * elements. Those elements were a straight parallel connection between two
     * merged nodes and they will have flow=0 as their solution.
     */
    private boolean[] elementBridged;

    /**
     * True for nodes between two bridged elements. Bridged elements are those
     * between two merged nodes if they are a direct, non-splitting connection
     * between those nodes. Those elements will not be part of child network.
     */
    private int[] nodeBridged;

    /**
     * Nodes that are no longer used due to an open connection resulting from a
     * flow source that is not part of this layer will be marked as obsolete.
     * Note that merged nodes from effort sources are NOT included here.
     */
    private boolean[] nodeObsolete;

    /**
     * Marks ADDITIONAL elements which will no longer be part of the child
     * network as they have now an open connection due to a removal of a flow
     * source. Note that this is only as a result of missing flow sources.
     * Bridged connections from effort sources or the missing flow source itself
     * will be considered in arrays wasFlowSource and wasEffortSource.
     */
    private boolean[] elementOutOfClosedCircuit;

    /**
     * The removal of a flow source can lead to a network that is actually not
     * doing anything anymore. In this case, there will be a solution of no flow
     * no matter how the remaining sources values will be set.
     */
    private boolean resultAlwaysZeroFlow;

    /**
     * Holds the solver which has to solve the network - here we use the
     * recursive simplifier with all its features.
     */
    private RecursiveSimplifier solver;

    public Overlay() {
        wasEffortSource = new boolean[INIT_ARRAY_SIZE];
        wasFlowSource = new boolean[INIT_ARRAY_SIZE];

        groundNodeIdx = -1;
        nodeMergedWith = new int[INIT_ARRAY_SIZE];

        nodeObsolete = new boolean[INIT_ARRAY_SIZE];
        elementOutOfClosedCircuit = new boolean[INIT_ARRAY_SIZE];
    }

    /**
     * Call prepareCaluclation on parent and child elements. This gets called
     * from SuperPosition class for each layer of this type. The superposition
     * class already transferred the new resistance values to the elements in
     * list elements of this class. Here we now need to transfer all values from
     * elements to corresponding child elements.
     */
    @Override
    public void prepareCalculation() {
        super.prepareCalculation(); // calls prepareCalculation on all parents
        if (resultAlwaysZeroFlow) {
            return;
        }
        // transfer all parameters to child layer:
        for (int idx = 0; idx < elements.size(); idx++) {
            if (elementOutOfClosedCircuit[idx] || wasEffortSource[idx]
                    || wasFlowSource[idx] || elementBridged[idx]) {
                continue;
            }
            transferPropertyToChild(idx);
        }
        solver.prepareRecursiveCalculation();
    }

    @Override
    public void doCalculation() {
        FlowThrough ft;

        // First, origins and sources need to be calculated to prevent setting
        // efforts or flow back to them.
        iterativeSolver.doCalculationOnEnforcerElements();

        if (!resultAlwaysZeroFlow) {
            solver.doRecursiveCalculation(); // solve child network
        }

        // transfer results back
        for (int idx = 0; idx < elements.size(); idx++) {
            if (elements.get(idx).getElementType() == ElementType.ORIGIN) {
                continue;
            }
            // but first, consider which elements were left out and set the
            // values accordingly
            ft = (FlowThrough) elements.get(idx);
            if (elementOutOfClosedCircuit[idx] || elementBridged[idx]) {
                ft.setFlow(0.0, true);
                continue;
            }
            if (wasEffortSource[idx]) {
                ((LinearDissipator) ft).setExternalDeltaEffort(0.0);
                continue;
            }
            if (wasFlowSource[idx]) {
                ft.setFlow(0.0, true);
                continue;
            }
            ft.setFlow(
                    ((FlowThrough) childElements.get(
                            childElementOfElement[idx])).getFlow(), false);
        }

        // After the flows are applied to all elements, the network will be
        // calculated by just calling all elements doCalulation method.
        iterativeSolver.doCalculation();

        // Transfer missing node effort values back. This is needed as the
        // child network has solutions to solve "floating" efforts and we 
        // need to have those in this network here for calculations.
        for (int idx = 0; idx < nodes.size(); idx++) {
            if (nodeObsolete[idx]) {
                continue;
            }
            if (nodes.get(idx).effortUpdated()) {
                continue;
            }
            if (nodeMergedWith[idx] >= 0) {
                if (childNodeOfNode[nodeMergedWith[idx]] >= 0) {
                    // if node was merged with another one, directly get the
                    // effort value from the corresponding merged node.
                    nodes.get(idx).setEffort(
                            childNodes.get(childNodeOfNode[nodeMergedWith[idx]])
                                    .getEffort());
                    continue;
                }
            }
            if (nodeBridged[idx] >= 0) {
                nodes.get(idx).setEffort(
                        childNodes.get(childNodeOfNode[nodeBridged[idx]])
                                .getEffort());
                continue;
            }
            if (childNodeOfNode[idx] == -1) {
                continue;
            }
            nodes.get(idx).setEffort(
                    childNodes.get(childNodeOfNode[idx]).getEffort());
        }

        // Another run will ensure missing values will be considered now.
        iterativeSolver.doCalculation();

        if (!iterativeSolver.isCalculationFinished()) {
            LOGGER.log(Level.WARNING,
                    "Iterative solver did not finish with full solution.");
        }
    }

    /**
     * Adds Elements to this overlay. Additional parameters will be used for
     * marking to-be-removed elements like bridged effort sources and flow
     * sources. Its only allowed to add one origin, the ground node.
     *
     * @param e Element which is to be added
     * @param isOpen True for replaced flow sources
     * @param isShortcut True for replaced effort sources
     */
    public void registerElement(AbstractElement e, boolean isOpen,
            boolean isShortcut) {

        // check array sizes and increase them if necessary
        boolean[] tempboolarray;
        if (wasEffortSource.length < elements.size() + 1) {
            tempboolarray = new boolean[wasEffortSource.length + 1];
            System.arraycopy(wasEffortSource, 0, tempboolarray, 0,
                    wasEffortSource.length);
            wasEffortSource = tempboolarray;
        }
        if (wasFlowSource.length < elements.size() + 1) {
            tempboolarray = new boolean[wasFlowSource.length + 1];
            System.arraycopy(wasFlowSource, 0, tempboolarray, 0,
                    wasFlowSource.length);
            wasFlowSource = tempboolarray;
        }

        checkElementArraySize(elements.size() + 1);

        wasEffortSource[elements.size()] = isShortcut;
        wasFlowSource[elements.size()] = isOpen;

        if (groundNodeIdx >= 0
                && (e.getElementType() == ElementType.EFFORTSOURCE
                || e.getElementType() == ElementType.FLOWSOURCE)) {
            throw new UnsupportedOperationException("Only one source is "
                    + "allowed to be added to an overlay.");
        }

        // register with LinearNetwork class, this will generate node0idx and
        // perfoms additional checks.
        super.registerElement(e);

        // ground node will be added to the node 0 of the only added source.
        // if the first source is added, get the node from that element.
        if (groundNodeIdx == -1
                && (e.getElementType() == ElementType.EFFORTSOURCE
                || e.getElementType() == ElementType.FLOWSOURCE)) {
            groundNodeIdx = nodes.indexOf(e.getNode(0));
        }
    }

    @Override
    public void registerNode(GeneralNode p) {
        // keep array sizes sufficient
        int[] tempintarray;
        boolean[] tempboolarray;
        if (nodeMergedWith.length < nodes.size() + 1) {
            tempintarray = new int[nodeMergedWith.length + 1];
            System.arraycopy(nodeMergedWith, 0, tempintarray, 0,
                    nodeMergedWith.length);
            nodeMergedWith = tempintarray;
        }
        if (nodeObsolete.length < nodes.size() + 1) {
            tempboolarray = new boolean[nodeObsolete.length + 1];
            System.arraycopy(nodeObsolete, 0, tempboolarray, 0,
                    nodeObsolete.length);
            nodeObsolete = tempboolarray;
        }
        checkNodesArraySize(nodes.size());

        super.registerNode(p);
    }

    @Override
    public void registerElement(AbstractElement e) {
        throw new UnsupportedOperationException(
                "Illegal call, use registerElement(AbstractElement e, boolean "
                + "isOpen, boolean isShortcut) instead!");
    }

    /**
     * When nodes and elements were added, this will setup the whole network
     * including the underlaying solvers needed to provide a solution back to
     * those added elements.
     *
     * <p>
     * This method just calls the indivual steps, they are grouped like this to
     * keep this class a little more organized.
     */
    public void overlaySetup() {
        checkMergeableNodes();
        checkForBridgedPaths();
        checkOpenConnections();
        if (resultAlwaysZeroFlow) {
            return;
        }
        checkForShortcutsByMerging();
        createChildNetworkObjects();
        connectChildNetworkObjects();
        setupSolver();
    }

    /**
     * Find nodes which will be merged.If an effort source was replaced by a
     * shortcut, they were added to this network as linear dissipator with
     * element type shortcut. To remove those elements, the nodes on both ends
     * of that element will be merged, those merged nodes will not be part of
     * child network. Merged nodes will only consider replaced effort sources,
     * nothing more and nothing less.
     *
     * <p>
     * The method does not effectively merge the nodes, it prepares an array
     * that will hold information about which node is getting merged to which
     * node. Chains of merged nodes will be handled later on.
     */
    private void checkMergeableNodes() {
        AbstractElement e;
        int idx;
        int jdx;

        // we need to make 2 iterations, here we save what we worked on
        // in first iteration.
        boolean[] elementChecked = new boolean[elements.size()];

        for (idx = 0; idx < nodeMergedWith.length; idx++) {
            nodeMergedWith[idx] = -1; // init for non-merged nodes
        }

        // usually it does not matter which node will be merged and which will
        // be kept, except for the ground node, this is why those will be 
        // checked first.
        for (idx = 0;
                idx < nodes.get(groundNodeIdx).getNumberOfElements();
                idx++) {
            e = nodes.get(groundNodeIdx).getElement(idx);
            jdx = elements.indexOf(e);
            if (wasEffortSource[jdx]) {
                try {
                    // nodeMergedWith[other node] = ground node
                    nodeMergedWith[nodes.indexOf(
                            e.getOnlyOtherNode(nodes.get(groundNodeIdx)))]
                            = groundNodeIdx;
                } catch (NoFlowThroughException ex) {
                    throw new ModelErrorException("An element that has been "
                            + "a source and was replaced must have had exactly "
                            + "two connections.");
                }

                elementChecked[elements.indexOf(e)] = true;
            }
        }
        // check all other nodes from remaining elements. be aware that idx
        // is now index of elements, above it was index for nodes.
        for (idx = 0; idx < elements.size(); idx++) {
            if (elementChecked[idx]) {
                continue;
            }
            if (wasEffortSource[idx]) {
                // merge node 1 to node 0. This is random, could also be done
                // the other way.
                jdx = nodes.indexOf(elements.get(idx).getNode(1));
                nodeMergedWith[jdx]
                        = nodes.indexOf(elements.get(idx).getNode(0));
            }
            elementChecked[idx] = true; // unneccessary but usefull for debug
        }
        // it is possible that a merged node is merged to a node that is also
        // merged to another node (forgive me). for example, it can be
        // nodeMergedWith[5] = 4
        // nodeMergedWith[4] = 0
        // In this case, nodeMergedWith[5] must be 0, not 4, as 4 is also
        // nonexistant.
        // Iterate through this array until there are no more such thins
        // occuring. It could be a whole chain.
        for (idx = 0; idx < nodeMergedWith.length; idx++) {
            if (nodeMergedWith[idx] >= 0) { // merged[6] = 4
                if (nodeMergedWith[nodeMergedWith[idx]] >= 0) {
                    // the node which the node is merged to, is also merged.
                    // set this as the new node
                    nodeMergedWith[idx] = nodeMergedWith[nodeMergedWith[idx]];
                    idx = -1; // evil: This will restart the loop
                }
            }
        }
    }

    /**
     * When merging nodes, additional paths between those merged nodes will be
     * bridged, making them useless. There is a test case with the same name as
     * this mehtod assigned to this class that does document such a case and
     * runs a test specifically for this method. Refer to the test case
     * documentation for details about what this method does.
     *
     * <p>
     * For each pair of merged nodes, it will be checked for single, direct
     * connections that start at one node and end on the other node. A direct
     * connection exists if its a single path without sources.
     */
    private void checkForBridgedPaths() {
        int idx, jdx, kdx, ldx, mdx;
        GeneralNode nodeI;
        GeneralNode nodeJ, fN, nN;
        AbstractElement traceElem;
        boolean bridgedPathFound;
        boolean[] tmpElementBridged, tmpNodeBridged;

        elementBridged = new boolean[elements.size()]; // init array
        nodeBridged = new int[nodes.size()];
        for (idx = 0; idx < nodes.size(); idx++) {
            nodeBridged[idx] = -1;
        }

        for (idx = 0; idx < nodes.size(); idx++) {
            // Iterate over all nodes
            if (nodeMergedWith[idx] == -1) {
                continue;
            }
            jdx = nodeMergedWith[idx];
            // get the pair of nodes that shall be merged
            nodeI = nodes.get(idx);
            nodeJ = nodes.get(jdx);

            for (kdx = 0; kdx < nodeI.getNumberOfElements(); kdx++) {
                // iterate through all connections on nodeI
                traceElem = nodeI.getElement(kdx); // get element to start trace
                fN = nodeI;
                if (wasEffortSource[elements.indexOf(traceElem)]) {
                    // the bridged source which caused the nodes to be merged
                    // will stay and will not be further analyzed.
                    continue;
                }
                if (traceElem.getElementType() == ElementType.ORIGIN) {
                    continue; // this will never be a path start.
                }
                // prepare an array where bridged elements will be stored
                // during iteration:
                bridgedPathFound = false;
                ldx = -1;
                tmpElementBridged = new boolean[elements.size()];
                tmpNodeBridged = new boolean[nodes.size()];
                // loop is now prepared like this:
                //    fN                        (fN = firstNode, nN = nextNode)
                //    o-----[traceElem]----o----
                for (;;) {
                    try {
                        nN = traceElem.getOnlyOtherNode(fN);
                    } catch (NoFlowThroughException ex) {
                        throw new ModelErrorException("Unexpected non-flow-"
                                + "through type element found");
                    }
                    ldx++; // starts with -1 so first one will be "0".

                    //    fN                   nN    (unknown...)
                    // ----o----[traceElem]----o----XXXXXX----o
                    if (!isElementResistorType(traceElem)) {
                        // found a source or something, so this path will
                        // generate its own current and need to be calculated.
                        break;
                    }
                    // save elment index in array temporarily. This will
                    // be used later if this is identified as a bridged path.
                    tmpElementBridged[elements.indexOf(traceElem)] = true;
                    tmpNodeBridged[nodes.indexOf(nN)] = true;

                    if (nN.equals(nodeJ)) {
                        // if the end is reached and no other break was
                        // hit, we found a full path to the merged node
                        // that is now obsolete.                        
                        bridgedPathFound = true;
                        // remove the last node, just those inbetween shall stay
                        tmpNodeBridged[nodes.indexOf(nN)] = false;
                        break;
                    }
                    // get next element in series - if possible. if its not
                    // possible, we have more than 2 elements on a node and
                    // this is not a single path.
                    // This will then look like this:
                    //    fN                nN   
                    // ----o----[XXXXXX]----o----[traceElem]----o
                    try {
                        traceElem = nN.getOnlyOtherElement(traceElem);
                    } catch (NoFlowThroughException ex) {
                        break; // not a straight single path
                    }
                    // set fN to nN to prepare for next iteration.
                    fN = nN;
                    //                      fN   
                    // ----o----[XXXXXX]----o----[traceElem]----o
                }
                if (bridgedPathFound) {
                    // Merge the information from the array from this iteration
                    // to the class fields to provide this information for
                    // later methods.
                    for (mdx = 0; mdx < elements.size(); mdx++) {
                        elementBridged[mdx]
                                = elementBridged[mdx] || tmpElementBridged[mdx];
                    }
                    for (mdx = 0; mdx < nodes.size(); mdx++) {
                        if (tmpNodeBridged[mdx]) {
                            // use the node index where those nodes can be 
                            // merged to to have something where to get an
                            // effort value from.
                            nodeBridged[mdx] = jdx;
                        }
                    }
                }
            } // end for iterating connections on given node
        } // end for all nodes
    }

    /**
     * Replaced flow sources will be represented as an open connection and can
     * therefore be removed, this is what is done here. The method call will
     * also search for more additional elements which will then not be included
     * in child network, as for example, if those elements are in series with
     * the removed flow source. For solving, their flow will be set to zero,
     * allowing the model to pass the effort through by just calling the
     * calculation methods of those elements.
     */
    private void checkOpenConnections() {
        AbstractElement e, f, g;
        GeneralNode n;
        int idx;
        int jdx;
        elementOutOfClosedCircuit = new boolean[elements.size()];
        for (idx = 0; idx < elements.size(); idx++) {
            if (!wasFlowSource[idx] || elementOutOfClosedCircuit[idx]) {
                continue; // not relevant or source already handled
            }
            e = elements.get(idx); // This must be the flow source now.
            elementOutOfClosedCircuit[idx] = true;
            // go in both directions and check wether those elements are now
            // a loose end connection and mark them for removal also.
            for (jdx = 0; jdx < 2; jdx++) {
                f = e; // start with the flow source as 
                n = e.getNode(jdx); // and the node definde by the loop
                //       f       n
                // ----XXXXXX----o
                for (;;) {
                    // what is behind n?
                    try {
                        g = n.getOnlyOtherElement(f);

                    } catch (NoFlowThroughException ex) {
                        //       f     n | 
                        // ----XXXXXX----o-
                        break; // node has different than 2 elements, finished.
                    }
                    // if we are here without the break above, we have exactly
                    // 1 element behind n, which is now g.
                    //       f       n      g      
                    // ----XXXXXX----o----XXXXXX---
                    // as a consequence, we will not use n anymore and mark it
                    // as obsolete, same for g.
                    nodeObsolete[nodes.indexOf(n)] = true;
                    elementOutOfClosedCircuit[elements.indexOf(g)] = true;
                    // set n to the port behind g
                    try { // assign next node
                        n = g.getOnlyOtherNode(n);
                    } catch (NoFlowThroughException ex) {
                        throw new ModelErrorException("Unexpected non-flow-"
                                + "through type element found");
                    }
                    //       f              g       n
                    // ----XXXXXX----o----XXXXXX----o
                    f = g; // assign for next loop
                    //                      f       n
                    // ----XXXXXX----o----XXXXXX----o
                }
            }
        }
        // Check for special case: All resistor elements out of closed circuit?
        resultAlwaysZeroFlow = true; // init
        for (idx = 0; idx < elements.size(); idx++) {
            if (isElementResistorType(elements.get(idx))) {
                if (!elementOutOfClosedCircuit[idx]) {
                    resultAlwaysZeroFlow = false;
                    break;
                }
            }
        }
    }

    /**
     * It is possible that an element gets shorted to the same node after it is
     * clear which ports have to be merged. This has to be detected here and
     * such elements will be marked also as elementOutOfClosedCircuit so they
     * will not be included in the cild network.
     */
    private void checkForShortcutsByMerging() {
        int node0idx, node1idx;
        for (int idx = 0; idx < elements.size(); idx++) {
            if (elementOutOfClosedCircuit[idx] || elementBridged[idx]
                    || wasEffortSource[idx] || wasFlowSource[idx]
                    || elements.get(idx).getElementType()
                    == ElementType.ORIGIN) {
                continue; // skip these
            }
            // check which nodes will be used after merging
            node0idx = nodes.indexOf(elements.get(idx).getNode(0));
            if (nodeMergedWith[node0idx] >= 0) {
                node0idx = nodeMergedWith[node0idx];
            }
            node1idx = nodes.indexOf(elements.get(idx).getNode(1));
            if (nodeMergedWith[node1idx] >= 0) {
                node1idx = nodeMergedWith[node1idx];
            }
            if (node0idx == node1idx) {
                elementOutOfClosedCircuit[idx] = true;
            }
        }
    }

    /**
     * Creates child elements and nodes and connects them accordingly.
     */
    private void createChildNetworkObjects() {
        int idx;
        GeneralNode p;
        // Create nodes, this skips some and adds those we still need.
        for (idx = 0; idx < nodes.size(); idx++) {
            if (nodeObsolete[idx] || nodeMergedWith[idx] >= 0
                    || nodeBridged[idx] >= 0) {
                // no corresponding child node will exist here
                childNodeOfNode[idx] = -1; // mark as unavailable
                continue;
            }
            p = new GeneralNode(PhysicalDomain.ELECTRICAL);
            childNodes.add(p);

            checkChildNodesArraySize(childNodes.size());
            checkNodesArraySize(nodes.size());
            nodeOfChildNode[childNodes.size() - 1] = idx;
            childNodeOfNode[idx] = childNodes.size() - 1;
        }

        // create and connect a new origin. As only flows will be used in 
        // superposition, any effort shift will not be an issue. The origin will
        // be connected to the node 0 of the only source element later.
        childElements.add(new ClosedOrigin(PhysicalDomain.ELECTRICAL));

        checkElementArraySize(elements.size());
        for (idx = 0; idx < elements.size(); idx++) {
            if (elementOutOfClosedCircuit[idx] || wasEffortSource[idx]
                    || wasFlowSource[idx] || elementBridged[idx]) {
                // no child element will exist for this one
                childElementOfElement[idx] = -1;
                continue;
            }
            switch (elements.get(idx).getElementType()) {
                case DISSIPATOR:
                case OPEN:
                case BRIDGED:
                    childElements.add(
                            new LinearDissipator(PhysicalDomain.ELECTRICAL));
                    break;

                case FLOWSOURCE:
                    childElements.add(
                            new FlowSource(PhysicalDomain.ELECTRICAL));
                    break;

                case EFFORTSOURCE:
                    childElements.add(
                            new EffortSource(PhysicalDomain.ELECTRICAL));
                    break;

                case ORIGIN:
                    continue; // continue for-loop
            }
            childElements.get(childElements.size() - 1).setName(
                    elements.get(idx).toString().split("@")[0]);

            checkChildElementArraySize(childElements.size());
            elementOfChildElement[childElements.size() - 1] = idx;
            childElementOfElement[idx] = childElements.size() - 1;
        }
        // Now, elements and nodes are created and we have horrifying arrays
        // which somehow hold all the information about how to put together the
        // new model.
    }

    private void connectChildNetworkObjects() {
        int idx, jdx, parentNodeIdx, childNodeIdx;
        AbstractElement parent, child;
        // Connect the new origin to the node 0 of the only source-
        childElements.get(0).registerNode(
                childNodes.get(childNodeOfNode[groundNodeIdx]));
        childNodes.get(childNodeOfNode[groundNodeIdx]).registerElement(
                childElements.get(0));

        for (idx = 1; idx < childElements.size(); idx++) {
            // idx: Index in childElements
            // elementOfChildElement[idx]: corresponding parent in "elements"
            child = childElements.get(idx);
            parent = elements.get(elementOfChildElement[idx]);
            // Start with node 0, it must exist on all elements
            parentNodeIdx = node0idx[elementOfChildElement[idx]];
            for (jdx = 0; jdx < 2; jdx++) { // loop to repeat this for node 1
                // check if it has to be merged with a different one
                if (nodeMergedWith[parentNodeIdx] >= 0) {
                    if (childNodeOfNode[parentNodeIdx] != -1) {
                        throw new ModelErrorException("Merged node must not "
                                + "exist as child node but that index exists.");
                    }
                    // use the node instead that this shall be merged to
                    parentNodeIdx = nodeMergedWith[parentNodeIdx];
                }
                // get the corresponding child node to that parent node
                childNodeIdx = childNodeOfNode[parentNodeIdx];
                // make connection:
                childNodes.get(childNodeIdx).registerElement(child);
                child.registerNode(childNodes.get(childNodeIdx));
                // check if node 1 exists and repeat above statements if this
                // was the first run
                if (jdx == 0) {
                    if (parent.getNumberOfNodes() == 2) {
                        // get the node index for node 1 for next run
                        parentNodeIdx = node1idx[elementOfChildElement[idx]];
                    } else if (parent.getElementType() == ElementType.ORIGIN) {
                        // its only allowed for origins to have only one node
                        break;
                    } else {
                        throw new ModelErrorException("Non-origin element with "
                                + "node number different from 2 detected.");
                    }
                }
            } // end for node 0 and 1
        } // end for idx child elements
    }

    private void setupSolver() {
        solver = new RecursiveSimplifier();
        for (GeneralNode p : childNodes) {
            solver.registerNode(p);
        }
        for (AbstractElement e : childElements) {
            solver.registerElement(e);
        }
        solver.recursiveSimplificationSetup(0);
    }
}
