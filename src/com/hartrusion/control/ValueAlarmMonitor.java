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
public class ValueAlarmMonitor extends ThresholdMonitor {

    private final boolean[] active = new boolean[AlarmState.values().length];
    private final double[] threshold = new double[AlarmState.values().length];

    private boolean suppressed = false;

    private AlarmState alarmState, oldAlarmState;

    @Override
    protected void checkNewValue() {
        if (suppressed) {
            alarmState = AlarmState.NONE;
        } else if (active[AlarmState.MAX2.ordinal()]
                && value >= threshold[AlarmState.MAX2.ordinal()]) {
            alarmState = AlarmState.MAX2;
        } else if (active[AlarmState.MAX1.ordinal()]
                && value >= threshold[AlarmState.MAX1.ordinal()]) {
            alarmState = AlarmState.MAX1;
        } else if (active[AlarmState.HIGH2.ordinal()]
                && value >= threshold[AlarmState.HIGH2.ordinal()]) {
            alarmState = AlarmState.HIGH2;
        } else if (active[AlarmState.HIGH1.ordinal()]
                && value >= threshold[AlarmState.HIGH1.ordinal()]) {
            alarmState = AlarmState.HIGH1;
        } else if (active[AlarmState.MIN2.ordinal()]
                && value <= threshold[AlarmState.MIN2.ordinal()]) {
            alarmState = AlarmState.MIN2;
        } else if (active[AlarmState.MIN1.ordinal()]
                && value <= threshold[AlarmState.MIN1.ordinal()]) {
            alarmState = AlarmState.MIN1;
        } else if (active[AlarmState.LOW2.ordinal()]
                && value <= threshold[AlarmState.LOW2.ordinal()]) {
            alarmState = AlarmState.LOW2;
        } else if (active[AlarmState.LOW1.ordinal()]
                && value <= threshold[AlarmState.LOW1.ordinal()]) {
            alarmState = AlarmState.LOW1;
        } else {
            alarmState = AlarmState.NONE;
        }

        if (alarmState != oldAlarmState) {
            
            pcs.firePropertyChange(monitorName + "_Alarm",
                    oldAlarmState, alarmState);
        }
        oldAlarmState = alarmState;
    }

    private void setSuppressed(boolean suppressAlarm) {
        suppressed = suppressAlarm;
    }

    private void enableAlarm(double value, AlarmState alarmState) {
        active[alarmState.ordinal()] = true;
        threshold[alarmState.ordinal()] = value;
    }

    private void disableAlarm(AlarmState alarmState) {
        active[alarmState.ordinal()] = false;
    }

}
