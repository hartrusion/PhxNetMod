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
package com.hartrusion.modeling.general;

/**
 * Common interface of all elements that contain a fluid mass, which allows
 * other parts of the model to read the mass without knowing the element class
 * or its handler. The material layer uses it to mix the concentrations of the
 * inflowing fluid with the fluid stored inside the element, so the mass must
 * be the mass that is relevant for ideal mixing.
 *
 * <p>
 * The mass is never set through this interface, because elements with a
 * constant mass and elements with a mass that changes during the simulation
 * need a different meaning of the set operation. These are provided by the
 * derived interfaces ConstantMassElement and VariableMassElement.
 */
public interface MassedElement {

    /**
     * Returns the mass that is currently stored inside the element, which is
     * the mass at the beginning of the current calculation run for elements
     * with a variable mass.
     *
     * @return Mass in kg
     */
    public double getMass();
}
