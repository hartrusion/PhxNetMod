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
 * General components can be used for modeling dynamic systems for simple
 * disciplines as well as simple electronic circuits.
 *
 * <p>
 * This table shows how the elements and variables are used across different
 * domains:
 *
 * <pre>
 *            Effort     Flow        Capacitance  Inductance     Dissipator
 * Mechanic   Force      Velocity    Spring       Mass           Dampener
 * Hydraulic  Pressure   Volume flow Tank         Long Pipe      Press. Drop
 * Electric   Voltage    Current     Capacitor    Coil           Resistor
 * Thermal    Temp.      Heat flow   Mass         n/a            Heat transfer
 * </pre>
 *
 * <p>
 * Those generalized elements will be used no matter which domain is in use.
 * "HeatFluid" will allow to transport a heated fluid with interfaces to other
 * domain.
 * 
 * <p>
 * 
 *
 */
package com.hartrusion.modeling.general;
