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
package com.hartrusion.modeling.heatfluid;

/**
 * All heat Elements have heat nodes and a heat handler instance. This interface
 * provides Interfaces to acces the heat handler, which can then be called via
 * casting to this interface. It is also required for heat elements to register
 * with heat nodes instead of normal nodes.
 *
 * <p>
 * Heatfluid extends hydraulic or pneumatic fluid, which is represented by
 * generalized components, in terms of temperature transportation and
 * temperature mixture.
 *
 * @author Viktor Alexander Hartung
 */
public interface HeatElement {

    /**
     * Set the effort from a connected node.This method is meant to perform a
     * set-only thing, they will eventually pass through this element and
     * continue setting to other elements but they will never trigger a
     * calculation. As effort will be saved in nodes, it might not have any
     * consequence at all.
     *
     * @param effort Effort set from node to this element
     * @param source HeatNode calling the function to identify source
     */
    public void setEffort(double effort, HeatNode source);

    /**
     * Set the flow from a connected node. As flow has a direction, it is a
     * signed value. This mehtod is meant to perform a set-only thing, they will
     * eventually pass through this element and continue setting to other
     * elements but they will never trigger a calculation.
     *
     * @param flow positive: flows into this element and comes from node.
     * negative: flows out of this element towards node.
     * @param source HeatNode calling the function to identify source
     */
    public void setFlow(double flow, HeatNode source);

    /**
     * Get reference to instance which implements heat handler which is used in
     * this heat element. The heat handler handles the overlaying heat on top of
     * the general element base fluid network, so everything regarding
     * temperature properties will be accessed through this heat handler
     * reference.
     *
     * <p>
     * Access to the heat handler can be used to observe model details, like
     * simply get the temperature, or set the initial conditions for the
     * elements heat properties.
     *
     * @return reference to the heat handler used by the thermal element.
     */
    public HeatHandler getHeatHandler();
}
