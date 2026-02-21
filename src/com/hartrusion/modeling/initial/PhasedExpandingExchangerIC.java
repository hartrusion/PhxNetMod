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
package com.hartrusion.modeling.initial;

/**
 * Specialized initial condition for the PhasedExpandingThermalExchanger class.
 * The properties used here are only used by that class which already breaks
 * some of the used conventions.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedExpandingExchangerIC extends PhasedEnergyStorageIC {

    private double innerHeatedMass;
    private double delayedInHeatEnergy;
    private double negativeMass;

    public double getNegativeMass() {
        return negativeMass;
    }

    public void setNegativeMass(double negativeMass) {
        this.negativeMass = negativeMass;
    }

    public double getInnerHeatedMass() {
        return innerHeatedMass;
    }

    public void setInnerHeatedMass(double innerHeatedMass) {
        this.innerHeatedMass = innerHeatedMass;
    }

    public double getDelayedInHeatEnergy() {
        return delayedInHeatEnergy;
    }

    public void setDelayedInHeatEnergy(double delayedInHeatEnergy) {
        this.delayedInHeatEnergy = delayedInHeatEnergy;
    }

}
