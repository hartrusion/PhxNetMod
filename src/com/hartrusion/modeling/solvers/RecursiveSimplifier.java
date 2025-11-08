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

import com.hartrusion.modeling.ElementType;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.ClosedOrigin;

/**
 * Provides methods to simplify the linear network by one step. Holds references
 * to a parent and a child network, child network will be a network that is
 * simplified from this instance by one step. Each step will be a new instance
 * of this object.
 * <p>
 * By calling the recursiveSimplificationSetup method, a simplified version of
 * this network will be created and this simplified version will then call
 * recursiveSimplificationSetup on the simplified version to generate the next
 * simplification until no further simplification is possible. Therefore, it is
 * called recursive simplifier, it's the same as you would do if you try to
 * solve such circuits manually on paper.
 * <p>
 * This class extends LinearNetwork, LinearNetwork holds the network which is to
 * be analyzed and simplified. This extension then holds all needed information
 * about the child network which is the simplified form, if possible.
 * <p>
 * Child nodes and child elements are new created elements which will be used in
 * a new network. Both networks, the extended LinearNetwork and a new network
 * derived with child elements, will be independent and not connected in any way
 * except by this class knowing of both of them.
 * <p>
 * To set up the network, connected elements and nodes are required, they have
 * to be registered to each other. First add nodes, then add the elements. Run
 * recursiveSimplificationSetup(0) to create all children, running the
 * simplification process. Use prepareRecursiveCalculation() and
 * doRecursiveCalculation() to transfer all values to child network, update them
 * and ultimately calculate all values on all elements added to this network.
 *
 * @author Viktor Alexander Hartung
 */
public class RecursiveSimplifier extends ChildNetwork {

    private static final Logger LOGGER = Logger.getLogger(
            RecursiveSimplifier.class.getName());

    private RecursiveSimplifier parent; // from what this instance is simplified
    private RecursiveSimplifier child; // the simplified instance created here

    private boolean[] isSubstituted; // true for all replaced elements
    private boolean[] nodeObsolete; // true for all diminished nodes
    private boolean[] hasFloatingLoop; // if port has a float loop connected
    private boolean[] deadEnd; // true for all dead end elements

    /**
     * Holds a list of all substitutes, those are basically containers holding
     * series or parallel resistors. One substitution represents one resistor in
     * child network (it extends the LinearDissipator class) and holds all
     * elements which were substituted by this one.
     */
    private final List<SimplifiedResistor> substElements;

    /**
     * Represents the number of recursions or which layer depth this instance
     * is.
     */
    private int childLayer;

    private boolean substitutesFound, obsoleteNodesListUpdated,
            childElementsCreated, newElementsConnected, containsFloatingLoop;

    /**
     * If star delta transform is done on this recursion, this will hold a
     * reference to the center node which has the three resistors connected. The
     * star node will be missing in the child network.
     */
    private GeneralNode starNode;

    private StarDeltaTransform starDelta;
    private StarSquareTransform starSquare;

    public RecursiveSimplifier() {
        substElements = new ArrayList<>();

        isSubstituted = new boolean[INIT_ARRAY_SIZE];
        nodeObsolete = new boolean[INIT_ARRAY_SIZE];
        hasFloatingLoop = new boolean[INIT_ARRAY_SIZE];
        deadEnd = new boolean[INIT_ARRAY_SIZE];

        substitutesFound = false;
        obsoleteNodesListUpdated = false;
        childElementsCreated = false;
        newElementsConnected = false;
        containsFloatingLoop = false;
        childLayer = 0;
    }

