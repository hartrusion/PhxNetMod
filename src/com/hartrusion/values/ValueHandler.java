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
package com.hartrusion.values;

import java.beans.PropertyChangeEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.hartrusion.mvc.UpdateReceiver;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages and holds primitive values identified by a string. It is intended to
 * act as an uncompressed live data holder that gets its values written on a
 * cyclic event, like all 100 milliseconds.
 * <p>
 * It holds multiple series of 601 values, one series saves each value, every
 * second, every fifth and so on. With the known step time, a time series can be
 * obtained in real time from this class.
 * <p>
 * Those history arrays always put the newest value on index 0, the provided
 * time base arrays have 0.0 at index 0 and after that, time values are negative
 * as they are referring to past values.
 * <p>
 * The history is designed to be used by the plot library, therefor float
 * precision is used instead of double.
 *
 * @author Viktor Alexander Hartung
 */
public class ValueHandler implements ValueReceiver {

    public ValueHandler() {
        // initialize with 0.1 s, this will also initialize the time series
        // arrays.
        setStepTime(0.1);
    }

    private static final Logger LOGGER = Logger.getLogger(ValueHandler.class.getName());

    private final Map<String, PrimitiveBooleanValue> parametersBoolean
            = new ConcurrentHashMap<>();
    private final Map<String, PrimitiveDoubleValue> parametersDouble
            = new ConcurrentHashMap<>();
    private final Map<String, PrimitiveIntValue> parametersInt
            = new ConcurrentHashMap<>();

    /**
     * Step time in Seconds, used to initialize the time series scales of each
     * parameter object on creation.
     */
    private double stepTime;

    private final float[] times1 = new float[601];
    private final float[] times2 = new float[601];
    private final float[] times5 = new float[601];
    private final float[] times10 = new float[601];
    private final float[] times20 = new float[601];
    private final float[] times50 = new float[601];
    private final float[] times1d60 = new float[601];
    private final float[] times2d60 = new float[601];
    private final float[] times5d60 = new float[601];
    private final float[] times10d60 = new float[601];
    private final float[] times20d60 = new float[601];
    private final float[] times50d60 = new float[601];
    private final float[] times1d3600 = new float[601];
    private final float[] times2d3600 = new float[601];
    private final float[] times5d3600 = new float[601];
    private final float[] times10d3600 = new float[601];
    private final float[] times20d3600 = new float[601];
    private final float[] times50d3600 = new float[601];

    /**
     * To set a parameter via PropertyChangeEvent, used in GUIs.
     *
     * @param evt
     */
    public void processAction(PropertyChangeEvent evt) {
        if (evt.getNewValue() instanceof Boolean) {
            setParameterValue(evt.getPropertyName(), ((Boolean) evt.getNewValue()));
        } else if (evt.getNewValue() instanceof Double) {
            setParameterValue(evt.getPropertyName(), ((Double) evt.getNewValue()));
        } else if (evt.getNewValue() instanceof Integer) {
            setParameterValue(evt.getPropertyName(), (int) ((Integer) evt.getNewValue()));
        }
    }

    @Override
    public void setParameterValue(String component, boolean value) {
        if (parametersBoolean.containsKey(component)) {
            parametersBoolean.get(component).setValue(value);
        } else {
            PrimitiveBooleanValue param = new PrimitiveBooleanValue();
            param.setComponent(component);
            param.setValue(value);
            parametersBoolean.put(component, param);
            LOGGER.log(Level.INFO, "New boolean parameter: " + component);
        }
    }

    @Override
    public void setParameterValue(String component, double value) {
        if (parametersDouble.containsKey(component)) {
            parametersDouble.get(component).setValue(value);
        } else {
            PrimitiveDoubleValue param = new PrimitiveDoubleValue();
            param.setComponent(component);
            param.setValue(value);
            parametersDouble.put(component, param);
            LOGGER.log(Level.INFO, "New double parameter: " + component);
        }
    }

    @Override
    public void setParameterValue(String component, int value) {
        if (parametersInt.containsKey(component)) {
            parametersInt.get(component).setValue(value);
        } else {
            PrimitiveIntValue param = new PrimitiveIntValue();
            param.setComponent(component);
            param.setValue(value);
            parametersInt.put(component, param);
            LOGGER.log(Level.INFO, "New int parameter: " + component);
        }
    }

