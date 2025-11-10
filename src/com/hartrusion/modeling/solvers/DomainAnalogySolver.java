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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.general.SelfCapacitance;
import com.hartrusion.modeling.phasedfluid.PhasedExpandingThermalExchanger;
import com.hartrusion.modeling.steam.SteamIsobaricIsochoricEvaporator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds a full dynamic model, provides means to solve it and some other neat
 * features.
 * <p>
 * This class is used to do all the work after a model was set up. It will
 * initialize itself by calling addNetwork with any node that is part of the
 * model, starting a traverse algorithm that will scan the whole network and set
 * up the solvers.
 * <p>
 * The prepareCalculation and doCalculation methods can be called and those
 * calls will be redirected to the used solving objects.
 *
 * @author Viktor Alexander Hartung
 */
public class DomainAnalogySolver {

    private static final Logger LOGGER = Logger.getLogger(
            DomainAnalogySolver.class.getName());

    private final List<GeneralNode> modelNodes = new ArrayList<>();

    private final List<AbstractElement> modelElements = new ArrayList<>();

    /**
     * TRUE for all modelNodes [index] which have their effort forced either by
     * an origin or a self-capacitance element.
     */
    private boolean[] hasForcedEffort;

    /**
     * Contains all elements that will immediately force an effort value on the
     * connected ports, those are basically origins and self capacitances.
     */
    private final List<AbstractElement> effortForcingElements = new ArrayList<>();

    /**
     * For each node that has a forced effort there is an element that does this
     * effort forcing. It can not be more than one element. This array holds the
     * index in modelElements for each modelNodes with the index of modelNodes
     * as the array index.
     *
     * forcingElementForNode[index in modelNodes] = index in modelElements
     */
    private int[] forcingElementForNode;

    /**
     * This list contains all elements that are placed between two effort forced
     * ports. Those elements can get a solution in terms of flow by just calling
     * the doCalculation method once. *
     */
    private final List<AbstractElement> selfSolvingElements = new ArrayList<>();

    /**
     * Holds references to all created TransferSubnet solver instances.
     */
    private final List<TransferSubnet> subnets = new ArrayList<>();

    /**
     * Holds references to all created TransferSubnet solver instances.
     */
    private final List<SimpleIterator> nonLinearNets = new ArrayList<>();

    /**
     * Holds references to all created SuperPosition instances which can solve a
     * network on their own.
     */
    private final List<SuperPosition> superPosNets = new ArrayList<>();

    /**
     * An iterative solver that will be called at the end of the calculation
     * run, it contains all elements and will ensure that the temperature and
     * properties distribution along the calculated flow is calculated.
     */
    private final SimpleIterator lastIterator = new SimpleIterator();

    /**
     * Holds a reference to a thread pool that can will be used for
     * superPosition solver for parallelization of solving process. If null, no
     * parallel computing will be used.
     */
    private ExecutorService threadPool;