    /**
     * Go through all elements and perform simplification if possible. This will
     * check for parallel and series items, creating new substElements of type
     * SimplifiedReplacement which include the found parallel or series items.
     * If proper elements are found, isSubstituted will be set true with the
     * same index as the elements list.
     * <p>
     * After the method is run, it is known which elements can be substituted
     * and their substitution objects are prepared. They are just prepared, not
     * updated or calculated.
     * <p>
     * Writes to isSubstituted array, creates new SimplifiedReplacement classes
     * and puts references in list substElements
     *
     * @return true if at least one simplification was found and successful.
     */
    private boolean searchSubstitutes() {
        AbstractElement elem_i, elem_j, loopcheck;
        SimplifiedResistor rep = null;
        GeneralNode nextNode, firstNode, startNode, lastNode = null;
        boolean prevSubstituesFound = false;
        int jdx, kdx;
        checkArraySizes();
        for (int idx = 0; idx < elements.size(); idx++) {
            if (isSubstituted[idx]) {
                continue; // already substituted
            }
            elem_i = elements.get(idx);
            if (!isElementResistorType(elem_i)) {
                continue; // can not be substituted (voltage source, ...)
            }
            // Check if this item might be in series with others. this can be
            // done by check how many connections the nodes of that element
            // has.
            //             node0    elem_i   node1
            // ----XXXXX-----*------XXXXX-----*----XXXXX---
            // Elements which may be in series only have two connections on
            // the node they share.
            if (elem_i.getNode(0).getNumberOfElements() == 2
                    || elem_i.getNode(1).getNumberOfElements() == 2) {
                // you are here: element is not in parallel. go to last element
                // that may be in series in direction of node 0 to start there.
                // As we do not know where we started, we start to try to follow
                // in one direction and get to the last element of the series.
                firstNode = elem_i.getNode(0); // n is node next to elem
                //            firstNode elem_i   
                // ----XXXXX-----*------XXXXX-----*----XXXXX---
                loopcheck = elem_i; // remember where we started
                for (;;) {
                    try {
                        elem_j = firstNode.getOnlyOtherElement(elem_i);
                    } catch (NoFlowThroughException ex) {
                        break; // nothing more that is in series.
                    }
                    if (loopcheck == elem_j) {
                        throw new ModelErrorException(
                                "Series loops into itself.");
                    }
                    // get and examine elem_j element behind node.
                    //    elem_j firstNode  elem_i
                    // ----XXXXX-----*------XXXXX-----*----XXXXX---
                    if (!isElementResistorType(elem_j)) {
                        break;
                    }
                    if (elem_j.getNumberOfNodes() != 2) {
                        // unexpected, as each element in network must
                        // have only exactly 2 nodes. Should not be allowed
                        // but will be checked anyway.
                        throw new ModelErrorException(
                                "Unexpected number of nodes");
                    }
                    // get node behind element and set it as firstNode. as there
                    // are only two nodes, it must be the other one.
                    if (elem_j.getNode(0) == firstNode) {
                        firstNode = elem_j.getNode(1);
                    } else {
                        firstNode = elem_j.getNode(0);
                    }
                    //  firstNode elem_j          elem_i   
                    // -----*----XXXXX-----*------XXXXX-----*----XXXXX---
                    //
                    elem_i = elem_j; // set for nex iteration.
                    //  firstNode elem_i              
                    // -----*----XXXXX-----*------XXXXX-----*----XXXXX---

                    if (!elements.contains(elem_i)) {
                        throw new ModelErrorException("Found element "
                                + "during serial search which is not part "
                                + "of the network.");
                    }
                } // end while

                // you are here: elem_i is the first of possible series, next
                // node facing in the direction away from the direction where we
                // want to examine how many elements are now following up in
                // series. elem_j is the first element to examine.
                nextNode = firstNode; // remember this node
                // get the node on the other side of elem_i
                if (elem_i.getNode(0) == nextNode) {
                    nextNode = elem_i.getNode(1);
                } else {
                    nextNode = elem_i.getNode(0);
                }
                //  firstNode   elem_i   nextNode   
                // ------*-------XXXXX-------*-------XXXXX----*---

                // index idx of for-loop is not usable here so we need new one.
                kdx = elements.indexOf(elem_i);

                //  firstNode   elem_i   nextNode   
                // ------*-------XXXXX-------*-------XXXXX----*---
                // now, same as above, just in the other direction.
                loopcheck = elem_i; // remember where we started
                startNode = firstNode; // also this for detecting floating ring
                for (;;) {
                    // if this not the first iterations, it will look like this:
                    //                      firstNode   elem_i   nextNode 
                    // ------*-------XXXXX-------*-------XXXXX----*---
                    try { // get element behind next node:
                        elem_j = nextNode.getOnlyOtherElement(elem_i);
                    } catch (NoFlowThroughException ex) {
                        break; // for series, we need exactly two connections.
                    }
                    if (loopcheck == elem_j) {
                        throw new ModelErrorException(
                                "Series loops into itself.");
                    }
                    if (!elements.contains(elem_j)) {
                        throw new ModelErrorException("Found element"
                                + "during series search which is not part"
                                + "of the network.");
                    }
                    //  firstNode   elem_i   nextNode   elem_j  (lastNode)
                    // ------*-------XXXXX-------*-------XXXXX----*---
                    if (!isElementResistorType(elem_j)) {
                        break; // element is of a different type (source,...)
                    }
                    // if we reach this line, we have elem_i and elem_j in
                    // series, while jdx is the index od elem_i in elements.
                    if (!isSubstituted[kdx]) { // kdx is set to first element
                        // first call: create new object
                        rep = new SimplifiedResistor(false);
                        // in case of open ring, we need to undo the set of
                        // substitutesFound when it's clear that we have an
                        // open ring detected.
                        prevSubstituesFound = substitutesFound;
                        substitutesFound = true;
                        substElements.add(rep); // save in list here
                        isSubstituted[kdx] = true; // mark as replaced
                        rep.addParentNode(firstNode, 0); // save first node
                        rep.addParentElements((LinearDissipator) elem_i);
                    }
                    jdx = elements.indexOf(elem_j);
                    isSubstituted[jdx] = true;
                    if (rep != null) { // suppress warning, logic is okay
                        rep.addParentElements((LinearDissipator) elem_j);
                    }

                    // set node behind elem_j as lastNode in case we will
                    // exit this while-loop to finish
                    if (elem_j.getNode(0) == nextNode) {
                        lastNode = elem_j.getNode(1);
                    } else {
                        lastNode = elem_j.getNode(0);
                    }
                    //  firstNode   elem_i   nextNode   elem_j  lastNode
                    // ------*-------XXXXX-------*-------XXXXX----*---

                    // prepare next iteration:
                    firstNode = nextNode;
                    nextNode = lastNode;
                    elem_i = elem_j;
                    //                      firstNode   elem_i   nextNode 
                    // ------*-------XXXXX-------*-------XXXXX----*---
                } // end while

                // was there any series detected? in this case, we need to
                // add the last node to the replacement element.
                // the != null is just to suppress warning, should never happen.
                if (isSubstituted[kdx] && rep != null) {
                    if (startNode == nextNode) {
                        // Another special case: if the last found node is the
                        // same as the first one, the series is actually a loop
                        // that can not be added to the next simplification.
                        substitutesFound = prevSubstituesFound; // undo
                        rep.setFloatingLoop();
                        rep.calculateEnclosedNodes();
                        // just some random check:
                        if (rep.getParentNode(0) != startNode) {
                            throw new ModelErrorException("Expected the node "
                                    + "to be another one?");
                        }
                        hasFloatingLoop[nodes.indexOf(startNode)] = true;
                        containsFloatingLoop = true;
                    } else {
                        rep.addParentNode(lastNode, 1);
                    }
                    rep.generateNameFromParents();
                }
                // no matter what, further parallel checks requires
                // at least 3 connections on both nodes so we're done here.
                continue;
            }

            // search for items parallel to this one by checking them all
            for (jdx = 0; jdx < elements.size(); jdx++) {
                if (isSubstituted[jdx]) {
                    continue; // already substituted
                } else if (idx == jdx) {
                    continue; // don't compare same elements
                }
                elem_j = elements.get(jdx);
                if (!isElementResistorType(elem_j)) {
                    continue; // can not be substituted (voltage source, ...)
                }
                // you are here: elements can be compared now.
                // parallel elements share the same nodes, making them parallel.
                if (elem_i.getNode(0) == elem_j.getNode(0)
                        && elem_i.getNode(1) == elem_j.getNode(1)
                        || elem_i.getNode(0) == elem_j.getNode(1)
                        && elem_i.getNode(1) == elem_j.getNode(0)) {
                    // nodes match! elements are in parallel.
                    if (!isSubstituted[idx]) {
                        // first one creates a new replacement
                        rep = new SimplifiedResistor(true);
                        substitutesFound = true;
                        substElements.add(rep); // save in list here
                        isSubstituted[idx] = true;
                        rep.addParentNodes(
                                elem_i.getNode(0), elem_i.getNode(1));
                        rep.addParentElements((LinearDissipator) elem_i);
                    }
                    // add all further elements to this replacement:
                    if (rep != null) {
                        rep.addParentElements((LinearDissipator) elem_j);
                    }
                    isSubstituted[jdx] = true;
                }
                if (isSubstituted[jdx] && rep != null) { // something was done
                    rep.generateNameFromParents();
                }
            }
        }
        return substitutesFound;
    }

