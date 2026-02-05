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

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.Capacitance;
import com.hartrusion.modeling.general.ClosedOrigin;
import com.hartrusion.modeling.general.Dummy;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.FlowSource;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.general.SelfCapacitance;
import com.hartrusion.modeling.phasedfluid.PhasedExpandingThermalExchanger;
import com.hartrusion.modeling.steam.SteamIsobaricIsochoricEvaporator;
import static com.hartrusion.util.ArraysExt.*;

/**
 * Creates a duplicate network of given elements and nodes which can be a part
 * of a large network, solves it and transfers the results back to the given
 * elements. This network holds lists with new nodes and elements (called
 * network elements and nodes) derived from the original network (the original
 * part here is called linked elements and linked nodes) which is to solve.
 * Subnet descibes the part of the model which is to be solved. As the large
 * physical model can and should be divided into smaller, independably solveable
 * parts, the term subnet is used.
 *
 * <p>
 * The derived network contains only closed loops in terms of electrical
 * circuits. There are no open origins or self-capacitances present in the
 * network so the new network can be solved with means of electronic circuit
 * mehtods.
 *
 * <p>
 * The network is a first representation of the subnet in a more simplified
 * form, representing all subnet elements just as general elements which can
 * then be solved by means of electronic circuits. This also means that NO
 * solution for heat fluid or other extension will be provided, however, the
 * model should be able to calculate those things based on the results of this
 * network.
 *
 * <p>
 * It is basically the same as if you do those calculations manually on paper,
 * there are some methods available to provide solutions but you will always
 * have to some calculations left to do. Those are quite easy but they are still
 * there. Sames goes for this class, it will provide only effort and flow values
 * for all resistors, but this should be enough to get a fully calculated
 * network by just calling doCalculation on the remaining elements.
 *
 * <p>
 * <strong>There is a certain order to in which the network element has to be
 * set up. </strong>
 * <ul>
 * <li>Define which part shall be the subnet.
 * <li>Link all nodes from the subnet which shall be included in the network by
 * calling {@code registerNode} method.
 * <li>Link all relevant elements using {@code registerElement}. Only elements
 * with two node connections can be added. It will be checked that all nodes
 * connected to those elements were added to this network. The first origin that
 * is added will be the only one that stays in the model. All other origins will
 * be connected to this one or be replaced with effort sources.
 * <li>Call setupTransferSubnet to create and setup the network. This will
 * initialize additional solvers, also creating lots of networks, to provide
 * methods for solving.
 * </ul>
 *
 * <p>
 * Capacitors get replaced internally with effort sources to imply the effort
 * value of the subnet to the network. If they represent a boundary of the
 * network (for example, in fluid systems, a pipe network may be surrounded with
 * tanks to form a boundary).
 *
 * <p>
 * As this transfers a network to a strict linear electronic circuit, only
 * closed origins are available. All origins will be transfered to a single
 * ground origin, origins with a different effort value than the first found
 * origin will be replaced with an effort source which is connected to the
 * ground origin.
 *
 * <p>
 * The created network will be then further transfered into more networks as
 * necessary, most times to be solved with superposition layering which then
 * will create even more networks.
 *
 * <p>
 * Ultimately, this will be a copy of a part of a larger network, therefore the
 * part of the original network is called subnet. The solution of the network
 * will be transfered back to the subnet by copying all node properties from the
 * network to the subnet. As all variables are stored in nodes and not in
 * elements, this will result in all linked nodes to get the values from the
 * nodes of the calculated network.
 *
 * <p>
 * This will call prepare and docalculation-methods on all elements of the
 * subnet, but, as this subnet is only part of a large network usually, it will
 * not ensure the calculation for the whole model and it will not check that the
 * subnet is in fully calculated state. Only the network must be in fully
 * calulated state, otherwise a warning is issued.
 *
 * @author Viktor Alexander Hartung
 */
public class TransferSubnet extends LinearNetwork {
    
    private static final Logger LOGGER = Logger.getLogger(
            TransferSubnet.class.getName());

    /**
     * Holds a list of all nodes from the subnet that are to be considered by
     * this class.
     */
    private final List<GeneralNode> linkedNodes = new ArrayList<>();

