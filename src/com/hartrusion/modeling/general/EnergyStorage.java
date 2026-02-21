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
package com.hartrusion.modeling.general;

import com.hartrusion.modeling.initial.InitialConditions;
import com.hartrusion.modeling.initial.AbstractIC;
import com.hartrusion.modeling.initial.EnergyStorageIC;
import com.hartrusion.modeling.PhysicalDomain;

/**
 * Extends Element to be able to store energy, either flow or effort. Stored
 * energy is a so-called state space variable. This class holds additional
 * variables and methods that will be needed for such elements. It has to be
 * further derived.
 *
 * @author Viktor Alexander Hartung
 */
abstract class EnergyStorage extends AbstractElement
        implements InitialConditions {

    protected double stepTime = 0.1;
    protected boolean stateValuePrepared = false;
    protected double stateValue = 0.0; // integral state space value
    protected double nextStateValue = 0.0;
    protected boolean deltaCalculated = false; // delta has new valid value
    protected double delta = 0; // Change of stateValue during stepTime
    protected double tau = 5; // integral time constant

    protected EnergyStorage(PhysicalDomain domain) {
        super(domain);
    }

    @Override
    public void prepareCalculation() {
        // this will set the new state value for next cycle - ONCE.
        if (stateValuePrepared) {
            stateValue = nextStateValue;
        }
        stateValuePrepared = false;
        deltaCalculated = false; // init
        super.prepareCalculation(); // continues calling prepCalc on all nodes
    }

    protected final void integrate() {
        if (!stateValuePrepared & deltaCalculated) {
            // simple forward euler method
            nextStateValue = stateValue + delta * tau;
            stateValuePrepared = true;
        }
    }

    @Override
    public void setStepTime(double dt) {
        stepTime = dt;
    }

    /**
     * Set time constant for integral behaviour
     *
     * @param t time constant in same time unit as stepTime, usually seconds.
     */
    public void setTimeConstant(double t) {
        this.tau = 1 / t;
    }

    @Override
    public AbstractIC getState() {
        EnergyStorageIC ic = new EnergyStorageIC();
        ic.setElementName(toString());
        ic.setStateValue(stateValue);
        return ic;
    }

    @Override
    public void setInitialCondition(AbstractIC ic) {
        checkInitialConditionName(ic);
        stateValue = ((EnergyStorageIC) ic).getStateValue();
    }
}
