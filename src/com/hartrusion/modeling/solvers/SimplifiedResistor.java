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
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Represents a resistor inside a network which was created by simplifying the
 * network by creating new resistors. This resistor is a result of two or more
 * resistors which were combined to this new resistor.
 *
 * <p>
 * This class provides methods to update itself out of the given values from
 * enclosed parent elements, as well as updating the parent elements from values
 * which were calculated from this element.
 *
 * <p>
 * It serves as a link between parent and child networks using the
 * RecursiveSimplifier solver class.
 *
 * @author Viktor Alexander Hartung
 */
public class SimplifiedResistor extends LinearDissipator {
    private static final Logger LOGGER = Logger.getLogger(
            SimplifiedResistor.class.getName());

    private final GeneralNode[] parentNodes = new GeneralNode[2]; // ref to the two original elements nodes
    private final List<LinearDissipator> parentResistors = new ArrayList<>(); // list of original elements
    private final List<GeneralNode> enclosedParentNodes = new ArrayList<>(); // only for series

    /**
     * For series simplification it is important to know which direction the
     * resistor is placed in series. The flow will be applied to all series
     * elements, there is a convention on the sign of the flow and the port
     * order. If the element is in reverse order than the simplified resistor,
     * the flow has to be set with a negative sign.
     */
    private boolean reverseOrder[];

    private final boolean parallel;

    private boolean enclosedNodesUpdated = false;
    private boolean parentValuesUpdated = false;

    /**
     * For series simplifications. Updated with prepareCalculation, used for
     * calculation. Is true for series which contain at least one open element,
     * makin the whole flow zero. Will be a special case for distributing the
     * voltages.
     */
    boolean atLeastOneOpenElement;

    /**
     * Used for series and parallel, describes the case that all elements
     * contained by this simplification are open (infinite resistance) elements.
     */
    boolean allElementsOpen;

    /**
     * For parallel simplification: If one resistor is bridged, the result is a
     * bridged resistor.
     */
    boolean bridgedParallel;

    /**
     * Another rare, special case: The resistor is actually not used as it is an
     * floating loop. See setFloatingLoop method description.
     */
    boolean floatingLoop;

    /**
     * For series: Holds the resistors which are connected on parent port 0 and
     * parent port 1. those will be searched on initial setup and can be saved
     * here so save some time in the assignResultsToParents method.
     */
    LinearDissipator[] resistorAtNode = new LinearDissipator[2];

    /**
     * For parallel simplifications. Updated with prepareCalculation, used for
     * calculation. Used for series resistors when at least one resistor is a
     * shortcut, making the resulting simplification also a shortcut. If there
     * are more than one, the flow is evenly distributed.
     */
    int numberOfShortcuts;

    /**
     * Creates instance of LinearDissipator for use as a replacement for
     * multiple resistors in networks.
     *
     * @param isParallel true: replaces parallels, false: replaces series
     */
    public SimplifiedResistor(boolean isParallel) {
        super(PhysicalDomain.ELECTRICAL);
        this.parallel = isParallel;
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation(); // resets all connected nodes
        calculateNewResistance(); // recalculate this on each call
        parentValuesUpdated = false;
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        if (!floatingLoop) {
            didSomething = super.doCalculation() || didSomething;
            didSomething = assignResultsToParents() || didSomething;
        } else {
            throw new ModelErrorException("doCalculation called on floating "
                    + "loop type simplified resistor.");
        }
        return didSomething;
    }

    /**
     * When the simplified resistor is actually not a resistor but a floating
     * loop situation, the calculation will be done by calling this method.
     *
     * @return true if something was done.
     */
    public boolean doFloatingLoopCalculation() {
        if (!parentValuesUpdated) {
            if (!parentNodes[0].effortUpdated()) {
                throw new ModelErrorException("Updated effort required to "
                        + "perform floating loop calulation.");
            }
            // Set same effort to all nodes (for series elements)
            for (GeneralNode p : enclosedParentNodes) {
                p.setEffort(parentNodes[0].getEffort());
            }
            // Flow is zero on all elements.
            for (LinearDissipator r : parentResistors) {
                r.setFlow(0.0, false);
            }
            parentValuesUpdated = true;
            return true;
        }
        return false;
    }

    // This method is now disabled. it was used to check if also all parent
    // values are updated. As this is not necessary and sometimes not possible
    // in case of some shortcuts or open connections, it will be sufficient
    // to use the super methods. The solver of the parent network is good 
    // enough to use what it gets during the do-calulation run.
    @Override
    public boolean isCalculationFinished() {
        // usually this checks only if all port values are se
        return super.isCalculationFinished() && parentValuesUpdated;
    }

