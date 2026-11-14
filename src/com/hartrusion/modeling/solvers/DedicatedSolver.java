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
package com.hartrusion.modeling.solvers;

/**
 * A solver that was designed to solve a very specific network constellation. A
 * call on such a dedicated solver is the fastest way to solve such a network as
 * it has the analytic way of solving wired in, which saves the systematic
 * decomposition that the general solvers like {@link NodalAnalysis} or
 * {@link SuperPosition} would have to perform.
 * <p>
 * Dedicated solvers are used by {@link TransferSubnet} and
 * {@link RecursiveSimplifier}, which check if one of the known dedicated
 * solvers fits the network they hold before falling back to a general solver.
 * <p>
 * Every implementation must follow two conventions which can not be enforced
 * by this interface, because Java does not allow an interface to demand static
 * methods or constructors:
 * <ul>
 * <li>A constructor {@code (List<GeneralNode> nodes,
 * List<AbstractElement> elements)} which analyzes the given network, assigns
 * all elements and nodes to their role in the solver and throws a
 * {@link com.hartrusion.modeling.exceptions.WrongSolverException} if the
 * network does not match the constellation the solver was made for.</li>
 * <li>A method {@code public static boolean isSolveableBySolver(
 * List<GeneralNode> nodes, List<AbstractElement> elements)} which tells
 * whether the solver can be used for the given network, so that a caller can
 * pick a solver without having to handle the exception.</li>
 * </ul>
 * The check is static so that it can be called before an instance exists,
 * which is the moment when the caller has to decide which solver to create.
 * <p>
 * Interface documentation updated with LLM: LLM code generated with Claude
 * Opus 5.5.
 *
 * @author Viktor Alexander Hartung
 */
public interface DedicatedSolver {

    /**
     * Fully calculates the network in one step.
     */
    public void solveNetwork();
}
