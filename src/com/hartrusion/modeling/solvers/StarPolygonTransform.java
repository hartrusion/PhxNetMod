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
package com.hartrusion.modeling.solvers;

import com.hartrusion.modeling.ElementType;
import static com.hartrusion.modeling.ElementType.BRIDGED;
import static com.hartrusion.modeling.ElementType.OPEN;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import static com.hartrusion.util.ArraysExt.addObject;
import static com.hartrusion.util.ArraysExt.containsObject;
import static com.hartrusion.util.ArraysExt.indexOfObject;
import java.util.logging.Logger;

/**
 * Generalized approach of the star polygon transform that transforms a star
 * shaped network into a polygon for further simplification.
 * <p>
 * The polygon consists of a dissipator element between each node. As no element
 * exists twice to connect two nodes and nodes do not connects to themselves,
 * there is a defined number of elements and that number is known as soon as the
 * class gets initialized.
 * <p>
 * It is defined now that we have two numbers which describe the element between
 * two nodes and those numbers are called first and secondary index. The
 * secondary index will always be higher than the first index. To illustrate
 * this, those two numbers and the absolute element number are shown in a table
 * here:
 * <pre>
 *     first:  0   1   2   3   4   5  idx
 * second:     |   |   |   |   |   |
 *   0   ===   -   -   -   -   -   -
 *   1   ===   0   -   -   -   -   -
 *   2   ===   1   2   -   -   -   -
 *   3   ===   3   4   5   -   -   -
 *   4   ===   6   7   8   9   -   -
 *   5   ===  10  11  12  13  14   -   second: jdx
 * </pre>
 * <p>
 * So for example, there is NO element between node 4 and node 1. There is also
 * no element between node 2 and 2. The element between node 1 and node 4 will
 * have the index 7 in the array of elements.
 * <p>
 * The said element can be found in the arrays of this instance like shown:
 * <ul>
 * <li>firstIndexToArray[7] = 1</li>
 * <li>secondIndextoArray[7] = 4</li>
 * <li>indexToFirstAndSecond[1,4] = 7</li>
 * </ul>
 *
 *
 * @author Viktor Alexander Hartung
 */
public class StarPolygonTransform {

    private static final Logger LOGGER = Logger.getLogger(
            StarSquareTransform.class.getName());

    private int branches; // Number of resistors attached to the star.


    private GeneralNode starNode;
    private GeneralNode[] parentStarNodes;
    private LinearDissipator[] parentStarElements;

    private int polyElements;
    private GeneralNode[] childPolygonNodes;
    private LinearDissipator[] childPolygonElements;

    /**
     * Holds the first (smaller) index number for each entry in the child array.
     */
    private int[] firstIndexToArray;
    /**
     * Holds the second (larger) index number for each entry in the child array.
     */
    private int[] secondIndextoArray;
    /**
     * Holds the position in the childPolygonElements array for [first][second]
     * indizies.
     */
    private int[][] indexToFirstAndSecond;

    private boolean polygonElementsCreated = false;

