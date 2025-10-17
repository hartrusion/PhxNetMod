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
 * Domain analogy modeling uses the analogy of various physical domains towards
 * electrical networks. Linear electric circuits can have the same linear
 * ordinary differential equations for their behaviour over time as
 * one-dimensional mechanical systems like a spring damper mass combination.
 * This means that more complex systems can be solved using methods of
 * electrical engineering and this modeling library does exactly that.
 * <p>
 * This modeling library is designed to provide methods of model creation on
 * runtime as well as providing methods to make a discrete time step simulation
 * on the created physical models.
 * <p>
 * Scope of those models are time-discrete analysis for multi domain systems.
 * See modeling.general package-info for more detail about what kind of domains
 * this is designed for. It was primarily written to provide a simulation engine
 * for the rbmk simulator project.
 * <p>
 * The main approach is not to go the nodal analysis path which will create
 * equation systems and matrix to solve. Such equations become hard to solve on
 * non-linear occasions like closed valves or hitting an end stop with a moving
 * mass. The solver packages provide algorithms which will translate the model
 * for each discrete time step to an electrical circuit and provides means for
 * solving those circuits, generating all the values we need. As said, no nodal
 * analysis is used. Instead, those solvers feature methods to handle open
 * connections or shortcuts inside electrical circuits by doing simplifications
 * on those as you would do them on a sheet of paper. This allows to set
 * infinite or zero resistance values without having to fear a division by zero
 * or multiplications with infinite values. The model will be stable and provide
 * a solution even if conditions are at the boundary of what the physical model
 * could ever calculate.
 * <p>
 * The general idea is to define nodes and elements, connect them to a network
 * and use the DomainAnalogySolver to translate the network into electrical 
 * circuits. Elements have methods to connect them to nodes. The solver has a
 * prepareCalculation method which will do what it says and a doCalculation
 * method to run one calculation for one discrete time steps. For real time
 * simulations, those methods can be called in discrete timed steps.
 * 
 */
package com.hartrusion.modeling;
