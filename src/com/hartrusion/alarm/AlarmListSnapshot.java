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

import com.hartrusion.mvc.net.ClassBlueprints;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Flat network transfer object for the full current alarm list.
 *
 * @author Viktor Alexander Hartung
 */
public class AlarmListSnapshot {

    public final List<AlarmEntrySnapshot> alarms;

    public AlarmListSnapshot(List<AlarmEntrySnapshot> alarms) {
        this.alarms = alarms;
    }

    public static AlarmListSnapshot fromAlarmList(List<AlarmObject> alarmList) {
        List<AlarmEntrySnapshot> snapshotEntries
                = new ArrayList<>(alarmList.size());
        for (AlarmObject alarm : alarmList) {
            snapshotEntries.add(AlarmEntrySnapshot.fromAlarmObject(alarm));
        }
        return new AlarmListSnapshot(snapshotEntries);
    }

    public List<AlarmObject> toAlarmList() {
        List<AlarmObject> result = new ArrayList<>(alarms.size());
        for (AlarmEntrySnapshot entry : alarms) {
            result.add(entry.toAlarmObject());
        }
        return result;
    }

    public static void registerToRegistry(ClassBlueprints registry) {
        registry.registerType(AlarmListSnapshot.class,
                (dos, snapshot) -> {
                    dos.writeInt(snapshot.alarms.size());
                    for (AlarmEntrySnapshot entry : snapshot.alarms) {
                        dos.writeUTF(entry.component);
                        dos.writeUTF(entry.description != null ? entry.description : "");
                        registry.writeObject(dos, entry.state);
                        dos.writeBoolean(entry.suppressed);
                        dos.writeBoolean(entry.acknowledged);
                    }
                },
                (dis) -> {
                    int size = dis.readInt();
                    if (size < 0 || size > 10000) {
                        throw new IOException("Invalid alarm list size: " + size);
                    }

                    List<AlarmEntrySnapshot> alarms = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) {
                        String component = dis.readUTF();
                        String description = dis.readUTF();
                        AlarmState state = (AlarmState) registry.readObject(dis);
                        boolean suppressed = dis.readBoolean();
                        boolean acknowledged = dis.readBoolean();

                        alarms.add(new AlarmEntrySnapshot(
                                component,
                                description,
                                state,
                                suppressed,
                                acknowledged
                        ));
                    }
                    return new AlarmListSnapshot(alarms);
                });
    }
}
