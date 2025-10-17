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
public interface SteamHandler {

    /**
     * Register a steam port with this steam handler. This is usually called
     * from the element having a heat handler when registering ports, as the
     * element is extended by the capability of having steam ports.
     *
     * @param sp
     * @return
     */
    public boolean registerSteamPort(SteamNode sp);

    /**
     * As for general elements, this will prepare a calculation run inside the
     * steam handler. It will reset all known-flags for steam properties. Will
     * be called by the element if prepareCalculation is called.
     *
     * <p>
     * The usual consequence of this is a reset of all known-flags on all
     * connected steam ports regarding steam properties.
     *
     * <p>
     * This gets called from the elements prepareCalulation method which is
     * using this steam handler.
     */
    public void prepareSteamCalculation();

    /**
     * Tries to calculate as much as possible regarding the steam domain. This
     * only works if there are all required values are in a calculated, known
     * state.
     *
     * <p>
     * This gets called from the elements doCalculation method which is using
     * this steam handler.
     *
     * @return true, if the calculation was able to calculate something.
     */
    public boolean doSteamCalculation();

    /**
     * Checks if this handler has calculated everything he can.
     *
     * <p>
     * This gets called from the elements isCalulationFinished method which is
     * using this steam handler. It will be added to the result to provide the
     * information regarding the completeness of the calculation.
     *
     * @return true if all variables are known.
     */
    public boolean isSteamCalulationFinished();

    /**
     * Set steam properties of element which has this handler assigned.This is
     * usually done to set initial conditions of the model during setup, its not
     * possible to steam properties upon a heated mass instantly.<p>
     * Does only have an effect if the steam handler instance actually supports
     * it, otherwise it may throw an exeption.
     *
     * @param index: 0: temperature, 1: enthalpy, 2: entropy, 3: vapour fraction
     * @param value
     */
    public void setSteamProperty(int index, double value);

    /**
     * Return steam properties of element which has this handler assigned.Does
     * only have an effect if the steam handler instance actually supports it,
     * otherwise it may throw an exeption. Supporting means that the element
     * which this is used on has its own volume. If not, use the connected
     * ports.
     *
     * @param index 0: temperature, 1: enthalpy, 2: entropy, 3: vapour fraction
     * @return requested state
     */
    public double getSteamProperty(int index);

    /**
     * Sets toe mass that is present in the parent element to the steam handler.
     * Only used when the handler does handle masses, otherwise it should throw
     * an exception.
     *
     * @param mass kg
     */
    public void setTotalMass(double mass);
    
    /**
     * Returns the mass inside the parent element of the steam handler.
     * Only used when the handler does handle masses, otherwise it should throw
     * an exception.
     *
     * @return kg
     */
    public double getTotalMass();
}
