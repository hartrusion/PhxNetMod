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
package com.hartrusion.control;

import com.hartrusion.modeling.automated.PropertyChangeProvider;
import com.hartrusion.modeling.initial.AbstractAC;
import com.hartrusion.modeling.initial.AutomatedConditions;
import com.hartrusion.values.ValueHandler;
import com.hartrusion.values.ValueProvider;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

/**
 * Extends the serial runner with the capability to manage the signal listener
 * and parameter handlers for the submitted objects, also allows saving and
 * loading the automation initial conditions for those objects.
 *
 * @author Viktor Alexander Hartung
 */
public class AutomationRunner extends SerialRunner {

    private PropertyChangeListener signalListener;

    private ValueHandler parameterHandler;

    /**
     * Sets a signal listener instance for all known and future added objects.
     *
     * @param signalListener
     */
    public void setSignalListener(PropertyChangeListener signalListener) {
        this.signalListener = signalListener;
        for (Runnable r : step) {
            if (r instanceof PropertyChangeProvider) {
                ((PropertyChangeProvider) r)
                        .registerSignalListener(signalListener);
            }
        }
    }

    /**
     * Sets a Value Handler for all known and future added objects.
     *
     * @param parameterHandler
     */
    public void setParameterHandler(ValueHandler parameterHandler) {
        this.parameterHandler = parameterHandler;
        for (Runnable r : step) {
            if (r instanceof ValueProvider) {
                ((ValueProvider) r)
                        .registerParameterHandler(parameterHandler);
            }
        }

    }

    @Override
    public void submit(Runnable s) {
        super.submit(s);
        if (parameterHandler != null && s instanceof ValueProvider) {
            ((ValueProvider) s).registerParameterHandler(parameterHandler);
        }
        if (signalListener != null && s instanceof PropertyChangeProvider) {
            ((PropertyChangeProvider) s).registerSignalListener(signalListener);
        }
    }
    
    /**
     * Generates a List which holds all initial conditions for all elements that
     * do have such conditions and are known to this solver.
     *
     * @return List of AbstractIC elements.
     */
    public List<AbstractAC> getCurrentAutomationCondition() {
        List<AbstractAC> acList = new ArrayList<>();
        for (Runnable r : step) {
            if (r instanceof AutomatedConditions) {
                AbstractAC ac
                        = ((AutomatedConditions) r).getState();
                if (ac != null) {
                    acList.add(ac);
                }
            }
        }
        return acList;
    }

    /**
     * Sets an initial condition as the new current state of the network. The
     * provided List of ICs must match to what the solver has, its intended to
     * generate the IC list using getCurrentNetworkCondition method.
     *
     * @param states List of AbstractIC elements.
     */
    public void setRunnablesAutomationCondition(List<AbstractAC> states) {
        for (AbstractAC ac : states) {
            for (Runnable r : step) {
                if (r instanceof AutomatedConditions
                        && r.toString().equals(ac.getObjectName())) {
                    ((AutomatedConditions) r).setAutomationCondition(ac);
                    break;
                }
            }
        }
    }

}