    /**
     * Updates array nodeObsolete.After the substitutions are created, this will
     * ûpdate the array nodeObsolete to allow quick access to the information
     * weather a node is still in use in the derived child network or not.
     * <p>
     * Called after searchSubstitutes(). Will set nodeObsolete[idx] to true for
     * each index of nodes.get(idx) when a node will not be replicated for the
     * child network.
     */
    private void updateObsoleteNodesList() {
        GeneralNode n;
        if (!substitutesFound) {
            throw new ModelErrorException(
                    "No substitutes were found. Maybe network simplification "
                    + "was unsuccessful or method was not even called.");
        }
        for (SimplifiedResistor sr : substElements) {
            if (sr.isParallel()) {
                continue;
            }
            sr.calculateEnclosedNodes();
        }
        for (int idx = 0; idx < nodes.size(); idx++) {
            n = nodes.get(idx);
            for (SimplifiedResistor sr : substElements) {
                if (sr.isParallel()) {
                    continue;
                }
                if (sr.isNodeEnclosed(n)) {
                    nodeObsolete[idx] = true;
                    break;
                }
            }
        }
        obsoleteNodesListUpdated = true;
    }

    /**
     * Create new elements and nodes for simplified network. This will create
     * new nodes and elements for use in the child network where they are still
     * in place. References to all new nodes and elements will be added to the
     * corresponding lists. Enclosed nodes and elements which were simplified
     * will not be created. For substituted elements, the already created
     * elements in substElements will to be used.
     * <p>
     * After this is finished, all elements for new model are existing. The
     * information on how those elements need to be connected can be derived
     * from the arrays holding the corresponding indexes.
     * <p>
     * call after updateEnclosedNodesList() or checkDeadEnds().
     */
    private void createChildElements() {
        int idx;
        if (!obsoleteNodesListUpdated) {
            throw new ModelErrorException(
                    "List of enclosed nodes needs to be"
                    + " updated before calling this!");
        }
        for (idx = 0; idx < nodes.size(); idx++) {
            // Each new node will be added by going through the existing
            // nodes, assignment will be done using two arrays.
            if (!nodeObsolete[idx]) {
                childNodes.add(new GeneralNode(PhysicalDomain.ELECTRICAL));
                // set name from parent node
                childNodes.get(childNodes.size() - 1).setName(
                        nodes.get(idx).toString().split("@")[0]);
                // save indexes in array to be able to track and link nodes of
                // original and simplification network.
                nodeOfChildNode[childNodes.size() - 1] = idx;
                childNodeOfNode[idx] = childNodes.size() - 1;
            }
        }

        for (idx = 0; idx < elements.size(); idx++) {
            if (!isSubstituted[idx] && !deadEnd[idx]) {
                switch (elements.get(idx).getElementType()) {
                    case DISSIPATOR:
                    case OPEN:
                    case BRIDGED:
                        childElements.add(
                                new LinearDissipator(
                                        PhysicalDomain.ELECTRICAL));
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
                        childElements.add(
                                new ClosedOrigin(PhysicalDomain.ELECTRICAL));
                        break;
                }
                childElements.get(childElements.size() - 1).setName(
                        elements.get(idx).toString().split("@")[0]);
                // Save indexes in arrays to be able to track which element
                // was which one before and after simplification.
                elementOfChildElement[childElements.size() - 1] = idx;
                childElementOfElement[idx] = childElements.size() - 1;
            }
        }
        childElementsCreated = true;
    }

