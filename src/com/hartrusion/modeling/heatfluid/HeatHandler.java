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
 * A heat handler is helper class which is used by elements, giving those
 * elements the ability to handle temperatures. This interface provides methods
 * that a heat handler has to offer, those will be called from the elements to
 * perform calculations.
 *
 * <p>
 * Usually a heat handler has a list of heat nodes, serving as an extension to
 * an element handling the temperatures assigned to the element.
 *
 * <p>
 * The main thing for a heat handler to do is to calculate temperatures of flows
 * that are leaving the element by checking if all incoming flows have known
 * temperatures assigned. The calculation has to start somewhere on elements
 * which have a flow leaving the element with a known temperature and will be
 * completed if all temperatures are known. Depending on the flow directions,
 * this can require a different number of iterations for the same network.
 *
 * <p>
 * Note that there is a strict no-redundancy-policy throughout this whole
 * project. The heat handler is an extension for an element. There are elements
 * which do not have their own variables when all the information is already
 * known in the connected nodes. This can be true for temperatures, for example,
 * a pressure source does only force an effort difference and it does not have
 * any thermal capacity, therefore it can not have a temperature itself. Only
 * the connected nodes have temperatures. In such cases, methods like
 * getTemperature will throw an exception as the element does not have its own
 * temperature.
 *
 * @author Viktor Alexander Hartung
 */
public interface HeatHandler {

    /**
     * Register a thermal node with this thermal handler.This is usually called
     * from the element having a heat handler when registering nodes, as the
     * element is extended by the capability of having heat nodes.
     *
     * @param hn
     * @return
     */
    public boolean registerHeatNode(HeatNode hn);

    /**
     * As for general elements, this will prepare a calculation run inside the
     * heat handler. It will reset all known-flags for temperature properties.
     * Will be called by the element if prepareCalculation is called.
     *
     * <p>
     * The usual consequence of this is a reset of all known-flags on all
     * connected heat nodes regarding heat properties.
     *
     * <p>
     * This gets called from the elements prepareCalulation method which is
     * using this steam handler.
     *
     */
    public void prepareHeatCalculation();

    /**
     * Tries to calculate as much as possible regarding the temperature. This
     * only works if there are all required values are in a calculated, known
     * state.
     *
     * <p>
     * This gets called from the elements doCalculation method which is using
     * this heat handler.
     *
     * @return true, if the calculation was able to calculate something.
     */
    public boolean doThermalCalculation();

    /**
     * Checks if this handler has calculated everything he can.
     *
     * <p>
     * This gets called from the elements isCalulationFinished method which is
     * using this heat handler. It will be added to the result to provide the
     * information regarding the completeness of the calculation.
     *
     * @return true if all variables are known.
     */
    public boolean isHeatCalulationFinished();

    /**
     * Set Temperature of element which has this handler assigned. This is
     * usually done to set initial conditions of the model during setup, its not
     * possible to force a temperature upon a heated mass instantly.
     *
     * <p>
     * Does only have an effect if the heat handler instance actually supports
     * it, otherwise it may throw an exeption.
     *
     * @param t new temperature value
     */
    public void setInitialTemperature(double t);

    /**
     * Return Temperature of element which has this handler assigned. Does only
     * have an effect if the heat handler instance actually supports it,
     * otherwise it may throw an exeption. Supporting means that the element
     * which this is used on has its own thermal mass. If not, use the
     * temperature of connected nodes or elements nearby.
     *
     * @return temperature value
     */
    public double getTemperature();

    public void setInnerThermalMass(double storedMass);
}
