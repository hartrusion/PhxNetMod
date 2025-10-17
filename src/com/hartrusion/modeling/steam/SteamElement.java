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
package com.hartrusion.modeling.steam;

/**
 *
 * @author Viktor Alexander Hartung
 */
public interface SteamElement {

    /**
     * Set the effort from a connected port.This method is meant to perform a
 set-only thing, they will eventually pass through this element and
 continue setting to other elements but they will never trigger a
 calculation. As effort will be saved in ports, it might not have any
 consequence at all.
     *
     * @param effort Absolute pressure in Pa (Effort variable(
     * @param source SteamNode calling the function to identify source
     */
    public void setEffort(double effort, SteamNode source);

    /**
     * Set the flow from a connected port.As flow has a direction, it is a
 signed value. This mehtod is meant to perform a set-only thing, they will
 eventually pass through this element and continue setting to other
 elements but they will never trigger a calculation.
     *
     * @param flow Mass flow in kg/s. Positive: flows into this element and
     * comes from port. negative: flows out of this element towards port.
     * @param source SteamNode calling the function to identify source
     */
    public void setFlow(double flow, SteamNode source);

    /**
     * Get reference to instance which implements steam handler which is used in
     * this steam element. This is not used by the model itself, but can be of
     * use for external analysis and it forces the elements to implement the
     * method, marking the fact that all steam elements need to have a handler.
     *
     * <p>
     * Most of steam calculations will be done by the steam handlder so there is
     * no need to add more phased fluid specific methods on the steam element
     * class.
     *
     * <p>
     * Access to the steam handler can be used to observe model details, like
     * simply get the temperature or vapour fraction.
     *
     * <p>
     * The main reason behind this method is to force all heat elements to have
     * a heat handler.
     *
     * @return reference to object which implements the heat handler interface
     */
    public SteamHandler getSteamHandler();

    /**
     * Returns the instance of the used steam table that was used when
     * constructing the element. This is intended to be used by steam handlers
     * which work kind of inside the element handling the steam properties to
     * have access to the steam tables calculation methods.
     *
     * @return SteamTable
     */
    public SteamTable getSteamTable();
    
    public SteamNode getSteamNode(int idx);
}