    /**
     * Updates the element itself, providing new resistance value (or no value
     * at all, in case of missing connection or shortcuts). Also updates the
     * element type in case of any of the resistors is open or a shortcut.
     *
     * The new resistance value and maybe element type will be derived from
     * parent elements which were used to create this simplified resistor.
     *
     * This needs to be called from parent network down to child network.
     *
     */
    private void calculateNewResistance() {
        double r;
        double g;
        atLeastOneOpenElement = false; // init
        allElementsOpen = true; // init
        bridgedParallel = false; // init
        numberOfShortcuts = 0;
        elementType = ElementType.DISSIPATOR; // re-init
        if (parallel) { // parallel network: sum up conductance
            g = 0.0;
            for (LinearDissipator e : parentResistors) {
                if (e.getElementType() == ElementType.BRIDGED) {
                    // one shortcut puts the whole parallel thing to shortcut
                    numberOfShortcuts++;
                    bridgedParallel = true;
                    allElementsOpen = false;
                    continue;
                } else if (e.getElementType() == ElementType.OPEN) {
                    // open connections will just be ignored.
                    continue;
                }
                if (bridgedParallel) {
                    continue; // no more need for sum up g
                }
                allElementsOpen = false;
                g = g + 1 / e.getResistance();
            }
            if (bridgedParallel) {
                if (numberOfShortcuts >= 2) {
                    throw new ModelErrorException("Two parallel shortcuts can "
                            + "not be provided with causal solution.");
                }
                setBridgedConnection();
            } else if (allElementsOpen) {
                setOpenConnection();
            } else if (g <= 0.0) {
                // this is a fallback to prevent div/0 - shall never be needed
                elementType = ElementType.OPEN;
            } else if (elementType != ElementType.BRIDGED) {
                resistance = 1 / g;
            }
        } else { // series network: sum up resistance
            r = 0.0;
            for (LinearDissipator e : parentResistors) {
                if (e.getElementType() == ElementType.OPEN) {
                    atLeastOneOpenElement = true;
                    // one shortcut puts the whole parallel thing to shortcut
                    setOpenConnection(); // sets parent r to infinitiy
                    break;
                } else if (e.getElementType() == ElementType.BRIDGED) {
                    allElementsOpen = false;
                    // shortcuts will just be ignored.
                    // continue;
                } else {
                    allElementsOpen = false;
                    r = r + e.getResistance();
                }
            }
            // if somehow the resistance value is zero, manually set
            // the type to bridged. this happens, if r did not get any
            // resistances added and all connections were bridges.
            if (r == 0.0 && !atLeastOneOpenElement) {
                elementType = ElementType.BRIDGED;
            } else if (elementType != ElementType.OPEN) {
                setResistanceParameter(r);
            }
        }
    }