    /**
     * Connects all created child elements and substElements to child nodes.
     * This creates the full model which is the simplified version of the parent
     * network.
     * <p>
     * As we saved node0idx and node1idx when adding, we can now use this
     * information to put each connection to the corresponding side of the
     * element. We will have correct flow direction this way.
     */
    private void connectNewElementsAndSubstitutes() {
        GeneralNode p;
        int idx;
        int cpIdx;
        if (!childElementsCreated) {
            throw new ModelErrorException(
                    "New elements need to be created first.");
        }
        registerChildElements();
        // Add substitute elements to the new network. Each substitute element
        // knows the nodes to which the original elements were connected.
        for (SimplifiedResistor sr : substElements) {
            if (sr.isFloatingLoop()) {
                // a floating loop will not be part of the new subnet.
                continue;
            }
            for (idx = 0; idx < 2; idx++) {
                cpIdx = childNodeOfNode[nodes.indexOf(sr.getParentNode(idx))];
                p = childNodes.get(cpIdx);
                p.registerElement(sr);
                sr.registerNode(p);
            }
        }
        newElementsConnected = true;
    }

    /**
     * Registers nodes and elements which have been duplicated to child network
     * with each other. It reads the assignment in arrays childNodeOfNode and
     * elementOfChildElement, so those arrays need to be set up properly. The
     * method was once part of a larger method, it's just a single method now
     * for reusability.
     */
    public void registerChildElements() {
        AbstractElement e;
        GeneralNode n;
        int idx, cpIdx;
        for (idx = 0; idx < childElements.size(); idx++) {
            e = childElements.get(idx);
            // if anyone ever reads this, I am sorry. Here we check which
            // element corresponds to the child element and get the index.
            // then we see to which node it is connected on index 0 of the 
            // element. Then we get the index of the corresponding child node
            // of the simplified network.            
            cpIdx = childNodeOfNode[node0idx[elementOfChildElement[idx]]];
            n = childNodes.get(cpIdx);
            // register node and element
            n.registerElement(e);
            e.registerNode(n);
            // check if original element has a second connection (all except 
            // origins have two nodes) and connect other side also.
            if (elements.get(elementOfChildElement[idx])
                    .getNumberOfNodes() == 2) {
                cpIdx = childNodeOfNode[node1idx[elementOfChildElement[idx]]];
                n = childNodes.get(cpIdx);
                n.registerElement(e);
                e.registerNode(n);
            }
        }
    }

