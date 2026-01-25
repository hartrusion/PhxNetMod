/*
 * The MIT License
 *
 * Copyright 2026 Viktor Alexander Hartung
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
 * Defines methods for a no-mass thermal exchangers, those can be implemented by
 * heat or phased domain. They do not know the type of the other side and work
 * only with the temperature. Both handlers use the methods defined here to
 * interact with each other.
 *
 * @author Viktor Alexander Hartung
 */
public interface NoMassThermalExchanger {

    /**
     * To be called from the other heat handler of the other side of that heat
     * exchanger. Allows the calculation to be done by one handler only.
     *
     * @param outTemp
     */
    public void setOutTemperature(double outTemp);

    /**
     * Checks if all flow values from nodes toward the element which is assigned
     * to this handler are updated (flow updated and inbound temperature known).
     * Method is public so the other sides handler can call this one too.
     *
     * @return
     */
    public boolean isNodeStateMet();

    /**
     * Checks if the flow through the assigned element is zero, either by the
     * flow itself or by the assigned no-temperature condition.
     *
     * @return
     */
    public boolean isNoFlowCondition();

    /**
     * Requires the calculation to be in the corresponding state. Public as this
     * is also called from the other heat exchanger side.
     *
     * @return flow inbound in kg/s
     */
    public double getInFlow();

    /**
     * Requires the calculation to be in the corresponding state. Public as this
     * is also called from the other heat exchanger side.
     *
     * @return Temperature in K
     */
    public double getInTemperature();

    public void setOtherSideHandler(NoMassThermalExchanger otherSide);

    public double getSpecHeatCap();
}
