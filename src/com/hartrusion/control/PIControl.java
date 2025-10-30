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

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PIControl extends AbstractController {

    private double kR = 1.0;
    private double TN = 10;

    private boolean stopIntegrator;
    private double xIntegral;

    @Override
    public void run() {
        super.run();
        
        double dIntegral;

        if (stopIntegrator) {
            dIntegral = 0;
        } else {
            dIntegral = eInput * stepTime / TN;
        }

        double proportionalPart = eInput * kR;

        // Manual mode sets the integrator properly so the output matches the
        // followUp input value.
        if (manualMode) {
            xIntegral = uFollowUp - dIntegral - proportionalPart;
        }

        double integralPart = xIntegral + dIntegral;

        double sumControl = integralPart + proportionalPart;

        // In case the sum would exceed the limit, the integral part will
        // be limited so the controller does not run away.
        if (sumControl > uMax) {
            uOutput = uMax;
            xIntegral = uMax - proportionalPart - integralPart;
        } else if (sumControl < uMin) {
            uOutput = uMin;
            xIntegral = uMin - proportionalPart - integralPart;
        } else { // default: sum up to integrate
            uOutput = sumControl;
            xIntegral = integralPart; // assign what was summed up previously
        }

        if (manualMode) { // overwrite output value
            uOutput = uFollowUp;
        }
    }

    public double getParameterK() {
        return kR;
    }

    public void setParameterK(double kR) {
        this.kR = kR;
    }

    public double getParameterTN() {
        return TN;
    }

    public void setParameterTN(double TN) {
        this.TN = TN;
    }

    public boolean isStopIntegrator() {
        return stopIntegrator;
    }

    public void setStopIntegrator(boolean sti) {
        this.stopIntegrator = sti;
    }

}