    /**
     * Registers all created child nodes and elements to a given network, which
     * is usually the next recursive iteration of the simplification process.
     * <p>
     * A reference to the network given as argument will be saved as field
     * "child" in this class.
     *
     * @param n Reference to network where to add child elements to.
     */
    public void addChildElementsToNewNetwork(RecursiveSimplifier n) {
        if (!newElementsConnected) {
            throw new ModelErrorException(
                    "This requires new elements to be connected first.");
        }
        for (GeneralNode p : childNodes) {
            n.registerNode(p);
        }
        for (AbstractElement e : childElements) {
            n.registerElement(e);
        }
        for (SimplifiedResistor e : substElements) {
            if (e.isFloatingLoop()) {
                continue;
            }
            n.registerElement(e);
        }
        child = n; // save reference to new child network
    }

    /**
     * Checks all nodes in nodes list for possible star nodes. If one star node
     * is found, it will be set as star node. A star node is a node that has
     * three resistors connected to it, making it suitable for star delta
     * transform.
     * <p>
     * As star delta transform is only used to allow further simplification and
     * the network might be totally different in terms of what to simplify after
     * the transformation, this is only done once per simplification run. It is
     * preferred to do small steps per recursion, this way it is still possible
     * to debug and understand the whole process. Unlike the simplification of
     * parallels and series, multiple star delta transforms are impossible to
     * track later on.
     *
     * @return true if a star node is found.
     */
    private boolean searchStarDelta() {
        checkArraySizes();
        for (GeneralNode n : nodes) {
            if (StarDeltaTransform.checkForStarNode(n)) {
                starNode = n;
                return true;
            }
        }
        return false;
    }

    private void setupStarReplacementDelta() {
        starDelta = new StarDeltaTransform();
        // This will create the delta arrangement (3 nodes and 3 resistors):
        starDelta.setupStar(starNode);
        // Star node will be enclosed by the delta arrangement
        nodeObsolete[nodes.indexOf(starNode)] = true;
        // also nodes that are now represented in star delta will not be needed
        // to be created again, their representatives are already existing.
        for (GeneralNode p : nodes) {
            if (starDelta.starContainsNode(p)) {
                nodeObsolete[nodes.indexOf(p)] = true;
            }
        }
        obsoleteNodesListUpdated = true;
        // same goes for elements, mark elements which are now represented in
        // the star delta arrangement
        for (AbstractElement e : elements) {
            if (starDelta.starContainsElement(e)) {
                isSubstituted[elements.indexOf(e)] = true;
            }
        }
        // add the three nodes to child elements list, those
        // will be the first three nodes and elements in both lists.
        for (int idx = 0; idx < 3; idx++) {
            childNodes.add(starDelta.getDeltaNode(idx));
            childNodeOfNode[nodes.indexOf(starDelta.getStarNode(idx))] = idx;
            nodeOfChildNode[idx] = nodes.indexOf(starDelta.getStarNode(idx));
        }

    }