    /**
     * Holds a list of all elements from the subnet that are to be considered by
     * this class.
     */
    private final List<AbstractElement> linkedElements = new ArrayList<>();

    /**
     * Instance of a solver which is used to solve the derived electronic
     * circuit, providing a solution that is then transfered back to the subnet.
     */
    private final SuperPosition solver = new SuperPosition();

    /**
     * For debug reasons only, shows the number of layers that were generated by
     * the superposition solver. If the solver is replaced by a different one,
     * this might be obsolete.
     */
    private int solverNumberOfLayers;

    /**
     * linkedElements.get(idxGroundOrigin) is the only remaining origin in the
     * network which is derived from the subnet. It might be the first origin
     * that is forund in the linkedElements list. The origin will not be placed
     * at a different node, however, all additional origins will be merged with
     * this one or be replaced.
     */
    private int idxGroundOrigin;

    /**
     * isTransferable[idx] is true for linkedElements.get(idx) when the element
     * will recceive a transfered solution by this network.
     */
    private boolean isTransferable[] = new boolean[INIT_ARRAY_SIZE];

    /**
     * If the origin linkedElements.get(idx) has a different effort value than
     * the first as ground defined origin, the orgin will be replaced by an
     * effort source which is connected to the ground origin via ground node.
     * isReplacedOrigin[idx] will be true to identify this situation.
     */
    private boolean isReplacedOrigin[] = new boolean[INIT_ARRAY_SIZE];

    private boolean isCapacitanceBoundary[] = new boolean[INIT_ARRAY_SIZE];

    private boolean noFlowTranfser[] = new boolean[INIT_ARRAY_SIZE];

    /**
     * The node [index] will not be part of the network, even if there is a copy
     * of it existing in the list. Instead of this node, the ground node will be
     * used.
     */
    private boolean[] useGroundNode = new boolean[INIT_ARRAY_SIZE];

    /**
     * If the value for node [index] is > -1, the node [index] will be merged to
     * the node with the int value on that array position. This is used if a
     * node has to be merged as it was a duplicate in the subnet, like having
     * two or more nodes on a self capacitance class.
     */
    private int[] nodeMergedToNodeIndex = new int[INIT_ARRAY_SIZE];

    private ClosedOrigin groundOrigin; // refers to network, not subnet
    private GeneralNode groundNode; // refers to network, not subnet

    public TransferSubnet() {

        for (int idx = 0; idx < INIT_ARRAY_SIZE; idx++) {
            isTransferable[idx] = false;
            isReplacedOrigin[idx] = false;
            isCapacitanceBoundary[idx] = false;
            useGroundNode[idx] = false;
            nodeMergedToNodeIndex[idx] = -1;
            noFlowTranfser[idx] = false;
        }
        idxGroundOrigin = -1;
    }

    /**
     * Registers a node which will be linked to this network. The reference to
     * the node will be saved and a new node for this network will be created.
     *
     * Linking all nodes is the first thing that has to be done when setting up
     * this subnet.
     *
     * Each linked node will have exactly a corresponding network node which
     * gets created when calling this.
     *
     * @param p
     */
    @Override
    public void registerNode(GeneralNode p) {
        if (linkedNodes.contains(p)) {
            throw new UnsupportedOperationException("node already added");
        }

        linkedNodes.add(p);
        // create a duplicate
        GeneralNode pn;
        pn = new GeneralNode(PhysicalDomain.ELECTRICAL);
        pn.setName("n-" + p.toString());
        super.registerNode(pn);

        // keep track of array sizes, they are all the same size so we
        // check one and set all. Dangerous but I am that arrogant.
        if (nodeMergedToNodeIndex.length <= linkedNodes.size()) {
            nodeMergedToNodeIndex = newArrayLength(nodeMergedToNodeIndex, linkedNodes.size() + 1);
            nodeMergedToNodeIndex[nodeMergedToNodeIndex.length - 1] = -1;
        }
    }

