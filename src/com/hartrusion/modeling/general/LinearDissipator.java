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

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dissipates energy by transforming it to excess heat. Requires exactly two
 * nodes to be used. Does not store energy and therefore has no state space
 * variable. Allows different effort values on each node and has same flow value
 * on both nodes.
 *
 * Linear: effort = flow * resistance;
 *
 * As the effort rises with positive flow the direction has to be into this
 * element for positive flow values.
 *
 * @author Viktor Alexander Hartung
 */
public class LinearDissipator extends FlowThrough {

    private static final Logger LOGGER = Logger.getLogger(
            LinearDissipator.class.getName());

    protected double resistance = 10.0; // main parameter
    private double externalDeltaEffort;
    private boolean externalDeltaEffortKnown;

    /**
     * Creates an instance of a linear dissipator with a given resistance value,
     * acting as an energy dissipating element.
     *
     * @param domain Physical Domain
     */
    public LinearDissipator(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.DISSIPATOR;
    }

    /**
     * Creates an instance of a linear dissipator which will either be a
     * shortcut or an open connection
     *
     * @param isShortcut true for shortcuts, false for open connection.
     */
    public LinearDissipator(PhysicalDomain domain, boolean isShortcut) {
        super(domain);
        if (isShortcut) {
            resistance = 0.0;
            elementType = ElementType.BRIDGED;
        } else {
            resistance = Double.POSITIVE_INFINITY;
            elementType = ElementType.OPEN;
        }
    }

    public void setResistanceParameter(double r) {
        if (r <= 0.0) {
            throw new ModelErrorException("Resistance must be a positive "
                    + "value, otherwise set as open or bridged connection.");
        }
        if (!Double.isFinite(r)) {
            throw new ModelErrorException("Resistance must be a finite value, "
                    + "use set as open or bridged connection for infinity.");
        }
        elementType = ElementType.DISSIPATOR;
        resistance = r;
    }

    public void setConductanceParameter(double g) {
        if (!Double.isFinite(g)) {
            throw new ModelErrorException("Conductance must be a finite value, "
                    + "use set as open for infinity.");
        }
        if (g <= 0.0) { // allow less than 0.0 due to numeric errors
            resistance = Double.POSITIVE_INFINITY;
            elementType = ElementType.OPEN;
            return;
        }
        elementType = ElementType.DISSIPATOR;
        resistance = 1.0 / g;
    }

    public void setOpenConnection() {
        resistance = Double.POSITIVE_INFINITY;
        elementType = ElementType.OPEN;
    }

    public void setBridgedConnection() {
        resistance = 0.0;
        elementType = ElementType.BRIDGED;
    }

    /**
     * Returs resistance value. Resistance = Effort / Flow.
     *
     * @return Resistance value
     */
    public double getResistance() {
        return resistance;
    }

    /**
     * Calculates and returns conductance value. Conductance = 1/Resistance =
     * Flow / Effort.
     *
     * @return
     */
    public double getConductance() {
        if (elementType == ElementType.OPEN) {
            return 0;
        } else if (elementType == ElementType.BRIDGED) {
            return Double.POSITIVE_INFINITY;
        } else {
            return 1 / resistance;
        }
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        externalDeltaEffortKnown = false;
    }

    @Override
    public boolean doCalculation() {
        boolean retVal = calculateOhmsLaw();
        double diff;
        // Perfom some additional checks to validate results
        if (elementType == ElementType.OPEN) {
            if (nodes.get(0).flowUpdated(this)
                    && nodes.get(1).flowUpdated(this)) {
                if (nodes.get(0).getFlow(this) > 1e-6
                        || nodes.get(0).getFlow(this) < -1e-6 // was 1e-10
                        || nodes.get(1).getFlow(this) > 1e-6
                        || nodes.get(1).getFlow(this) < -1e-6) {
                    //throw new CalculationException(
                    //         "A node contains flow to an open Element.");
                    LOGGER.log(Level.WARNING, "A node contains flow to this "
                            + "open Element. Forcing 0.0");
                    nodes.get(0).setFlow(0.0, this, false);
                    nodes.get(1).setFlow(0.0, this, false);
                }
            }
        } else if (elementType == ElementType.BRIDGED) {
            if (nodes.get(0).effortUpdated()
                    && nodes.get(1).effortUpdated()) {
                diff = Math.abs(nodes.get(0).getEffort()
                        - nodes.get(1).getEffort());
                if (diff > 1e-2) {
                    // update 1: reduce diff from 1e-8 to 1e-3.
                    // update 2: reducig from 1e-3 to 1e-2 now, seems to be a 
                    // real issue during valve opening and closing O.o
                    // update 3: remove Exception, log a warning instead.
                    // throw new CalculationException(
                    //        "Different effort on nodes of bridged element.");
                    // update 4: The issue comes from Overlay class and the
                    // simplificationt there not to be compatible with the
                    // superposition solver. This was fixed by adding a small
                    // workaround in overlay.
                    // update 5: This workaround is not good, let's add the
                    // flow too. The issue only happens if there is zero flow 
                    // and due to the zero flow, the overlay made some bad
                    // simplification.
                    if (nodes.get(0).getFlow(this) > 1e-6
                            || nodes.get(0).getFlow(this) < -1e-6
                            || nodes.get(1).getFlow(this) > 1e-6
                            || nodes.get(1).getFlow(this) < -1e-6) {
                        LOGGER.log(Level.WARNING, "Different effort on nodes "
                                + "of bridged element: " + diff);
                    }
                }
            }
        }
        return retVal;
    }

