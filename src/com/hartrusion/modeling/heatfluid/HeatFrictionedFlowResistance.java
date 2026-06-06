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
package com.hartrusion.modeling.heatfluid;

/**
 * A fluid resistance that adds the dissipated energy as heat energy to the
 * passing fluid. The temperature increase is calculated by;
 * <p>
 * DeltaT = Delta Pressure / density / specific heat capacity
 * <p>
 * The flow rate is not there as more flow would transport more heat energy so
 * that value just vanishes from the equation. Specific heat capacity and
 * density have to be provided as separate values, default settings are for
 * water.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFrictionedFlowResistance extends HeatAbstractFlowResistance {

    public HeatFrictionedFlowResistance() {
        heatHandler = new HeatFrictionedHandler(this);
    }

    /**
     * Sets the fluid parameters used for the temperature increase by the
     * friction of the fluid (which is represented by the pressure drop.
     *
     * @param density - default for water: 1000 kg/m³
     * @param specHeatCap - default for water: 4200 J/kg/K
     */
    public void setFrictionHeatupParameters(
            double density, double specHeatCap) {
        ((HeatFrictionedHandler) heatHandler).
                setVolHeatCap(density * specHeatCap);
    }
}