    /**
     * Called when setting up the solver, this can be called for each node, and
     * it will check if the given node is the so-called star node. Star node is
     * a node with more than four resistors connected to it. If a star node is
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
        if (n.getNumberOfElements() <= 4) {
            return false;
        }
        for (idx = 0; idx < n.getNumberOfElements(); idx++) {
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
        int idx, jdx;
        if (!checkForStarNode(n)) {
            throw new ModelErrorException("Provided node is not suitable for "
                    + "setting up star polygon.");
        }
        starNode = n;
        branches = n.getNumberOfElements();
        polyElements = branches * (branches - 1) / 2;
        // Prepare the arrays that will hold the elements.
        parentStarNodes = new GeneralNode[branches];
        parentStarElements = new LinearDissipator[branches];
        childPolygonNodes = new GeneralNode[branches];
        // there might be a lot of reistors but fortunately there is a formula.
        childPolygonElements = new LinearDissipator[polyElements];

        // get all resistors around the star node and the outer nodes and save
        // them in the star array.
        for (idx = 0; idx < branches; idx++) {
            r = (LinearDissipator) n.getElement(idx);
            addObject(parentStarElements, r);
            try {
                addObject(parentStarNodes, r.getOnlyOtherNode(n));
            } catch (NoFlowThroughException ex) {
                throw new ModelErrorException("Expected a flow through element"
                        + " when setting up star.");
            }
        }

        // initilize arrays to access the elements and vice versa. Those arrays
        // serve as simple lookup tables.
        firstIndexToArray = new int[polyElements];
        secondIndextoArray = new int[polyElements];
        indexToFirstAndSecond = new int[branches][branches];
        java.util.Arrays.fill(firstIndexToArray, -1);
        java.util.Arrays.fill(secondIndextoArray, -1);
        int kdx = -1;
        for (idx = 0; idx < branches; idx++) { // first
            for (jdx = 0; jdx < branches; jdx++) { // second
                indexToFirstAndSecond[jdx][idx] = -1; // initialize array
                if (idx <= jdx) {
                    continue;
                }
                kdx += 1;
                firstIndexToArray[kdx] = jdx;
                secondIndextoArray[kdx] = idx;
                indexToFirstAndSecond[jdx][idx] = kdx;
            }
        }

        // generate new square nodes and elements.
        for (idx = 0; idx < branches; idx++) {
            addObject(childPolygonNodes,
                    new GeneralNode(PhysicalDomain.ELECTRICAL));
        }
        for (idx = 0; idx < childPolygonElements.length; idx++) {
            addObject(childPolygonElements,
                    new LinearDissipator(PhysicalDomain.ELECTRICAL));
        }
        // connect all elements between the nodes using the data provided in 
        // those arrays (see javadoc header of this class). This generates the
        // network between those nodes here.
        for (kdx = 0; kdx < polyElements; kdx++) {
            childPolygonElements[kdx].connectBetween(
                    childPolygonNodes[firstIndexToArray[kdx]],
                    childPolygonNodes[secondIndextoArray[kdx]]);
        }

        polygonElementsCreated = true;
    }

    /**
     * For preparing calculation, all resistor values inside the polygon
     * arrangement have to be updated and recalculated. This will also consider
     * star resistors to be shorted connection.
     */
    public void calculatePolygonResistorValues() {
        int bridgedElements = 0;
        int openElements = 0;
        LinearDissipator rN, rM, rNM;
        for (LinearDissipator r : parentStarElements) {
            switch (r.getElementType()) {
                case OPEN:
                    openElements++;
                    break;
                case BRIDGED:
                    bridgedElements++;
                    break;
            }
        }
        // First special thing: All elements open means all child elements
        // open.
        if (openElements == branches) {
            for (LinearDissipator r : childPolygonElements) {
                r.setOpenConnection();
            }
            return;
        }
        // All elements bridged will result in all child elements bridged.
        if (bridgedElements == branches) {
            for (LinearDissipator r : childPolygonElements) {
                r.setBridgedConnection();
            }
            return;
        }
        // Default behavior from literature: This is the only case where the
        // known formula for star polygon transform is valid: No bridges.
        if (bridgedElements == 0) {
            // Sum up the conductance of all resistors
            double sumOfConductances = 0;
            for (LinearDissipator r : parentStarElements) {
                if (r.getElementType() == ElementType.OPEN) {
                    continue;
                }
                sumOfConductances += r.getConductance();
            }
            // Iterate through all resistors of the polygon. The formula used
            // here is G_n,m = G_n,0 * G_m,0 / sum(G) with G_x,0 beeing the 
            // star element from node x to center and G_n,m beeing the element 
            // inside the polygon between node m and n. We make explicit use 
            // of all the preinitialized index values in those arrays now.
            for (int kdx = 0; kdx < polyElements; kdx++) {
                rN = parentStarElements[firstIndexToArray[kdx]];
                rM = parentStarElements[secondIndextoArray[kdx]];
                rNM = childPolygonElements[kdx];
                // Special: One open connection means the poly resistor is
                // also open. Do not use getConductance to find out about that,
                // use the element type explicitly to avoid numeric issues.
                if (rN.getElementType() == ElementType.OPEN
                        || rM.getElementType() == ElementType.OPEN) {
                    rNM.setOpenConnection();
                    continue;
                }
                // Apply formula using conductances.
                rNM.setConductanceParameter(
                        rN.getConductance() * rM.getConductance()
                        / sumOfConductances);
            }
            return;
        }
        // Now comes the fun part. We have at least one bridge. This will make 
        // any calculation like used above not possible as the sum of 
        // conductances is always infinity. The equation is only valid if there 
        // is a division by infinity (which makes the result 0) but there are 
        // some cases which would end up in inf/inf and that is not defined.
        // However, drawing the circuit allows to make a very strange 
        // conclusion which is not prooved here but seems to be obvious. Having 
        // a bridge makes the star arrangement vanish and the resistors get 
        // drawn towards the bridged part. It can be assumed that when having 
        // multiple bridges, there are still more resistors which get their 
        // conductance split evenly by the number of bridges. 
        for (int kdx = 0; kdx < polyElements; kdx++) {
            rN = parentStarElements[firstIndexToArray[kdx]];
            rM = parentStarElements[secondIndextoArray[kdx]];
            rNM = childPolygonElements[kdx];
            if (rN.getElementType() != ElementType.BRIDGED
                    && rM.getElementType() != ElementType.BRIDGED) {
                // Both elements have non-infinite conductance. This means the 
                // new inductance is divided by zero and therefore 0, those 
                // elements are always open.
                rNM.setOpenConnection();
                continue;
            }
            if (rN.getElementType() == ElementType.BRIDGED
                    && rM.getElementType() == ElementType.BRIDGED) {
                // Both elements are bridged: This is also a bridge. Only 
                // happens with at least 2 bridges.
                rNM.setBridgedConnection();
                continue;
            }
            // one of both elements is bridged (we checked both other 
            // possibilities above. The remaining resistor will be calculated
            // by a formula which is based on the way the polygon looks if 
            // drawn with bridges. If 2 elements are brdiged, there will be two
            // of the nodes bridged. Two resistors will be attached to both 
            // sides of the bridge, this is the resistor from the star split 
            // into equal resistors. 
            if (rN.getElementType() == ElementType.BRIDGED) {
                rNM.setConductanceParameter(rM.getConductance()
                        / (double) bridgedElements);
            } else if (rM.getElementType() == ElementType.BRIDGED) {
                rNM.setConductanceParameter(rN.getConductance()
                        / (double) bridgedElements);
            }
        }
    }

