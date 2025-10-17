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
package com.hartrusion.modeling.general;

import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;

/**
 * Connects two or n Elements (which can be of any type) and holds effort and
 * flow variables. As effort and flow variables both are stored inside this
 * node, they will not be stored in Element objects separately. Note that flow
 * is also saved as a list as it is different for each connected element, while
 * effort is one value for all connected elements.
 *
 * <p>
 * This class does not only hold the flow and effort variables, it also saves
 * the corresponding update-flag marking that the value has been updated.
 *
 * <p>
 * The general idea behind this is to reset all update-flags for a new
 * calculation and then try to calculate only the remaining, missing items.
 *
 * @author Viktor Alexander Hartung
 */
public class GeneralNode {

    protected double effort; // on this node
    protected boolean effortUpdated;
    protected final List<AbstractElement> connectedElements = new ArrayList<>();

    private final List<FlowProperties> flowToElements = new ArrayList<>();

    /**
     * true, if there are only two elements connected to this node. This is not
     * needed here but classes extending this one are using that information.
     */
    protected boolean flowThrough;

    protected PhysicalDomain physicalDomain;

    private String nodeName;

    @SuppressWarnings("unused")
    private GeneralNode() { // do not allow constructor without parameters
    }

    public GeneralNode(PhysicalDomain domain) {
        physicalDomain = domain;
    }

    /**
     * Sets a unique name for this element. The name can be returned by using
     * the toString function of java.lang.object.
     *
     * @param name
     */
    public void setName(String name) {
        this.nodeName = name;
    }

    @Override
    public String toString() {
        if (nodeName == null) {
            return super.toString();
        } else {
            return nodeName;
        }
    }

    /**
     * Returns the physical domain this node was created to. The physical domain
     * must be identical for all connected nodes and elements and serves as a
     * validation for the model itself.
     *
     * @return PhysicalDomain
     */
    public PhysicalDomain getPhysicalDomain() {
        return physicalDomain;
    }

    /**
     * Registeres an element with this node. Used to set up the dynamic model.
     *
     * <p>
     * It will be checked that an element is not already registered to this
     * node. This registering is also heavily used by the automated solvers,
     * they are written in a way that should never register an element twice.
     * Such things only happen when there is some kind of illegal loop present
     * that was previously not detected as such a thing.
     *
     * <p>
     * It is advised to use proper efforts to set up the model, so checking for
     * correct registration does not allow to just randomly call registering
     * functions, this is an intended behaviour.
     *
     * @param el General element connected to this node
     */
    public void registerElement(AbstractElement el) {
        if (!connectedElements.contains(el)) {
            if (el.getPhysicalDomain() != physicalDomain
                    && el.getPhysicalDomain() != PhysicalDomain.MULTIDOMAIN) {
                throw new ModelErrorException("Node and element must have "
                        + " matching physical domain!");
            }
            connectedElements.add(el);
            flowToElements.add(new FlowProperties());
        } else {
            throw new ModelErrorException(
                    "Element already added to node!");
        }
        flowThrough = connectedElements.size() == 2;
    }

    /**
     * Resets state of effort and flow variables to not updated. This has to be
     * done before making any calculation to mark this node to be written with a
     * variable by upcoming calculations.
     */
    public void prepareCalculation() {
        effortUpdated = false;
        for (FlowProperties fp : flowToElements) {
            fp.setFlowUpdated(false);
        }
    }
    
    /**
     * Set effort value for this node and connected elements. Calling this
     * method will mark the effort as updated. Effort will be the same value on
     * each connected element.
     *
     * @param effort New effort value
     * @param source Element to identify which direction the value came from
     * @param update pass the effort to all other registered elements,
     * triggering a model update.
     */
    public void setEffort(double effort, AbstractElement source,
            boolean update) {
        if (effortUpdated) {
            throw new ModelErrorException(
                    "Tried to set an effort value that is already updated.");
        }
        if (!Double.isFinite(effort)) {
            throw new CalculationException(
                    "Non-finite effort value (NaN or inf) set to node.");
        }
        this.effort = effort;
        if (!update) {
            effortUpdated = true;
            return;
        }
        if (!effortUpdated) {
            effortUpdated = true; // prevent infinite loops
            for (AbstractElement e : connectedElements) {
                if (e == source) {
                    continue;
                }
                e.setEffort(effort, this);
            }
        }
    }

