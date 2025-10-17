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
/**
 * Heat fluid is an extension for fluids, which are themselves still generalized
 * elements. The term "heat" is used as this is an ability to handle
 * temperatures. "Thermal" is used for describing temperature flow and
 * temperature capacity when using general elements to describe thermal systems.
 *
 * <p>
 * Heat fluid elements use a so called heat handler on each element which
 * extends all elements to the ability to transport temperature as a scalar
 * value. The temperature is using the flow value from the general elements to
 * to transport and mix the temperature accordingly.
 *
 * <p>
 * The heat transport is calculated as soon as flows from elements are
 * available, calling the doCalculation will calculate the temperatures.
 *
 * <p>
 * As this is just a model, it has clear limitations. Mixing is always ideal and
 * complete, thermal capacity is always the same and there is no volume change
 * when the temperature changes. It is a simple value that gets transported with
 * the flow.
 */
package com.hartrusion.modeling.heatfluid;
