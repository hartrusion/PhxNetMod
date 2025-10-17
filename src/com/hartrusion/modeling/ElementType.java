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
package com.hartrusion.modeling;

/**
 * asdf.
 *
 * @author Viktor Alexander Hartung
 */
public enum ElementType {
    /**
     * Undefinded - shall never occur in set up models.
     */
    NONE,
    /**
     * The element is actually not present, there is no connection between the
     * nodes. No flow can go through (it will be forced to be 0.0) and any
     * effort is valid.
     */
    OPEN,
    /**
     * Direct connection between the nodes. Effort values from nodes will be the
     * same for all connected nodes. If there are two nodes, the flow will flow
     * through the element.
     */
    BRIDGED,
    DISSIPATOR,
    CAPACTIANCE,
    INDUCTANCE,
    FLOWSOURCE,
    EFFORTSOURCE,
    /**
     * Forces an indiviual flow and effort value to the node on its connection.
     * This is not a common network element and cant be used in closed
     * electronic circuits.
     */
    ENFORCER,
    /**
     * Sets the effort value to a specified value that will not change during
     * the model run.
     */
    ORIGIN
}