    /**
     * Registers an element which will be linked to this network. A reference to
     * the Element will be saved as well as a new duplicate of this element will
     * be created in this network for solving it later.
     *
     * <p>
     * Only the first added origin will be used in the model. All other origin
     * nodes will be replaced and be connected to that so called ground origin.
     * If they have different effort values than the ground orgin, they will be
     * replaced with an effort source.
     *
     * @param e Element from subnet which is to be linked
     */
    @Override
    public void registerElement(AbstractElement e) {
        int nodesRegistered = 0;

        if (linkedElements.contains(e)) {
            throw new ModelErrorException("Element already linked");
        }

        if (e.getNumberOfNodes() == 0) {
            throw new ModelErrorException("Element has no nodes connected, "
                    + "make connections before adding to a network.");
        }

        if (!linkedNodes.contains(e.getNode(0))
                && e.getElementType() != ElementType.CAPACTIANCE) {
            throw new ModelErrorException("Node 0 of element not"
                    + " linked to transfer subnet");
        }

        // if (e.getNumberOfNodes() == 2) {
        if (e instanceof FlowThrough) {
            if (!linkedNodes.contains(e.getNode(1))) {
                throw new ModelErrorException("node 1 of element not"
                        + " linked to transfer subnet");
            }
        }

        // Recently added: a self capacitance might have more than one node, 
        // if so, exaclty one and only one must be a node of the network.
        if (e instanceof SelfCapacitance) {
            for (int idx = 0; idx < e.getNumberOfNodes(); idx++) {
                if (linkedNodes.contains(e.getNode(idx))) {
                    nodesRegistered++;
                }

            }
            if (nodesRegistered < 1) {
                throw new ModelErrorException("A class self capacitance "
                        + "must have at least one node that is linked to "
                        + "the network");
            }
        }

        // keep track of array sizes, they are all the same size so we
        // check one and set all. Dangerous but I am that arrogant.
        if (isTransferable.length <= linkedElements.size()) {
            isTransferable = newArrayLength(
                    isTransferable, linkedElements.size() + 1);
            isReplacedOrigin = newArrayLength(
                    isReplacedOrigin, linkedElements.size() + 1);
            isCapacitanceBoundary = newArrayLength(
                    isCapacitanceBoundary, linkedElements.size() + 1);
            noFlowTranfser = newArrayLength(
                    noFlowTranfser, linkedElements.size() + 1);
        }

        // init array values
        isTransferable[linkedElements.size()] = false;
        isCapacitanceBoundary[linkedElements.size()] = false;
        isReplacedOrigin[linkedElements.size()] = false;
        // note: size does not yet include the new element so its perfect 
        // for array index here.

        // make some checks to make sure only consistent elements will be added
        if (e.getNumberOfNodes() >= 3 && !(e instanceof SelfCapacitance)) {
            throw new ModelErrorException("Element has more than"
                    + " two nodes");
        }
        for (int idx = 0; idx <= 1; idx++) {
            if (!linkedNodes.contains(e.getNode(idx))
                    && !(e instanceof SelfCapacitance)) {
                throw new ModelErrorException("Node of element is not"
                        + " known/linked to this network.");
            }
            if (e.getNumberOfNodes() <= idx + 1) {
                break;
            }
        }

        linkedElements.add(e);
        iterativeSolver.addElement(e);
    }

    /**
     * Manually sets an element to not receive a transfered flow by this solver.
     * It will however not be excluded from the iterative part that runs after
     * the solution was found.
     *
     * <p>
     * This was intended to be used to provide a solution to a network which
     * contains an expanding heated volume that adds extra flow to one output.
     * The flow of the subnet there is different than the linear calculated flow
     * of this solver.
     *
     * @param e Element
     */
    public void setNoFlowTransfer(AbstractElement e) {
        if (!linkedElements.contains(e)) {
            throw new ModelErrorException("Element is not linked.");
        }
        noFlowTranfser[linkedElements.indexOf(e)] = true;
    }