    /**
     * Fires all parameters towards an PrimitiveParameterReceiver. This will
     * transfer all values to the object implementing the interface.
     *
     * @param receiver Instance that gets all primitive values fired to.
     */
    public void fireAllToReceiver(ValueReceiver receiver) {
        if (receiver == this) {
            throw new UnsupportedOperationException(
                    "Can not fire own data to myself.");
        }
        for (Map.Entry<String, PrimitiveBooleanValue> pair : parametersBoolean.entrySet()) {
            receiver.setParameterValue(pair.getValue().getComponent(), pair.getValue().isValue());
        }
        for (Map.Entry<String, PrimitiveDoubleValue> pair : parametersDouble.entrySet()) {
            receiver.setParameterValue(pair.getValue().getComponent(), pair.getValue().getValue());
        }
        for (Map.Entry<String, PrimitiveIntValue> pair : parametersInt.entrySet()) {
            receiver.setParameterValue(pair.getValue().getComponent(), pair.getValue().getValue());
        }
    }

    /**
     * Fires all known parameters to an update receiver that supports primitive
     * types. The propertyName will be the component.
     *
     * @param receiver A view instance
     */
    public void fireAllToMvcView(UpdateReceiver receiver) {
        for (Map.Entry<String, PrimitiveBooleanValue> pair : parametersBoolean.entrySet()) {
            receiver.updateComponent(pair.getValue().getComponent(), pair.getValue().isValue());
        }
        for (Map.Entry<String, PrimitiveDoubleValue> pair : parametersDouble.entrySet()) {
            receiver.updateComponent(pair.getValue().getComponent(), pair.getValue().getValue());
        }
        for (Map.Entry<String, PrimitiveIntValue> pair : parametersInt.entrySet()) {
            receiver.updateComponent(pair.getValue().getComponent(), pair.getValue().getValue());
        }
    }

    public boolean getParameterBoolean(String component) {
        return parametersBoolean.get(component).isValue();
    }

    public int getParameterInt(String component) {
        return parametersInt.get(component).getValue();
    }

    public double getParameterDouble(String component) {
        return parametersDouble.get(component).getValue();
    }

    /**
     * Sets the time difference between each setParameterValue call. This
     * initializes the time series arrays.
     *
     * @param stepTime Time in Seconds (Default: 0.1)
     */
    public void setStepTime(double stepTime) {
        this.stepTime = stepTime;

        for (int idx = 1; idx < 601; idx++) {
            times1[idx] = -(float) idx * (float) stepTime;
            times2[idx] = -(float) idx * (float) stepTime * 2.0F;
            times5[idx] = -(float) idx * (float) stepTime * 5.0F;
            times10[idx] = -(float) idx * (float) stepTime * 10.0F;
            times20[idx] = -(float) idx * (float) stepTime * 20.0F;
            times50[idx] = -(float) idx * (float) stepTime * 50.0F;
            times1d60[idx] = -(float) idx * (float) stepTime / 60.0F;
            times2d60[idx] = -(float) idx * (float) stepTime * 2.0F / 60.0F;
            times5d60[idx] = -(float) idx * (float) stepTime * 5.0F / 60.0F;
            times10d60[idx] = -(float) idx * (float) stepTime * 10.0F / 60.0F;
            times20d60[idx] = -(float) idx * (float) stepTime * 20.0F / 60.0F;
            times50d60[idx] = -(float) idx * (float) stepTime * 50.0F / 60.0F;
            times1d3600[idx] = -(float) idx * (float) stepTime / 3600.0F;
            times2d3600[idx] = -(float) idx * (float) stepTime * 2.0F / 3600.0F;
            times5d3600[idx] = -(float) idx * (float) stepTime * 5.0F / 3600.0F;
            times10d3600[idx] = -(float) idx * (float) stepTime * 10.0F / 3600.0F;
            times20d3600[idx] = -(float) idx * (float) stepTime * 20.0F / 3600.0F;
            times50d3600[idx] = -(float) idx * (float) stepTime * 50.0F / 3600.0F;
        }
    }

