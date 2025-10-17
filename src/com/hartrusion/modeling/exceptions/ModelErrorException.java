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
package com.hartrusion.modeling.exceptions;

/**
 * Unchecked exception that gets thrown if there are errors in the model that do
 * not allow further processing of the model. Those exceptions cant be handled,
 * they occur if the model is misconfigured or corrupt. Things that are
 * physically impossible lead to this exception, like trying to enforce a value
 * on a part where it cant be enforced (causality issue).
 *
 * <p>
 * As throwing unchecked exceptions everywhere is discouraged, it is done on
 * purpose here. There is no excuse of trying to build a non-functional,
 * physically and mathematically impossible model, so why would you even try it
 * - its like trying to divide by zero, you would not write a try catch thing
 * around it, if you divide by zero, something went wrong and you're propably
 * using the wrong formula.
 *
 * <p>
 * Most of those unchecked exceptions are build in to allow better debug of the
 * code, trying to catch the errors in an eary stage by having some additional
 * checks which will throw this exception. 
 *
 * @author Viktor Alexander Hartung
 */
public class ModelErrorException extends RuntimeException {

    public ModelErrorException(String message) {
        super(message);
    }
}