    /**
     * Manipulate the effort externally. This is used by some solvers if the
     * node needs to get its effort forced by some external calculation. This
     * will not trigger any updates and
     *
     * <p>
     * Use with extreme caution! This is only necessary for things which are out
     * of the usual modeling scope, like floating effort values that are
     * somewhat disconnected from the origin and do not have any coordinate
     * system.
     *
     * @param effort The effort that will be set on this node.
     */
    public void setEffort(double effort) {
        if (!Double.isFinite(effort)) {
            throw new CalculationException(
                    "Non-finite effort value (NaN or inf) set to node.");
        }
        this.effort = effort;
        effortUpdated = true; // prevent infinite loops
    }

    /**
     * Sets flow value TO this node from an element and sets flow to connected
     * elements. Sum of all flows into or from a node must be zero, so in order
     * to have all flows known, n-1 flow values need to be set from this method
     * to calculate the remaining one. If only two elements are connected, this
     * won't be complicated. To distribute flow from a capacitance object,
     * prefer connecting multiple nodes to the capacitance instead of using the
     * node to distribute it, making it forcing effort onto each node to keep
     * the model solveable.
     *
     * @param flow positive: flows out of this node into element. negative:
     * flows into this node from an element.
     * @param source Element to identify which direction the value came from
     * @param update true if the set operation shall trigger possible
     * calculations and update as many things as possible. Set to false to only
     * transfer values without triggering any update process.
     */
    public void setFlow(double flow, AbstractElement source, boolean update) {
        if (!Double.isFinite(flow)) {
            throw new CalculationException(
                    "Non-finite flow value (NaN or inf) set to node.");
        }
        AbstractElement e;
        FlowProperties fp;
        int sourceIdx = connectedElements.indexOf(source);
        int idx;
        double flowSum;

        // Save flow value from source object and mark it as updated.
        // The source element itself doesn't save the updated state, it's here.
        fp = flowToElements.get(sourceIdx);
        fp.setFlowValue(flow);
        if (fp.isFlowUpdated()) {
            return; // to prevent infinite loops, just accept new value
        }
        fp.setFlowUpdated(true);

        // check how many flow values are still unknown
        int unknownFlows = flowToElements.size();
        int idxUnknown = -1;
        for (idx = 0; idx < flowToElements.size(); idx++) {
            fp = flowToElements.get(idx);
            if (fp.isFlowUpdated()) {
                unknownFlows--;
            } else {
                // save for later use, if usable this got called only once
                idxUnknown = idx;
            }
        }
        // if there's just one remaining missing flow value, it can be
        // calculated as the sum of all flows has to be zero.
        if (unknownFlows == 1) {
            flowSum = 0;
            for (idx = 0; idx < flowToElements.size(); idx++) {
                if (idx == idxUnknown) {
                    continue;
                }
                flowSum = flowSum - flowToElements.get(idx).getFlowValue();
                // note the negative sign, cause f3 = -f1 -f2 -f4
            }
            // set to remaining node
            fp = flowToElements.get(idxUnknown);
            fp.setFlowUpdated(true);
            fp.setFlowValue(flowSum);

            // set at corresponding element
            e = connectedElements.get(idxUnknown);
            e.setFlow(flowSum, this);
        }
    }

    /**
     * Returns current effort variable value. Effort value represents energy
     * storage by having a potential, like inside a capacitive energy storage
     * element.
     *
     * @return effort
     */
    public double getEffort() {
        if (!effortUpdated) {
            throw new ModelErrorException("Tried to get "
                    + "non-updated effort value.");
        }
        return effort;
    }

    /**
     * Returns current flow variable value which comes from element e. Note that
     * a positive value means that the flow goes OUT of this node. Flow value
     * represents energy storage by having a flow motion, like a moving mass. It
     * can be stored in Inductance elements.
     *
     * @param e Element to which the flow value goes out
     * @return flow
     */
    public double getFlow(AbstractElement e) {
        if (!flowToElements.get(connectedElements.indexOf(e))
                .isFlowUpdated()) {
            throw new ModelErrorException("Tried to get "
                    + "non-updated flow value.");
        }
        return getFlow(connectedElements.indexOf(e));
    }

