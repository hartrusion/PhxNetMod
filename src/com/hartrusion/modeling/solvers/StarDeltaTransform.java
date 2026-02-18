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

import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import static com.hartrusion.util.ArraysExt.addObject;
import static com.hartrusion.util.ArraysExt.containsObject;
import static com.hartrusion.util.ArraysExt.indexOfObject;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages three LinearDissipator elements that will be transformed using the
 * star to delta transform. This is used in RecursiveSimplifier if the usual
 * simplification using series and parallel resistors fail. A star delta
 * transform will change the circuit in a way that further simplification by
 * series and parallel transform will be possible.
 * <p>
 * The star delta transform is explicitly used, not delta to star, as it allows
 * admittances to be used instead of resistances, allowing closed valve
 * connections to be calculated. A closed valve would otherwise be a division by
 * zero.
 * <p>
 * On successful transformation, this class will create the replacement elements
 * and nodes, providing a delta arrangement of three resistors. Methods are
 * provided to integrate those nodes into new network.
 *
 * <p>
 * All parent star elements wil be connected to the star node and one
 * corresponding parentStarNodes, parentStarElements.get(1) is the resistor that
 * is connected to parentStarNodes.get(1). Delta elements will be connected
 * between the same node index as the element and the node with index + 1. So
 * childDeltaElements.get(1) will be placed between childDeltaNodes.get(1) and
 * childDeltaNodes.get(2). Delta resistor 2 will be between node 2 and 0.
 *
 *
 * @author Viktor Alexander Hartung
 */
public class StarDeltaTransform {
    private static final Logger LOGGER = Logger.getLogger(
            StarDeltaTransform.class.getName());

    private GeneralNode starNode;

    private final GeneralNode[] parentStarNodes = new GeneralNode[3];
    private final GeneralNode[] childDeltaNodes = new GeneralNode[3];
    private final LinearDissipator[] parentStarElements = new LinearDissipator[3];
    private final LinearDissipator[] childDeltaElements = new LinearDissipator[3];

    private boolean deltaElementsCreated = false;

    /**
     * Called when setting up the solver, this can be called for each node, and
     * it will check if the given node is the so-called star node. Star node is
     * a node with exactly three resistors connected to it. If a star node is
     * found, the method will return true - and nothing else. Therefore, it's
     * static.
     *
     * @param n node to be checked
     * @return true if the node can be successfully used as star node
     */
    public static boolean checkForStarNode(GeneralNode n) {
        int idx;
        ElementType type;
        GeneralNode otherNode;
        if (n.getNumberOfElements() != 3) {
            return false;
        }
        for (idx = 0; idx < 3; idx++) {
            type = n.getElement(idx).getElementType();
            if (type == ElementType.CAPACTIANCE
                    || type == ElementType.INDUCTANCE
                    || type == ElementType.EFFORTSOURCE
                    || type == ElementType.FLOWSOURCE
                    || type == ElementType.NONE
                    || type == ElementType.ORIGIN) {
                return false;
            }
            // Additional check: A star node requires all the elements to be
            // connected to something on the other node. So called dead ends,
            // which can exist from simplification of networks and are basically
            // resistors with no connection on the other side, will not qualify
            // for a valid simplification. They will produce wrong network
            // results and have to be handled elsewhere.
            try {
                otherNode = n.getElement(idx).getOnlyOtherNode(n);
            } catch (NoFlowThroughException ex) {
                return false; // and by the way, all R need to be flowthrough.
            }
            if (otherNode.getNumberOfElements() <= 1) {
                return false; // detected a dead end.
            }
        }
        return true;
    }

