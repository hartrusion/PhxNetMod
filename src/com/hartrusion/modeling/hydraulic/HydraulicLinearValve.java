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
package com.hartrusion.modeling.hydraulic;

import com.hartrusion.modeling.ElementType;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.general.LinearDissipator;

/**
 * Represents a valve which can be set between 0 and 100 % opening. This extends
 * the linear resistor for use as a hydraulic valve. It could also be an
 * electrical potentiometer. The flow resistance at 100 % opening will be set
 * with setValveOperatingPoint. The valve will behave linear. Nonliner
 * resistances would require solver which are able to solve nonlinear problems,
 * this is not available here.
 *
 * The valve also features a maximum open and close rate, it will not change its
 * state faster than set maximum rate.
 *
 * @author Viktor Alexander Hartung
 */
public class HydraulicLinearValve extends LinearDissipator {

    private double opening; // 0..100 %
    private double resistanceFullOpen;
    protected double stepTime;
    private boolean linear = true;
    private double closedFactor = 5;
    
    private double cfM, cfB;

    public HydraulicLinearValve() {
        super(PhysicalDomain.HYDRAULIC);
        opening = 0.0; // init as closed valve
        elementType = ElementType.OPEN; // means closed (open connection!)
        resistanceFullOpen = 1e6;
    }

    /**
     * Set pressure drop for fully open valve
     *
     * @param flow
     * @param effort
     */
    public void setValveOperatingPoint(double effort, double flow) {
        setResistanceFullOpen(effort / flow);
    }

    /**
     * Sets the resistance parameter for the valve when it is on 100 % opening
     * state.
     *
     * @param r Resistence as Effort/Flow
     */
    public void setResistanceFullOpen(double r) {
        resistanceFullOpen = r;
        updateCfValues();
    }

    /**
     * Set the valve opening as percentage. To have a more realistic valve
     * behaviour, use a setpoint integrator to call this.
     *
     * @param o valve opening 0..100
     */
    public void setOpening(double o) {
        if (o >= 100.0) {
            opening = o;
            elementType = ElementType.DISSIPATOR;
            resistance = resistanceFullOpen;
        } else if (o <= 0.01) {
            opening = 0.0;
            elementType = ElementType.OPEN;
            resistance = Double.POSITIVE_INFINITY;
        } else {
            opening = o;
            elementType = ElementType.DISSIPATOR;
            if (linear) {
                resistance = resistanceFullOpen * 100 / opening;
            } else {
                resistance = cfM * opening + cfB;
            }
        }
    }

    /**
     * Retuns the current set opening of the valve that is active. Note that
     * this value might be different from the value that was set with setOpening
     * as the set method has some filtering of the input value.
     *
     * @return value between 0 and 100
     */
    public double getOpening() {
        return opening;
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething = false;
        // resistance = resistanceFullOpen * 100 / opening;

        if (opening <= 0.0 && elementType == ElementType.OPEN) {
            // valve closed. no matter what effort is set to the valve,
            // all flows will be zero.
            for (GeneralNode p : nodes) {
                if (!p.flowUpdated(this)) {
                    didSomething = true;
                    p.setFlow(0.0, this, true);
                }
            }
        } else {
            if (linear) {
                resistance = resistanceFullOpen * 100 / opening;
            } else {
                resistance = cfM * opening + cfB;
            }
            didSomething = super.doCalculation();
        }

        return didSomething;
    }

    @Override // LinearDissipator has empty implementation
    public void setStepTime(double dt) {
        stepTime = dt;
    }

    /**
     * Sets the characteristic between opening and the actual flow resistance.
     * The default value is linear and the calculation will be
     *
     * <pre>
     * R = R_max * 100 / opening
     * </pre>
     *
     * <p>
     * This is basically a a linear behaviour of the conductance (1/R) value. It
     * will result in a high resistance only in values close to 0.
     *
     * <p>
     * Alternatively a factor and linear=false can be specified. It will then be
     *
     * <pre>
     * R = closedFactor * R_max + opening * (1 - closedFactor) / 100
     * </pre>
     *
     * @param linear: Default, uses the 100/opening formula when true.
     * @param closedFactor:
     */
    public void setCharacteristic(boolean linear, double closedFactor) {
        if (!linear && closedFactor <= 1.0) {
            throw new IllegalArgumentException("closedFactor must be "
                    + "higher than 1.0!");
        }
        this.linear = linear;
        this.closedFactor = closedFactor;
        updateCfValues();
    }
    
    /**
     * Calculates line values for y=mx+b for the closedFactor operating curve.
     */
    private void updateCfValues() {
        cfM = (resistanceFullOpen - closedFactor * resistanceFullOpen) / 100;
        cfB = closedFactor * resistanceFullOpen;
    }

}
