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
package com.hartrusion.modeling.phasedfluid;

import com.hartrusion.modeling.initial.AbstractIC;
import com.hartrusion.modeling.initial.InitialConditions;
import com.hartrusion.modeling.initial.PhasedEnergyStorageIC;

/**
 * A flow resistance element with fixed mass. Does ideal mixing of the heat
 * energy of the fluid. Can be used to add some delay to pure phased systems,
 * should be used with caution as it might not behave as expected during
 * transitions of unwanted phase change.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedVolumizedFlowResistance extends PhasedAbstractFlowResistance
        implements InitialConditions {

    public PhasedVolumizedFlowResistance(PhasedFluidProperties fluidProperties) {
        phasedHandler = new PhasedVolumizedHandler(fluidProperties, this);
    }

    @Override
    public PhasedFluidProperties getPhasedFluidProperties() {
        return ((PhasedVolumizedHandler) phasedHandler).getPhasedFluidProperties();
    }

    /**
     * Sets the inner thermal mass (usually kg) of the element. Has to be set
     * when initializing the model.
     *
     * @param heatedMass value, mostly in kg.
     */
    public void setInnerHeatedMass(double heatedMass) {
        phasedHandler.setInnerHeatedMass(heatedMass);
    }

    /**
     * Sets the initial heat energy by using the temperature.
     *
     * @param temperature Temperature of the fluid (X=0) in Kelvin
     */
    public void initConditions(double temperature) {
        phasedHandler.setInitialHeatEnergy(
                temperature * ((PhasedVolumizedHandler) phasedHandler)
                        .getPhasedFluidProperties().getSpecificHeatCapacity());
    }

    @Override
    public AbstractIC getState() {
        // use state value to save the temperature, this element just has one
        // value that needs to be stored.
        PhasedEnergyStorageIC ic = new PhasedEnergyStorageIC();
        ic.setElementName(toString());
        ic.setHeatEnergy(phasedHandler.getHeatEnergy());
        return ic;
    }

    @Override
    public void setInitialCondition(AbstractIC ic) {
        checkInitialConditionName(ic);
        PhasedEnergyStorageIC cIc = (PhasedEnergyStorageIC) ic;
        phasedHandler.setInitialHeatEnergy(cIc.getHeatEnergy());
    }

}
