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
package com.hartrusion.modeling.heatfluid;

import com.hartrusion.modeling.general.GeneralNode;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.SelfCapacitance;
import com.hartrusion.modeling.initial.AbstractIC;
import com.hartrusion.modeling.initial.HeatedEnerergyStorageIC;

/**
 * Represents an open tank that can be filled with a fluid with different
 * temperatures. This tank is derived from self capacitance, meaning it will
 * have the same pressure on all nodes. Use only if there is no boiling, if it
 * boils, go for the HeatClosedSteamedReservoir to consider boiling pressure.
 *
 * <p>
 * Configuration: Set time constant with T = A / g.
 *
 * <pre>
 * Some notes on where those equations came from:
 *
 * Effort: pressure p (Pa = N/m^2)
 * Flow: mass flow m. = dm/dt (kg/s)
 *
 * Time constant calculation (Time constant describes relation between pressure
 * builtup and inner folume change:
 *
 * p = rho * g * h           use h = V / A
 * p = rho * g * V / A       use m = rho * V as V = m / rho
 * p = rho * g * m / rho / A
 * p = g / A * m              this is obvious.   use m = int(dm/dt)dt
 * p = g / A * int(dm/dt)dt
 *
 * This matches the equation of the capacitance energy storage:
 *
 * effort = tau * int(flow)
 *
 * Therefore it is tau = g / A With tau = 1/T, time constant can be
 * described as T = A / g and there is a method in the parent class
 * EnergyStorage to set this value.
 *
 * Replacing int(dm/dt) with m, which is obvious, also leads to the obvious 
 * equaiton of p = tau * m or m = p / tau. As p is the stateValue, this is how 
 * the thermal mass is set to the heathandler.
 * 
 * To get a fill height, simply invert first equation
 * 
 * h = p / rho / g
 * 
 * and remember that p is the effort value. rho is 997 kg/m^3 and g is 9.81 
 * m/s^2. 1/997/9.81 is 1.0224e-4 for meters or 1.0224e-5 for centimeters when 
 * using water.
 * </pre>
 *
 * <p>
 * Due to limitations of how heat fluids work, this tank must never be empty. It
 * could not implement a heat out fluid that way, so make sure your model will
 * never reach such a conditions (just implement an autoclosing valve or trigger
 * a pump trip).
 *
 * @author Viktor Alexander Hartung
 */
public class HeatFluidTank extends SelfCapacitance implements HeatElement {

    protected final HeatVolumizedHandler heatHandler
            = new HeatVolumizedHandler(this);

    public HeatFluidTank() {
        super(PhysicalDomain.HEATFLUID);
    }


    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        // for explainaiton of stateValue / tau see beginning of file.
        heatHandler.setInnerThermalMass(stateValue / tau);
        heatHandler.prepareHeatCalculation();
    }

    @Override
    public boolean doCalculation() {
        boolean didSomething;
        HeatNode tp;

        didSomething = super.doCalculation();

        if (stateValuePrepared && nextStateValue < 0.0) {
            // Such things can not be simulated.
            throw new ModelErrorException("No negative state value allowed, "
                    + "make sure your HeatFluidTank never runs empty!");
        }

        // Add call for thermalhandler calculation
        didSomething = heatHandler.doThermalCalculation() || didSomething;

        // call calulation on thermal nodes - contrary to flow, it is not
        // possible to do this with the set-operation as it is unknown when 
        // that calculation will be possible.
        for (GeneralNode p : nodes) {
            tp = (HeatNode) p;
            didSomething = tp.doCalculateTemperature() || didSomething;
        }

        return didSomething;
    }

    @Override
    public boolean isCalculationFinished() {
        // Add call for thermalhandler calculationfinished
        return super.isCalculationFinished()
                && heatHandler.isHeatCalulationFinished();
    }

    @Override
    public void registerNode(GeneralNode p) {
        super.registerNode(p);
        heatHandler.registerHeatNode((HeatNode) p);
    }

    @Override
    public HeatHandler getHeatHandler() {
        return heatHandler;
    }

    @Override
    public void setEffort(double effort, HeatNode source) {
        setEffort(effort, (GeneralNode) source);
    }

    @Override
    public void setFlow(double flow, HeatNode source) {
        setFlow(flow, (GeneralNode) source);
    }

    @Override
    public void setStepTime(double dt) {
        super.setStepTime(dt);
        heatHandler.setStepTime(dt);
    }
    
    @Override
    public AbstractIC getState() {
        HeatedEnerergyStorageIC ic
                = new HeatedEnerergyStorageIC();
        ic.setElementName(elementName);
        ic.setStateValue(stateValue);
        ic.setTemperature(heatHandler.getTemperature());
        return ic;
    }

    @Override
    public void setInitialCondition(AbstractIC ic) {
        checkInitialConditionName(ic);
        stateValue
                = ((HeatedEnerergyStorageIC) ic).getStateValue();
        heatHandler.setInitialTemperature(((HeatedEnerergyStorageIC) ic).getTemperature());
    }

}