    /**
     * After calculation on the child network which contains all the polygon
     * resistors, this method will transfer the results from that network by
     * taking the values from the nodes of the child network and transferring
     * the effort values back to the parent network.
     */
    public void calculateStarValuesFromPolygon() {
        // transfer all node efforts from child back to parent, first check,
        // that all values are valid
        for (int idx = 0; idx < branches; idx++) {
            if (!childPolygonNodes[idx].effortUpdated()) {
                throw new ModelErrorException("Effort on child polygon nodes "
                        + "not in required updated state.");
            }
            if (!childPolygonNodes[idx].allFlowsUpdated()) {
                throw new ModelErrorException("Not all flows on child polygon "
                        + "node are in required updated state.");
            }
        }
        for (int idx = 0; idx < branches; idx++) {
            // Always transfer efforts to parent networks nodes, those will be  
            // the same.
            if (!parentStarNodes[idx].effortUpdated()) {
                parentStarNodes[idx].setEffort(
                        childPolygonNodes[idx].getEffort(),
                        parentStarElements[idx], true); // use as source
            }
        }
        int bridgedElements = 0;
        int openElements = 0;
        int bridgedConnectionIdx = -1;
        for (int idx = 0; idx < branches; idx++) {
            switch (parentStarElements[idx].getElementType()) {
                case OPEN:
                    parentStarElements[idx].setFlow(0.0, starNode);
                    openElements++;
                    break;
                case BRIDGED:
                    bridgedConnectionIdx = idx; // save last one
                    bridgedElements++;
                    break;
            }
        }
        if (openElements == branches) { // all elements open
            for (LinearDissipator r : childPolygonElements) {
                r.setOpenConnection();
            }
            // Set the average effort from all nodes to the star node so it has
            // a value, even if it is currently floating.
            double avgStarEffort = 0.0;
            for (int idx = 0; idx < branches; idx++) {
                avgStarEffort += parentStarNodes[idx].getEffort();
            }
            avgStarEffort = avgStarEffort / branches;
            starNode.setEffort(avgStarEffort, parentStarElements[0], true);
            return;
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
    public GeneralNode getPolygonReplacementNode(GeneralNode n) {
        if (!polygonElementsCreated) {
            throw new ModelErrorException("Star polygon has to be set up "
                    + "before using this function.");
        }
        if (!containsObject(parentStarNodes, n)) {
            return null;
        }
        return childPolygonNodes[indexOfObject(parentStarNodes, n)];
    }

    /**
     * After setupStar was called and the star polygon transform was successful,
     * this can be used to check weather an element is part of the star or not.
     * It is used in recursive simplifier to determine if the element is
     * consumed or outside the star.
     *
     * @param e Element to check
     * @return true: element is part of the star.
     */
    public boolean starContainsElement(AbstractElement e) {
        if (!polygonElementsCreated) {
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
        if (!polygonElementsCreated) {
            throw new ModelErrorException("Star delta has to be set up before "
                    + "using this function.");
        }
        return containsObject(parentStarNodes, n);
    }

    public GeneralNode getPolygonNode(int idx) {
        return childPolygonNodes[idx];
    }

    public LinearDissipator getPolygonElement(int idx) {
        return childPolygonElements[idx];
    }

    public GeneralNode getStarNode(int idx) {
        return parentStarNodes[idx];
    }
    
    public int getNumberOfBranches() {
        return branches;
    }

    public int getNumberOfPoylElements() {
        return polyElements;
    }

}