    /**
     * Sets up a star network around given node n, requires the node to be the
     * star node between three resistors. Calling this function will add all
     * surrounding resistors and nodes as the star part. The delta components
     * will be created (new elements) and connected to each other. After this
     * method is finished, the delta child elements and nodes can be obtained
     * and used for child network creation.
     *
     * @param n Star Node
     */
    public void setupStar(GeneralNode n) {
        LinearDissipator r;
        int idx;
        if (!checkForStarNode(n)) {
            throw new ModelErrorException("Provided node is not suitable for "
                    + "setting up star delta.");
        }
        starNode = n;
        // get all resistors around the star node and the outer nodes and save
        // them in lists. This represents the whole star.
        for (idx = 0; idx < 3; idx++) {
            r = (LinearDissipator) n.getElement(idx);
            addObject(parentStarElements, r);
            try {
                addObject(parentStarNodes, r.getOnlyOtherNode(n));
            } catch (NoFlowThroughException ex) {
                throw new ModelErrorException("Expected a flow through element"
                        + " when setting up star.");
            }
        }
        // generate new delta nodes and elements
        for (idx = 0; idx < 3; idx++) {
            addObject(childDeltaNodes,
                    new GeneralNode(PhysicalDomain.ELECTRICAL));
            addObject(childDeltaElements,
                    new LinearDissipator(PhysicalDomain.ELECTRICAL));
        }
        // connect delta nodes and elements
        for (idx = 0; idx < 3; idx++) {
            childDeltaNodes[idx].setName("p-Delta-" + idx);
            childDeltaElements[idx].setName("R-Delta-" + idx);

            childDeltaNodes[idx].registerElement(
                    childDeltaElements[idx]);
            childDeltaElements[idx].registerNode(childDeltaNodes[idx]);
            // connect second side to nodeidx+1
            if (idx == 2) { // but the last one is between 2 and 0
                childDeltaNodes[0].registerElement(
                        childDeltaElements[idx]);
                childDeltaElements[idx].registerNode(
                        childDeltaNodes[0]);
            } else {
                childDeltaNodes[idx + 1].registerElement(
                        childDeltaElements[idx]);
                childDeltaElements[idx].registerNode(
                        childDeltaNodes[idx + 1]);
            }
        }

        deltaElementsCreated = true;
    }

