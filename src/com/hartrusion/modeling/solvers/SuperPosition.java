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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.*;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;

/**
 * Solves a network using superposition method. This is used for networks
 * containing multiple sources, like mixing fluids from multiple tanks together.
 *
 * <p>
 * The network will be copied to n layer networks for n sources. Each network
 * will then be solved individually using the simplification solver, providing a
 * flow value for each element. The flow value of the network here is then
 * calculated by adding all currents of each linear network together.
 *
 * <p>
 * As the number of elements will be the same for each layer and the layers
 * match the base network, everything is organzied in two-dimensional array
 * instead of lists as it will be quite static and predictable. Each layer is a
 * network extended to simplification class to provide solving methods for each
 * layer.
 *
 * <p>
 * Earlier revision of this solver added all elements to a recursive
 * simplification solver, however it turned out that this will result in issues
 * as the created networks cant be simplified that way. The Overlay[] layer
 * array was an array of recursive simplifiers before but was replaced with
 * another layer to make some simplifications by manipulating the model by
 * removing the replaced sources instead of replacing them. It was decided to
 * keep this class as it is as it is tested and working, that is why the sources
 * which will not be needed in one layer are still beeing created, even though
 * they will be deleted in next step. This step was just added later.
 *
 * @author Viktor Alexander Hartung
 */
public class SuperPosition extends LinearNetwork {

    private static final Logger LOGGER = Logger.getLogger(
            SuperPosition.class.getName());

    /**
     * Holds references for all created nodes on all layers. [layer
     * number][index in nodes list]. The array has paired nodes to the nodes
     * list.
     */
    private GeneralNode[][] layerNodes;

    /**
     * Holds references for all created elements on all layers. [layer
     * number][index in elements list]. The array has paired elements to the
     * elements list.
     */
    private AbstractElement[][] layerElement;

    /**
     * True for all effort sources that were replaced with a shorted connection
     * resistor element. If elements.get(index) returns an effort source, this
     * array can be used to determine if it is not present in a certain layer
     * number. Usage: [layer number][index in elements] returns true for each
     * source that is not present in layer with the given number.
     */
    private boolean[][] layerElementReplacedEffortSource;

    /**
     * True for all flow sources that were replaced with a open connection
     * resistor element. If elements.get(index) returns a flow source, this
     * array can be used to determine if it is not present in a certain layer
     * number. Usage: [layer number][index in elements] returns true for each
     * source that is not present in layer with the given number.
     */
    private boolean[][] layerElementReplacedFlowSource;

    /**
     * Reference to overlays which were created by this superposition algorithm.
     */
    private Overlay[] layer;

    /**
     * Saves the index for elements.get(index) for each layer[layer-number].
     * Will hold the index of the only present source for each given layer.
     */
    private int[] soleSource;

    private boolean[] zeroValue;

    /**
     * All layers can be calculated parallel in multiple threads. If set to
     * false, all layers will be calulated using a single thread for loop. As
     * creating threads is costly, single thread seems to be faster at the time
     * of writing.
     */
    private final boolean useMultithreading = true;

    /**
     * Holds the runnable object which is given to a thread to perform
     * calculation when using multithreading.
     */
    private List<Callable<Void>> layerCalculationCallables;

    private static ExecutorService threadPool;

    int numberOfSources = 0;

    /**
     * Transfers all prameters from this network to all layer networks. This
     * will update all resistance values, transfering a new state of the network
     * throug all childs of the simplification later on. Used to prepare for a
     * new calculation cycle.
     */
    @Override
    public void prepareCalculation() {
        int idx, jdx;

        // reset elements of the network itself first.
        super.prepareCalculation();

        // transfer all values from parent to layer childs
        for (idx = 0; idx < numberOfSources; idx++) {
            for (jdx = 0; jdx < elements.size(); jdx++) {
                // iterate through layers through all elements 
                if (elements.get(jdx).getElementType()
                        != layerElement[idx][jdx].getElementType()
                        && (elements.get(jdx).getElementType()
                        == ElementType.FLOWSOURCE
                        || elements.get(jdx).getElementType()
                        == ElementType.EFFORTSOURCE)) {
                    // Skip replaced sources, they will be of a different
                    // element types when comparing the network with the
                    // individual layer.
                    continue;
                }

                switch (elements.get(jdx).getElementType()) {
                    case DISSIPATOR:
                        ((LinearDissipator) layerElement[idx][jdx])
                                .setResistanceParameter(
                                        ((LinearDissipator) elements.get(jdx))
                                                .getResistance());
                        break;
                    case OPEN:
                        ((LinearDissipator) layerElement[idx][jdx])
                                .setOpenConnection();
                        break;
                    case BRIDGED:
                        ((LinearDissipator) layerElement[idx][jdx])
                                .setBridgedConnection();
                        break;

                    case FLOWSOURCE:
                        ((FlowSource) layerElement[idx][jdx]).setFlow(
                                ((FlowSource) elements.get(jdx)).getFlow());
                        break;

                    case EFFORTSOURCE:
                        ((EffortSource) layerElement[idx][jdx]).setEffort(
                                ((EffortSource) elements.get(jdx)).getEffort());
                        break;
                }
            } // end for jdx elements
        } // end for idx layers

        for (idx = 0; idx < numberOfSources; idx++) {
            layer[idx].prepareCalculation();
        }
    }

