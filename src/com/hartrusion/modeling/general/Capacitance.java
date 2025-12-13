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
import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;

/**
 * Stores effort energy. To properly finish calculation, all nodes must have set
 * flow variables.
 * <p>
 * Capacitances have to be further classified as self or mutual capacitances.
 * The class is therefore an abstract class, representing the need for further
 * clarification of what kind of capacitance it is.
 * <p>
 * effort = tau * integral(flow), with tau = 1/time constant
 *
 * @author Viktor Alexander Hartung
 */
public abstract class Capacitance 
        extends EnergyStorage 
        implements InitialConditions {

    public Capacitance(PhysicalDomain domain) {
        super(domain);
        elementType = ElementType.CAPACTIANCE;
    }

    /**
     * Set initial effort. Effort can only be set using this method, it is not
     * allowed for connected conserving nodes to set the effort. As effort is
     * the state space variable for capacitance, this will update the state
     * space variable. Use for initialization only or with extreme caution.
     *
     * @param e Initial effort value
     */
    public void setInitialEffort(double e) {
        stateValue = e;
    }

    public double getEffort() {
        return stateValue;
    }

    @Override
    public boolean isCalculationFinished() {
        return deltaCalculated && super.isCalculationFinished();
    }
}