    /**
     * For preparing calculation, all delta resistor values have to be updated
     * and recalculated. This will also consider delta resistors to be a open
     * connection, therefore a lot of special cases will be considered here.
     * This is actually why its done this way.
     *
     */
    public void calculateDeltaResistorValues() {
        int idx, jdx;
        int bridgedElements = 0;
        int idxFirstBridge = -1;
        int idxSecondBridge = -1;
        double sumOfConductance = 0;
        // First special thingy: All parent star elements open means that
        // all child delta elements will be open, pretty easy.
        if (parentStarElements[0].getElementType()
                == ElementType.OPEN
                && parentStarElements[1].getElementType()
                == ElementType.OPEN
                && parentStarElements[2].getElementType()
                == ElementType.OPEN) {
            for (idx = 0; idx < 3; idx++) {
                // if all connections are open, just apply it to the delta
                // elements, avoiding dividing through zero.
                childDeltaElements[idx].setOpenConnection();
            }
            return;
        }
        // We would now need the formula to sum up the conductance as this is
        // part of the formula. We also check if we have some bridges 
        // that we might need to consider at this point.
        for (idx = 0; idx < 3; idx++) {
            if (parentStarElements[idx].getElementType()
                    == ElementType.BRIDGED) {
                if (bridgedElements == 0) {
                    idxFirstBridge = idx;
                } else if (bridgedElements == 1) {
                    idxSecondBridge = idx;
                }
                bridgedElements++;
                continue;
            }
            sumOfConductance
                    += parentStarElements[idx].getConductance();
        }
        // All elements bridged will result in all delta elements bridged.
        if (bridgedElements == 3) {
            for (idx = 0; idx < 3; idx++) {
                childDeltaElements[idx].setBridgedConnection();
            }
            return;
        }
        if (bridgedElements == 2) {
            // Having two bridged elements in the star connects two outer nodes
            // with the star node, so those three nodes have same efforts. This
            // means that the connection between those outer nodes that is then
            // formed will be present in the delta representation. The one
            // remaining resistor in the start is represented by two resistors
            // in the delta that can be merged together to the one resistor.
            // This means that we have one resistor in star and two parallel
            // resistors in delta, those two parallel resistors then simply
            // have the double resistance value.
            int idxDeltaBridged = -1;
            int idxDeltaNonBridged1 = -1;
            int idxDeltaNonBridged2 = -1;
            int idxStarNonBridged = -1;
            if (idxFirstBridge == 0 && idxSecondBridge == 1
                    || idxFirstBridge == 1 && idxSecondBridge == 0) {
                idxDeltaBridged = 0;
                idxDeltaNonBridged1 = 1;
                idxDeltaNonBridged2 = 2;
                idxStarNonBridged = 2;
            } else if (idxFirstBridge == 1 && idxSecondBridge == 2
                    || idxFirstBridge == 2 && idxSecondBridge == 1) {
                idxDeltaBridged = 1;
                idxDeltaNonBridged1 = 0;
                idxDeltaNonBridged2 = 2;
                idxStarNonBridged = 0;
            } else if (idxFirstBridge == 2 && idxSecondBridge == 0
                    || idxFirstBridge == 0 && idxSecondBridge == 2) {
                idxDeltaBridged = 2;
                idxDeltaNonBridged1 = 0;
                idxDeltaNonBridged2 = 1;
                idxStarNonBridged = 1;
            } else {
                throw new UnsupportedOperationException("How is this even???");
            }
            childDeltaElements[idxDeltaBridged].setBridgedConnection();
            if (parentStarElements[idxStarNonBridged].getElementType()
                    == ElementType.OPEN) {
                childDeltaElements[idxDeltaNonBridged1].setOpenConnection();
                childDeltaElements[idxDeltaNonBridged2].setOpenConnection();
            } else {
                childDeltaElements[idxDeltaNonBridged1]
                        .setResistanceParameter(2
                                * parentStarElements[idxStarNonBridged]
                                        .getResistance());
                childDeltaElements[idxDeltaNonBridged2]
                        .setResistanceParameter(2
                                * parentStarElements[idxStarNonBridged]
                                        .getResistance());
            }
            return;
        }
        if (bridgedElements == 1) {
            // Having one element bridged basically drags the star node in the
            // middle to the outer node that is bridged. The two remaining
            // resistors will be simply connected to the outer port as the outer
            // port is the same as the middle port now. This results in the 
            // resistor which connects the two outer ports to disappear or, in
            // this case, be the open one.

            // There are 3 cases, I was not able to find a nice index-formula.
            switch (idxFirstBridge) {
                case 0:
                    transferToChild(1, 0);
                    childDeltaElements[1].setOpenConnection();
                    transferToChild(2, 2);
                    break;
                case 1:
                    transferToChild(0, 0);
                    transferToChild(2, 1);
                    childDeltaElements[2].setOpenConnection();
                    break;
                case 2:
                    childDeltaElements[0].setOpenConnection();
                    transferToChild(1, 1);
                    transferToChild(0, 2);
                    break;
            }
            return;
        }

        // Iterate through parent star elements with idx = 0, 1, 2. Each child 
        // delta corresponds to two child delta elements next to it.
        for (idx = 0; idx < 3; idx++) {
            // Generate the corresponding delta index, we can then use idx 
            // and jdx for each idx.
            // 0: 0-1, 1: 1-2, 2: 2-0;
            if (idx == 2) {
                jdx = 0;
            } else {
                jdx = idx + 1;
            }
            // Another special case: If both star elements connecte to the node
            // are open, the connection between them must be open too.
            if (parentStarElements[idx].getElementType()
                    == ElementType.OPEN
                    || parentStarElements[jdx].getElementType()
                    == ElementType.OPEN) {
                childDeltaElements[idx].setOpenConnection();
                continue;
            }

            // This is the standard thingy for normal resistors here.
            // G_Delta is the product of both Gs of the resistor of the start
            // on the port divided through the sum of all G. As we set
            // a resistance at the end, we inverse this formula.
            // G_delta0 = G_star0 * G_star1 / Sum(G).
            // G_delta1 = G_star1 * G_star2 / Sum(G).
            // G_delta2 = G_star2 * G_star0 / Sum(G).
            childDeltaElements[idx].setResistanceParameter(
                    sumOfConductance
                    / parentStarElements[idx].getConductance()
                    / parentStarElements[jdx].getConductance());
        }
    }