    /**
     * Returns current flow variable value by index. Used only inside class and
     * derivatives to get access to flowElements, outside request should always
     * use elements for identification as index is a totally internal thing.
     *
     * @param idx Index
     * @return flow
     */
    public double getFlow(int idx) {
        if (!flowToElements.get(idx).isFlowUpdated()) {
            throw new ModelErrorException("Tried to get "
                    + "non-updated flow value.");
        }
        return flowToElements.get(idx).getFlowValue();
    }

    /**
     * Returns weather the effort value has been updated for this node. Effort
     * value represents energy storage by having a potential, like inside a
     * capacitive energy storage element. The effort value is considered as
     * updated if setEffort is called after prepareCalculation was called.
     *
     * @return true, if effort was set.
     */
    public boolean effortUpdated() {
        return effortUpdated;
    }

    /**
     * Returns weather the effort value has been updated for this node for given
     * Element. Elements can call this method to see if the flow value on a node
     * is up to date.
     *
     * @param e Element to which the flow value goes out
     * @return true: Flow was updated
     */
    public boolean flowUpdated(AbstractElement e) {
        return flowToElements.get(connectedElements.indexOf(e)).isFlowUpdated();
    }

    /**
     * Returns weather all flow values connected to this nodes were updated. Flow
     * value represents energy storage by having a flow motion, like a moving
     * mass. It can be stored in Inductance elements. The flow value is
     * considered as updated if setFlow is called after prepareCalculation was
     * called.
     *
     * @return true, if flow was set.
     */
    public boolean allFlowsUpdated() {
        boolean allFlowsUpdated = true;
        for (FlowProperties fp : flowToElements) {
            allFlowsUpdated = allFlowsUpdated && fp.isFlowUpdated();
        }
        return allFlowsUpdated;
    }

    /**
     * Get the number of connected Elements to this node.
     *
     * @return Number of elements (2 means index 0 and 1 are available).
     */
    public int getNumberOfElements() {
        return connectedElements.size();
    }

    /**
     * Get an element with a specific index from this node. Usefull for doing
     * iterations over all elements connected to one node.
     *
     * @param idx index between 0...getNumberOfConnectedElements()-1
     * @return AbstractElement
     */
    public AbstractElement getElement(int idx) {
        return connectedElements.get(idx);
    }

    /**
     * Returns the only other element registered with this node.Works only if
     * this node has exactly two registered elements.
     *
     * @param e Element connected to this node
     * @return The only other element connected
     * @throws NoFlowThroughException
     */
    public AbstractElement getOnlyOtherElement(AbstractElement e)
            throws NoFlowThroughException {
        if (connectedElements.size() != 2) {
            throw new NoFlowThroughException(
                    "Can only return the only other element if there are "
                    + "exactly two elements registered.");
        }
        int idx = connectedElements.indexOf(e);
        if (idx <= -1) {
            throw new ModelErrorException(
                    "Provided element is not connected to node");
        }
        idx = -idx + 1; // switch 0 and 1
        return connectedElements.get(idx);
    }
}

/**
 * Holds properties for flow value. nodes have multiple connections for flow and
 * as it is different for each connection it has to be saved wether the value is
 * calulated and updated.
 *
 * @author Viktor Alexander Hartung
 */
class FlowProperties {

    private boolean flowUpdated;
    private double flowValue;

    public boolean isFlowUpdated() {
        return flowUpdated;
    }

    public void setFlowUpdated(boolean flowUpdated) {
        this.flowUpdated = flowUpdated;
    }

    public double getFlowValue() {
        return flowValue;
    }

    public void setFlowValue(double flowValue) {
        // Eliminate negative Zeros which can occur by having a sum of
        // zero flows where flow balance was calulated.
        if (flowValue == 0.0 && Double.compare(flowValue, 0.0) != 0) {
            flowValue = 0.0;
        }
        this.flowValue = flowValue;
    }
}