    public void setupTransferSubnet() {
        boolean nodeUsed;
        boolean allElementsAreOfResistorType;
        int nodeIdx = 0, idx;
        boolean hasNodeInNetwork;
        EffortSource s;
        AbstractElement e = null;
        AbstractElement le;
        boolean additionalGroundNode = false; // if theres no origin in subnet

        LOGGER.log(Level.INFO, "Start TransferSubnet setup...");

        // Perform another check to validate data, each added node has to be
        // used with at least one element. We already checked the elements
        // nodes when registering the linked elements but not the nodes itself.
        // Also check that all resistance-type elements are linked.
        for (GeneralNode p : linkedNodes) {
            nodeUsed = false;
            for (idx = 0; idx < p.getNumberOfElements(); idx++) {
                // iterate through the elements connected to the node and see
                // if we can find at least one of them.
                if (linkedElements.contains(p.getElement(idx))) {
                    nodeUsed = true;
                    break;
                }
            }
            if (!nodeUsed) {
                throw new ModelErrorException("A linked node is not used in"
                        + " any of the linked elements.");
            }
            // If the node has only resistors connected, all those elements have
            // to be linked to the network to provide a working solution. Its 
            // ok to have non-linked resistors if theres a capacity for example
            // forcing the effort value on the node anyway. First, check all
            // elements types
            allElementsAreOfResistorType = true;
            for (idx = 0; idx < p.getNumberOfElements(); idx++) {
                if (!isElementResistorType(p.getElement(idx))) {
                    allElementsAreOfResistorType = false;
                    break;
                }
            }
            // and then, if all are resistors (or also shortcuts or open conn.),
            // check that they are all part of the network:
            if (allElementsAreOfResistorType) {
                for (idx = 0; idx < p.getNumberOfElements(); idx++) {
                    if (!linkedElements.contains(p.getElement(idx))) {
                        throw new ModelErrorException("A linked node has a "
                                + "resistor type element connected that is "
                                + "not linked as an element to this solver.");
                    }
                }
            }
        }

        // keep track of array size
        if (useGroundNode.length < linkedNodes.size()) {
            useGroundNode = newArrayLength(useGroundNode, linkedNodes.size());
            useGroundNode[useGroundNode.length - 1] = false;
        }

        // Define ground node, mark all other nodes from other origins with same 
        // effort values to be replaced with the ground node and replace origins 
        // with different effort values with sources. The ground node has to be
        // known as we encounter elements which need to be connected to it 
        // before it might be found in the for loop below this for loop.
        for (idx = 0; idx < linkedElements.size(); idx++) {
            le = linkedElements.get(idx);
            if (le.getElementType() == ElementType.ORIGIN) {
                if (idxGroundOrigin <= -1) { // no ground node set yet?
                    // First added origin will serve as the only origin for the
                    // whole network. Its node is saved as the groundNode.
                    idxGroundOrigin = idx; // remember this one
                    nodeIdx = linkedNodes.indexOf(le.getNode(0));
                    groundNode = nodes.get(nodeIdx); // remember this node
                    // create the ground origin for network. Note that this is
                    // a closed origin, no matter what the original element
                    // was.
                    groundOrigin = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
                    groundOrigin.setEffort(((OpenOrigin) le).getEffort());
                    // connect groundOrigin to groundNode
                    groundOrigin.registerNode(groundNode);
                    groundNode.registerElement(groundOrigin);
                    groundNode.setName("GND-" + groundNode.toString());
                    groundOrigin.setName("GND");
                } else if (((OpenOrigin) le).getEffort()
                        == groundOrigin.getEffort()) {
                    // Other origins with same effort value as groundOrigin
                    // will later be replaced with dummys. The correspoding
                    // nodes will not be used, instead, the other elements
                    // on this nodes will be connected to groundNode.
                    // This has to be done here as it has to be known when
                    // iterating through the elements.
                    nodeIdx = linkedNodes.indexOf(le.getNode(0));
                    useGroundNode[nodeIdx] = true; // remember
                }
            } // end if type origin
        } // end for all linked Elements

        // It is possible that there was no ground orig set if the model itself
        // did not contain any origin element. This behaviour can happen on 
        // subsystems which are placed between capacitance elements only, here,
        // we now have to create a new ground node and origin.
        if (groundOrigin == null) {
            groundOrigin = new ClosedOrigin(PhysicalDomain.ELECTRICAL);
            groundOrigin.setName("GND-Add");
            groundNode = new GeneralNode(PhysicalDomain.ELECTRICAL);
            groundNode.setName("GND-Node-Add");
            groundOrigin.registerNode(groundNode);
            groundNode.registerElement(groundOrigin);
            // Node and element need to be registered separately as there is no
            // existing node for this one!
            super.registerNode(groundNode);
            additionalGroundNode = true;
        }

        // Check self capacitances for multiple nodes. If a self capacitance has
        // more than one node which is part of this network, those nodes have to
        // be merged to have only one target node for solving the electonic 
        // equivalent circuit.
        for (idx = 0; idx < linkedElements.size(); idx++) {
            le = linkedElements.get(idx);
            if (le.getElementType() == ElementType.CAPACTIANCE
                    && le instanceof SelfCapacitance) {
                hasNodeInNetwork = false;
                nodeIdx = -1; // init to prevent compiler error
                for (int jdx = 0; jdx < le.getNumberOfNodes(); jdx++) {
                    if (linkedNodes.contains(le.getNode(jdx))) {
                        if (hasNodeInNetwork) {
                            nodeMergedToNodeIndex[linkedNodes
                                    .indexOf(le.getNode(jdx))]
                                    = nodeIdx;
                        } else {
                            // remember this one if we have another match in
                            // .contains if thingy
                            nodeIdx = linkedNodes.indexOf(le.getNode(jdx));
                        }
                        hasNodeInNetwork = true;
                    }
                }
            }
        }

        // after sources are organized, continue with all other elements.
        // Here: create a new element that will be a clone of le, representing
        // the abstraction of the original model.
        for (idx = 0; idx < linkedElements.size(); idx++) {
            le = linkedElements.get(idx);
            if (le.getElementType() == ElementType.ORIGIN) {
                if (idx == idxGroundOrigin) {
                    super.registerElement(groundOrigin);
                    groundOrigin.setName("n-GND");
                    // connection to ground node was done before, so thats it.
                    continue;
                }
                // origins with exactly the same effort value as the ground
                // origin will not be part of the network.
                if (((OpenOrigin) le).getEffort() != groundOrigin.getEffort()) {
                    s = new EffortSource(PhysicalDomain.ELECTRICAL);
                    // origins will be replaced by a new source which will
                    // apply the effort value
                    s.registerNode(groundNode);  // node idx 0 to ground
                    groundNode.registerElement(s);
                    // get node where the linked origin was placed to ...
                    nodeIdx = linkedNodes.indexOf(le.getNode(0));
                    // and connect it with node1 of the new source.
                    s.registerNode(nodes.get(nodeIdx));
                    nodes.get(nodeIdx).registerElement(s);
                    // set effort from origin level to new replacement source
                    s.setEffort(((OpenOrigin) le).getEffort());
                    super.registerElement(s);
                    s.setName("n-" + le.toString());
                    isReplacedOrigin[idx] = true;
                } else {
                    // create a dummy element to keep list index values synced
                    e = new Dummy(PhysicalDomain.ELECTRICAL);
                    e.setName("Dummy_" + idx);
                    super.registerElement(e);
                    continue;
                }

            } else if (isElementResistorType(le)) {
                e = new LinearDissipator(PhysicalDomain.ELECTRICAL);
                // The network will only transfer values back from resistor
                // type elements, it sould be sufficient to solve the whole
                // network afterwards.
                isTransferable[idx] = true;
                // Special occasion: Exclude the expanding volume element which
                // behaves like a flow throgh in the circuit but adds his own
                // flow value for modeling fluid expansion.
                if (le instanceof SteamIsobaricIsochoricEvaporator
                        || le instanceof PhasedExpandingThermalExchanger) {
                    isTransferable[idx] = false;
                }
            } else if (le.getElementType() == ElementType.EFFORTSOURCE) {
                e = new EffortSource(PhysicalDomain.ELECTRICAL);
            } else if (le.getElementType() == ElementType.FLOWSOURCE) {
                e = new FlowSource(PhysicalDomain.ELECTRICAL);
            } else if (le.getElementType() == ElementType.CAPACTIANCE
                    && le instanceof SelfCapacitance) {
                e = new EffortSource(PhysicalDomain.ELECTRICAL);
                isCapacitanceBoundary[idx] = true;
            } else {
                // there should be an inductance replaced with a flow source
                // here. should be pretty straightforward, but its not yet 
                // implemeted.
                throw new ModelErrorException("Unable to create element.");
            }

            if (e == null) { // this is just to spuress nullpointer warning
                throw new ModelErrorException("No new element was "
                        + "created to represent" + le.toString());
            }

            // examine both nodes and connect already existing network nodes
            // like they are connected in subnet. linkedNodes and nodes
            // share same index, as well as elements. 
            for (int jdx = 0; jdx <= 1; jdx++) {
                // get index of node from linked element in subnet, indizies 
                // are the same in subnet and in network.
                if (isReplacedOrigin[idx] && jdx == 1) {
                    // origin had only one node, 0, if its replaced, node 1 of
                    // the effort source which is replacing it will be connected
                    // to the node which was node 0 on that origin.
                    nodeIdx = linkedNodes.indexOf(le.getNode(0));
                } else if (isCapacitanceBoundary[idx]) { // && jdx == 1) {
                    //    && le.getNumberOfNodes() == 1) {
                    // if a capacitance has only one node, its node 0 was
                    // connected to the network and is now the origin. Therefore
                    // we have to use that node0 of capacitance as node1 on new
                    // effort source.
                    // nodeIdx = linkedNodes.indexOf(le.getNode(0));
                    // UPDATED: There might be multiple nodes on the self
                    // capacitance, try to figure out which is the one we need.
                    for (int kdx = 0; kdx < le.getNumberOfNodes(); kdx++) {
                        if (linkedNodes.contains(le.getNode(kdx))) {
                            nodeIdx = linkedNodes.indexOf(le.getNode(kdx));
                        }
                    }
                } else {
                    nodeIdx = linkedNodes.indexOf(le.getNode(jdx));
                }

                // redirect nodes which were replaced by the groundNode
                if (useGroundNode[nodeIdx]) {
                    nodeIdx = nodes.indexOf(groundNode);
                }

                // redirect nodes which are merged due to multiple nodes in
                // self capacitance elements.
                if (nodeMergedToNodeIndex[nodeIdx] >= 0) {
                    nodeIdx = nodeMergedToNodeIndex[nodeIdx];
                }

                // replaced origins or capacitances will be replaced with an 
                // effort source where ground node will be connected as node0.
                // if we get here, e is the effort source replacing the origin
                // or the capacitance and le is the linked element in the linked
                // network that is to be replaced with e.
                if (isCapacitanceBoundary[idx] || isReplacedOrigin[idx]) {
                    switch (jdx) {
                        case 0:
                            e.registerNode(groundNode);
                            groundNode.registerElement(e);
                            break;
                        case 1:
                            if (isReplacedOrigin[idx]) {
                                e.registerNode(nodes.get(nodeIdx));
                                nodes.get(nodeIdx).registerElement(e);
                            } else { // for replaced capacitance:
                                // only one node of the replaced capacitance
                                // is linked as part of the subnet. Check which
                                // one it is on the original capacitance
                                // and connect it to node 1 of the effort source
                                // which was created (e holds a reference to it)
                                // to replace the capacitance.
                                /*
                                * This code somehow triggered the What-the-hell
                                * exception. I don't know why i wrote it that
                                * way and it is now replaced with the loop
                                * following below. Maybe I was lazy. 
                                if (linkedNodes.contains(le.getNode(0))) {
                                    nodeIdx = linkedNodes.indexOf(
                                            le.getNode(0));
                                } else if (linkedNodes.contains(
                                        le.getNode(1))) {
                                    nodeIdx = linkedNodes.indexOf(
                                            le.getNode(1));
                                } else {
                                    throw new ModelErrorException(
                                            "What the hell?");
                                } */
 /*
                                * well this was obvious. there can be more than
                                * 2 nodes on the linkedElement which is a self
                                * capacitance. Simply look through all nodes and
                                * see which one is the linked one to get the
                                * index.
                                 */
                                nodeIdx = -1;
                                for (int kdx = 0;
                                        kdx < le.getNumberOfNodes(); kdx++) {
                                    if (linkedNodes.contains(le.getNode(kdx))) {
                                        nodeIdx = linkedNodes.indexOf(
                                                le.getNode(kdx));
                                        break;
                                    }
                                }
                                if (nodeIdx <= -1) {
                                    throw new ModelErrorException(
                                            "Still what the hell?");
                                }
                                // - end of what-the-hell-fix
                                e.registerNode(nodes.get(nodeIdx));
                                nodes.get(nodeIdx).registerElement(e);
                            }
                            break;
                    }
                    continue; // for two nodes loop
                }

                // this part here is for all remaining 2-node-elements
                // like resistors and sources.
                if (nodeIdx < 0) {
                    // all elements except origins must have 2 nodes!
                    throw new ModelErrorException("Unable to "
                            + "determine node index for element from subnet. "
                            + "Check if all nodes were added and if model "
                            + "containing subnet is set up properly.");
                } else {
                    // connect new created node and element in network
                    e.registerNode(nodes.get(nodeIdx));
                    nodes.get(nodeIdx).registerElement(e);
                }
            } // end for (int jdx = 0; jdx <= 1; jdx++) - two nodes loop

            // finally, register the new element that is now connected to the
            // nodes, with the network. Adding it will perform a check that all
            // nodes are valid.
            super.registerElement(e);
            e.setName("n-" + le.toString());
        } // end for (idx = 0; idx < linkedElements.size(); idx++) element loop#

        // If there was no element representing the ground origin present and no
        // paired element was created, the ground origin will be registered here
        // at last. Note that this means that there will be one more element in
        // this network than in the subnet, but the solving and transfer will 
        // only use the linked elements lists, so it is okay to have one more
        // element here.
        if (additionalGroundNode) {
            super.registerElement(groundOrigin);
        }

        // Now, after the network is fully set up, it is time to
        // setup the solver. As there are still dummys present in the network,
        // they will not be added to the solver. This is the reason why we have
        // a subnet and a network in this instance of this class.
        for (GeneralNode p : nodes) {
            if (p.getNumberOfElements() == 0) {
                continue;
            }
            solver.registerNode(p);
        }
        for (AbstractElement el : elements) {
            if (el.getElementType() == ElementType.NONE) {
                continue;
            }
            solver.registerElement(el);
        }

        // and, finally, let the solver set up itself
        solverNumberOfLayers = solver.superPositionSetup();
        LOGGER.log(Level.INFO, "... generated TransferSubnet.");
    }

