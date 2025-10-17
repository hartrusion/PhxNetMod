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
 * Extends the threshold monitor to monitor a valves state (0 to 100 %) and fire
 * PropertyChangeEvent with _Pos for describing valve position
 *
 * @author Viktor Alexander Hartung
 */
public class ValveActuatorMonitor extends ThresholdMonitor {

    ValveState valveState, oldValveState;

    @Override
    protected void checkNewValue() {
        if (value >= 80.0) {
            valveState = ValveState.OPEN;
        } else if (value <= 5) {
            valveState = ValveState.CLOSED;
        } else {
            valveState = ValveState.INTERMEDIATE;
        }
        if (valveState != oldValveState) {
            pcs.firePropertyChange(monitorName + "_Pos",
                    oldValveState, valveState);
        }
        oldValveState = valveState;
    }
}
