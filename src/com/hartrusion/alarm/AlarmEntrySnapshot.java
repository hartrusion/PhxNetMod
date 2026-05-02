/*
 * The MIT License
 *
 * Copyright 2026 viktor.
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

/**
 * Network transfer object for one alarm objects contents.
 *
 * @author Viktor Alexander Hartung
 */
public class AlarmEntrySnapshot {

    public final String component;
    public final String description;
    public final AlarmState state;
    public final boolean suppressed;
    public final boolean acknowledged;

    public AlarmEntrySnapshot(String component,
            String description,
            AlarmState state,
            boolean suppressed,
            boolean acknowledged) {
        this.component = component;
        this.description = description;
        this.state = state;
        this.suppressed = suppressed;
        this.acknowledged = acknowledged;
    }

    public static AlarmEntrySnapshot fromAlarmObject(AlarmObject alarm) {
        return new AlarmEntrySnapshot(
                alarm.getComponent(),
                alarm.getDescription(),
                alarm.getState(),
                alarm.isSuppressed(),
                alarm.isAcknowledged()
        );
    }

    public AlarmObject toAlarmObject() {
        AlarmObject alarm = new AlarmObject(component);
        alarm.setDescription(description);
        alarm.setState(state);
        alarm.setSuppressed(suppressed);
        alarm.setAcknowledged(acknowledged);
        return alarm;
    }
}