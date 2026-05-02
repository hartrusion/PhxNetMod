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
package com.hartrusion.modeling.automated;

import com.hartrusion.modeling.initial.AbstractAC;
import com.hartrusion.modeling.initial.ValveManual;

/**
 * A non-existing valve that allows the control system to control something.
 * This is used to generate a valve position without having a valve, usually
 * that position is then used to do other simplified modeling.
 *
 * @author Viktor Alexander Hartung
 */
public class DummyValve extends BaseAutomatedValve implements Runnable {

    public void initOpening(double opening) {
        swControl.forceOutputValue(opening);
    }

    @Override
    public void run() {
        checkSafety();
        
        swControl.run();
        monitor.setInput(swControl.getOutput());
        monitor.run();

        // Send valve position as parameter value for monitoring
        if (outputValues != null) {
            outputValues.setParameterValue(name,
                    swControl.getOutput());
        }
    }

    @Override
    public AbstractAC getState() {
        ValveManual vm = new ValveManual();
        writeToAcStateObject(vm);
        return vm;
    }

}
