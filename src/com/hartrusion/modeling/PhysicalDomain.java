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
 *
 * @author Viktor Alexander Hartung
 */
public enum PhysicalDomain {

    /**
     * Electrical system.
     * <ul>
     * <li>Flow variable: Current (Ampere)</li>
     * <li>Effort variable: Voltage (Volts)</li>
     * </ul>
     */
    ELECTRICAL,
    /**
     * Mechanic system.
     * <ul>
     * <li>Flow variable: Velocity (Meters per second)</li>
     * <li>Effort variable: Force (Newton)</li>
     * </ul>
     */
    MECHANICAL,
    /**
     * Hydraulic system.
     * <ul>
     * <li>Flow variable: Volume flow (Cubic meters per second)</li>
     * <li>Effort variable: Pressure (Pascal)</li>
     * </ul>
     */
    HYDRAULIC,
    /**
     * Pneumatic system.
     * <ul>
     * <li>Flow variable: Volume flow (Cubic meters per second)</li>
     * <li>Effort variable: Pressure (Pascal)</li>
     * </ul>
     */
    PNEUMATIC,
    /**
     * Thermal system.
     * <ul>
     * <li>Flow variable: Heat flow (Watt = Joule/Second)</li>
     * <li>Effort variable: Temperature (Kelvin)</li>
     * </ul>
     * Theremal systems are a represenation of thermal flow with general
     * elements. They are limited to not having conductances as this is not
     * possible in physics.
     */
    THERMAL,
    /**
     * Heated fluid system.
     * <ul>
     * <li>Flow variable: Mass flow (Kilogram per second)</li>
     * <li>Effort variable: Pressure (Pascal)</li>
     * </ul>
     * <p>
     * Heated fluid systems are an extension of hydraulic systems, they have a
     * mixing temperature feature that allows to mix and determine temperatures
     * depending on the flow and its directions.
     */
    HEATFLUID,
    /**
     * Phased fluid system.
     * <ul>
     * <li>Flow variable: Mass flow (Kilogram per second)</li>
     * <li>Effort variable: Pressure (Pascal)</li>
     * </ul>
     * <p>
     * An extension of the heated fluid system. The heated fluid can be
     * evaporated. The phased fluid has some kind of heat energy Q attached to
     * it instead of a temperature. 
     */
    PHASEDFLUID,
    /**
     * Steam system. Implements a steam table for calulating properties of a
     * fluid that supports beeing fluid, vapour or vapour and fluid.
     * <ul>
     * <li>Flow variable: Mass flow (Kilogram per second)</li>
     * <li>Effort variable: Pressure (Pascal)</li>
     * </ul>
     * <p>
     * As the calculation is focused on phase changes and enthalpy and the whole
     * steam thing is a lot to calculate, this supports only a few objects. Use
     * domain converters if the state does not change in certain parts of the
     * network.
     */
    STEAM,
    /**
     * Elements of this domain are usually converters or interfaces between two
     * domains and therefore they are not bound to be a specific domain element.
     */
    MULTIDOMAIN
}