    @Override
    public void prepareCalculation() {
        AbstractElement e, le;

        iterativeSolver.prepareCalculation(); // reset and prepare the subnet
        super.prepareCalculation(); // resets all nodes of network (not subnet!)

        // get current parameters from subnet and transfer them to the 
        // network. This will copy the subnet values to the network here.
        for (int idx = 0; idx < linkedElements.size(); idx++) {
            e = elements.get(idx);
            le = linkedElements.get(idx);

            // Both origins and Dummy-Elements will not get any changes, as
            // dynamic chainging of origin values is not allowed.
            if (e.getElementType() == ElementType.ORIGIN
                    || le.getElementType() == ElementType.ORIGIN) {
                continue;
            }
            if (le.getElementType() == ElementType.NONE
                    || e.getElementType() == ElementType.NONE) {
                continue;
            }
            if (isElementResistorType(e)) {
                switch (le.getElementType()) {
                    case DISSIPATOR:
                        ((LinearDissipator) e).setResistanceParameter(
                                ((LinearDissipator) le).getResistance());
                        continue;
                    case OPEN:
                        ((LinearDissipator) e).setOpenConnection();
                        continue;
                    case BRIDGED:
                        ((LinearDissipator) e).setBridgedConnection();
                        continue;
                }
            }
            if (e.getElementType() == ElementType.EFFORTSOURCE
                    && le.getElementType() == ElementType.EFFORTSOURCE) {
                ((EffortSource) e).setEffort(((EffortSource) le).getEffort());
                continue;
            }
            if (e.getElementType() == ElementType.FLOWSOURCE
                    && le.getElementType() == ElementType.FLOWSOURCE) {
                ((FlowSource) e).setFlow(((FlowSource) le).getFlow());
                continue;
            }
            if (e.getElementType() == ElementType.EFFORTSOURCE
                    && le.getElementType() == ElementType.CAPACTIANCE) {
                ((EffortSource) e).setEffort(((Capacitance) le).getEffort());
                continue;
            }
            throw new ModelErrorException("There was an element where no "
                    + "properties were transfered from subnet to network.");
        }

        super.prepareCalculation(); // resets all nodes of network (not subnet!)
        solver.prepareCalculation();
    }