    /**
     * After calculation on the child network which contains the delta
     * resistors, this will transfer the results from that network back to the
     * parent network.
     */
    public void calculateStarValuesFromDelta() {
        int idx;
        int openElements;
        int bridgedConnections;
        double avgStarEffort;
        // tranfer all node efforts from child back to parent, first check,
        // that all values are valid
        for (idx = 0; idx < 3; idx++) {
            if (!childDeltaNodes[idx].effortUpdated()) {
                throw new ModelErrorException("Effort on child delta nodes "
                        + "not in required updated state.");
            }
            if (!childDeltaNodes[idx].allFlowsUpdated()) {
                throw new ModelErrorException("Not all flows on child delta "
                        + "node are in required updated state.");
            }
        }
        for (idx = 0; idx < 3; idx++) {
            if (!parentStarNodes[idx].effortUpdated()) {
                parentStarNodes[idx].setEffort(
                        childDeltaNodes[idx].getEffort(),
                        parentStarElements[idx], true); // use as source
            } //else {
                // what have I forgotten here? Maybe nothing? maybe this was
                // completely unnecessary. Some day I will come back here and
                // remember what I did here.
        }
        // how many elements are in "open" state?
        openElements = 0;
        bridgedConnections = 0;
        for (idx = 0; idx < 3; idx++) {
            if (parentStarElements[idx].getElementType()
                    == ElementType.OPEN) {
                openElements++;
            } else if (parentStarElements[idx].getElementType()
                    == ElementType.BRIDGED) {
                bridgedConnections++;
            }
        }
        // If all elements are open connections, calculation is obsolete.
        // star node will get its effort set to the average effort value
        // from the outer nodes to have at least a solution. There is no proper
        // solution for this, other commercial solutions would bring up a model 
        // error or require the conductances to be something like 1e-20 to allow
        // a calculation. If such cases exist, like if all valves are closed, we
        // usually examine a trapped amount of fluid, which either shall be 
        // modeled always (capacitance) or is not part of the model. This is a 
        // nonlinear way of keeping the value in the model.
        // All flows from star and through the elements will be zero.
        if (openElements == 3) {
            avgStarEffort = 0.0;
            for (idx = 0; idx < 3; idx++) {
                avgStarEffort += parentStarNodes[idx].getEffort();
            }
            avgStarEffort = avgStarEffort / 3;
            if (!starNode.effortUpdated()) { // parent can already set this.
                starNode.setEffort(avgStarEffort, parentStarElements[0], true);
            }
            for (idx = 0; idx < 3; idx++) {
                parentStarElements[idx].setFlow(0, starNode);
                starNode.setFlow(0, parentStarElements[idx], true);
            }
            return;
        }
        // Two open connections also means no flow, but the star node effort
        // value can be set
        if (openElements == 2) {
            // set flow to zero through all elements
            for (idx = 0; idx < 3; idx++) {
                parentStarElements[idx].setFlow(0, starNode);
                starNode.setFlow(0, parentStarElements[idx], true);
            }
            // find the non-open element and set the effort from the only outer
            // node to the star node by using getOnlyOtherNode
            for (idx = 0; idx < 3; idx++) {
                if (parentStarElements[idx].getElementType()
                        != ElementType.OPEN) {
                    try {
                        if (!starNode.effortUpdated()) {
                            starNode.setEffort(parentStarElements[idx]
                                    .getOnlyOtherNode(starNode).getEffort(),
                                    parentStarElements[idx], true);
                        }
                    } catch (NoFlowThroughException ex) {
                        throw new ModelErrorException("Non flow through "
                                + "element occured in stardelta");
                    }
                    break;
                }
            }
            return;
        }
        // Star port is shortened: Copy effort from the shortened node. If 
        // there are more nodes shortened, they already have the same effort
        // so that >= 1 does the job here.
        if (bridgedConnections >= 1) {
            for (idx = 0; idx < 3; idx++) {
                if (parentStarElements[idx].getElementType()
                        == ElementType.BRIDGED) {
                    starNode.setEffort(
                            childDeltaNodes[idx].getEffort());
                    break;
                }
            }
            return;
        }

        /* commented out, can be handled well with the code below and this
         * is much more easy that way. one admittance will be zero.
        if (openElements == 1) {
            // Not yet implemented
            return;
        }
         */
        // here: All elements are regular resistors. Calculate the star effor
        // value and set it to the star node.
        //            U1     U2     U3    with G = 1/R:
        //           ---- + ---- + ----
        //            R1     R2     R3      U1*G1 + U2*G2 + U3*G3    <- num
        // U_star = -------------------- = -----------------------
        //            1      1      1        G1  +   G2   +  G3      <- den
        //           ---- + ---- + ----
        //            R1     R2     R3
        double den = 0;
        double num = 0;
        double cond;
        for (idx = 0; idx < 3; idx++) {
            cond = parentStarElements[idx].getConductance();
            num += childDeltaNodes[idx].getEffort() * cond;
            den += cond;
        }
        if (!starNode.effortUpdated()) {
            starNode.setEffort(num / den, null, true);
        } else {
            if (Math.abs(num / den - starNode.getEffort()) > 1e-3) {
                // throw new CalculationException("Validation of already set"
                //        + " effort value failed.");
                LOGGER.log(Level.WARNING, "Validation of already set" +
                 "effort value failed.");
            }
        }
    }

