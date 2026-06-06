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
package com.hartrusion.control;

/**
 * A two-point control that sets to max output on a certain value and to min
 * output on another value.
 * <p>
 * If input is bigger than hysteresis/2, the output will be set to maxOutput. If
 * input is less than -hysteresis/2, the output will be set to minOutput. To
 * reverse this behavior, use setReversed.
 *
 * @author Viktor Alexander Hartung
 */
public class TwoPointControl extends AbstractController {

    private double hysteresis = 5.0;

    private boolean reversed;

    @Override
    public void run() {
        super.run();

        if (controlState != ControlCommand.AUTOMATIC) {
            // Just use and limit followup value
            if (uFollowUp > uMax) {
                uOutput = uMax;
            } else if (uFollowUp < uMin) {
                uOutput = uMin;
            } else {
                uOutput = uFollowUp;
            }
        } else {
            // 2 point logic:            
            if (eInput > hysteresis) {
                if (reversed) {
                    uOutput = uMin;
                } else {
                    uOutput = uMax;
                }

            } else if (eInput < -hysteresis) {
                if (reversed) {
                    uOutput = uMax;
                } else {
                    uOutput = uMin;
                }

            }
        }
    }
    
    /**
     * Gets the hysteresis of the controller. The hysteresis is the distance 
     * between the two points where the output changes. The controller will set 
     * the output to maxOutput if the input is bigger than hysteresis/2 and 
     * to minOutput if the input is less than -hysteresis/2.
     * 
     * @return the hysteresis of the controller
     */
    public double getHysteresis() {
        return hysteresis * 2.0;
    }

    /** 
     * Sets the hysteresis of the controller. The hysteresis is the distance 
     * between the two points where the output changes. The controller will set 
     * the output to maxOutput if the input is bigger than hysteresis/2 and 
     * to minOutput if the input is less than -hysteresis/2.
     *
     * @param hysteresis the hysteresis of the controller
     */
    public void setHysteresis(double hysteresis) {
        this.hysteresis = hysteresis / 2.0;
    }

    public boolean isReversed() {
        return reversed;
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }
    
    /**
     * Restores the state of the controller for the given input and output value
     * pair. This allows loading a save of the controller state even after
     * parameters were changed and restores the output part to the saved value.
     *
     * @param input value
     * @param output value
     */
    public void acSetCondition(double input, double output) {
        uOutput = output;
        eInput = input;

        skipGetFromProvidersAfterLoading = true;
    }
}
