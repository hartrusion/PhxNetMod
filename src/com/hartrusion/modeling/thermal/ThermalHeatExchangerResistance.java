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
package com.hartrusion.modeling.thermal;

import com.hartrusion.modeling.heatfluid.HeatThermalExchanger;
import com.hartrusion.modeling.heatfluid.HeatThermalVolumeHandler;
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.exceptions.NoFlowThroughException;
import com.hartrusion.modeling.general.EffortSource;
import com.hartrusion.modeling.general.FlowThrough;

/**
 * Base class for thermal resistors which are to be placed between two heat
 * elements representing heat exchangers.
 *
 * <p>
 * It is a non-linear element designed to be placed between two heat exchange
 * elements which provide open origin elements. It can not be solved with the
 * provided linear solvers.
 *
 * <p>
 * As this is also connected to the heat handlers of the corresponding element,
 * initHeatHandler method has to be used to initialize this element.
 *
 * <p>
 * Using a regular resistor as thermal resistor for thermal flow between two
 * heat exchanger elements would allow to model a heat transfer between two
 * volumes but in reality heat exchangers are mostly built as counter flow heat
 * exchangers, requiring some extra modeling work. These thermal elements
 * present a way of doing so without the need of discrete volume elements.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class ThermalHeatExchangerResistance extends FlowThrough {

    protected double kTimesA = 425.0; // k = 85 W/m^2/K, A = 5 m²

    /**
     * Additional inlet temperature of the heat exchanger. This is saved as a
     * value for each of the two ports here and has to be set externally.
     */
    protected final double[] inTemp = new double[2];

    /**
     * Variables inTemp and noInTempValue have updated values that can be used.
     */
    protected final boolean[] inTempUpdated = new boolean[2];

    /**
     * If there is no flow, no temperature is available.
     */
    protected final boolean[] noInTempValue = new boolean[2];

    protected final HeatThermalVolumeHandler[] heatHandler
            = new HeatThermalVolumeHandler[2];

    public ThermalHeatExchangerResistance() {
        super(PhysicalDomain.THERMAL); // this is restricted to thermal domains.
        elementType = ElementType.DISSIPATOR;
    }

    /**
     * Additional init method that has to be invoked to make additional
     * connections. This resitor type element uses additional information from
     * heat handlers of the connected heat exchanger elements, so the heat
     * handlers have to be known. Just call this when connecting elements and
     * ports and so on twiche, using each side of the heat exchanger as
     * argument.
     *
     * <p>
     * The heat handlers from the corresponding element where this was added to
     * have to be known in this class to get the inlet temperature. As the
     * element here itself is only connected to an origin element for the
     * thermal system representation, there is no knowledge about the heat
     * exchanger that is to be modeled using this resistance parameter.
     *
     * <p>
     * The connection between ports and elements have do be done before this is
     * called. It has to be called for both heat exchanger sides.
     *
     * @param he HeatThermalExchanger element
     */
    public void initHeatHandler(HeatThermalExchanger he) {
        EffortSource source = he.getInnerThermalEffortSource();
        EffortSource tmpOrigin;
        for (int idx = 0; idx < 2; idx++) {
            try {
                // which port has this origin?
                tmpOrigin = ((EffortSource) nodes.get(idx)
                        .getOnlyOtherElement(this));

            } catch (NoFlowThroughException ex) {
                throw new ModelErrorException("Port must have exaclty two"
                        + "connected elements. No thermal distribution here.");
            }
            if (tmpOrigin == source) {
                heatHandler[idx]
                        = (HeatThermalVolumeHandler) he.getHeatHandler();
            }
        }
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        inTempUpdated[0] = false;
        inTempUpdated[1] = false;
        noInTempValue[0] = false;
        noInTempValue[1] = false;
    }

    @Override
    public void setStepTime(double dt) {
        // not present yet
    }

    /**
     * Set the thermal conductance, which is the product of thermal
     * transmittance k and the surface area A. It is specified in Watt per
     * Kelvin. For heat exchangers, k can be somewhere between 50 and 150.
     *
     * This is the essential value that describes how much thermal energy is
     * transferred in relation to the temperature difference.
     *
     * Inital value is 425 W/K.
     *
     * @param kTimesA
     */
    public void setThermalConductance(double kTimesA) {
        if (kTimesA <= 0.0) {
            throw new ModelErrorException("k * A can not be equal or less "
                    + "than zero.");
        }
        this.kTimesA = kTimesA;
    }

    @Override
    public boolean isLinear() {
        return false;
    }

}
