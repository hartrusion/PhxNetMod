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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.FlowThrough;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.OpenOrigin;
import com.hartrusion.modeling.general.SelfCapacitance;
import com.hartrusion.modeling.initial.AbstractIC;
import com.hartrusion.modeling.initial.InitialConditions;
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

    /**
     * Globally enables the per-cycle diagnostic scans (missing-flow warnings,
     * unsolved-element detection and the root-cause analysis). These only
     * produce log output and never influence the simulation result, so they are
     * disabled by default to keep the cyclic calculation as cheap as possible on
     * low-end hardware. Set to {@code true} once during startup to get the
     * detailed model-setup diagnostics back. It is static so debugging can be
     * toggled globally for all solver instances at once.
     */
    public static boolean diagnosticsEnabled = false;

    private final List<GeneralNode> modelNodes = new ArrayList<>();

    private final List<AbstractElement> modelElements = new ArrayList<>();

    /**
     * Pre-computed list of dead-end nodes (nodes connected to one element or
     * none). The network topology is fixed once setup is finished, so this set
     * never changes and is built once instead of being rediscovered by scanning
     * every model node on every cycle. These are the only nodes the dead-end
     * handling in {@link #doCalculation()} ever acts on.
     */
    private final List<GeneralNode> deadEndNodes = new ArrayList<>();

    private String name;

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
     * This list contains all dissipator (resistor, open or bridged) elements
     * that are placed between two effort forced ports. Those elements can get a
     * solution in terms of flow by just calling the doCalculation method once.
     * The calculation is called already as they are also part of the
     * effortForcingElements list, but for network traversing this is required.
     */
    private final List<AbstractElement> selfSolvingDissipatorElements
            = new ArrayList<>();

    /**
     * If self-solving dissipators are connected directly between effort
     * enforcing elements, (those are origins and self-capacitances) there might
     * be no necessity to even add the effort enforcing element to any solver as
     * no complex network needs to be solved. Such elements are handled in this
     * list and handled accordingly.
     */
    private final List<AbstractElement> selfSolvingEffortEnforcers
            = new ArrayList<>();

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
     * properties distribution along the calculated flow is calculated. Uses a
     * {@link DirectedFlowSolver} so this final propagation over the whole model
     * follows the flow direction instead of re-scanning every element on every
     * pass.
     */
    private final DirectedFlowSolver lastIterator = new DirectedFlowSolver();

    private final List<AbstractElement> unsolvedElements = new ArrayList<>();

    /**
     * Holds a reference to a thread pool that will be used to solve the
     * detected networks in parallel. If null, no parallel computing will be
     * used. It is static so it can be configured once during application
     * startup via {@link #setThreadPool(ExecutorService)} and is then shared by
     * all solver instances, exactly like in {@link SuperPosition}.
     */
    private static ExecutorService threadPool;

    /**
     * The transfer subnets that need a complex (and therefore expensive)
     * solver, i.e. those where {@link TransferSubnet#usesDedicatedSolver()}
     * returns false. Only these are worth solving on separate threads.
     */
    private List<TransferSubnet> heavySubnets;

    /**
     * The transfer subnets that are solved by a fast dedicated solver in a
     * single, cheap call. These are calculated in series together with the
     * other light networks and never get their own thread.
     */
    private List<TransferSubnet> lightSubnets;

    /**
     * Cached parallel work packages wrapping the {@link #heavySubnets}. Built
     * once after the networks are detected. These are the only tasks ever
     * handed to the thread pool.
     */
    private List<Callable<Void>> heavyTasks;

    /**
     * True once the shared lock has been handed to the model nodes, so the
     * injection is only ever done once.
     */
    private boolean locksInjected;

    /**
     * Monitor handed to the shared model nodes while parallel solving is
     * active. The independent sub networks only ever meet on the forced-effort
     * boundary nodes, where they contribute flows concurrently; this single
     * re-entrant lock serializes exactly those flow contributions and is owned
     * here in the solver instead of inside the node.
     */
    private final Object parallelLock = new Object();

    public void setString(String name) {
        this.name = name;
    }

    /**
     * Configures the thread pool used to solve the heavy networks in parallel.
     * Only the transfer subnets that need a complex solver
     * ({@link TransferSubnet#usesDedicatedSolver()} returns false) are handed
     * to the pool. All light networks (dedicated-solver subnets, self solving
     * dissipators, super positions and non-linear nets) are solved in series in
     * the calling thread, overlapping with the heavy work for free.
     * <p>
     * The pool is stored statically and shared by all solver instances, so it
     * only needs to be set once during application startup. It must be set
     * <b>before</b> {@link #addNetwork(GeneralNode)} is called, so that the
     * model nodes can be prepared for concurrent access.
     * <p>
     * The pool must be a <b>separate</b> pool, not the single threaded executor
     * that drives the cyclic {@code doCalculation} call, otherwise the blocking
     * wait inside this solver would dead-lock. Sizing it to the number of
     * available CPU cores is a sensible default. Pass {@code null} to disable
     * parallel solving.
     *
     * @param pool Executor used for parallel solving, or null to disable it.
     */
    public static void setThreadPool(ExecutorService pool) {
        threadPool = pool;
    }

    /**
     * Splits the detected transfer subnets into the heavy ones (worth a
     * separate thread) and the light ones (dedicated solver) and builds the
     * cached parallel work packages from the heavy ones. This classification is
     * stable after setup and independent of whether a thread pool is used.
     */
    private void buildSolverBatches() {
        heavySubnets = new ArrayList<>();
        lightSubnets = new ArrayList<>();
        for (TransferSubnet ts : subnets) {
            if (ts.usesDedicatedSolver()) {
                lightSubnets.add(ts);
            } else {
                heavySubnets.add(ts);
            }
        }
        heavyTasks = new ArrayList<>(heavySubnets.size() + superPosNets.size());
        for (final TransferSubnet ts : heavySubnets) {
            heavyTasks.add(() -> {
                ts.doCalculation();
                return null;
            });
        }
        // Standalone super position closed circuits are independent of every
        // other network, so they join the parallel batch too.
        for (final SuperPosition sp : superPosNets) {
            heavyTasks.add(() -> {
                sp.doCalculation();
                return null;
            });
        }
    }

    /**
     * Hands the shared lock to the real model nodes so several threads may
     * safely contribute flows to the boundary nodes. Only the forced-effort
     * boundary nodes are ever touched by more than one solver thread: by design
     * the independent sub networks are partitioned by exactly these nodes and
     * only meet there. Every other node belongs to a single sub network and is
     * therefore written by a single thread, so it stays lock-free. Leaving the
     * interior nodes unlocked keeps the expensive iterative back-solve of the
     * big subnets (thousands of synchronized flow accesses per cycle) free of
     * monitor overhead. The copy networks used internally by the solvers keep
     * no lock either. Safe to call more than once; the work is only done on the
     * first call.
     */
    private void injectNodeLock() {
        if (locksInjected) {
            return;
        }
        for (int i = 0; i < modelNodes.size(); i++) {
            if (hasForcedEffort[i]) {
                modelNodes.get(i).setLock(parallelLock);
            }
        }
        locksInjected = true;
    }

    /**
     * Solves all light networks in series in the calling thread. These are
     * cheap enough that the scheduling overhead of a separate thread would cost
     * more than the calculation itself. They are independent of each other and
     * of the heavy subnets (all separated by forced-effort boundary nodes), so
     * this may safely overlap with the heavy subnets running on the pool.
     */
    private void solveLightNetworks() {
        // Transfer subnets solved by a fast dedicated solver in one call.
        for (TransferSubnet ts : lightSubnets) {
            ts.doCalculation();
        }
        // Dissipators sitting directly between two forced-effort nodes.
        for (AbstractElement e : selfSolvingDissipatorElements) {
            e.doCalculation();
        }
        // Non-linear iterative networks.
        for (SimpleIterator si : nonLinearNets) {
            si.doCalculation();
        }
    }

    /**
     * Blocks until all given futures are finished. Any exception raised inside
     * a worker is re-thrown on the calling thread so that calculation errors
     * are not silently swallowed.
     *
     * @param futures Submitted heavy-subnet tasks to wait for.
     */
    private void awaitAll(List<Future<Void>> futures) {
        try {
            for (Future<Void> f : futures) {
                f.get(); // re-throws anything that happened in a worker
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ModelErrorException(
                    "Parallel calculation was interrupted.");
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new ModelErrorException(
                    "Parallel calculation failed: " + cause);
        }
    }

    @Override
    public String toString() {
        if (name != null) {
            return name;
        }
        return super.toString();
    }

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
                    // Mark all nodes which have their efforts forced directly
                    // by an element in an array of bools.
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

        // There is no need to add resistance elements which can be solved by
        // simply using ohms law to a solver algorithm, 
        // make a list of those too. 
        for (AbstractElement e : modelElements) {
            if (LinearNetwork.isElementResistorType(e)) {
                idx = modelNodes.indexOf(e.getNode(0));
                jdx = modelNodes.indexOf(e.getNode(1));
                if (hasForcedEffort[idx] && hasForcedEffort[jdx]) {
                    selfSolvingDissipatorElements.add(e);
                }
            }
        }

        // It is possible that effort forcing elements are only connected to 
        // self solving elments. As those are not getting added to any solver,
        // the effort forcing element can happen to not be added to any solver
        // too. We need to add them to a separate list to also handle them 
        // properly.
        boolean hasOnylySelfSolvingElementsConnected;
        for (AbstractElement e : effortForcingElements) {
            // Check ALL other elements on the attached nodes
            hasOnylySelfSolvingElementsConnected = true; // init
            for (idx = 0; idx < e.getNumberOfNodes(); idx++) {
                node = e.getNode(idx); // iterate through all nodes:
                for (jdx = 0; jdx < node.getNumberOfElements(); jdx++) {
                    element = node.getElement(jdx);
                    if (element == e) {
                        continue; // self
                    }
                    // if one of them is not self-solving, we're done here.
                    if (!selfSolvingDissipatorElements.contains(element)) {
                        hasOnylySelfSolvingElementsConnected = false;
                        break;
                    }
                }
            }
            if (hasOnylySelfSolvingElementsConnected) {
                selfSolvingEffortEnforcers.add(e);
            }
        }

        // Perform a rudimentary check: No element that is of type flowthrough
        // must have less than two nodes (it must be exactly 2), otherwise 
        // there will be some weird error later.
        for (AbstractElement e : modelElements) {
            if (e instanceof FlowThrough) {
                if (e.getNumberOfNodes() != 2) {
                    throw new ModelErrorException("A flow-through element "
                            + "with not exactly two nodes is illegal.");
                }
            }
        }

        // Check that all nodes have at least 2 connections. It is not illegal
        // to have a node that is floating around but probably this is not
        // wanted.
        for (GeneralNode n : modelNodes) {
            if (n.getNumberOfElements() <= 1) {
                LOGGER.log(Level.WARNING, "Suspicious connection: Element "
                        + n.getElement(0).toString() + " connected to floating "
                        + "node " + n.toString());
            }
        }

        // Iterate through all elements until they are all added to the transfer
        // subnets. Just search for an element that has to be part of such a
        // network and use it to start setting up the solver.
        while (!allElementsInSolvers()) {
            iterations--;
            if (iterations <= 0) {
                for (AbstractElement e : modelElements) {
                    if (!isElementInSolver(e)) {
                        LOGGER.log(Level.SEVERE, "Aborting with iterations "
                                + "Eception now. Element "
                                + e.toString()
                                + " was NOT added to any solver.");
                    }
                }
                throw new ModelErrorException("Endless iterations, aborting.");
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

        // Classify the detected subnets into heavy (own thread) and light
        // (series) once, now that all nodes and networks are known. If a thread
        // pool was configured (statically, during startup), also hand the
        // shared lock to the model nodes for safe concurrent access.
        buildSolverBatches();
        if (threadPool != null) {
            injectNodeLock();
        }

        // Pre-compute the dead-end nodes (one or zero connected elements). The
        // topology is fixed from here on, so this set never changes and the
        // dead-end handling no longer has to scan every node on every cycle.
        deadEndNodes.clear();
        for (GeneralNode n : modelNodes) {
            if (n.getNumberOfElements() <= 1) {
                deadEndNodes.add(n);
            }
        }

        LOGGER.log(Level.INFO, "...setup finished. "
                + "TransferSubnets: " + subnets.size()
                + ", NonLinearNets: " + nonLinearNets.size()
                + ", SuperPositions: " + superPosNets.size());
    }

    private boolean allElementsInSolvers() {
        boolean found;
        for (AbstractElement e : modelElements) {
            found = selfSolvingDissipatorElements.contains(e);
            found |= selfSolvingEffortEnforcers.contains(e);
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
        if (selfSolvingDissipatorElements.contains(e)) {
            return true;
        }
        if (selfSolvingEffortEnforcers.contains(e)) {
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
        boolean noEnergySavingElements = true;
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
            if (e.getElementType() == ElementType.ENFORCER
                    || e instanceof SelfCapacitance
                    || e instanceof OpenOrigin
                    || e instanceof SteamIsobaricIsochoricEvaporator
                    || e instanceof PhasedExpandingThermalExchanger) {
                closedCircuit = false;
            }
            if (e.getElementType() == ElementType.CAPACTIANCE
                    || e.getElementType() == ElementType.INDUCTANCE) {
                noEnergySavingElements = false;
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

        } else if (allElementsLinear && closedCircuit
                && noEnergySavingElements) {
            // Networks that can be solved by superposition solver will be added
            // to that solver type directly without the use of any transfer type
            // layer.
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
        unsolvedElements.clear();

        lastIterator.prepareCalculation();
        for (AbstractElement e : selfSolvingDissipatorElements) {
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
        // Hard-assign all flows to 0.0 on elements which are of OPEN type. 
        // This eliminates numerical issues and ensures an exact 0.0 value when
        // adding flows in superposition.
        /*
        for (int idx = 0; idx < modelNodes.size(); idx++) {
            for (int jdx = 0; jdx < modelNodes.get(idx).getNumberOfElements(); jdx++) {
                if (modelNodes.get(idx).getElement(jdx).getElementType() == ElementType.OPEN) {
                    modelNodes.get(idx).setFlow(0.0, modelNodes.get(idx).getElement(jdx), false);
                }
            }
        } */

        // Resolve all forced efforts first. Origins and self capacitances push
        // their effort onto the boundary nodes, so every node shared between
        // independent sub networks already carries its effort before parallel
        // solving begins. The sub networks then only read those efforts and
        // contribute flows, which confines the concurrent work to flow values
        // and means a boundary node never resolves a flow into a neighbouring
        // sub network (its absorbing element is already fixed).
        for (AbstractElement e : effortForcingElements) {
            e.doCalculation();
        }
        if (threadPool != null && heavyTasks != null
                && !heavyTasks.isEmpty()) {
            injectNodeLock(); // safety net if pool was set after addNetwork
            // Submit the heavy work (complex subnets + standalone super
            // positions) to the pool and immediately solve all the cheap
            // networks in this thread, so the light series work overlaps with
            // the heavy parallel work for free. The futures are always joined
            // (even on error) so no task leaks into the next cycle.
            List<Future<Void>> heavyFutures
                    = new ArrayList<>(heavyTasks.size());
            for (Callable<Void> task : heavyTasks) {
                heavyFutures.add(threadPool.submit(task));
            }
            try {
                solveLightNetworks();
            } finally {
                awaitAll(heavyFutures);
            }
        } else {
            // Fully sequential: heavy work first, then all light networks.
            for (TransferSubnet ts : heavySubnets) {
                ts.doCalculation();
            }
            for (SuperPosition sp : superPosNets) {
                sp.doCalculation();
            }
            solveLightNetworks();
        }
        // Dead-End-Nodes may not have a solution as there is nothing so far
        // that would provide a solution at all, they might not even be 
        // found and assigned to one of the solvers. Provide solutions for such
        // dead end nodes here and set flow to the attached element to 0.0 
        // manually. This can happen if a reservoir assembly is attached but
        // nothing is connected to the primary inlet. The set of dead-end nodes
        // is structural and pre-computed once, so this no longer scans every
        // node of the model on every cycle.
        GeneralNode otherNode;
        for (GeneralNode n : deadEndNodes) {
            if (n.getNumberOfElements() == 1) {
                n.setFlow(0.0, n.getElement(0), true);
            }
            if (!n.effortUpdated()) {
                if (n.getNumberOfElements() == 0) {
                    n.setEffort(0.0); // at least priovide some sort of solution
                    continue; // what is this even, only corrupt things here
                }
                try {
                    otherNode = n.getElement(0).getOnlyOtherNode(n);
                } catch (NoFlowThroughException ex) {
                    otherNode = null;
                }
                if (otherNode != null) { // copy value from this node here.
                    if (otherNode.effortUpdated()) { // but only if possible.
                        n.setEffort(otherNode.getEffort());
                    }
                }
            }
        }
        lastIterator.doCalculation();

        if (!diagnosticsEnabled) {
            return true;
        }

        for (GeneralNode n : modelNodes) {
            if (!n.allFlowsUpdated()) {
                LOGGER.log(Level.WARNING,
                        "Missing flow on node " + n.toString());
            }
        }
        /* for (AbstractElement e : modelElements) {
            if (!e.isCalculationFinished()) {
                LOGGER.log(Level.WARNING,
                        "No full solution for element " + e.toString());
            }
        } */

        // Check for missing solutions. In case something is missing, there will
        // be a more detailed output that does not hold errors that are due to
        for (AbstractElement e : modelElements) {
            if (!e.isCalculationFinished()) {
                unsolvedElements.add(e);
            }
        }

        if (!unsolvedElements.isEmpty()) {
            List<AbstractElement> rootCauses = new ArrayList<>();

            for (AbstractElement e : unsolvedElements) {
                boolean hasUnsolvedUpstream = false;

                // Prüfe alle Knoten des Elements
                for (int i = 0; i < e.getNumberOfNodes(); i++) {
                    GeneralNode node = e.getNode(i);

                    // Annahme: Flow > 0 bedeutet, dass Fluid vom Knoten IN das Element fließt.
                    // (Falls deine Konvention andersherum ist, also Flow < 0 für Zufluss, 
                    // musst du die Bedingung unten auf < 0.0 ändern).
                    double flowIntoElement = node.getFlow(e);

                    if (flowIntoElement > 1e-18) { // Nur echte Zuflüsse betrachten
                        // Welches Element bringt diesen Fluss in den Knoten?
                        for (int j = 0; j < node.getNumberOfElements(); j++) {
                            AbstractElement upstreamElement = node.getElement(j);

                            if (upstreamElement == e) {
                                continue; // Sich selbst ignorieren
                            }
                            // Wenn das Element in den Knoten fördert (Flow in den Knoten hinein)
                            // und dieses Element selbst nicht vollständig gelöst ist:
                            double flowOutOfUpstream = -node.getFlow(upstreamElement);

                            if (flowOutOfUpstream > 1e-9 && unsolvedElements.contains(upstreamElement)) {
                                hasUnsolvedUpstream = true;
                                break;
                            }
                        }
                    }
                    if (hasUnsolvedUpstream) {
                        break;
                    }
                }

                if (!hasUnsolvedUpstream && e.getCoupledElement() != null) {
                    if (unsolvedElements.contains(e.getCoupledElement())) {
                        hasUnsolvedUpstream = true;
                        // Optional für mehr Details:
                        LOGGER.info(e.toString() + " is waiting for its coupled element: " + e.getCoupledElement().toString());
                    }
                }

                // Wenn es kein ungelöstes Upstream-Element gibt, ist dieses Element
                // vermutlich der Auslöser der Kettenreaktion.
                if (!hasUnsolvedUpstream) {
                    rootCauses.add(e);
                }
            }

            // Ausgabe
            if (!rootCauses.isEmpty()) {
                for (AbstractElement rootCause : rootCauses) {
                    LOGGER.log(Level.SEVERE, "ROOT CAUSE: No full solution for element " + rootCause.toString()
                            + ". Upstream elements seem to be solved or there is no upstream.");
                }
            } else {
                // Fallback, z.B. bei einem Ring ohne Lösung (Circular Dependency)
                // In this case, some thermal delays have to be added. When having parallel pumps, there might be an issue with
                // some pumps having nonequal characteristics, this will force a reverse flow through pumps. It is not supported
                // to have a circle of flows which does mix something, this would require some additional solver that is
                // not available.
                LOGGER.log(Level.SEVERE, "Circular dependency detected. All unsolved elements depend on other unsolved elements.");
                for (AbstractElement e : unsolvedElements) {
                    LOGGER.log(Level.WARNING, "Unsolved element in cycle: " + e.toString());
                }
            }
        }
        return true;
    }

    /**
     * Generates a List which holds all initial conditions for all elements that
     * do have such conditions and are known to this solver.
     *
     * @return List of AbstractIC elements.
     */
    public List<AbstractIC> getCurrentNetworkCondition() {
        List<AbstractIC> icList = new ArrayList<>();
        for (AbstractElement e : modelElements) {
            if (e instanceof InitialConditions) {
                AbstractIC ic
                        = ((InitialConditions) e).getState();
                if (ic != null) {
                    icList.add(ic);
                }
            }
        }
        return icList;
    }

    /**
     * Sets an initial condition as the new current state of the network. The
     * provided List of ICs must match to what the solver has, its intended to
     * generate the IC list using getCurrentNetworkCondition method.
     *
     * @param states List of AbstractIC elements.
     */
    public void setNetworkInitialCondition(List<AbstractIC> states) {
        for (AbstractIC ic : states) {
            for (AbstractElement e : modelElements) {
                if (e instanceof InitialConditions
                        && e.toString().equals(ic.getElementName())) {
                    ((InitialConditions) e).setInitialCondition(ic);
                    break;
                }
            }
        }
    }
}
