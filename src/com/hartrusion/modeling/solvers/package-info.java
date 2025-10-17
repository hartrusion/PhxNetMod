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
 * Provides classes for solving models. The goal of a model is to provide all
 * state variables, calculate its next state out of the current state and then,
 * provide full state again etc.
 * <p>
 * Small, simple models can be solved without the need of solvers just by
 * calling the calculation-methods until all values were set to the ports by the
 * elements itself. However, it can happen that more elements form a network of
 * elements which has then to be solved by means of network analysis.
 * <p>
 * For obvious reasons of possible shortcuts and open connections, nodal
 * analysis is not an option.
 * <p>
 * All solvers are derived from methods of electrical engineering and provide a
 * steady state solution only. However, it is intended to replace energy storage
 * elements with corresponding effort or flow sources to calculate the network
 * state from the energy storage values for that specific time. This is achieved
 * by the so called TransferSubnet class.
 */
package com.hartrusion.modeling.solvers;
