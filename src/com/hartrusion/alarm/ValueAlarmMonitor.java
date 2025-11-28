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
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Observes a given value and triggers alarms when set values are present.
 * <p>
 * If an AlarmManager is known, the alarm will be set to the AlarmManager.
 * <p>
 * Alarm events can be defined by providing an AlarmAction class, this class
 * has a run method which will be called once if the alarm is triggered.
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

    /**
     * Holds alarm actions that will be called when a certain alarm state is
     * reached the first time.
     */
    private final Map<AlarmState, AlarmAction> actions = new HashMap<>();

    @Override
    protected void checkNewValue() {
        // true if the changed alarm state has higher priority than the
        // previous set state (used to fire actions)
        boolean higherPriority = false;

        if (suppressionProvider != null) {
            suppressed = suppressionProvider.getAsBoolean();
        }

        if (suppressed) {
            alarmState = AlarmState.NONE;
        } else if (active[AlarmState.MAX2.ordinal()]
                && value >= threshold[AlarmState.MAX2.ordinal()]) {
            alarmState = AlarmState.MAX2;
            higherPriority = true;
        } else if (active[AlarmState.MAX1.ordinal()]
                && value >= threshold[AlarmState.MAX1.ordinal()]) {
            alarmState = AlarmState.MAX1;
            higherPriority = oldAlarmState == AlarmState.HIGH2
                    || oldAlarmState == AlarmState.HIGH1
                    || oldAlarmState == AlarmState.NONE;
        } else if (active[AlarmState.HIGH2.ordinal()]
                && value >= threshold[AlarmState.HIGH2.ordinal()]) {
            alarmState = AlarmState.HIGH2;
            higherPriority = oldAlarmState == AlarmState.HIGH1
                    || oldAlarmState == AlarmState.NONE;
        } else if (active[AlarmState.HIGH1.ordinal()]
                && value >= threshold[AlarmState.HIGH1.ordinal()]) {
            alarmState = AlarmState.HIGH1;
            higherPriority = oldAlarmState == AlarmState.NONE;
        } else if (active[AlarmState.MIN2.ordinal()]
                && value <= threshold[AlarmState.MIN2.ordinal()]) {
            alarmState = AlarmState.MIN2;
            higherPriority = true; // min2 is the highest prio of low values.
        } else if (active[AlarmState.MIN1.ordinal()]
                && value <= threshold[AlarmState.MIN1.ordinal()]) {
            alarmState = AlarmState.MIN1;
            higherPriority = oldAlarmState == AlarmState.LOW2
                    || oldAlarmState == AlarmState.LOW1
                    || oldAlarmState == AlarmState.NONE;
        } else if (active[AlarmState.LOW2.ordinal()]
                && value <= threshold[AlarmState.LOW2.ordinal()]) {
            alarmState = AlarmState.LOW2;
            higherPriority = oldAlarmState == AlarmState.LOW1
                    || oldAlarmState == AlarmState.NONE;
        } else if (active[AlarmState.LOW1.ordinal()]
                && value <= threshold[AlarmState.LOW1.ordinal()]) {
            alarmState = AlarmState.LOW1;
            higherPriority = oldAlarmState == AlarmState.NONE;
        } else {
            alarmState = AlarmState.NONE;
        }

        if (alarmState != oldAlarmState) {
            // If there's an alarm manager set, set the
            if (alarmManager != null) {
                alarmManager.fireAlarm(monitorName, alarmState, suppressed);
            }
            
            // Run assigned alarm action if there is one assigned to the state.
            if (higherPriority) {
                if (actions.containsKey(alarmState)) {
                    actions.get(alarmState).run();
                    Logger.getLogger(ValueAlarmMonitor.class.getName())
                            .log(Level.INFO, monitorName
                                    + " called AlarmAction due to new state "
                                    + alarmState);
                }
            }

            pcs.firePropertyChange(monitorName + "_AlarmState",
                    oldAlarmState, alarmState);
        }
        oldAlarmState = alarmState;
    }

    /**
     * Sets the alarm to suppressed, this can be used if an alarm can be ignored
     * due to some other state. For example, there is no need to fire a low flow
     * alarm if the whole part of the system is intentionally set to offline.
     *
     * @param suppressAlarm true to suppress this monitor.
     */
    private void setSuppressed(boolean suppressAlarm) {
        suppressed = suppressAlarm;
    }

    /**
     * Enables an alarm state with the given value.
     *
     * @param value Value where the alarm should trigger
     * @param alarmState State of the alarm which has to be set (MAX2, MIN,..)
     */
    public void defineAlarm(double value, AlarmState alarmState) {
        active[alarmState.ordinal()] = true;
        threshold[alarmState.ordinal()] = value;
    }

    /**
     * Makes an alarm manager instance known to this monitor. It will put its
     * alarms automatically to this alarm manager.
     *
     * @param am AlarmManager instance
     */
    public void registerAlarmManager(AlarmManager am) {
        alarmManager = am;
    }

    public void addAlarmAction(AlarmAction alarmAction) {
        if (actions.containsKey(alarmAction.getState())) {
            throw new IllegalArgumentException("AlarmAction with this state "
                    + "was already added.");
        }
        actions.put(alarmAction.getState(), alarmAction);
    }

}