    /**
     * Traverses through a network, needs one node as a starting point. Calling
     * This will add the whole network to this Instance.
     *
     * @param randomNode Any node of the network to start from
     */
    public void addNetwork(GeneralNode randomNode) {
        GeneralNode node;
        AbstractElement element;
        int idx, jdx, nr;
        ElementType eType;
        int iterations = 50;
        // all found nodes and elements will be stored here so we know what we
        // have already seen
        // Set<GeneralNode> foundNodes = new HashSet<>();
        // Set<AbstractElement> foundElements = new HashSet<>();
        // Those two deques will hold all nodes and elements that we find 
        // during our search.
        Deque<GeneralNode> pendingNodes = new ArrayDeque<>();
        Deque<AbstractElement> pendingElements = new ArrayDeque<>();

        // insert start node
        modelNodes.add(randomNode);
        pendingNodes.add(randomNode);

        while (!pendingNodes.isEmpty() || !pendingElements.isEmpty()) {
            // As long as we have nodes that we need to take a look at:
            while (!pendingNodes.isEmpty()) {
                // poll all of that nodes out of the deque:
                node = pendingNodes.poll();
                nr = node.getNumberOfElements();
                for (idx = 0; idx < nr; idx++) {
                    // and take a look at all elements.
                    element = node.getElement(idx);
                    if (!modelElements.contains(element)) {
                        // new element found? Add it to the found-list and 
                        // mark it for further search. Each time we find a new
                        // element, we will add it to the element list to make
                        // sure we will take a look on all nodes later with 2nd
                        // while loop down below.
                        modelElements.add(element);
                        pendingElements.add(element);
                        // when encountering elements that connect to other 
                        // network, we will just add them to the same list.
                        if (element.getCoupledElement() != null) {
                            if (!pendingElements.contains(
                                    element.getCoupledElement())
                                    && !modelElements.contains(
                                            element.getCoupledElement())) {
                                pendingElements.add(
                                        element.getCoupledElement());
                            }
                        }
                    }
                }
            }

            // The element loop works the same as the node loop, as long as 
            // there are elements where we did not perform a search on, check
            // if we know all nodes of the element. If not, add them to the list
            while (!pendingElements.isEmpty()) {
                element = pendingElements.poll();
                nr = element.getNumberOfNodes();
                for (idx = 0; idx < nr; idx++) {
                    node = element.getNode(idx);
                    if (!modelNodes.contains(node)) {
                        modelNodes.add(node);
                        pendingNodes.add(node);
                        // and this will trigger the above loop again.
                    }
                }
            }
        }

        LOGGER.log(Level.INFO, "Found " + modelElements.size() + " Elements "
                + "connected to " + modelNodes.size() + " Nodes. Setting up "
                + "solvers...");

        // Now, as all nodes and elements are known, mark all nodes that have
        // a connection to an effort enforcing element. We do this to determine
        // how to solve the network. This will mark boundaries for possible 
        // usage of TransferSubnet.
        hasForcedEffort = new boolean[modelNodes.size()];
        forcingElementForNode = new int[modelNodes.size()];
        Arrays.fill(forcingElementForNode, -1);

        for (GeneralNode n : modelNodes) {
            for (idx = 0; idx < n.getNumberOfElements(); idx++) {
                if (n.getElement(idx).getElementType() == ElementType.ORIGIN
                        || n.getElement(idx) instanceof SelfCapacitance) {
                    hasForcedEffort[modelNodes.indexOf(n)] = true;
                    if (forcingElementForNode[modelNodes.indexOf(n)] != -1) {
                        throw new ModelErrorException("There can only be one "
                                + "effort forcing element on each node.");
                    }
                    forcingElementForNode[modelNodes.indexOf(n)]
                            = modelElements.indexOf(n.getElement(idx));

                    if (!effortForcingElements.contains(n.getElement(idx))) {
                        effortForcingElements.add(n.getElement(idx));
                    }
                }
            }
        }

        // There is no need to add elements which can solve themselves, so we
        // make a list of those too. It is valid that flow sources will appear 
        // here too.
        for (AbstractElement e : modelElements) {
            if (LinearNetwork.isElementResistorType(e)) {
                idx = modelNodes.indexOf(e.getNode(0));
                jdx = modelNodes.indexOf(e.getNode(1));
                if (hasForcedEffort[idx] && hasForcedEffort[jdx]) {
                    selfSolvingElements.add(e);
                }
            }
        }

        // Iterate through all elements until they are all added to the transfer
        // subnets. Just search for an element that has to be part of such a
        // network and use it to start setting up the solver.
        while (!allElementsInSolvers()) {
            iterations--;
            if (iterations <= 0) {
                throw new ModelErrorException("Endless iterations.");
            }
            for (AbstractElement e : modelElements) {
                if (!isElementInSolver(e)
                        && !effortForcingElements.contains(e)) {
                    setupSubnetSolver(e);
                }
            }
        }

        // Additionally, add all elements to one iterative solver, this one
        // is needed at the end to calculate the thermal distribution.
        for (AbstractElement e : modelElements) {
            lastIterator.addElement(e);
        }

        LOGGER.log(Level.INFO, "...setup finished. "
                + "TransferSubnets: " + subnets.size()
                + ", NonLinearNets: " + nonLinearNets.size()
                + ", SuperPositions: " + superPosNets.size());
    }

