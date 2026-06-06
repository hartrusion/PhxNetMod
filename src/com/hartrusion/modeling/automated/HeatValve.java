/*
 * Copyright (C) 2025 Viktor Alexander Hartung
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.hartrusion.modeling.automated;

import com.hartrusion.modeling.heatfluid.HeatLinearValve;
import com.hartrusion.modeling.initial.AbstractAC;
import com.hartrusion.modeling.initial.ValveManual;
/**
 * HeatLinearValve with SetpointIntegrator as actuator and a Monitor for firing
 * state change properties.
 * <p>
 * Received ActionCommands with the set name (to be set with initName) will be
 * used to control the value. Depending on the type, integers can be used to
 * perform opening or closing until a int 0 value is send. Boolean values will
 * either fully open or fully close the valve.
 * <p>
 * The attached monitor will fire property changes to all registered listeners
 * which are defined in
 *
 * @author Viktor Alexander Hartung
 */
public class HeatValve extends BaseAutomatedValve implements Runnable {
    
    /**
     * The valve element of the model itself.
     */
    protected final HeatLinearValve valve = new HeatLinearValve(true);

    @Override
    public void initName(String name) {
        super.initName(name);
        valve.setName(name);
    }
    
    /**
     * Sets the resistance parameter for the valve when it is on 100 % opening
     * state. This disables the advanced characteristic curve and uses a 1/R 
     * approach.
     *
     * @param resistanceFullOpen Resistance as Effort/Flow on full open position
     */
    public void initCharacteristicSimple(double resistanceFullOpen) {
        valve.setResistanceFullOpen(resistanceFullOpen);
    }
    
    /**
     * Sets the valves characteristic to behave linear when connected to a
     * certain system that is defined by an effort and a resistance value. The
     * effort value describes the overall effort pushing flow through a given
     * resistance. The valve will behave linear if effort is matched and
     * resistance is correct. The provided effort and flow values are only used
     * to calculate an effective resistance for any given valve position, if the
     * effort of the system is different, the valve will not behave as desired
     * but still work.
     *
     * @param flowOpen Desired flow on maximum open position.
     * @param effort Effort that is between the valve and the resistance.
     * @param resistance Resistance of a system that this valve is connected to.
     * Note that this is NOT the valves resistance.
     */
    public void initCharacteristicAdvanced(double flowOpen,
            double effort, double resistance) {
        valve.setAdvancedCharacteristic(flowOpen, effort, resistance);
    }

    public void initOpening(double opening) {
        valve.setOpening(opening);
        swControl.forceOutputValue(opening);
    }
    
    @Override
    public void run() {
        checkSafety();
                
        swControl.run();
        valve.setOpening(swControl.getOutput());
        monitor.setInput(swControl.getOutput());
        monitor.run();
        
         // Send valve position as parameter value for monitoring
        if (outputValues != null) {
            outputValues.setParameterValue(valve.toString(),
                    valve.getOpening());
        }
    }

    public HeatLinearValve getValveElement() {
        return valve;
    }
    
    @Override
    public AbstractAC getState() {
        ValveManual vm = new ValveManual();
        writeToAcStateObject(vm);
        return vm;
    }
}
