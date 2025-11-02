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
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import static com.hartrusion.util.ArraysExt.addObject;
import static com.hartrusion.util.ArraysExt.containsObject;
import static com.hartrusion.util.ArraysExt.indexOfObject;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages four resistors that are connected to a middle element called star.
 * This is the four-resistor variant of the star delta transform. The class has
 * many similarities with the StarDelta transform but, as its more possible
 * cases here, has more things to consider and is not that straight forward.
 * <p>
 * Four resistors will be replaced by six resistors, the square formation will
 * have a resistor connecting each node directly with all other nodes, making it
 * a total of six elements. Those are more than initially but as this is only
 * used by the RecursiveSimplifier if nothing else works, there is hope that the
 * new circuit with the square elements can be further simplified after the star
 * square algorithm was applied.
 * <p>
 * This is an implementation of a star polygon transform for four nodes,
 * containing special cases with shortcuts and open connections.
 *
 * @author Viktor Alexander Hartung
 */
public class StarSquareTransform {
    private static final Logger LOGGER = Logger.getLogger(
            StarSquareTransform.class.getName());

    private GeneralNode starNode;

    private final GeneralNode[] parentStarNodes = new GeneralNode[4];
    private final GeneralNode[] childSquareNodes = new GeneralNode[4];
    private final LinearDissipator[] parentStarElements = new LinearDissipator[4];
    private final LinearDissipator[] childSquareElements = new LinearDissipator[6];

    private boolean squareElementsCreated = false;