    @Override
    public void doCalculation() {
        int idx, jdx;
        double flow, effort, value;
        LinearDissipator r;
        FlowThrough target;
        // Call calculation for each child network. This needs to be successful
        // to be able to use those results for superposition. But: Networks that
        // have either a flow or an effort value of exactly 0.0 do not need any
        // calculation, so check the value before fully firing the calculation. 
        for (idx = 0; idx < numberOfSources; idx++) {
            switch (elements.get(soleSource[idx]).getElementType()) {
                case FLOWSOURCE:
                    value = ((FlowSource) elements
                            .get(soleSource[idx])).getFlow();
                    break;
                case EFFORTSOURCE:
                    value = ((EffortSource) elements
                            .get(soleSource[idx])).getEffort();
                    break;
                default:
                    throw new ModelErrorException("Source must either be "
                            + "of flow or effort source type.");
            }
            // save here, will be used for assignment later
            zeroValue[idx] = value == 0.0;
            if (zeroValue[idx]) {
                ((LayerCalculationCall) layerCalculationCallables
                        .get(idx)).setSkip(true);
            }
        }

        if (threadPool != null && layerCalculationCallables.size() > 0) {
            List<Future<Void>> futures;
            try {
                futures = threadPool.invokeAll(layerCalculationCallables);
                for (Future<Void> f : futures) {
                    f.get(); // Exceptions abfangen
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, (String) null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, (String) null, ex);
            }

        } else {
            for (idx = 0; idx < numberOfSources; idx++) {
                if (!zeroValue[idx]) {
                    layer[idx].doCalculation();
                }
            }
        }

        for (idx = 0; idx < numberOfSources; idx++) {
            if (!zeroValue[idx]) {
                if (!layer[idx].isCalculationFinished()) {
                    LOGGER.log(Level.WARNING, "Superposition layer index " + idx
                            + " failed to provide full solution.");
                    // assume this layer to be not calculated due to a source
                    // value of 0, this will skip this layer on the for loop to
                    // sum it up. This is cheaper than having to call the more
                    // expensive isCalculationFinished method all the time.
                    zeroValue[idx] = true;
                }
            }
        }
        // Layer results are now available. update the elements assigned to this
        // solver first to avoid assignment conflicts like forcing efforts or
        // flows on effort or flow sources.
        iterativeSolver.doCalculationOnEnforcerElements();

        // iterate over all elements of this network, get flow for each resistor
        // from all layers
        for (idx = 0; idx < elements.size(); idx++) {
            if (!isElementResistorType(elements.get(idx))) {
                continue;
            }
            // sum up flow on each layer
            flow = 0;
            for (jdx = 0; jdx < numberOfSources; jdx++) {
                if (zeroValue[jdx]) {
                    continue; // the result of getFlow would be 0.0
                }
                r = (LinearDissipator) layer[jdx].getElement(idx);
                flow = flow + r.getFlow();
            }
            // Assign the flow to the elment of the superposition network.
            // It may not be a resistor as the network provided may use
            // different elements.
            target = (FlowThrough) elements.get(idx); // get the target resist
            target.setFlow(flow, true); // assign superpositioned value
        }

        // this should be enough to solve the whole network
        iterativeSolver.doCalculation();

        // Remaining non-updated effort values on nodes are most likely a
        // result of floating nodes which are temporarily not connected to
        // any source. The solvers solving the layers provide means to handle
        // those issues, so we will use some similar tricks here to at least
        // generate values.
        for (idx = 0; idx < nodes.size(); idx++) {
            if (!nodes.get(idx).effortUpdated()) {
                effort = 0;
                for (jdx = 0; jdx < numberOfSources; jdx++) {
                    // if the effort or flow value on the layer was zero, it
                    // was not calculated and there is no effort to get. it 
                    // would be zero anyway so we can skip it.
                    if (zeroValue[jdx]) {
                        continue;
                    }
                    // Sum up all efforts
                    effort += layerNodes[jdx][idx].getEffort();
                }
                if (numberOfSources == 0) {
                    // This can happen if there is no source in subnet network,
                    // mostly during development and testing.
                    nodes.get(idx).setEffort(0.0);
                } else {
                    nodes.get(idx).setEffort(effort / numberOfSources);
                }

            }
        }

        // in case of we had non-updated efforts, we need to update the
        // model again to get a full solution.
        iterativeSolver.doCalculation();

        if (!iterativeSolver.isCalculationFinished()) {
            LOGGER.log(Level.WARNING,
                    "Iterative solver did not finish with full solution.");
        }

        // Check that the sum of all flows to all nodes is zero or near the
        // zero double value. 
        for (GeneralNode p : nodes) {
            if (p.allFlowsUpdated()) {
                flow = 0;
                for (idx = 0; idx < p.getNumberOfElements(); idx++) {
                    flow = flow + p.getFlow(idx);
                }
                // For some reason this fired off after changing the chornobyl
                // simulation from m^3 to kg/s, so the check range was changed
                // from 1e-11 to 1e-5. Still unknown why that happens.
                // if (flow > 1e-11 || flow < -1e-11) {
                // with growing model, it was set to 1e-2. This is not good.
                if (flow > 1e-3 || flow < -1e-3) {
                    LOGGER.log(Level.WARNING,
                            "High deviation on expected flow sum on node.");
                }
            } else {
                LOGGER.log(Level.WARNING,
                        "No full solution, missing flow on node!");
            }

        }
    }

