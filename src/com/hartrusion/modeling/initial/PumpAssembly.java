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
package com.hartrusion.modeling.initial;

import com.hartrusion.modeling.automated.PumpState;
import java.time.Duration;

/**
 * Allows saving the state of a pump assembly with two valves, including a
 * current state of the state machine that can be restored.
 *
 * @author Viktor Alexander Hartung
 */
public class PumpAssembly extends AbstractAC {

    private double suctionValvePosition;
    private double suctionValveTarget;
    private double dischargeValvePosition;
    private double dischargeValveTarget;
    private PumpState pumpState;
    private Duration stateTime;
    private Duration switchOnTime;

    public double getSuctionValvePosition() {
        return suctionValvePosition;
    }

    public void setSuctionValvePosition(double suctionValvePosition) {
        this.suctionValvePosition = suctionValvePosition;
    }

    public double getSuctionValveTarget() {
        return suctionValveTarget;
    }

    public void setSuctionValveTarget(double suctionValveTarget) {
        this.suctionValveTarget = suctionValveTarget;
    }

    public double getDischargeValvePosition() {
        return dischargeValvePosition;
    }

    public void setDischargeValvePosition(double dischargeValvePosition) {
        this.dischargeValvePosition = dischargeValvePosition;
    }

    public double getDischargeValveTarget() {
        return dischargeValveTarget;
    }

    public void setDischargeValveTarget(double dischargeValveTarget) {
        this.dischargeValveTarget = dischargeValveTarget;
    }

    public PumpState getPumpState() {
        return pumpState;
    }

    public void setPumpState(PumpState pumpState) {
        this.pumpState = pumpState;
    }

    public Duration getStateTime() {
        return stateTime;
    }

    public void setStateTime(Duration stateTime) {
        this.stateTime = stateTime;
    }

    public Duration getSwitchOnTime() {
        return switchOnTime;
    }

    public void setSwitchOnTime(Duration switchOnTime) {
        this.switchOnTime = switchOnTime;
    }
}