    /**
     * Uses the
     *
     * @return
     */
    private boolean assignResultsToParents() {
        int idx;
        LinearDissipator r = null;
        GeneralNode fp, np; // first, next port
        if (parentValuesUpdated) {
            return false; // already done
        }
        if (!super.isCalculationFinished()) {
            // all values from the child model which uses this element need 
            // to be known to transfer those reults to the parent elements
            // which were used to create this element. As this function gets
            // called from doCalculation of the simplified resistor inside the
            // child network, it might not be possible to do this
            // in first calls.
            return false;
        }
        // Both parent nodes will get the same effort nodes assigned
        // as this resistor. Update is false to leave update to the
        // parent class.
        if (!parentNodes[0].effortUpdated()) {
            parentNodes[0].setEffort(nodes.get(0).getEffort(), null, false);
        } else {
            if (Math.abs(parentNodes[0].getEffort() - nodes.get(0).getEffort())
                    > 1e-8) {
                // throw new CalculationException("Validation of already set"
                //         + " effort value failed.");
                LOGGER.log(Level.WARNING, "Validation of already set effort"
                            + " value failed.");
            }
        }
        if (!parentNodes[1].effortUpdated()) {
            parentNodes[1].setEffort(nodes.get(1).getEffort(), null, false);
        } else {
            if (Math.abs(parentNodes[1].getEffort() - nodes.get(1).getEffort())
                    > 1e-8) {
                // throw new CalculationException("Validation of already set"
                //        + " effort value failed.");
                LOGGER.log(Level.WARNING, "Validation of already set effort"
                            + " value failed.");
            }
        }
        if (parallel) {
            // that information is sufficient to calculate the flow through
            // the resistors by using ohms law
            for (LinearDissipator res : parentResistors) {
                res.doCalculation();
            }
            // Todo: If there are parallel shortcuts only, the flow needs to be
            // distributed evenly, while the effort is zero!
        } else { // series
            if (atLeastOneOpenElement) {
                // Any open element will set the flow to 0 for all elements.
                for (LinearDissipator pr : parentResistors) {
                    pr.setFlow(0, false);
                }
                // the more pain is to somehow find a voltage on floating nodes
                // to provide a solution. we will start from both sides and see
                // how far we can push the voltage through the nodes, each non-
                // open resistor will enforce the voltage to his port as flow
                // is zero. 
                for (idx = 0; idx < 2; idx++) {
                    fp = parentNodes[idx];
                    r = resistorAtNode[idx];
                    //   fp      r
                    //   o-----XXXXXX-----o-----XXXXXX--
                    while (true) { // go through all nodes
                        try {
                            np = r.getOnlyOtherNode(fp);
                        } catch (NoFlowThroughException ex) {
                            throw new ModelErrorException("Expected two ports "
                                    + "for resistor type element.");
                        }
                        // If the element is open, this loop is done.
                        if (r.getElementType() == ElementType.OPEN) {
                            break;
                        }
                        //   fp      r       np
                        //   o-----XXXXXX-----o-----XXXXXX--
                        if (np == parentNodes[1] || np == parentNodes[0]) {
                            throw new ModelErrorException("Reached the end of "
                                    + "an open series while trying not to "
                                    + "reach it!");
                        }
                        // Set the effort value on connected np to the same
                        // as on fp.
                        if (!np.effortUpdated()) {
                            np.setEffort(fp.getEffort(), r, false);
                        }
                        // prepare next while iteration
                        //   fp      r       np
                        //   o-----XXXXXX-----o-----XXXXXX--
                        fp = np;
                        //           r       fp
                        //   o-----XXXXXX-----o-----XXXXXX--
                        try {
                            r = (LinearDissipator) fp.getOnlyOtherElement(r);
                        } catch (NoFlowThroughException ex) {
                            throw new ModelErrorException("Expected two ports "
                                    + "for resistor type element.");
                        }
                        //                   fp       r
                        //   o-----XXXXXX-----o-----XXXXXX--
                    }
                } // end for both nodes
                // There might be some remaining, floating elements left. We
                // already assigned flow = 0 to all elements but floating ones
                // still wont have an assigned effort. We will now search them
                // and get the previous effort value, knowing it is not set,
                // and force these values to 0.0. There were other things
                // considered before but as the floating resistor could be 
                // a simplification itself, it was possible for other values to
                // generate artifact values by forcing flows or efforts where
                // they should not be. 0.0 seems to work fine.
                for (LinearDissipator pr : parentResistors) {
                    if (pr.getElementType() == ElementType.OPEN) {
                        if (!pr.getNode(0).effortUpdated()) {
                            pr.getNode(0).setEffort(
                                    0.0, pr, false);
                        }
                        if (!pr.getNode(1).effortUpdated()) {
                            pr.getNode(1).setEffort(
                                    0.0, pr, false);
                        }
                    }
                }
            } else {
                // all child resistors will have the same current as this.
                // The direction of the elements has to be considered.
                for (idx = 0; idx < parentResistors.size(); idx++) {
                    if (reverseOrder[idx]) {
                        parentResistors.get(idx).setFlow(-this.getFlow(), true);
                    } else {
                        parentResistors.get(idx).setFlow(this.getFlow(), true);
                    }
                }
            }
        }
        parentValuesUpdated = true;
        return true;
    }

