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
 * Extends the heated fluid pump to a more realistic behavior with some extras
 * for usage by an operator.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFluidPumpRealSwitching extends HeatFluidPump {

    private boolean ready;
    private Instant stateTime; // for state machine control
    private Instant switchOnTime;

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

        if (switchOnTime == null) {
            ready = suctionControl.getOutput() >= 95
                    && dischargeControl.getOutput() <= 1.0;
        } else {
            // if there is a switch-on-time recorded, some time must pass as 
            // its not allowed to turn on the pump immediately.
            ready = suctionControl.getOutput() >= 95
                    && dischargeControl.getOutput() <= 1.0
                    && Duration.between(switchOnTime, now).toMillis() >= 30000;
        }

        switch (state) {
            case 0: // inactive
                if (ready) {
                    state = 1;
                    stateTime = Instant.now();
                }
                break;
            case 1: // ready time delay
                if (Duration.between(stateTime, now).toMillis() >= 1500) {
                    pumpState = PumpState.READY;
                    state = 2;
                }
            case 2: // Pump ready to be switched on
                if (!ready) { // no more ready state 
                    state = 0;
                    pumpState = PumpState.OFFLINE;
                }
                break;
            case 3: // Start the startup phase after short delay
                if (Duration.between(stateTime, now).toMillis() >= 800) {
                    stateTime = Instant.now();
                    pumpState = PumpState.STARTUP;
                    state = 4;
                } else if (!ready) { // abort
                    state = 0;
                    pumpState = PumpState.OFFLINE;
                }
                break;
            case 4: // Startup phase
                if (Duration.between(stateTime, now).toMillis() >= 3000) {
                    stateTime = Instant.now();
                    state = 5;
                    // Remember this time for restart lock time
                    switchOnTime = Instant.now();
                    super.operateStartPump(); // this will set pumpState
                } else if (!ready) { // abort
                    state = 0;
                    pumpState = PumpState.OFFLINE;
                }
                break;
            case 5: // Pump is running
                // nothing to automate so far
                break;
        }
    }

    @Override
    public void operateStartPump() {
        if (state == 2) {
            state = 3; // switch state machine
            stateTime = Instant.now();
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