    private void connectNewElementsAndDeltaReplacement() {
        if (!childElementsCreated) {
            throw new ModelErrorException(
                    "New elements need to be created first.");
        }
        registerChildElements();
        for (int idx = 0; idx < 3; idx++) {
            childElements.add(starDelta.getDeltaElement(idx));
        }
        newElementsConnected = true;
    }

    private boolean searchStarSquare() {
        checkArraySizes();
        for (GeneralNode n : nodes) {
            if (StarSquareTransform.checkForStarNode(n)) {
                starNode = n;
                return true;
            }
        }
        return false;
    }

    private void setupStarReplacementSquare() {
        starSquare = new StarSquareTransform();
        // This will create the star square arrangement (4 nodes and 6
        // resistors, which are more than before btw):
        starSquare.setupStar(starNode);
        // Star node will be enclosed by the square arrangement
        nodeObsolete[nodes.indexOf(starNode)] = true;
        // also nodes that are now represented in star square will not be needed
        // to be created again, their representatives are now already existing.
        for (GeneralNode p : nodes) {
            if (starSquare.starContainsNode(p)) {
                nodeObsolete[nodes.indexOf(p)] = true;
            }
        }
        obsoleteNodesListUpdated = true;
        // same goes for elements, mark elements which are now represented in
        // the star square arrangement
        for (AbstractElement e : elements) {
            if (starSquare.starContainsElement(e)) {
                isSubstituted[elements.indexOf(e)] = true;
            }
        }
        // add the generated outer nodes to child elements list, those
        // will be the first three nodes and elements in both lists.
        for (int idx = 0; idx < 4; idx++) {
            childNodes.add(starSquare.getSquareNode(idx));
            childNodeOfNode[nodes.indexOf(starSquare.getStarNode(idx))] = idx;
            nodeOfChildNode[idx] = nodes.indexOf(starSquare.getStarNode(idx));
        }
        LOGGER.log(Level.INFO,
                "Complex network: StarSquare transform necessary.");

    }

    private void connectNewElementsAndSquareReplacement() {
        if (!childElementsCreated) {
            throw new ModelErrorException(
                    "New elements need to be created first.");
        }
        registerChildElements();
        for (int idx = 0; idx < 6; idx++) {
            childElements.add(starSquare.getSquareElement(idx));
        }
        newElementsConnected = true;
    }

    /**
     * Dead ends are elements which have one loose end, a node, that is not
     * connected to anything else. Dead ends have to be removed to provide a
     * working network for the next recursion.
     *
     * <p>
     * Uses the same nodeObsolete array like the other methods here.
     *
     * @return true, if at least one dead end element was found.
     */
    private boolean checkDeadEnds() {
        boolean deadEndFound = false;
        checkArraySizes();
        for (GeneralNode n : nodes) {
            if (n.getNumberOfElements() == 1) {
                if (elements.indexOf(n.getElement(0)) > deadEnd.length - 1) {
                    throw new ModelErrorException(
                            "This blows my mind. How is it possible");
                }
                deadEnd[elements.indexOf(n.getElement(0))] = true;
                nodeObsolete[nodes.indexOf(n)] = true;
                deadEndFound = true;
                obsoleteNodesListUpdated = true;
            }
        }
        return deadEndFound;
    }

    /**
     * Checks for proper sizes of arrays, needs to be called before working with
     * them. We work with some arrays and indexes here which are used on
     * multiple lists. This method just allocates the needed memory.
     */
    private void checkArraySizes() {
        // Todo: replace with arrayutils functions
        boolean[] tempboolarray;
        if (nodeObsolete.length < nodes.size()) {
            tempboolarray = new boolean[nodes.size()];
            System.arraycopy(nodeObsolete, 0, tempboolarray, 0,
                    nodeObsolete.length);
            nodeObsolete = tempboolarray;
        }
        if (hasFloatingLoop.length < nodes.size()) {
            tempboolarray = new boolean[nodes.size()];
            System.arraycopy(hasFloatingLoop, 0, tempboolarray, 0,
                    hasFloatingLoop.length);
            hasFloatingLoop = tempboolarray;
        }
        if (isSubstituted.length < elements.size()) {
            tempboolarray = new boolean[elements.size()];
            System.arraycopy(isSubstituted, 0, tempboolarray, 0,
                    isSubstituted.length);
            isSubstituted = tempboolarray;
        }
        if (deadEnd.length < elements.size()) {
            tempboolarray = new boolean[elements.size()];
            System.arraycopy(deadEnd, 0, tempboolarray, 0,
                    deadEnd.length);
            deadEnd = tempboolarray;
        }

        checkChildNodesArraySize(nodes.size());
        checkNodesArraySize(nodes.size());
        checkChildElementArraySize(elements.size());
        checkElementArraySize(elements.size());
    }

