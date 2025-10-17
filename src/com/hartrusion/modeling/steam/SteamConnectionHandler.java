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

import com.hartrusion.modeling.general.AbstractElement;

/**
 * Implements a one-port handler to connect a boundary to the steam domain. It
 * sets specified steam properties which come from outside. This will be used by
 * the SteamHeatFluidConverter for the steam side of the element for example, it
 * sets steam properties to exactly one port.
 *
 * @author Viktor Alexander Hartung
 */
public class SteamConnectionHandler implements SteamHandler {

    /**
     * The steam connection handler has only one port so we do not have an array
     * of ports here like other handlers do have.
     */
    private SteamNode node;

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final SteamElement element;

    public SteamConnectionHandler(SteamElement parent) {
        element = parent;
    }

    @Override
    public boolean registerSteamPort(SteamNode sn) {
        if (node == null) {
            node = sn;
            return true;
        }
        return false;
    }

    @Override
    public void prepareSteamCalculation() {
        // as this does not save any energy, there is nothing to do here.
    }

    @Override
    public boolean doSteamCalculation() {
        return false;
    }

    /**
     * Sets the steam properties to the connected steam port. This is the main
     * thing this class is doing. Gets called from SteamHeatFluidConverter for
     * example.
     * 
     * <p>
     * According to the calculation of the SteamHeatFluidConverter, the
     * temperature is split into two parts: The fluid temperature and an
     * additional virtual temperature part. The virutal temperature is the
     * difference to the saturation temperature, marking the additional or
     * missing thermal energy which is in there with the same heat capactity as
     * the fluid.
     *
     * @param pressure Heat fluid pressure in Pascal
     * @param temperature Heat fluid temperature in Kelvin
     */
    public void setSteamFromConverter(double pressure, double temperature) {
        // Get Properties for the liquid part:
        double satTemp = element.getSteamTable().get("TSat_p", pressure);
        double hLiq = element.getSteamTable().get("hLiq_p", pressure);
        // The virtual temperature is what is additional to the saturation
        // temperature:
        double virtTemp = temperature - satTemp;
        double specHeatCap = element.getSteamTable().get("c_ph",
                            pressure, hLiq);
        double hDiff = specHeatCap * virtTemp;
        double spefificEnthalpy = hLiq + hDiff;
        node.setSteamProperties(
                temperature,
                spefificEnthalpy,
                element.getSteamTable().get("s_pT",
                        pressure, temperature),
                element.getSteamTable().get("x_ph",
                        pressure, spefificEnthalpy),
                (AbstractElement) element);
    }

    /**
     * Sets the steam state specified by pressure and enthalpy to the steam
     * port.
     *
     * @param properties Full steam properties array
     */
    public void setSteamProperties(double[] properties) {
        node.setSteamProperties(
                properties[0],
                properties[1],
                properties[2],
                properties[3],
                (AbstractElement) element);
    }

    @Override
    public boolean isSteamCalulationFinished() {
        return node.steamPropertiesUpdated((AbstractElement) element);
    }

    @Override
    public void setSteamProperty(int index, double value) {

    }

    @Override
    public double getSteamProperty(int index) {
        throw new UnsupportedOperationException(
                "This steam handler does not contain own properties.");
    }

    @Override
    public void setTotalMass(double mass) {
        throw new UnsupportedOperationException(
                "This steam handler does not support having a mass.");
    }

    @Override
    public double getTotalMass() {
        throw new UnsupportedOperationException(
                "This steam handler does not support having a mass.");
    }
}
