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
package com.hartrusion.modeling.automated;

import com.hartrusion.alarm.AlarmManager;
import com.hartrusion.alarm.AlarmState;
import com.hartrusion.values.ValueHandler;
import com.hartrusion.control.SetpointIntegrator;
import com.hartrusion.control.ValveActuatorMonitor;
import com.hartrusion.modeling.initial.AbstractAC;
import com.hartrusion.modeling.initial.AutomatedConditions;
import com.hartrusion.modeling.initial.ValveManual;
import com.hartrusion.mvc.ActionCommand;
import com.hartrusion.values.ValueProvider;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.function.BooleanSupplier;

/**
 * Base class with common valve control elements.
 * <p>
 * Safe Closed: Valve will stay closed if condition for opening is not met.
 * <p>
 * Safe Open: Valve will stay open if condition for closing is not met, this 
 * can be used for safety relief valves.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class BaseAutomatedValve implements ValueProvider,
        PropertyChangeProvider, AutomatedConditions {

    /**
     * Generates limited values to mimic motor drive behavior.
     */
    protected final SetpointIntegrator swControl
            = new SetpointIntegrator();

    protected final ValveActuatorMonitor monitor
            = new ValveActuatorMonitor();

    protected final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    protected ValueHandler outputValues;

    protected boolean safeOpen = true;
    private BooleanSupplier safeOpenProvider;
    protected boolean safeClosed = true;
    private BooleanSupplier safeClosedProvider;

    private boolean oldSafeOpen, oldSafeClosed;

    private AlarmManager alarmManager;
    private String safeOpenAlarmMessage;
    private String safeCloseAlarmMessage;
    
    private boolean skipSafetyCheckOnLoad = true; // also for starting the sim

    public BaseAutomatedValve() {
        swControl.setMaxRate(25);
        swControl.setUpperLimit(100);
        swControl.setLowerLimit(-5.0);
    }

    protected String name = "unnamedAutomatedValve";

    protected void checkSafety() {
        if (skipSafetyCheckOnLoad) {
            skipSafetyCheckOnLoad = false;
            return;
        } else {
            // This is a bad workarodund, loading a state might have some
            // old valvues on nodes which are checked during safety, causing
            // some issues, so the values on first cycle are kept from the 
            // loaded state, the second and all cycles after that will then
            // use the proper checks.
            if (safeOpenProvider != null) {
                safeOpen = safeOpenProvider.getAsBoolean();
            }
            if (safeClosedProvider != null) {
                safeClosed = safeClosedProvider.getAsBoolean();
            }
        }

        // Force valve open or closed if safety signal is missing
        if (!safeClosed) {
            swControl.setInputMin();
        } else if (!safeOpen) {
            swControl.setInputMax();
        }

        // Send alarms if configured.
        if (alarmManager != null) {
            if (oldSafeOpen != safeOpen && safeOpenAlarmMessage != null) {
                if (safeOpen) {
                    alarmManager.fireAlarm(safeOpenAlarmMessage,
                            AlarmState.NONE, false);
                } else { // Valve is now no longer safe to be closed
                    alarmManager.fireAlarm(safeOpenAlarmMessage,
                            AlarmState.ACTIVE, false);
                }
            }

            if (oldSafeClosed != safeClosed && safeCloseAlarmMessage != null) {
                if (safeClosed) {
                    alarmManager.fireAlarm(safeCloseAlarmMessage,
                            AlarmState.NONE, false);
                } else { // Valve is now no longer safe to be opened
                    alarmManager.fireAlarm(safeCloseAlarmMessage,
                            AlarmState.ACTIVE, false);
                }
            }
        }

        // remember previous value.
        oldSafeOpen = safeOpen;
        oldSafeClosed = safeClosed;
    }

    /**
     * Optionally: Allows to set a message that will be fired to an alarm
     * manager with ACTIVE alarm state in case of the valve being forced to
     * OPEN by the safety condition.
     *
     * @param alarmMessage Alarm Message to set to ACTIVE
     * @param alarmManager The alarm manager which will handle alarms.
     */
    public void initSafeOpenAlarmMessage(String alarmMessage,
            AlarmManager alarmManager) {
        safeOpenAlarmMessage = alarmMessage;
        if (alarmManager != null) {
            this.alarmManager = alarmManager;
        }
    }

    /**
     * Optionally: Allows to set a message that will be fired to an alarm
     * manager with ACTIVE alarm state in case of the valve being forced to OPEN
     * by the safety condition.
     *
     * @param alarmMessage Alarm Message to set to ACTIVE
     * @param alarmManager The alarm manager which will handle alarms.
     */
    public void initSafeCloseAlarmMessage(String alarmMessage,
            AlarmManager alarmManager) {
        safeCloseAlarmMessage = alarmMessage;
        if (alarmManager != null) {
            this.alarmManager = alarmManager;
        }
    }

    public void initName(String name) {
        this.name = name;
        monitor.setName(name);
    }

    @Override
    public void registerSignalListener(PropertyChangeListener signalListener) {
        pcs.addPropertyChangeListener(signalListener);
        monitor.addPropertyChangeListener(signalListener);
    }

    /**
     * Sets a ParameterHandler that will get the valve position on each run
     * call.
     *
     * @param h reference to ParameterHandler
     */
    @Override
    public void registerParameterHandler(ValueHandler h) {
        outputValues = h;
    }

    /**
     * Allows processing received events by the class itself. The Property name
     * must begin with the same name as this classes elements, which was
     * initialized with the initName function call.
     *
     * @param ac ActionCommand, will be further checked if it's matching.
     * @return true if event was processed by this instance.
     */
    public boolean handleAction(ActionCommand ac) {
        if (!ac.getPropertyName().equals(name)) {
            return false;
        }
        // Int values are sent from so-called Integral switches, as long as they
        // are pressed, value integrates. The press sends a +1 or -1 and the
        // release of the button sends a 0, but this is done with default.
        if (ac.getValue() instanceof Integer) {
            switch ((int) ac.getValue()) {
                case -1 ->
                    operateCloseValve();
                case +1 ->
                    operateOpenValve();
                default ->
                    stopValve();
            }
        } else if (ac.getValue() instanceof Boolean) {
            if ((boolean) ac.getValue()) {
                operateOpenValve();
            } else {
                operateCloseValve();
            }
        }
        return true;
    }

    public void operateOpenValve() {
        swControl.setInputMax();
    }

    public void operateCloseValve() {
        swControl.setInputMin();
    }

    public void operateSetOpening(double opening) {
        swControl.setInput(opening);
    }

    public void stopValve() {
        swControl.setStop();
    }

    public double getOpening() {
        return swControl.getOutput();
    }

    public SetpointIntegrator getIntegrator() {
        return swControl;
    }

    /**
     * Adds an external provider which defines criteria for safe open of this
     * valve. Note that safety logic is reversed, meaning the boolean value must
     * be true for the valve element to be able to close it. Can be used with
     * anonymous classes or lambda expression.
     * <p>
     * Short: if the returned value is FALSE, the valve will be forced OPEN.
     *
     * @param safeOpenProvider a BooleanSupplier which returns FALSE for OPEN
     */
    public void addSafeOpenProvider(BooleanSupplier safeOpenProvider) {
        this.safeOpenProvider = safeOpenProvider;
    }

    /**
     * Adds an external provider which defines criteria for safe shut of this
     * valve. Note that safety logic is reversed, meaning the boolean value must
     * be true to be able to open the valve. Can be used with anonymous classes
     * or lambda expression.
     * <p>
     * Short: if the returned value is FALSE, the valve will be forced CLOSED.
     *
     * @param safeClosedProvider a BooleanSupplier which returns FALSE for SHUT
     */
    public void addSafeClosedProvider(BooleanSupplier safeClosedProvider) {
        this.safeClosedProvider = safeClosedProvider;
    }

    /**
     * Writes the state of the integrator object to the provided initial AC.
     *
     * @param ac
     */
    protected void writeToAcStateObject(AbstractAC ac) {
        ac.setObjectName(name);
        ((ValveManual) ac).setOpening(swControl.getOutput());
        ((ValveManual) ac).setTargetOpening(swControl.getInput());
    }

    @Override
    public void setAutomationCondition(AbstractAC ac) {
        swControl.setInput(((ValveManual) ac).getTargetOpening());
        swControl.forceOutputValue(((ValveManual) ac).getOpening());

        // Reset the save closed providers, in case of them beeing active, 
        // they will be reset to false in the next run-cycle.
        safeClosed = true;
        safeOpen = true;
        
        skipSafetyCheckOnLoad = true;

        // this has to be discussed, not a good idea maybe - TODO
        // oldSafeOpen = safeOpen;
        // oldSafeClosed = safeClosed;
    }

    public String toString() {
        return name;
    }
}
