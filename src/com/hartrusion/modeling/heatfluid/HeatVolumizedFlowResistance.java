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

import com.hartrusion.modeling.initial.TemperatureIC;
import com.hartrusion.modeling.initial.AbstractIC;
import com.hartrusion.modeling.initial.InitialConditions;

/**
 * Extends linear dissipator for heat fluid, including a heat volume.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatVolumizedFlowResistance extends HeatAbstractFlowResistance
        implements InitialConditions {

    private final HeatVolumizedHandler volumeHeatHandler;

    public HeatVolumizedFlowResistance() {
        volumeHeatHandler = new HeatVolumizedHandler(this);
        heatHandler = volumeHeatHandler; // interface for abstract base class
    }

    @Override
    public void setStepTime(double dt) {
        super.setStepTime(dt);
        volumeHeatHandler.setStepTime(dt);
    }
    
    /**
     * Sets the inner thermal mass (usually kg) of the element. Has to be set
     * when initializing the model.
     * 
     * @param thermalMass value, mostly in kg.
     */
    public void setInnerThermalMass(double thermalMass) {
        volumeHeatHandler.setInnerThermalMass(thermalMass);
    }
    
    @Override
    public AbstractIC getState() {
        TemperatureIC ic = new TemperatureIC();
        ic.setElementName(toString());
        ic.setTemperature(heatHandler.getTemperature());
        return ic;
    }

    @Override
    public void setInitialCondition(AbstractIC ic) {
        checkInitialConditionName(ic);
        heatHandler.setInitialTemperature(((TemperatureIC) ic).getTemperature());
    }
}