    @Override
    public void prepareCalculation() {
        // for a reason that is not yet fully understood, this call has to
        // be placed here, it will call the iterativeSolver.prepareCalculation
        // method and this will call it on all elements of this network that
        // are the simplified ones. Otherwise, large networks will not do a
        // correct calculation of resistances from top to bottom, child resistor
        // values will be wrong. As it works, we will leave it as it is.
        super.prepareCalculation();

        if (childElementsCreated) {
            // set values to child elements from this layer.
            // iterate over parents
            for (int idx = 0; idx < elements.size(); idx++) {
                if (isSubstituted[idx] || deadEnd[idx]) {
                    continue;
                }
                transferPropertyToChild(idx);
            }
        }

        // super.prepareCalculation();
        // this will call prepareCalculation on all elements, including the
        // simplified resistors. they will update the child resistors values
        // through this call.
        // However, for some whatever reason this was moved up in this method.
    }

    /**
     * Perform a search of possible substitutes on this network. If any are
     * found, a new network which includes the simplified elements will be
     * created. It will then get this method called by this call also, ending up
     * in a recursion.
     * <p>
     * To start this from the top level network, simply put "0" as an argument.
     *
     * @param parentLayers Current number of parent layers to keep track of
     * recursion depth. Set to 0 for call from application.
     * @return Created number of child layers
     */
    public int recursiveSimplificationSetup(int parentLayers) {
        int numberOfElements;
        
        if (checkDeadEnds()) {
            createChildElements();
            registerChildElements();
            newElementsConnected = true;

            return generateChild(parentLayers);
        }

        if (searchSubstitutes()) {
            updateObsoleteNodesList();
            createChildElements();
            connectNewElementsAndSubstitutes();

            return generateChild(parentLayers);
        }

        if (searchStarDelta()) {
            setupStarReplacementDelta();
            createChildElements();
            connectNewElementsAndDeltaReplacement();

            return generateChild(parentLayers);
        }

        if (searchStarSquare()) {
            setupStarReplacementSquare();
            createChildElements();
            connectNewElementsAndSquareReplacement();

            return generateChild(parentLayers);
        }
        
        // We might end with a floating loop in the last recursion. In this 
        // case, elements which are part of the loop are isSubstituted[idx] =
        // true but no substitutuion was performed (otherwise we would not get
        // here because of the return statements above).
        numberOfElements = elements.size();
        if (containsFloatingLoop) { // subtract those.
            for (int idx = 0; idx < isSubstituted.length; idx++) {
                numberOfElements -= 1;
            }
        }

        if (numberOfElements <= 4) {
            LOGGER.log(
                    Level.INFO, (parentLayers + 1)
                    + " layers created, last layer containing "
                    + numberOfElements + " elements.");
        } else {
            LOGGER.log(
                    Level.WARNING, (parentLayers + 1)
                    + " layers created, last layer still containing "
                    + numberOfElements + " elements. No solution guaranteed.");
        }
        return parentLayers;
    }

    /**
     * Generates the child layers by adding all elements to a new instance of
     * this class. This is a shortcut of a few lines from the above method, as
     * we had those lines now three times, it's time to move it to a separate
     * method.
     *
     * @param parentLayers Number of parent layers to pass through
     * @return generated layers after recursion reached its end.
     */
    private int generateChild(int parentLayers) {
        parentLayers = parentLayers + 1;
        if (parentLayers >= 1000) {
            // usually a few layers, like 5-10, should be enough.
            throw new ModelErrorException("Endless recusion - more than "
                    + "1000 layers created.");
        }
        addChildElementsToNewNetwork(new RecursiveSimplifier());
        child.setParent(this);
        childLayer = parentLayers;
        // recursive call
        return child.recursiveSimplificationSetup(parentLayers);
    }

    public LinearNetwork getParent() {
        return parent;
    }

    public void setParent(RecursiveSimplifier parent) {
        this.parent = parent;
    }

    public LinearNetwork getChild() {
        return child;
    }

    public void setChild(RecursiveSimplifier child) {
        this.child = child;
    }

