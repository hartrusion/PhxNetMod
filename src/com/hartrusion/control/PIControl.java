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
package com.hartrusion.control;

import java.util.function.DoubleSupplier;

/**
 * A proportional-integral controller (PI) with output limitation and output
 * limit feedback. The integral part of the controller will be limited to not
 * run away when the output value is at its limit. This has two effects: The
 * controller won't need time to get back to its operation point after any
 * potential overshoot over the output limitation. On the other hand, the
 * limited integral might result in a behavior of the controller raising the
 * output value even if the input is still negative if the input is rising
 * faster than the hold down integral part is still being pushed down by the
 * integral. To prevent such a behavior, a value controlling controller can be
 * set to an output limit value of -5 to let the controller run negative on
 * purpose.
 *
 * @author Viktor Alexander Hartung
 */
public class PIControl extends AbstractController {

    private double integralAdaption = 0.0;
    protected DoubleSupplier integralAdaptionProvider;

    private double kR = 1.0;
    private double TN = 10;

    private boolean stopIntegrator;
    private double xIntegral;
    
    private boolean noProportional;

    @Override
    public void run() {
        super.run();

        if (integralAdaptionProvider != null) {
            integralAdaption = integralAdaptionProvider.getAsDouble();
        }

        double dIntegral;

        if (stopIntegrator || controlState != ControlCommand.AUTOMATIC) {
            dIntegral = 0;
        } else {
            if (noProportional) {
                dIntegral = eInput * stepTime / TN
                        + integralAdaption * stepTime;
            } else {
                dIntegral = eInput * kR * stepTime / TN
                        + integralAdaption * stepTime;
            }
        }

        double proportionalPart = eInput * kR;

        // Manual mode sets the integrator properly so the output matches the
        // followUp input value.
        if (controlState != ControlCommand.AUTOMATIC) {
            xIntegral = uFollowUp - proportionalPart;
        }

        double integralPart = xIntegral + dIntegral;

        double sumControl = integralPart + proportionalPart;

        // In case the sum would exceed the limit, the integral part will
        // be limited so the controller does not run away.
        if (sumControl > uMax) {
            uOutput = uMax;
            xIntegral = uMax - proportionalPart;
        } else if (sumControl < uMin) {
            uOutput = uMin;
            xIntegral = uMin - proportionalPart;
        } else { // default: sum up to integrate
            uOutput = sumControl;
            xIntegral = integralPart; // assign what was summed up previously
        }

        if (controlState != ControlCommand.AUTOMATIC) { // overwrite output value
            uOutput = uFollowUp;
        }
    }

    public double getParameterK() {
        return kR;
    }

    public void setParameterK(double kR) {
        this.kR = kR;
        noProportional = kR == 0.0;
    }

    /**
     * Defines after what amount of time the output has reached K times input.
     * Note that the integral part is multiplied with K also.
     *
     * @return TN Integral time constant (in Seconds), Initial value: 10 s
     */
    public double getParameterTN() {
        return TN;
    }

    /**
     * Defines after what amount of time the output has reached K times input.
     * Note that the integral part is multiplied with K also.
     * <p>
     * If kR is zero, the k part will be ignored and the controller works like 
     * an I control with TI instead of TN.
     *
     * @param TN Time constant to set (in Seconds), Initial value: 10 s
     */
    public void setParameterTN(double TN) {
        this.TN = TN;
    }

    public boolean isStopIntegrator() {
        return stopIntegrator;
    }

    public void setStopIntegrator(boolean sti) {
        this.stopIntegrator = sti;
    }

    /**
     * This can be used to attach an instance that provides the integral
     * adaption value instead of having to make some complicated set methods.
     * Overriding the only method of the DoubleSupplier class allows this to be
     * called with an anonymous class or lambda expression to define what to
     * call to get the input value.
     *
     * @param integralAdaptionProvider Instance that will provide control
     * difference input that will only be applied to the integral part of the
     * controller..
     */
    public void addIntegralAdaptionProvider(DoubleSupplier integralAdaptionProvider) {
        this.integralAdaptionProvider = integralAdaptionProvider;
    }

    /**
     * Restores the state of the controller for the given input and output value
     * pair. This allows loading a save of the controller state even after
     * parameters were changed and restores the integral part in the proper way.
     *
     * @param input value
     * @param output value
     */
    public void acSetCondition(double input, double output) {
        xIntegral = output - input * kR;
        uOutput = output;
        eInput = input;

        skipGetFromProvidersAfterLoading = true;
    }
}