    /**
     * Returns a history (array) of a double parameter value.
     *
     * @param component String to identify the value.
     * @param div Time divider, can be 1, 2, 5, 10, 20 or 50. Describes which
     * nTh value was logged with 50 being the longest time.
     * @return
     */
    public float[] getParameterDoubleSeries(String component, int div) {
        switch (div) {
            case 1:
                return parametersDouble.get(component).getValues1();
            case 2:
                return parametersDouble.get(component).getValues2();
            case 5:
                return parametersDouble.get(component).getValues5();
            case 10:
                return parametersDouble.get(component).getValues10();
            case 20:
                return parametersDouble.get(component).getValues20();
            case 50:
                return parametersDouble.get(component).getValues50();
            default:
                throw new IllegalArgumentException("Wrong div argument.");
        }
    }

    /**
     * Returns a time scale with the same unites as the stepTime (seconds).
     * Intended to be used with plot command.
     *
     * @param div Can be 1, 2, 5, 10, 20 or 50
     * @return Array of Float with time values.
     */
    public float[] getTime(int div) {
        switch (div) {
            case 1:
                return times1;
            case 2:
                return times2;
            case 5:
                return times5;
            case 10:
                return times10;
            case 20:
                return times20;
            case 50:
                return times50;
            default:
                throw new IllegalArgumentException("Wrong div argument.");
        }
    }

    /**
     * Returns a time scale with the same unites as the stepTime (seconds)
     * divided by 60 (making them minutes). Intended to be used with plot
     * command.
     *
     * @param div Can be 1, 2, 5, 10, 20 or 50
     * @return Array of Float with time values.
     */
    public float[] getTime60(int div) {
        switch (div) {
            case 1:
                return times1d60;
            case 2:
                return times2d60;
            case 5:
                return times5d60;
            case 10:
                return times10d60;
            case 20:
                return times20d60;
            case 50:
                return times50d60;
            default:
                throw new IllegalArgumentException("Wrong div argument.");
        }
    }

    /**
     * Returns a time scale with the same unites as the stepTime (seconds)
     * divided by 3600 (making them hours). Intended to be used with plot
     * command.
     *
     * @param div Can be 1, 2, 5, 10, 20 or 50
     * @return Array of Float with time values.
     */
    public float[] getTime3600(int div) {
        switch (div) {
            case 1:
                return times1d3600;
            case 2:
                return times2d3600;
            case 5:
                return times5d3600;
            case 10:
                return times10d3600;
            case 20:
                return times20d3600;
            case 50:
                return times50d3600;
            default:
                throw new IllegalArgumentException("Wrong div argument.");
        }
    }

    /**
     * Erzeugt einen zustandslosen Snapshot der aktuellsten Werte aller
     * Parameter. Sollte aus demselben Thread aufgerufen werden, der auch
     * setParameterValue aufruft, um inkonsistente Zwischenzustände während des
     * Iterierens zu vermeiden.
     *
     * @return Ein ValueSnapshot Objekt mit den reinen Werten.
     */
    public ValueSnapshot getSnapshot() {
        // Initiale Kapazität setzen, um unnötiges Resizing zu vermeiden
        java.util.Map<String, Boolean> bools = new java.util.HashMap<>(parametersBoolean.size());
        for (Map.Entry<String, PrimitiveBooleanValue> entry : parametersBoolean.entrySet()) {
            bools.put(entry.getKey(), entry.getValue().isValue());
        }

        java.util.Map<String, Double> doubles = new java.util.HashMap<>(parametersDouble.size());
        for (Map.Entry<String, PrimitiveDoubleValue> entry : parametersDouble.entrySet()) {
            doubles.put(entry.getKey(), entry.getValue().getValue());
        }

        java.util.Map<String, Integer> ints = new java.util.HashMap<>(parametersInt.size());
        for (Map.Entry<String, PrimitiveIntValue> entry : parametersInt.entrySet()) {
            ints.put(entry.getKey(), entry.getValue().getValue());
        }

        return new ValueSnapshot(System.currentTimeMillis(), bools, doubles, ints);
    }

    /**
     * Verarbeitet einen vom Server empfangenen Snapshot und schreibt alle Werte
     * in die lokale Historie des Clients fort.
     *
     * @param snapshot Der über das Netzwerk empfangene Snapshot.
     */
    public void processSnapshot(ValueSnapshot snapshot) {
        for (Map.Entry<String, Boolean> entry : snapshot.booleanValues.entrySet()) {
            setParameterValue(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Double> entry : snapshot.doubleValues.entrySet()) {
            setParameterValue(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<String, Integer> entry : snapshot.intValues.entrySet()) {
            setParameterValue(entry.getKey(), entry.getValue());
        }
    }
}
