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

import java.beans.PropertyChangeEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.hartrusion.mvc.UpdateReceiver;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class ParameterHandler implements ParameterReceiver {

    private final Map<String, PrimitiveBooleanParameter> parametersBoolean = new ConcurrentHashMap<>();
    private final Map<String, PrimitiveDoubleParameter> parametersDouble = new ConcurrentHashMap<>();
    private final Map<String, PrimitiveIntParameter> parametersInt = new ConcurrentHashMap<>();

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
            PrimitiveBooleanParameter param = new PrimitiveBooleanParameter();
            param.setComponent(component);
            param.setValue(value);
            parametersBoolean.put(component, param);
        }
    }

    @Override
    public void setParameterValue(String component, double value) {
        if (parametersDouble.containsKey(component)) {
            parametersDouble.get(component).setValue(value);
        } else {
            PrimitiveDoubleParameter param = new PrimitiveDoubleParameter();
            param.setComponent(component);
            param.setValue(value);
            parametersDouble.put(component, param);
        }
    }

    @Override
    public void setParameterValue(String component, int value) {
        if (parametersInt.containsKey(component)) {
            parametersInt.get(component).setValue(value);
        } else {
            PrimitiveIntParameter param = new PrimitiveIntParameter();
            param.setComponent(component);
            param.setValue(value);
            parametersInt.put(component, param);
        }
    }

    /**
     * Fires all parameters towards an PrimitiveParameterReceiver. This will
     * transfer all values to the object implementing the interface.
     *
     * @param receiver Instance that gets all primitive values fired to.
     */
    public void fireAllToReceiver(ParameterReceiver receiver) {
        if (receiver == this) {
            throw new UnsupportedOperationException(
                    "Can not fire own data to myself.");
        }
        for (Map.Entry<String, PrimitiveBooleanParameter> pair : parametersBoolean.entrySet()) {
            receiver.setParameterValue(pair.getValue().getComponent(), pair.getValue().isValue());
        }
        for (Map.Entry<String, PrimitiveDoubleParameter> pair : parametersDouble.entrySet()) {
            receiver.setParameterValue(pair.getValue().getComponent(), pair.getValue().getValue());
        }
        for (Map.Entry<String, PrimitiveIntParameter> pair : parametersInt.entrySet()) {
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
        for (Map.Entry<String, PrimitiveBooleanParameter> pair : parametersBoolean.entrySet()) {
            receiver.updateComponent(pair.getValue().getComponent(), pair.getValue().isValue());
        }
        for (Map.Entry<String, PrimitiveDoubleParameter> pair : parametersDouble.entrySet()) {
            receiver.updateComponent(pair.getValue().getComponent(), pair.getValue().getValue());
        }
        for (Map.Entry<String, PrimitiveIntParameter> pair : parametersInt.entrySet()) {
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
}
