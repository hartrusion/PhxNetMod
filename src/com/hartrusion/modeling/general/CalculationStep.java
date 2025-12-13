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

/**
 * Provides methods for updating the whole model. Calculation basically consists
 * of those steps:
 *
 * <p>
 * Remove "updated" flag of all values from previous calculations to mark those
 * values to be calculated again. Update EnergyStorage elements to apply new
 * internal state space values to the model. Others may also prepare some
 * calculation values.
 *
 * <p>
 * Try to calculate elements by running doCalculation, if possible. There should
 * always be something that is possible to calculate, elements will return true
 * at doCalculation if there was some successful calculation. This will also
 * update values and set the updated-variable to true to let other elements know
 * that they can now do something with those values.
 *
 * <p>
 * The whole model is calculated if isCalculationFinished returns true for all
 * elements. The state space variables will remain the same during all
 * calculation calls.
 *
 * <p>
 * This requires the model to be in such a simple way that it can calculate
 * itself. There is no solver that will solve an equation systems, like if there
 * are three or more resistances connected to one node. Use the provided network
 * solvers for such cases.
 *
 * @author Viktor Alexander Hartung
 */
public interface CalculationStep {

    /**
     * Resets values to not updated and sets calculated state variables from
     * previous calculation as new state Variable. This has to be called on all
     * elements before calling doCalculation().
     */
    public void prepareCalculation();

    /**
     * Try to calculate something. This works if enough values are updated or if
     * the element is able to set something. This has to be called on each
     * element until the whole model is calculated.
     *
     * @return true if something was calculated. false: nothing was calculated,
     * also if everything is finished and there is nothing to do.
     */
    public boolean doCalculation();

    /**
     * Checks weather the calculation is complete for current cycle. This is
     * true if all values on all nodes connected to this element have their
     * values updated.
     *
     * @return true: Calculation for current cycle is finished.
     */
    public boolean isCalculationFinished();

    /**
     * Set time step to use for this model for calculation.
     *
     * @param dt Discrete time step in time unit, which is usually seconds, if
     * you don't want to mess around with units. Basically everything would work
     * if you transfer the complete model interpretation but this makes no
     * sense. So use seconds for god’s sake. Stay with SI units, stay safe.
     */
    public void setStepTime(double dt);
}
