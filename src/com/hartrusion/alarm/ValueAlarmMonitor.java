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
package com.hartrusion.alarm;

import com.hartrusion.control.ThresholdMonitor;
import java.util.function.BooleanSupplier;

/**
 * Observes a given value and triggers alarms when set values are present.
 * 
 * @author Viktor Alexander Hartung
 */
public class ValueAlarmMonitor extends ThresholdMonitor {

    private final boolean[] active = new boolean[AlarmState.values().length];
    private final double[] threshold = new double[AlarmState.values().length];
    
    private boolean suppressed = false;
    
    private BooleanSupplier suppressionProvider;

    private AlarmState alarmState, oldAlarmState;
    
    private AlarmManager alarmManager;

    @Override
    protected void checkNewValue() {
        if (suppressionProvider != null) {
            suppressed = suppressionProvider.getAsBoolean();
        }
        
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
            if (alarmManager != null) {
                alarmManager.setAlarm(monitorName, alarmState, suppressed);
            }
            
            pcs.firePropertyChange(monitorName + "_AlarmState",
                    oldAlarmState, alarmState);
        }
        oldAlarmState = alarmState;
    }

    /**
     * Sets the alarm to suppressed, this can be used if an alarm can be 
     * ignored due to some other state. For example, there is no need to fire a
     * low flow alarm if the whole part of the system is intentionally set to 
     * offline.
     * 
     * @param suppressAlarm 
     */
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
    
    private void setAlarmManager(AlarmManager am) {
        alarmManager = am;
    }

}