    /**
     * Creates a list which will hold all ports that were consumed by this
     * replacement. Enclosed ports are those which are between series of
     * resistors, they will be unavailable for the simplified network.
     * <p>
     * Used for initial model or network setup. When creating a child network by
     * simplification, this is used to determine which nodes will not be present
     * in child network.
     * <p>
     * Additionally, as this loops through all series resistors, it will also
     * save wether the resistors are in same direction as the replacement or
     * not, this will be checked here and saved to an array to keep work for
     * cyclic recalculations low.
     */
    public void calculateEnclosedNodes() {
        LinearDissipator r = null;
        GeneralNode fp, np; // first, next node
        if (parallel) {
            throw new ModelErrorException(
                    "Illegal call, parallel resistors will not enclose ports.");
        }
        // init array with proper size
        reverseOrder = new boolean[parentResistors.size()];

        // start with port 0 - which one is connected to port[0]?
        for (LinearDissipator resistor : parentResistors) {
            if (resistor.getNode(0) == parentNodes[0]
                    | resistor.getNode(1) == parentNodes[0]) {
                r = resistor;
                reverseOrder[0] = resistor.getNode(1) == parentNodes[0];
                resistorAtNode[0] = resistor;
                break;
            }
        }
        fp = parentNodes[0];
        if (r == null) {
            throw new ModelErrorException("Impossible model error.");
        }
        //   fp      r
        //   o-----XXXXXX-----o-----XXXXXX--
        while (true) { // go through all nodes
            try {
                np = r.getOnlyOtherNode(fp);
            } catch (NoFlowThroughException ex) {
                throw new ModelErrorException("Expected two ports for "
                        + "resistor type element.");
            }
            //   fp      r       np
            //   o-----XXXXXX-----o-----XXXXXX--
            if (np == parentNodes[1]) { // reached the end
                resistorAtNode[1] = r; // save this for later use.
                break;
            }
            if (np == parentNodes[0] && floatingLoop) {
                break; // reached the end for a floating loop
            }
            enclosedParentNodes.add(np);
            try {
                r = (LinearDissipator) np.getOnlyOtherElement(r);
            } catch (NoFlowThroughException ex) {
                throw new ModelErrorException("Expected two ports for "
                        + "resistor type element.");
            }
            //   fp              np       r
            //   o-----XXXXXX-----o-----XXXXXX--
            if (!parentResistors.contains(r)) {
                throw new ModelErrorException(
                        "Detected unregistered element in series");
            }
            // As we start from [0], we can now check wether the np is on
            // index 0 or 1 on r, if it is on index 1, the element is in
            // reverse order.
            reverseOrder[parentResistors.indexOf(r)]
                    = r.getNode(1) == np;

            fp = np;
            //           r      np,fp
            //   o-----XXXXXX-----o-----XXXXXX--
        }
        enclosedNodesUpdated = true;
    }

    /**
     * Add reference to parent ports. These are the two ports where the elements
     * which are replaced by this simplification are placed between. They belong
     * to the parent network and the derived simplified sequence.
     *
     * Used for initial model or network setup.
     *
     * @param port0
     * @param port1
     */
    public void addParentNodes(GeneralNode port0, GeneralNode port1) {
        parentNodes[0] = port0;
        parentNodes[1] = port1;
    }

    public void addParentNode(GeneralNode port, int idx) {
        parentNodes[idx] = port;
    }

    /**
     * Get the parent port which is the port of the original network from which
     * this element was simplified of.
     *
     * @param idx which port, 0 or 1
     * @return GeneralNode reference
     */
    public GeneralNode getParentNode(int idx) {
        return parentNodes[idx];
    }

    public void addParentElements(LinearDissipator resistor) {
        parentResistors.add(resistor); // cast must be possible
    }

    public boolean isParallel() {
        return parallel;
    }

    /**
     * If the series loops into itself and has only one connection, this
     * resistor will not be able to be present in the next simplification. There
     * is basically one port that has three connections, connecting the loop to
     * the rest of the parent network. In this case, the resistor can not be
     * added to the next simplification but serves as a container which will be
     * calculated different.
     */
    public void setFloatingLoop() {
        floatingLoop = true;
    }

    public boolean isFloatingLoop() {
        return floatingLoop;
    }

    /**
     * Checks wether a given port is enclosed by this replacement or not.
     *
     * Used for initial model or network setup.
     *
     * @param p
     * @return
     */
    public boolean isNodeEnclosed(GeneralNode p) {
        if (!enclosedNodesUpdated) {
            throw new ModelErrorException(
                    "isPortEnclosed() called before calculateEnclosedPorts()");
        }
        return enclosedParentNodes.contains(p);
    }

    /**
     * Generates a name for the replacement resitor by using the names of the
     * parent resistors.
     */
    public void generateNameFromParents() {
        String name;
        name = parentResistors.get(0).toString().split("@")[0];
        if (parallel) {
            for (int idx = 1; idx < parentResistors.size(); idx++) {
                name = name + "|"
                        + parentResistors.get(idx).toString().split("@")[0];
            }
        } else {
            for (int idx = 1; idx < parentResistors.size(); idx++) {
                name = name + "-"
                        + parentResistors.get(idx).toString().split("@")[0];
            }
        }

        setName(name);
    }

}