    /**
     * Transfers the calculated results back to the subnet. Note that this will
     * not update sources and energy storage elements, so further actions will
     * be needed to provide a full updated network.
     */
    @Override
    public void doCalculation() {
        FlowThrough targetElement;
        LinearDissipator sourceElement;
        GeneralNode targetNode, sourceNode;
        int idx, jdx;
        boolean noNoFlowCheck;
        solver.doCalculation(); // after this, all flows and efforts are there.
        // call all origin elements first to allow them forcing their 
        // effort values to their nodes. Otherwise that will throw an error.
        // Also flow sources must do this as otherwise the flow would be 
        // assigned back to them.
        for (AbstractElement le : linkedElements) {
            if (le.getElementType() == ElementType.ORIGIN
                    || le.getElementType() == ElementType.CAPACTIANCE
                    || le.getElementType() == ElementType.FLOWSOURCE) {
                le.doCalculation();
            }
        }
        // Dead-End-Nodes, which should not be there in a full network, will
        // manually receive a flow of 0.0, this will also be transferred to the
        // linked elements that way. Also copy the effort value.
        for (GeneralNode n : nodes) {
            if (n.getNumberOfElements() == 1) {
                n.setFlow(0.0, n.getElement(0), false);
            }
            if (!n.effortUpdated()) {
                if (n.getNumberOfElements() == 0) {
                    n.setEffort(0.0); // at least priovide some sort of solution
                    continue; // what is this even, only corrupt things here
                }
                try {
                    targetNode = n.getElement(0).getOnlyOtherNode(n);
                } catch (NoFlowThroughException ex) {
                    targetNode = null;
                }
                if (targetNode != null) {
                    n.setEffort(targetNode.getEffort());
                }
            }
        }
        for (idx = 0; idx < linkedElements.size(); idx++) {
            if (!isTransferable[idx]) {
                continue;
            }
            sourceElement = (LinearDissipator) elements.get(idx);
            targetElement = (FlowThrough) linkedElements.get(idx);
            // Transfer the flow value
            if (!noFlowTranfser[idx]) {
                targetElement.setFlow(sourceElement.getFlow(), true);
            }
            for (jdx = 0; jdx < 2; jdx++) {
                targetNode = targetElement.getNode(jdx);
                sourceNode = sourceElement.getNode(jdx);
                if (!targetNode.effortUpdated()) {
                    // use target element as identifier from where things are
                    targetNode.setEffort(sourceNode.getEffort(),
                            targetElement, false);
                }
                // this should be obsolete now:
                if (!targetNode.flowUpdated(targetElement)
                        && !noFlowTranfser[idx]) {
                    targetNode.setFlow(sourceNode.getFlow(sourceElement),
                            targetElement, false);
                }
            }
        }
        // get all network components to an as much as possible calculated state
        iterativeSolver.doCalculation();

        // Check that all efforts are now available:
        for (GeneralNode n : linkedNodes) {
            if (!n.effortUpdated()) {
                LOGGER.log(Level.WARNING, "Missing effort on " + n.toString());
            }
            if (!n.allFlowsUpdated()) {
                // All flows of the subnet must be calculated, at least for 
                // those nodes which have all elements linked to the subnet.
                noNoFlowCheck = false;
                for (jdx = 0; jdx < n.getNumberOfElements(); jdx++) {
                    if (!linkedElements.contains(n.getElement(jdx))) {
                        noNoFlowCheck = true;
                        break;
                    }
                    if (noFlowTranfser[linkedElements
                            .indexOf(n.getElement(jdx))]) {
                        noNoFlowCheck = true;
                        break;
                    }
                }
                if (noNoFlowCheck) {
                    continue;
                }
                LOGGER.log(Level.WARNING, "Missing flow on " + n.toString());
            }
        }
    }

    public int getNumberOfLayers() {
        return solverNumberOfLayers;
    }

    public boolean containsNode(GeneralNode node) {
        return linkedNodes.contains(node);
    }

    public boolean containsElement(AbstractElement element) {
        return linkedElements.contains(element);
    }
}