    @Override
    public void setEffort(double effort, GeneralNode source) {
        calculateOhmsLaw();
    }

    private boolean calculateOhmsLaw() {
        boolean didSomething = false;
        double deltaEffort;
        double myFlow;
        if (nodes.size() != 2) {
            throw new ModelErrorException(
                    "Dissipator must have exactly two nodes for calculation");
        } else if (isCalculationFinished()) {
            return false; // nothing to do.
        } else {
            // basically it is flow = delta of efforts / resistance. this means
            // 2 of 3 need to be known to calculate the third.
            if (elementType == ElementType.OPEN) {
                // Special occasion first: Open Element means no flow and no
                // enforcing of efforts possible anyhow.
                if (!nodes.get(0).flowUpdated(this)) {
                    nodes.get(0).setFlow(0.0, this, true);
                }
                if (!nodes.get(1).flowUpdated(this)) {
                    nodes.get(1).setFlow(0.0, this, true);
                }
            } else if (nodes.get(0).getNumberOfElements() == 1
                    || nodes.get(1).getNumberOfElements() == 1) {
                // Another special occasion: One node on one side is a dead end.
                // There will be no flow through the resistor then.
                if (!nodes.get(0).flowUpdated(this)) {
                    nodes.get(0).setFlow(0.0, this, true);
                }
                if (!nodes.get(1).flowUpdated(this)) {
                    nodes.get(1).setFlow(0.0, this, true);
                }
                // Set the dead end effort to the same value as the other node.
                if (nodes.get(1).effortUpdated()
                        && !nodes.get(0).effortUpdated()
                        && nodes.get(0).getNumberOfElements() == 1) {
                    nodes.get(0).setEffort(nodes.get(1).getEffort());
                } else if (nodes.get(0).effortUpdated()
                        && !nodes.get(1).effortUpdated()
                        && nodes.get(1).getNumberOfElements() == 1) {
                    nodes.get(1).setEffort(nodes.get(0).getEffort());
                }
            } else if (nodes.get(0).flowUpdated(this)
                    && nodes.get(0).effortUpdated()
                    && !nodes.get(1).effortUpdated()) {
                // case1: flow and effort on node 0 known
                deltaEffort = nodes.get(0).getFlow(this) * resistance;
                nodes.get(1).setEffort(nodes.get(0).getEffort() - deltaEffort, this, true);
                return true;
            } else if (nodes.get(0).flowUpdated(this)
                    && nodes.get(1).effortUpdated()
                    && !nodes.get(0).effortUpdated()) {
                // case2: flow and effort on node 1 known
                deltaEffort = nodes.get(1).getFlow(this) * resistance;
                nodes.get(0).setEffort(nodes.get(1).getEffort() - deltaEffort, this, true);
                return true;
            } else if (nodes.get(0).effortUpdated()
                    && nodes.get(1).effortUpdated()
                    && !nodes.get(0).flowUpdated(this)
                    && !nodes.get(1).flowUpdated(this)
                    && elementType != ElementType.BRIDGED) {
                // case 3: both efforts are given, flow is missing
                deltaEffort = nodes.get(1).getEffort()
                        - nodes.get(0).getEffort();

                myFlow = deltaEffort / resistance;

                nodes.get(0).setFlow(-myFlow, this, true);
                nodes.get(1).setFlow(myFlow, this, true);
                return true;
            }

            // additionally, if we somehow got the delta of efforts , we can
            // calculate flow even without knowing absolute effort values.
            if (externalDeltaEffortKnown && !nodes.get(0).flowUpdated(this)) {
                myFlow = externalDeltaEffort / resistance;
                nodes.get(0).setFlow(myFlow, this, true);
                nodes.get(1).setFlow(-myFlow, this, true);
                didSomething = true;
            }
            // if only one of both efforts are known but a delta is known,
            // it is possible to set the missing one.
            if (externalDeltaEffortKnown) {
                if (nodes.get(0).effortUpdated()
                        && !nodes.get(1).effortUpdated()) {
                    nodes.get(1).setEffort(nodes.get(0).getEffort()
                            - externalDeltaEffort, this, true);
                    didSomething = true;
                } else if (nodes.get(1).effortUpdated()
                        && !nodes.get(0).effortUpdated()) {
                    nodes.get(0).setEffort(nodes.get(1).getEffort()
                            + externalDeltaEffort, this, true);
                    didSomething = true;
                }
            }
        }
        return didSomething;
    }

    /**
     * Sets a known delta between the efforts of both nodes to be used for
     * calculation. The sign is according to the flow of super class
     * FlowThrough, meaning positive flow value on this element means: flow into
     * node 0, out of node 1, therefore positive effort means that effort on
     * node 0 is higher than on node 1
     *
     * <p>
     * This is used to provide information to the model for next steps of
     * calculation.
     *
     * @param e
     */
    public void setExternalDeltaEffort(double e) {
        externalDeltaEffort = e;
        externalDeltaEffortKnown = true;
    }

    @Override
    public void setStepTime(double dt) {
        // this class does not use steptime
    }

}
