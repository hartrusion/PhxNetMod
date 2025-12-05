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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holds all active alarm objects. It manages those objects by itself, meaning
 * it also generates the alarm objects if they do not exist yest.
 *
 * @author Viktor Alexander Hartung
 */
public class AlarmManager {

    private final Map<String, AlarmObject> alarmObjects
            = new ConcurrentHashMap<>();

    /**
     * Sets an alarm. Can be used to update or initialize an AlarmObject.
     *
     * @param component To identify the alarm itself
     * @param state Alarm state
     * @param suppressed The alarm is suppressed
     */
    public void fireAlarm(String component,
            AlarmState state, boolean suppressed) {
        AlarmObject a;
        // If the alarm object was not initialized before, create a new one.
        if (!alarmObjects.containsKey(component)) {
            a = new AlarmObject(component);
            alarmObjects.put(a.getComponent(), a);
        } else {
            a = alarmObjects.get(component);
        }

        AlarmState oldState = a.getState();
        a.setState(state);
        a.setSuppressed(suppressed);

        // Log all alarm events
        Logger.getLogger(AlarmManager.class.getName())
                .log(Level.INFO, "Updated Alarm: " + component
                        + ", Old state: " + oldState
                        + ", New state: " + state);
    }

    /**
     * Checks if a provided alarm is active. Alarms with higher priority will
     * also count for lower properties, for example, if an alarm is present with
     * state MAX2 and the provided state argument is HIGH2, the method will
     * return true as MAX2 is of a higher value than HIGH2.
     *
     * @param component String to identify the alarm object
     * @param state AlarmState to check
     * @return true if the alarm with given or higher priority state is active.
     */
    public boolean isAlarmActive(String component, AlarmState state) {
        if (!alarmObjects.containsKey(component)) {
            return false; // an unknown alarm can not be active.
        }
        AlarmObject alarmObject = alarmObjects.get(component);
        // Those to states require strict compare.
        if (state == AlarmState.NONE
                || state == AlarmState.ACTIVE) {
            return alarmObject.getState() == state;
        }
        // is there an alarm active at all? 
        if (alarmObject.getState() == AlarmState.NONE) {
            return false;
        }
        // check if equals or even a higher priority is active. 
        return ComparePriority.includes(alarmObject.getState(), state);
    }
}