    /**
     * Generate all layers with individual elements. Intended to be called after
     * all nodes and elements were added. This finalizes the model setup and
     * gets the object to a state where the calculation methods can be invoked
     * to get a solution.
     *
     * This will create one layer for each source element that is included in
     * the original network, each layer will then be passed to the recursive
     * simplifier solver to break them down to a network that can be solved.
     *
     * @return Number of created layers
     */
    @SuppressWarnings("unchecked")
    public int superPositionSetup() {
        int idx;
        int jdx;
        numberOfSources = 0;
        for (AbstractElement e : elements) {
            if (e.getElementType() == ElementType.EFFORTSOURCE
                    || e.getElementType() == ElementType.FLOWSOURCE) {
                numberOfSources++;
            }
        }

        // allocate memory
        layerNodes = new GeneralNode[numberOfSources][nodes.size()];
        layerElement = new AbstractElement[numberOfSources][elements.size()];
        layerElementReplacedEffortSource
                = new boolean[numberOfSources][elements.size()];
        layerElementReplacedFlowSource
                = new boolean[numberOfSources][elements.size()];
        layer = new Overlay[numberOfSources];
        soleSource = new int[numberOfSources];
        layerCalculationCallables = new ArrayList<>(numberOfSources);
        zeroValue = new boolean[numberOfSources];

        idx = 0;
        for (AbstractElement e : elements) {
            if (e.getElementType() == ElementType.EFFORTSOURCE
                    || e.getElementType() == ElementType.FLOWSOURCE) {
                // set which source should be kept on each layer
                soleSource[idx] = elements.indexOf(e);
                idx++;
            }
        }

        if (numberOfSources >= 1) {
            LOGGER.log(Level.INFO,
                    "Setting up SuperPosition solver...");
        }

        // generate one full network for each source.
        for (idx = 0; idx < numberOfSources; idx++) {
            layer[idx] = new Overlay();

            // generate the nodes
            for (jdx = 0; jdx < nodes.size(); jdx++) {
                layerNodes[idx][jdx] = new GeneralNode(
                        PhysicalDomain.ELECTRICAL);
                layerNodes[idx][jdx].setName("L" + idx + "-"
                        + nodes.get(jdx).toString().split("@")[0]);
                layer[idx].registerNode(layerNodes[idx][jdx]);
            }

            // generate elements, each time just one source
            for (jdx = 0; jdx < elements.size(); jdx++) {
                switch (elements.get(jdx).getElementType()) {
                    case DISSIPATOR:
                    case OPEN:
                    case BRIDGED:
                        layerElement[idx][jdx] = new LinearDissipator(
                                PhysicalDomain.ELECTRICAL);
                        layerElement[idx][jdx].setName(
                                "L" + idx + "-"
                                + elements.get(jdx).toString().split("@")[0]);
                        break;

                    case FLOWSOURCE:
                        if (soleSource[idx] == jdx) {
                            layerElement[idx][jdx] = new FlowSource(
                                    PhysicalDomain.ELECTRICAL);
                            layerElement[idx][jdx].setName(
                                    "L" + idx + "-"
                                    + elements.get(jdx).toString()
                                            .split("@")[0]);
                            layer[idx].setName("Layer-"
                                    + elements.get(jdx).toString());
                        } else {
                            // Replace flow source with open connection
                            layerElement[idx][jdx] = new LinearDissipator(
                                    PhysicalDomain.ELECTRICAL);
                            ((LinearDissipator) layerElement[idx][jdx])
                                    .setOpenConnection();
                            layerElement[idx][jdx].setName(
                                    "L" + idx + "-"
                                    + elements.get(jdx).toString()
                                            .split("@")[0] + "_open");
                            layerElementReplacedFlowSource[idx][jdx] = true;
                        }
                        break;

                    case EFFORTSOURCE:
                        if (soleSource[idx] == jdx) {
                            // This is the source that will remain in model
                            layerElement[idx][jdx] = new EffortSource(
                                    PhysicalDomain.ELECTRICAL);
                            layerElement[idx][jdx].setName(
                                    "L" + idx + "-"
                                    + elements.get(jdx).toString()
                                            .split("@")[0]);
                            layer[idx].setName("Layer-"
                                    + elements.get(jdx).toString());
                        } else {
                            // Replace effort source with bridged resistor
                            layerElement[idx][jdx] = new LinearDissipator(
                                    PhysicalDomain.ELECTRICAL);
                            ((LinearDissipator) layerElement[idx][jdx])
                                    .setBridgedConnection();
                            layerElement[idx][jdx].setName(
                                    "L" + idx + "-"
                                    + elements.get(jdx).toString()
                                            .split("@")[0] + "_bridged");
                            layerElementReplacedEffortSource[idx][jdx] = true;
                        }
                        break;

                    case ORIGIN:
                        layerElement[idx][jdx] = new ClosedOrigin(
                                PhysicalDomain.ELECTRICAL);
                        layerElement[idx][jdx].setName(
                                "L" + idx + "-"
                                + elements.get(jdx).toString().split("@")[0]);
                        break;
                }
            } // end for - creating new elements for layer

            // connect elements as they are connected in original network
            for (jdx = 0; jdx < elements.size(); jdx++) {
                layerElement[idx][jdx].registerNode(layerNodes[idx][node0idx[jdx]]);
                layerNodes[idx][node0idx[jdx]].registerElement(
                        layerElement[idx][jdx]);
                if (node1idx[jdx] >= 0) {
                    layerElement[idx][jdx].registerNode(layerNodes[idx][node1idx[jdx]]);
                    layerNodes[idx][node1idx[jdx]].registerElement(
                            layerElement[idx][jdx]);
                }
            }

            // add those connected elements to overlay
            for (jdx = 0; jdx < elements.size(); jdx++) {
                layer[idx].registerElement(layerElement[idx][jdx],
                        layerElementReplacedFlowSource[idx][jdx],
                        layerElementReplacedEffortSource[idx][jdx]);
            }

            // simplify layers to prepare solving
            layer[idx].overlaySetup();
        } // end for idx all layers

        LOGGER.log(Level.INFO, "... SuperPosition set up with "
                + numberOfSources + " layers.");

        // Generate optinal callables for mutltithreading as list
        for (idx = 0; idx < numberOfSources; idx++) {
            layerCalculationCallables.add(new LayerCalculationCall<Void>(idx));
        }

        return numberOfSources;
    }

    public boolean containsElement(AbstractElement element) {
        return elements.contains(element);
    }

    /**
     * All layers can be calculated parallel in multiple threads if a thread
     * pool is provided. If not set, all layers will be calculated using a
     * single call for loop.
     *
     * @param pool SingleThreadExecutor pool.
     */
    public static void setThreadPool(ExecutorService pool) {
        threadPool = pool;
    }

    class LayerCalculationCall<Void> implements Callable {

        int idx;
        boolean skip;

        public LayerCalculationCall(int idx) {
            this.idx = idx;
        }

        public void setSkip(boolean skip) {
            this.skip = skip;
        }

        @Override
        public Object call() throws Exception {
            if (!skip) {
                layer[idx].doCalculation();
            }
            skip = false;
            return null;
        }
    }
}