    public void prepareRecursiveCalculation() {
        prepareCalculation(); // reset from parents to child.

        if (starDelta != null) {
            starDelta.calculateDeltaResistorValues();
        }

        if (starSquare != null) {
            starSquare.calculateSquareResistorValues();
        }

        // Those floating loops are not part of a child so they need to be
        // explicitly called to reset a variable
        for (SimplifiedResistor sr : substElements) {
            if (sr.isFloatingLoop()) {
                sr.prepareCalculation();
            }
        }

        // prepare calculation will update resistance values on simplified
        // resistors so this will communicate new values from parent down to
        // all children.
        if (child != null) {
            child.prepareRecursiveCalculation();
        }
    }

    public void doRecursiveCalculation() {
        // first, all origins must be able to force their efforts upon
        // nodes, otherwise the efforts will be forced back to the origin,
        // resulting an exception. The attached iterative solver has its own
        // methods to handle this.
        iterativeSolver.doCalculationOnEnforcerElements();
        
        // Next we will call doCalculation on all open connections which will
        // have exactly zero flow. This will reduce numeric issues as we do not
        // need to calculate this like 5.23 - 1.23 - 4.0 = 0.0 what might not
        // be 0.0 bitwise, we just set it to the excatly double 0.0 value.
        // This little line dramatically helps to improve accuracy of the 
        // whole solver.
        for (AbstractElement e : elements) {
            if (e.getElementType() == ElementType.OPEN) {
                e.doCalculation();
            }
        }

        if (child != null) {
            child.doRecursiveCalculation();
        }
        // start calculation with last child. The network will be solved and
        // the used SimplifiedResistor will use its private method 
        // assignResultsToParents. Therefore, if the child network is solved,
        // the results will be available for the elements which are part of this
        // network here. The remaining missing efforts and flows can then be
        // calculated by simply calling all calculations on all objects and the
        // network will be in a fully calculated state.

        // In case of a star delta transform, a calculation method has to be
        // called explicitly to transform the results from the delta back to
        // the star circuit.
        if (starDelta != null) {
            starDelta.calculateStarValuesFromDelta();
        }
        if (starSquare != null) {
            starSquare.calculateStarValuesFromSquare();
        }

        // All nodes must have their corresponding effort values from child
        // nodes copied, usually this is done by the starDelta or the
        // simplified resistors themselves, however, there are some rare, not
        // yet investigated cases where this is not done automatically, so
        // we will ensure it here.
        for (int idx = 0; idx < childNodes.size(); idx++) {
            if (!nodes.get(nodeOfChildNode[idx]).effortUpdated()) {
                if (childNodes.get(idx).effortUpdated()) {
                    nodes.get(nodeOfChildNode[idx]).setEffort(
                            childNodes.get(idx).getEffort(), null, true);
                }
            } else {
                if (Math.abs((childNodes.get(idx).getEffort()
                        - nodes.get(nodeOfChildNode[idx]).getEffort()))
                        > 1e-3) { // from 1e-8 to 1e-3
                    //throw new CalculationException("Validation of already set"
                    //        + " effort value failed.");
                    LOGGER.log(Level.WARNING, "Validation of already set effort"
                            + " value failed.");
                }
            }
        }

        // now run the super class method, this will run an iterative call
        // on all elements until nothing can be calculated.
        doCalculation();

        // For some reason that is yet unknown, the doCalculation call might
        // not be sufficient on the simplified resistors, maybe the return
        // value or the isCalculationFinished is not properly overridden or
        // whatever. It does the job to just run the doCalculation on all
        // elements once again.
        for (AbstractElement e : elements) {
            e.doCalculation();
        }

        // After calculation,everything should be calculated but not yet the
        // floating loop, it has no idea that it is existing.
        if (containsFloatingLoop) {
            for (SimplifiedResistor sr : substElements) {
                if (sr.isFloatingLoop()) {
                    sr.doFloatingLoopCalculation();
                }
            }
            // As the floating loop calculation might generate results, a new
            // calculation run is necessary to make simplified resistors update
            // parent elements.
            for (AbstractElement e : elements) {
                e.doCalculation();
            }
        }
        // At this point, the network must be in a full calculated state, it is
        // made in a way that even open connections do produce values so we
        // throw a warning here in case that did not happen. This warning most
        // times resulted in additional solver coding work.
        if (!isCalculationFinished()) {
            LOGGER.log(Level.WARNING, "Network solving not finished "
                    + "on child layer no. " + childLayer);
        }
    }
}
