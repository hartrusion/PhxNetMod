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
package com.hartrusion.modeling.assemblies;

import java.time.Duration;
import java.time.Instant;

/**
 * Extends the heated fluid pump to a more realistic behaviour with some extras
 * for usage by an operator.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFluidPumpRealSwitching extends HeatFluidPump {

    private boolean ready;
    private Instant switchTime;
    
    /**
     * Describes the state of the startup (and shutdown) procedure.
     * 
     */
    private int state;

    @Override
    public void run() {
        super.run();

        // get current time
        Instant now = Instant.now();

        ready = suctionControl.getOutput() >= 95
                && dischargeControl.getOutput() <= 1.0
                && !pumpState;

        switch (state) {
            case 0:
                if (ready) {
                    state = 1;
                }
                break;
            case 2: // pump on switching requested
                if (Duration.between(switchTime, now).toMillis() >= 200) {
                    pcs.firePropertyChange(pump.toString() + "_StartupState",
                            state, 3); // make pump ready
                    switchTime = Instant.now();
                    state = 3;
                }
                break;
            case 3: // 
                if (Duration.between(switchTime, now).toMillis() >= 1200) {
                    pcs.firePropertyChange(pump.toString() + "_StartupState",
                            state, 4); // begin startup
                    switchTime = Instant.now(); 
                    state = 4;
                }
                break;
            case 4:
                if (Duration.between(switchTime, now).toMillis() >= 700) {
                    pcs.firePropertyChange(pump.toString() + "_StartupState",
                            state, 5); // pump running
                    switchTime = Instant.now(); 
                    state = 5;
                    super.operateStartPump();
                }
                break;
        }
    }

    @Override
    public void operateStartPump() {
        if (state == 1) {
            state = 2; // switch state machine
            switchTime = Instant.now();
        }
    }

    @Override
    public void operateStopPump() {
        state = 0;
        super.operateStopPump();
    }

    @Override
    public void setInitialCondition(boolean pumpActive, boolean suctionOpen,
            boolean dischargeOpen) {
        super.setInitialCondition(pumpActive, suctionOpen, dischargeOpen);
        if (pumpActive && suctionOpen && dischargeOpen) {
            state = 5;
        }
    }
}
