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
 * PID controller with filtered derivative part (DT1 element).
 * <p>
 * Parallel structure with common gain kR:
 * <p>
 * u = kR * e + integral + derivative
 * <p>
 * Integral part: d(integral)/dt = kR * e / TN
 * <p>
 * Derivative part: Gd(s) = kR * TV * s / (1 + T1 * s)
 * <p>
 * T1 is used only as a filter time constant and does not affect the derivative
 * amplitude defined by TV.
 *
 * @author Viktor Alexander Hartung
 */
public class PIDControl extends AbstractController {

    private double integralAdaption = 0.0;
    protected DoubleSupplier integralAdaptionProvider;

    private double kR = 1.0;
    private double TN = 10.0;
    private double TV = 0.0;
    private double T1 = 1.0;

    private boolean stopIntegrator;

    private double xIntegral;

    private double lastInput;
    private double xDerivative;
    private boolean derivativeInitialized;

    @Override
    public void run() {
        super.run();

        if (integralAdaptionProvider != null) {
            integralAdaption = integralAdaptionProvider.getAsDouble();
        }

        double proportionalPart = eInput * kR;
        double derivativePart = 0.0;

        if (controlState == ControlCommand.AUTOMATIC) {
            if (!derivativeInitialized) {
                lastInput = eInput;
                derivativeInitialized = true;
            }

            if (TV > 0.0) {
                double dInput = (eInput - lastInput) / stepTime;
                double dxDerivative = (kR * TV * dInput - xDerivative) / T1;
                xDerivative += dxDerivative * stepTime;
                derivativePart = xDerivative;
            } else {
                xDerivative = 0.0;
            }

            lastInput = eInput;
        } else {
            derivativePart = 0.0;
            xDerivative = 0.0;
            lastInput = eInput;
            derivativeInitialized = false;
        }

        if (controlState != ControlCommand.AUTOMATIC) {
            xIntegral = uFollowUp - proportionalPart - derivativePart;
        }

        double dIntegral;
        if (stopIntegrator || controlState != ControlCommand.AUTOMATIC) {
            dIntegral = 0.0;
        } else {
            dIntegral = eInput * kR * stepTime / TN
                        + integralAdaption * stepTime;
        }

        double integralPart = xIntegral + dIntegral;
        double sumControl = proportionalPart + integralPart + derivativePart;

        if (sumControl > uMax) {
            uOutput = uMax;
            xIntegral = uMax - proportionalPart - derivativePart;
        } else if (sumControl < uMin) {
            uOutput = uMin;
            xIntegral = uMin - proportionalPart - derivativePart;
        } else {
            uOutput = sumControl;
            xIntegral = integralPart;
        }

        if (controlState != ControlCommand.AUTOMATIC) {
            uOutput = uFollowUp;
        }
    }

    public double getParameterK() {
        return kR;
    }

    public void setParameterK(double kR) {
        this.kR = kR;
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

    /**
     * Defines the derivative time constant of the controller.
     *
     * @return TV Derivative time constant (in Seconds)
     */
    public double getParameterTV() {
        return TV;
    }

    /**
     * Defines the derivative time constant of the controller.
     *
     * @param TV Derivative time constant to set (in Seconds)
     */
    public void setParameterTV(double TV) {
        this.TV = TV;
    }

    /**
     * Defines the filter time constant for the derivative part.
     * This is only a filter time constant and does not scale the derivative
     * amplitude defined by TV.
     *
     * @return T1 Filter time constant (in Seconds)
     */
    public double getParameterT1() {
        return T1;
    }

    /**
     * Defines the filter time constant for the derivative part.
     * This is only a filter time constant and does not scale the derivative
     * amplitude defined by TV.
     *
     * @param T1 Filter time constant to set (in Seconds)
     */
    public void setParameterT1(double T1) {
        this.T1 = T1;
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
     * controller.
     */
    public void addIntegralAdaptionProvider(DoubleSupplier integralAdaptionProvider) {
        this.integralAdaptionProvider = integralAdaptionProvider;
    }

    /**
     * Restores the state of the controller for the given input and output value
     * pair. This allows loading a save of the controller state even after
     * parameters were changed and restores the integral part in the proper way.
     * The derivative state is reset so re-entry is smooth.
     *
     * @param input value
     * @param output value
     */
    public void acSetCondition(double input, double output) {
        xIntegral = output - input * kR;
        xDerivative = 0.0;
        lastInput = input;
        derivativeInitialized = true;

        uOutput = output;
        eInput = input;

        skipGetFromProvidersAfterLoading = true;
    }
}