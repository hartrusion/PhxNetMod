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
package com.hartrusion.modeling.phasedfluid;

/**
 * A flow resistance that limits the energy on the out node using a given vapor
 * fraction combined with a pressure difference. On that pressure difference,
 * the given energy will be removed from the phased fluid in that cycle. If the
 * pressure difference is 0, the energy will also be 0. The vanished energy
 * value can be obtained with a method afterwards.
 * <p>
 * This element is used for a very simple turbine model, by forcing a certain
 * vapor fraction at the end of the turbine or its output, the energy which was
 * taken by the turbine can be obtained.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedLimitedPhaseSimpleFlowResistance
        extends PhasedAbstractFlowResistance {

    private final PhasedFluidProperties fluidProperties;

    public PhasedLimitedPhaseSimpleFlowResistance(
            PhasedFluidProperties fluidProperties) {
        this.fluidProperties = fluidProperties;
        phasedHandler = new PhasedLimitSimpleHandler(this);
    }

    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return fluidProperties;
    }

    /**
     * Sets the main characteristic for energy transfer out of the phased fluid
     * on this element.
     *
     * @param vaporFraction Out vapor fraction (can be more than 1 for still
     * superheated steam, this refers to the temperature then)
     * @param pressureDifference Pressure difference in flow direction that is
     * designed for this energy transfer. The removed heat will be less if that
     * pressure difference is not there. This allows modeling of the heatup of
     * the turbine before steam flows through it.
     */
    public void setOutVaporFractionWithDiff(
            double vaporFraction, double pressureDifference) {
        ((PhasedLimitSimpleHandler) phasedHandler)
                .setOutVaporFractionWithDiff(vaporFraction, pressureDifference);
    }

    /**
     * Get the excess specific energy that was consumed by this element.
     *
     * @return value in J/kg
     */
    public double getExcessHeatEnergy() {
        return ((PhasedLimitSimpleHandler) phasedHandler).getExcessEnergy();
    }

    /**
     * Get the excess power that was consumed by this element. Requires the
     * element to be in fully updated state.
     *
     * @return value in J/s = Watts
     */
    public double getExcessPower() {
        return getExcessHeatEnergy() * Math.abs(getFlow());
    }

}
