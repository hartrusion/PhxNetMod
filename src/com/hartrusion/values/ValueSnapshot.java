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
package com.hartrusion.values;

import com.hartrusion.mvc.net.ClassBlueprints;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents
 *
 * @author viktor
 */
public class ValueSnapshot {

    public final long timestamp;
    public final Map<String, Boolean> booleanValues;
    public final Map<String, Double> doubleValues;
    public final Map<String, Integer> intValues;

    public ValueSnapshot(long timestamp,
            Map<String, Boolean> booleanValues,
            Map<String, Double> doubleValues,
            Map<String, Integer> intValues) {
        this.timestamp = timestamp;
        this.booleanValues = booleanValues;
        this.doubleValues = doubleValues;
        this.intValues = intValues;
    }

    /**
     * Adds the ValueSnapshot class (not this specific instance) to the network
     * registry to give it the ability to transfer this kind of object via a
     * data stream.
     *
     * @param registry
     */
    public static void registerToRegistry(ClassBlueprints registry) {
        registry.registerType(ValueSnapshot.class,
                // WRITER (Server-Seite)
                (dos, snap) -> {
                    dos.writeLong(snap.timestamp);

                    // 1. Booleans
                    dos.writeInt(snap.booleanValues.size());
                    for (Map.Entry<String, Boolean> e : snap.booleanValues.entrySet()) {
                        dos.writeUTF(e.getKey());
                        dos.writeBoolean(e.getValue());
                    }

                    // 2. Doubles
                    dos.writeInt(snap.doubleValues.size());
                    for (Map.Entry<String, Double> e : snap.doubleValues.entrySet()) {
                        dos.writeUTF(e.getKey());
                        dos.writeDouble(e.getValue());
                    }

                    // 3. Integers
                    dos.writeInt(snap.intValues.size());
                    for (Map.Entry<String, Integer> e : snap.intValues.entrySet()) {
                        dos.writeUTF(e.getKey());
                        dos.writeInt(e.getValue());
                    }
                },
                // READER (Client-Seite)
                (dis) -> {
                    long timestamp = dis.readLong();

                    // 1. Booleans lesen
                    int boolCount = dis.readInt();
                    if (boolCount < 0 || boolCount > 10000) {
                        throw new IOException("Invalid size");
                    }
                    Map<String, Boolean> bools = new HashMap<>(boolCount);
                    for (int i = 0; i < boolCount; i++) {
                        bools.put(dis.readUTF(), dis.readBoolean());
                    }

                    // 2. Doubles lesen
                    int doubleCount = dis.readInt();
                    if (doubleCount < 0 || doubleCount > 10000) {
                        throw new IOException("Invalid size");
                    }
                    Map<String, Double> doubles = new HashMap<>(doubleCount);
                    for (int i = 0; i < doubleCount; i++) {
                        doubles.put(dis.readUTF(), dis.readDouble());
                    }

                    // 3. Integers lesen
                    int intCount = dis.readInt();
                    if (intCount < 0 || intCount > 10000) {
                        throw new IOException("Invalid size");
                    }
                    Map<String, Integer> ints = new HashMap<>(intCount);
                    for (int i = 0; i < intCount; i++) {
                        ints.put(dis.readUTF(), dis.readInt());
                    }

                    return new ValueSnapshot(timestamp, bools, doubles, ints);
                }
        );
    }
}
