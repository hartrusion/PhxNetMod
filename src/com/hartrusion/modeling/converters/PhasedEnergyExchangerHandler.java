/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung.
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
package com.hartrusion.modeling.converters;

/**
 * Implemented by the no mass thermal exchanger for phased fluids and other
 * counterparts implementing this simplified heat exchange method.
 *
 * @author Viktor Alexander Hartung
 */
public interface PhasedEnergyExchangerHandler {

    public boolean isNodeStateMet();

    public boolean isNoFlowCondition();

    /**
     * Calculates the maximum possible heat energy that can be transferred with
     * the current mass flow of the assigned element and the given absolute
     * temperature. Positive means the energy goes into the element to which
     * this method is attached to.
     *
     * @return J/s (Watt)
     */
    public double getMaxEnergyDelta(double otherTemperature);

    /**
     * Sets the transferred power that this element will RECEIVE. Negative
     * values will be a loss of power.
     *
     * @param power J/s (Watt)
     */
    public void setPowerTransfer(double power);

    /**
     * Sets that this element will not receive any power.
     */
    public void setNoPowerTransfer();

    /**
     * Sets a reference to a handler that also implements this interface to
     * connect both handlers.
     *
     * @param otherSide
     */
    public void setOtherSide(PhasedEnergyExchangerHandler otherSide);

    /**
     * Returns the temperature at the inlet in kelvin.
     *
     * @return K
     */
    public double getInTemperature();
    
    public void setEfficency(double efficiency);

}