    private boolean allElementsInSolvers() {
        boolean found;
        for (AbstractElement e : modelElements) {
            found = selfSolvingElements.contains(e);
            for (TransferSubnet tf : subnets) {
                found |= tf.containsElement(e);
            }
            for (SimpleIterator si : nonLinearNets) {
                found |= si.containsElement(e);
            }
            for (SuperPosition sp : superPosNets) {
                found |= sp.containsElement(e);
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    private boolean isElementInSolver(AbstractElement e) {
        if (selfSolvingElements.contains(e)) {
            return true;
        }
        for (TransferSubnet tf : subnets) {
            if (tf.containsElement(e)) {
                return true;
            }
        }
        for (SimpleIterator si : nonLinearNets) {
            if (si.containsElement(e)) {
                return true;
            }
        }
        for (SuperPosition sp : superPosNets) {
            if (sp.containsElement(e)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds a subnet of the model to a solver. This will examine the network
     * beginning with one element until it hits a boundary that forces an effort
     * to the network. After the subnet is determined, it will be added to a
     * more specific solver.
     *
     * @param startElement a random element from INSIDE the subnet.
     */
    private void setupSubnetSolver(AbstractElement startElement) {
        Deque<GeneralNode> pendingNodes = new ArrayDeque<>();
        Deque<AbstractElement> pendingElements = new ArrayDeque<>();
        List<AbstractElement> foundElements = new ArrayList<>();
        List<GeneralNode> foundNodes = new ArrayList<>();
        boolean allElementsLinear = true;
        boolean closedCircuit = true;
        int numberOfSources = 0;
        // boolean containsExpansionElement;
        boolean expansionPathFound;
        TransferSubnet ts;
        SimpleIterator si;
        SuperPosition sp;
        int nr, idx, jdx;
        int iterations = 1000;
        AbstractElement element, f, g;
        GeneralNode node, v, b, n;

        if (startElement.getNumberOfNodes() != 2) {
            throw new ModelErrorException("Expected an element with 2 nodes "
                    + "as others are excluded at this point.");
        }

        pendingElements.add(startElement);
        foundElements.add(startElement);

        while (!pendingNodes.isEmpty() || !pendingElements.isEmpty()) {
            iterations--;
            if (iterations <= 0) {
                throw new ModelErrorException("Endless iterations?");
            }
            while (!pendingElements.isEmpty()) {
                element = pendingElements.poll();
                nr = element.getNumberOfNodes();
                if (nr != 2) {
                    throw new ModelErrorException("Expected exactly 2 nodes");
                }
                // Examine nodes of that element, but do only continue to search
                // if the node is not effort entforced. Effort enforced nodes
                // represent the boundary of the transfer subnet.
                for (idx = 0; idx < nr; idx++) {
                    node = element.getNode(idx);
                    if (!foundNodes.contains(node)) { // new node found
                        foundNodes.add(node); // add it to the network
                        jdx = modelNodes.indexOf(node);
                        if (jdx <= -1) {
                            // modelNodes must contain all nodes, otherwise wtf
                            throw new ModelErrorException("Found a node that "
                                    + "was not found in first search.");
                        }
                        if (hasForcedEffort[jdx]) {
                            // hit a boundary - add the effort enforcing element
                            // which is already known in an array we filled 
                            // before. Those are origins or capacitance 
                            // boundaries. Add the element that is responsible
                            // for enforcing the effort manually and do not 
                            // perform any further search on it.
                            if (!foundElements.contains(modelElements.get(
                                    forcingElementForNode[jdx]))) {
                                // it is possible to have 2 nodes on one
                                // of those forcer elements, so it might be
                                // already there. therefore !contains.
                                foundElements.add(modelElements.get(
                                        forcingElementForNode[jdx]));
                            }
                        } else {
                            // nodes without forced efforts are inside the 
                            // subnet and connect subnet elements, perform 
                            // further seach on those.
                            pendingNodes.add(node);
                        }
                    }
                }
            }
            while (!pendingNodes.isEmpty()) {
                // the nodes part is easy as this contains only nodes which
                // are inside the transfer subnet.
                node = pendingNodes.poll();
                nr = node.getNumberOfElements();
                jdx = modelNodes.indexOf(node);
                if (hasForcedEffort[jdx]) {
                    throw new ModelErrorException("No forced effort expected "
                            + "in this part of the search.");
                }
                for (idx = 0; idx < nr; idx++) {
                    element = node.getElement(idx);
                    if (!foundElements.contains(element)) {
                        // new element found, add it to the search list. There
                        // is no consideration of connected networks here as
                        // this only sets up a transfer subnet.
                        pendingElements.add(element);
                        foundElements.add(element);
                    }
                }
            }
        } // end of outer while
        // Examine all elements which were identified as this one subnet.
        for (AbstractElement e : foundElements) {
            if (!e.isLinear()) {
                allElementsLinear = false;
            }
            // Determine if this is a closed circuit without energy saving
            // elements. Such can be solved directly with a SuperPosition 
            // solver without the need of the TransferSubnet solver.
            if (e.getElementType() == ElementType.CAPACTIANCE
                    || e.getElementType() == ElementType.ENFORCER
                    || e.getElementType() == ElementType.INDUCTANCE
                    || e instanceof OpenOrigin
                    || e instanceof SteamIsobaricIsochoricEvaporator
                    || e instanceof PhasedExpandingThermalExchanger) {
                closedCircuit = false;
            }
            if (e.getElementType() == ElementType.FLOWSOURCE
                    || e.getElementType() == ElementType.EFFORTSOURCE) {
                numberOfSources++;
                // Todo: If there's only one source, use RecursiveSimplifier.
            }
        }
        if (allElementsLinear && !closedCircuit) {
            // add all to the subnet solver
            ts = new TransferSubnet();
            for (GeneralNode fn : foundNodes) {
                ts.registerNode(fn);
            }
            for (AbstractElement e : foundElements) {
                ts.registerElement(e);
                // Some elements (currently there is only one tb) might do an
                // expansion themselves. this might be replaced in future models
                // by some kinda containers but for now i dont have anything
                // better than this.
                //if (e instanceof SteamFixedVolumeThermalExchanger) {
                //    containsExpansionElement = true;
                //}
                // -> This part is unnecessary as we need to search through 
                // those items anyway in next step.
            }
            ts.setupTransferSubnet();
            subnets.add(ts);
            // make that expansion thingy workaround to not set flow by the
            // solution of the subnet. There has to be a single path between an 
            // expansion element and the next capacitance.
            // <editor-fold defaultstate="collapsed" desc="Exp. path tweak"> 
            for (AbstractElement e : foundElements) {
                if (e instanceof SteamIsobaricIsochoricEvaporator
                        || e instanceof PhasedExpandingThermalExchanger) {
                    ts.setNoFlowTransfer(e);
                    expansionPathFound = false;
                    for (idx = 0; idx < 2; idx++) { // go in both directions.
                        v = e.getNode(idx);
                        f = e;
                        // Start with this situation and expect at while start:
                        //      f   v
                        //  o--XXX--o
                        while (true) {
                            try {
                                g = v.getOnlyOtherElement(f);
                            } catch (NoFlowThroughException ex) {
                                break;
                            }
                            //      f   v   g
                            //  o--XXX--o--XXX--
                            // Found the capacitance at the end:
                            if (g.getElementType() == ElementType.CAPACTIANCE) {
                                // Go back to e and set all elements to no-
                                // flow-transfer
                                try {
                                    v = f.getOnlyOtherNode(v);
                                } catch (NoFlowThroughException ex) {
                                    // This is absolutely unexpected here but 
                                    // lets throw an exception, you never know.
                                    throw new ModelErrorException(
                                            "Unexpected no-non-flow while "
                                            + "going back, wtf?");
                                }
                                //  v   f       
                                //  o--XXX--o--XXX--
                                while (true) {
                                    ts.setNoFlowTransfer(f);
                                    if (f == e) {
                                        // back at the start element, thats it.
                                        break;
                                    }
                                    try {
                                        g = v.getOnlyOtherElement(f);
                                        //   g   v   f
                                        //  XXX--o--XXX--o--XXX--
                                    } catch (NoFlowThroughException ex) {
                                        // Same here, must be possible to go 
                                        // back without issues!
                                        throw new ModelErrorException(
                                                "Unexpected no-non-flow while "
                                                + "going back, wtf?");
                                    }
                                    try {
                                        v = g.getOnlyOtherNode(v);
                                    } catch (NoFlowThroughException ex) {
                                        throw new ModelErrorException(
                                                "Unexpected no-non-flow while "
                                                + "going back, wtf?");
                                    }
                                    //  v   g       f
                                    //  o--XXX--o--XXX--o--XXX--
                                    f = g;
                                    //  v   f        
                                    //  o--XXX--o--XXX--o--XXX--
                                }
                                expansionPathFound = true;
                                break;
                            }
                            try {
                                // Continue towards next element
                                v = g.getOnlyOtherNode(v);
                            } catch (NoFlowThroughException ex) {
                                break;
                            }
                            //      f       g   v
                            //  o--XXX--o--XXX--o
                            f = g;
                            //              f   v
                            //  o--XXX--o--XXX--o
                        }
                        if (expansionPathFound) {
                            break; // no need to check second direction.
                        }
                    } // end of for loop for both nodes of element
                    if (!expansionPathFound) {
                        throw new ModelErrorException("An expanding element "
                                + "is part of the network but there is no "
                                + "straight path found towards a Capacitance.");
                    }
                }
            }
            // </editor-fold>

        } else if (allElementsLinear && closedCircuit) {
            sp = new SuperPosition();
            for (GeneralNode fn : foundNodes) {
                sp.registerNode(fn);
            }
            for (AbstractElement e : foundElements) {
                sp.registerElement(e);
            }
            sp.superPositionSetup();
            superPosNets.add(sp);
        } else {
            // add all to the subnet solver
            si = new SimpleIterator();
            for (AbstractElement e : foundElements) {
                si.addElement(e);
            }
            nonLinearNets.add(si);
        }
    }

    public void prepareCalculation() {
        lastIterator.prepareCalculation();
        for (AbstractElement e : selfSolvingElements) {
            e.prepareCalculation();
        }
        for (TransferSubnet ts : subnets) {
            ts.prepareCalculation();
        }
        for (SuperPosition sp : superPosNets) {
            sp.prepareCalculation();
        }
        for (SimpleIterator si : nonLinearNets) {
            si.prepareCalculation();
        }
    }

    public boolean doCalculation() {
        // boolean retVal = false;
        for (TransferSubnet ts : subnets) {
            ts.doCalculation();
        }
        for (AbstractElement e : effortForcingElements) {
            e.doCalculation();
        }
        for (AbstractElement e : selfSolvingElements) {
            e.doCalculation();
        }
        for (SuperPosition sp : superPosNets) {
            sp.doCalculation();
        }
        for (SimpleIterator si : nonLinearNets) {
            si.doCalculation();
        }
        lastIterator.doCalculation();

        for (GeneralNode n : modelNodes) {
            if (!n.allFlowsUpdated()) {
                LOGGER.log(Level.WARNING,
                        "Missing flow on node " + n.toString());
            }
        }
        for (AbstractElement e : modelElements) {
            if (!e.isCalculationFinished()) {
                LOGGER.log(Level.WARNING,
                        "No full solution for element " + e.toString());
            }
        }
        return true;
    }
}