    /**
     * When creating the child network which shall include the delta
     * arrangement, this method will return the created nodes for the child
     * network. This allows integrating the nodes of this replacement into the
     * child network.
     *
     * @param n A node from parent network
     * @return Node which will replace given node n. Returns null if given node
     * n is not part of the delta.
     */
    public GeneralNode getDeltaReplacementNode(GeneralNode n) {
        if (!deltaElementsCreated) {
            throw new ModelErrorException("Star delta has to be set up before "
                    + "using this function.");
        }
        if (!containsObject(parentStarNodes, n)) {
            return null;
        }
        return childDeltaNodes[indexOfObject(parentStarNodes, n)];
    }

    /**
     * After setupStar was called and the star delta transform was sucessfull,
     * this can be used to check wether an element is part of the star or not.
     * It is used in recursive simplifier to determine if the element is
     * consumed or outside the star.
     *
     * @param e Element to check
     * @return true: element is part of the star.
     */
    public boolean starContainsElement(AbstractElement e) {
        if (!deltaElementsCreated) {
            throw new ModelErrorException("Star delta has to be set up before "
                    + "using this function.");
        }
        if (e instanceof LinearDissipator) {
            return containsObject(parentStarElements, (LinearDissipator) e);
        } else {
            return false;
        }
    }

    public boolean starContainsNode(GeneralNode n) {
        if (!deltaElementsCreated) {
            throw new ModelErrorException("Star delta has to be set up before "
                    + "using this function.");
        }
        return containsObject(parentStarNodes, n);
    }

    public GeneralNode getDeltaNode(int idx) {
        return childDeltaNodes[idx];
    }

    public AbstractElement getDeltaElement(int idx) {
        return childDeltaElements[idx];
    }

    public GeneralNode getStarNode(int idx) {
        return parentStarNodes[idx];
    }

    /**
     * Transfers the resistance from parent element to child element, considers
     * that the resistance can be Zero or Inf and sets the corresponding
     * connection type in such cases. There is no calculation, it simply copies
     * from one to the other resistor.
     *
     * @param idxStar Index in parentStarElements
     * @param idxDelta Index in childDeltaElements
     */
    private void transferToChild(int idxStar, int idxDelta) {
        switch (parentStarElements[idxStar].getElementType()) {
            case OPEN:
                childDeltaElements[idxDelta].setOpenConnection();
                break;
            case BRIDGED:
                childDeltaElements[idxDelta].setBridgedConnection();
                break;
            default:
                childDeltaElements[idxDelta].setResistanceParameter(
                        parentStarElements[idxStar].getResistance());
                break;
        }
    }
}
