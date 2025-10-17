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

import com.hartrusion.modeling.general.AbstractElement;

/**
 * Implements a heat handler as a connection to a steam element. Note that we
 * talk about heat fluid and not thermal here! This will allow the conversion
 * between heat and steam fluid. It is used by the SteamHeatFluidConverter on
 * the heat fluid side of the element.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatConnectionHandler implements HeatHandler {

    /**
     * The heat connection handler has only one port so we do not have an array
     * of ports here like other handlers do have.
     */
    private HeatNode port;

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final HeatElement element;

    public HeatConnectionHandler(HeatElement parent) {
        element = parent;
    }

    @Override
    public boolean registerHeatNode(HeatNode tp) {
        if (port == null) {
            port = tp;
            return true;
        }
        return false;
    }

    @Override
    public void prepareHeatCalculation() {
        // as this does not save any energy, there is nothing to do here
    }

    @Override
    public boolean doThermalCalculation() {
        return false;
    }

    /**
     * Gets called from SteamHeatFluidConverter and sets the heat fluid
     * properties to the connected steam port. This is the main thing this class
     * does.
     *
     * @param temperature steam temperature. All other props will be lost.
     */
    public void setTemperatureFromConverter(double temperature) {
        // temperature is the first property entry. everything else is lost.
        port.setTemperature(temperature, (AbstractElement) element);
    }

    @Override
    public boolean isHeatCalulationFinished() {
        return port.temperatureUpdated((AbstractElement) element);
    }

    // Throw exeptions in case of temperature is set or asked, this is only
    // possible if the handler actually has a temperature, which this one
    // doesnt.
    @Override
    public void setInitialTemperature(double t) {
        throw new UnsupportedOperationException(
                "Heat connection handler does not have its own temperature");
    }

    @Override
    public double getTemperature() {
        throw new UnsupportedOperationException(
                "Heat connection handler does not have its own temperature");
    }

    @Override
    public void setInnerThermalMass(double storedMass) {
        throw new UnsupportedOperationException(
                "Heat connection handler does not support heat volume.");
    }

}