    /**
     * Called when setting up the solver, this can be called for each node, and
     * it will check if the given node is the so-called star node. Star node is
     * a node with exactly four resistors connected to it. If a star node is
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
        if (n.getNumberOfElements() != 4) {
            return false;
        }
        for (idx = 0; idx < 4; idx++) {
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
            // if (otherNode.getNumberOfElements() <= 1) {
            //     return false; // detected a dead end.
            // }
            // - removed as it's not a restriction here and allows more
            // precise testing. model will crash at different place then.
        }
        return true;
    }

    /**
     * Sets up a star network around given node n, requires the node to be the
     * star node between four resistors. Calling this function will add all
     * surrounding resistors and nodes as the star part. The square components
     * will be created (new elements) and connected to each other. After this
     * method is finished, the square child elements and nodes can be obtained
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
        for (idx = 0; idx < 4; idx++) {
            r = (LinearDissipator) n.getElement(idx);
            addObject(parentStarElements, r);
            try {
                addObject(parentStarNodes, r.getOnlyOtherNode(n));
            } catch (NoFlowThroughException ex) {
                throw new ModelErrorException("Expected a flow through element"
                        + " when setting up star.");
            }
        }
        // generate new square nodes and elements
        for (idx = 0; idx < 4; idx++) {
            addObject(childSquareNodes,
                    new GeneralNode(PhysicalDomain.ELECTRICAL));
        }
        for (idx = 0; idx < 6; idx++) {
            addObject(childSquareElements,
                    new LinearDissipator(PhysicalDomain.ELECTRICAL));
        }
        // connect outer square nodes (0-3) and elements
        for (idx = 0; idx < 4; idx++) {
            childSquareNodes[idx].setName("p-Square-" + idx);
            childSquareElements[idx].setName("R-Square-" + idx);
            childSquareNodes[idx].registerElement(
                    childSquareElements[idx]);
            childSquareElements[idx].registerNode(childSquareNodes[idx]);
            // connect second side to nodeIdx+1
            if (idx == 3) { // but the last one is between 3 and 0
                childSquareNodes[0].registerElement(
                        childSquareElements[idx]);
                childSquareElements[idx].registerNode(
                        childSquareNodes[0]);
            } else {
                childSquareNodes[idx + 1].registerElement(
                        childSquareElements[idx]);
                childSquareElements[idx].registerNode(
                        childSquareNodes[idx + 1]);
            }
        }
        // connect inner connections (4 and 5) inside square
        childSquareNodes[0].registerElement(
                childSquareElements[4]);
        childSquareElements[4].registerNode(
                childSquareNodes[0]);
        childSquareNodes[2].registerElement(
                childSquareElements[4]);
        childSquareElements[4].registerNode(
                childSquareNodes[2]);
        childSquareNodes[1].registerElement(
                childSquareElements[5]);
        childSquareElements[5].registerNode(
                childSquareNodes[1]);
        childSquareNodes[3].registerElement(
                childSquareElements[5]);
        childSquareElements[5].registerNode(
                childSquareNodes[3]);

        squareElementsCreated = true;
    }

    /**
     * For preparing calculation, all square resistor values have to be updated
     * and recalculated. This will also consider star resistors to be shorted
     * connection, therefore a lot of special cases will be considered here.
     * This is actually why its done this way.
     */
    public void calculateSquareResistorValues() {
        int idx, jdx;
        int bridgedElements = 0;
        int bridge1Idx = -1;
        int bridge2Idx = -1;
        int bridge3Idx = -1;
        double sumOfConductance = 0;
        // First special thingy: All parent star elements open means that
        // all child square elements will be open, pretty easy.
        if (parentStarElements[0].getElementType()
                == ElementType.OPEN
                && parentStarElements[1].getElementType()
                == ElementType.OPEN
                && parentStarElements[2].getElementType()
                == ElementType.OPEN
                && parentStarElements[3].getElementType()
                == ElementType.OPEN) {
            for (idx = 0; idx < 6; idx++) {
                // apply it to all elements
                childSquareElements[idx].setOpenConnection();
            }
            return; // that's it, pretty easy
        }
        // Check how many resistors are bridged connections.
        for (idx = 0; idx < 4; idx++) {
            if (parentStarElements[idx].getElementType()
                    == ElementType.BRIDGED) {
                bridgedElements++;
                // Save idx of which nodes are bridged
                if (bridge1Idx == -1) {
                    bridge1Idx = idx;
                } else if (bridge2Idx == -1) {
                    bridge2Idx = idx;
                } else if (bridge3Idx == -1) {
                    bridge3Idx = idx;
                }
            }
        }
        // All elements bridged will result in all square elements bridged.
        if (bridgedElements == 4) {
            for (idx = 0; idx < 6; idx++) {
                childSquareElements[idx].setBridgedConnection();
            }
            return;
        }
        if (bridgedElements == 3) {
            throw new UnsupportedOperationException("Not yet implemented.");
            // return;
        }
        if (bridgedElements == 2) {
            if (bridge1Idx == 0 && bridge2Idx == 2) {
                // Diagonal bridge case 1, this will split the resistances
                // to both outer resistors and the brige goes by the middle
                // resistor.
                childSquareElements[0].setResistanceParameter(
                        parentStarElements[1].getResistance() * 2);
                childSquareElements[1].setResistanceParameter(
                        parentStarElements[1].getResistance() * 2);
                childSquareElements[2].setResistanceParameter(
                        parentStarElements[3].getResistance() * 2);
                childSquareElements[3].setResistanceParameter(
                        parentStarElements[3].getResistance() * 2);
                childSquareElements[4].setBridgedConnection();
                childSquareElements[5].setOpenConnection();
            } else if (bridge1Idx == 1 && bridge2Idx == 3) {
                // case 2: same as above but the other diagonal
                childSquareElements[0].setResistanceParameter(
                        parentStarElements[0].getResistance() * 2);
                childSquareElements[3].setResistanceParameter(
                        parentStarElements[0].getResistance() * 2);
                childSquareElements[1].setResistanceParameter(
                        parentStarElements[2].getResistance() * 2);
                childSquareElements[2].setResistanceParameter(
                        parentStarElements[2].getResistance() * 2);
                childSquareElements[4].setOpenConnection();
                childSquareElements[5].setBridgedConnection();
            } else {
                // V-shaped bridge connection, this can rotate to 4 positions
                // but fortunately there is some logic due to the rotation, the
                // square circuit also rotates. The inner resistors are always
                // open:
                childSquareElements[4].setOpenConnection();
                childSquareElements[5].setOpenConnection();
                // Set idx and jdx as clockwise identifier to describe th Vs
                // position. bridge1idx will always be a lower value. so we
                // will not get a 3-0 pair, therefore we need this if here:
                if (bridge1Idx == 0 && bridge2Idx == 3) {
                    idx = 3;
                    jdx = 0;
                } else {
                    idx = bridge1Idx;
                    jdx = bridge2Idx;
                }
                // There is also a pattern that rotates on the square, this
                // will be used here. I can't draw it, unfortunately.
                if (jdx == 3) {
                    transferToChild(0, jdx);
                } else {
                    transferToChild(jdx + 1, jdx);
                }
                if (idx == 0) {
                    transferToChild(3, 3);
                } else {
                    transferToChild(idx - 1, idx - 1);
                }
                // Shortcut is always the element with idx, pretty easy. If you
                // dont swap jdx and idx...
                childSquareElements[idx].setBridgedConnection();
                // Determine which side is open and shorted
                switch (idx) {
                    case 0:
                        childSquareElements[2].setOpenConnection();
                        break;
                    case 1:
                        childSquareElements[3].setOpenConnection();
                        break;
                    case 2:
                        childSquareElements[0].setOpenConnection();
                        break;
                    case 3:
                        childSquareElements[1].setOpenConnection();
                        break;
                }
            }
            return;
        }
        if (bridgedElements == 1) {
            switch (bridge1Idx) { // Which element is bridged?
                case 0:
                    transferToChild(1, 0);
                    childSquareElements[1].setOpenConnection();
                    childSquareElements[2].setOpenConnection();
                    transferToChild(3, 3);
                    transferToChild(2, 4);
                    childSquareElements[5].setOpenConnection();
                    break;
                case 1:
                    transferToChild(0, 0);
                    transferToChild(2, 1);
                    childSquareElements[2].setOpenConnection();
                    childSquareElements[3].setOpenConnection();
                    childSquareElements[4].setOpenConnection();
                    transferToChild(3, 5);
                    break;
                case 2:
                    childSquareElements[0].setOpenConnection();
                    transferToChild(1, 1);
                    transferToChild(3, 2);
                    childSquareElements[3].setOpenConnection();
                    transferToChild(0, 4);
                    childSquareElements[5].setOpenConnection();
                    break;
                case 3:
                    childSquareElements[0].setOpenConnection();
                    childSquareElements[1].setOpenConnection();
                    transferToChild(2, 2);
                    transferToChild(0, 3);
                    childSquareElements[4].setOpenConnection();
                    transferToChild(1, 5);
            }
            return;
        }

        // Calculate the sum of conductances. It's fine to have open elements
        // as they will have a value of 0.0
        sumOfConductance = 0.0;
        for (idx = 0; idx < 4; idx++) {
            sumOfConductance += parentStarElements[idx].getConductance();
        }

        // Iterate through parent star elements with idx = 0, 1, 2. Each child 
        // delta corresponds to two child delta elements next to it.
        for (idx = 0; idx < 4; idx++) {
            // Generate the corresponding square index, we can then use idx
            // and jdx for each idx.
            // 0: 0-1, 1: 1-2, 2: 2-3, 3: 3-0;
            if (idx == 3) {
                jdx = 0;
            } else {
                jdx = idx + 1;
            }
            // Another special case: If both star elements connected to the node
            // are open, the connection between them must be open too.
            if (parentStarElements[idx].getElementType()
                    == ElementType.OPEN
                    || parentStarElements[jdx].getElementType()
                    == ElementType.OPEN) {
                childSquareElements[idx].setOpenConnection();
                continue;
            }

            // Calculate the conductance for each element:
            childSquareElements[idx].setConductanceParameter(
                    parentStarElements[idx].getConductance()
                    * parentStarElements[jdx].getConductance()
                    / sumOfConductance);
        }
        // Conductance for the two middle resistor elements:
        childSquareElements[4].setConductanceParameter(
                parentStarElements[0].getConductance()
                * parentStarElements[2].getConductance()
                / sumOfConductance);
        childSquareElements[5].setConductanceParameter(
                parentStarElements[1].getConductance()
                * parentStarElements[3].getConductance()
                / sumOfConductance);
    }

