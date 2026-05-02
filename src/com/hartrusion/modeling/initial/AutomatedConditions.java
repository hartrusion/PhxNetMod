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
 * Classes that implement this interface support saving and restoring their
 * current state using the "Automation Condition", a term, that is similar to
 * the "Initial Condition" of the state space model, but as this has nothing to
 * do with the state space models, the term "automation" is chosen to
 * distinguish it.
 *
 * @author Viktor Alexander Hartung
 */
public interface AutomatedConditions {

    /**
     * Generate the initial automation condition from the current state of the
     * Object. This will likely generate different automation conditions or no
     * initial condition at all, depending on the element type.
     *
     * @return AbstractIC object that is derived to a more specific IC. This
     * will always return a new object.
     */
    public AbstractAC getState();

    /**
     * Sets the provided initial condition to the element so it has the same
     * state as when saving the IC.
     *
     * @param ac Initial Automation Condition object
     */
    public void setAutomationCondition(AbstractAC ac);
}