    /**
     * After calculation on the child network which contains all the square
     * resistors, this method will transfer the results from that network by
     * taking the values from the nodes of the child network and transferring
     * the effort values back to the parent network.
     */
    public void calculateStarValuesFromSquare() {
        int idx;
        int openElements;
        int bridgedConnections;
        int bridgedConnectionIdx = -1;
        int nonOpenIndex = -1;
        double avgStarEffort;
        // transfer all node efforts from child back to parent, first check,
        // that all values are valid
        for (idx = 0; idx < 4; idx++) {
            if (!childSquareNodes[idx].effortUpdated()) {
                throw new ModelErrorException("Effort on child delta nodes "
                        + "not in required updated state.");
            }
            if (!childSquareNodes[idx].allFlowsUpdated()) {
                throw new ModelErrorException("Not all flows on child delta "
                        + "node are in required updated state.");
            }
        }
        for (idx = 0; idx < 4; idx++) {
            // transfer efforts to parent networks elements
            if (!parentStarNodes[idx].effortUpdated()) {
                parentStarNodes[idx].setEffort(
                        childSquareNodes[idx].getEffort(),
                        parentStarElements[idx], true); // use as source
            }
        }
        // how many elements are in "open" or "bridged" state?
        openElements = 0;
        bridgedConnections = 0;
        for (idx = 0; idx < 4; idx++) {
            if (parentStarElements[idx].getElementType()
                    == ElementType.OPEN) {
                openElements++;
            } else if (parentStarElements[idx].getElementType()
                    == ElementType.BRIDGED) {
                bridgedConnectionIdx = idx;
                bridgedConnections++;
                nonOpenIndex = idx;
            } else {
                nonOpenIndex = idx;
            }
        }
        if (bridgedConnections >= 1) {
            // any bridge will have the effort of the startNode the same as
            // the bridged node, so we're done quite fast here. Its possible
            // the element did already set this by itself.
            if (!starNode.effortUpdated()) {
                starNode.setEffort(
                        parentStarNodes[bridgedConnectionIdx].getEffort());
            }
            return;
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
        if (openElements == 4) {
            avgStarEffort = 0.0;
            for (idx = 0; idx < 4; idx++) {
                avgStarEffort += parentStarNodes[idx].getEffort();
            }
            avgStarEffort = avgStarEffort / 3;
            starNode.setEffort(avgStarEffort, parentStarElements[0], true);
            for (idx = 0; idx < 4; idx++) {
                parentStarElements[idx].setFlow(0, starNode);
                starNode.setFlow(0, parentStarElements[idx], true);
            }
            return;
        }
        // Three open connections also means no flow, but the star node effort
        // value can be set equally to the resistor that is remaining. This
        // single resistor has zero flow then.
        if (openElements == 3) {
            if (!starNode.effortUpdated()) {
                starNode.setEffort(
                        parentStarNodes[nonOpenIndex].getEffort());
            }
            if (!starNode.flowUpdated(parentStarElements[nonOpenIndex])) {
                starNode.setFlow(0.0,
                        parentStarElements[nonOpenIndex], false);
            }
            return;
        }
        // use generic formula like in starDeltaTransform. See comment there
        // how this works.
        double den = 0;
        double num = 0;
        double cond;
        for (idx = 0; idx < 4; idx++) {
            cond = parentStarElements[idx].getConductance();
            num += childSquareNodes[idx].getEffort() * cond;
            den += cond;
        }
        if (!starNode.effortUpdated()) {
            starNode.setEffort(num / den, null, true);
        } else {
            if (Math.abs(num / den - starNode.getEffort()) > 1e-3) {
                // throw new CalculationException("Validation of already set"
                //         + " effort value failed.");
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
        if (!squareElementsCreated) {
            throw new ModelErrorException("Star delta has to be set up before "
                    + "using this function.");
        }
        if (!containsObject(parentStarNodes, n)) {
            return null;
        }
        return childSquareNodes[indexOfObject(parentStarNodes, n)];
    }

    /**
     * After setupStar was called and the star delta transform was successful,
     * this can be used to check weather an element is part of the star or not.
     * It is used in recursive simplifier to determine if the element is
     * consumed or outside the star.
     *
     * @param e Element to check
     * @return true: element is part of the star.
     */
    public boolean starContainsElement(AbstractElement e) {
        if (!squareElementsCreated) {
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
        if (!squareElementsCreated) {
            throw new ModelErrorException("Star delta has to be set up before "
                    + "using this function.");
        }
        return containsObject(parentStarNodes, n);
    }

    public GeneralNode getSquareNode(int idx) {
        return childSquareNodes[idx];
    }

    public LinearDissipator getSquareElement(int idx) {
        return childSquareElements[idx];
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
    private void transferToChild(int idxStar, int idxSquare) {
        switch (parentStarElements[idxStar].getElementType()) {
            case OPEN:
                childSquareElements[idxSquare].setOpenConnection();
                break;
            case BRIDGED:
                childSquareElements[idxSquare].setBridgedConnection();
                break;
            default:
                childSquareElements[idxSquare].setResistanceParameter(
                        parentStarElements[idxStar].getResistance());
                break;
        }
    }
}
