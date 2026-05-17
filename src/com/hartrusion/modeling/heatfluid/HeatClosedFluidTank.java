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
package com.hartrusion.modeling.heatfluid;

import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * Represents a closed fluid tank that compresses the gas above it to build up
 * pressure in a non-linear way.
 * <p>
 * The element still uses pressure as the state value but changes the way it is
 * calculated.
 * <p>
 * It is not allowed to completely fill or drain the fluid tank so there should
 * be some safety logic implemented on connected valves. As it uses the effort 
 * as state value, its initial condition has to be set with the setInitialEffort
 * method from the super class.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatClosedFluidTank extends HeatFluidTank {

    private double prechargePressure = 1e5;
    private double totalVolume = 1.0;
    /**
     * Polytropic index (air has 1.4)
     */
    private double polytropicIndex = 1.4;

    /**
     * Density used for weight calculation
     */
    private double density = 997.0;

    public HeatClosedFluidTank() {
        super();
        // tau is no longer used as intragral constant but still needed as 
        // we will manipulate values to still use super calls here.
        this.tau = 1.0;
    }

    @Override
    protected boolean calculateNewDeltaValue() {
        boolean allFlowSet = true;
        double flowSum = 0;

        if (!deltaCalculated) {
            for (GeneralNode cp : nodes) {
                allFlowSet = allFlowSet && cp.flowUpdated(this);
                if (cp.flowUpdated(this)) {
                    flowSum = flowSum + cp.getFlow(this);
                }
            }

            if (!allFlowSet) {
                return false;
            }

            // Mass will be calculated from the current pressure (state value)
            // instead of saving it directly.
            double currentMass = density * totalVolume
                    * (1.0 - Math.pow(stateValue / prechargePressure,
                            -1.0 / polytropicIndex));

            // Add mass flows from node
            double nextMass = currentMass + flowSum * stepTime;

            // check that this tank does neither run completely full nor empty
            if (nextMass >= density * totalVolume) {
                throw new ModelErrorException("Overfill occured");
            } else if (nextMass < 0.0) {
                throw new ModelErrorException("Fluid completely empty");
            }

            double nextPressure = prechargePressure
                    * Math.pow(totalVolume / (totalVolume
                            - (nextMass / density)), polytropicIndex);

            // Manipulate delta value to make it match the desired result, as
            // integrate() does nextStateValue = stateValue + delta * tau
            // we just modify delta in a way we get the next pressure by that
            // delta operation.
            this.delta = (nextPressure - stateValue) / tau;

            deltaCalculated = true;
            super.integrate(); // applies delta
            return true;
        }
        return false;
    }

    @Override
    public void prepareCalculation() {
        // call this for EnergyStorage and HeatFluidTank but note that it does 
        // set the mass to stateValue/tau which is not correct!
        super.prepareCalculation();

        // Calculate the correct mass and set it to the heat handler afterwards,
        // this will effectively overwrite the wrong mass
        heatHandler.setInnerThermalMass(density * totalVolume
                * (1.0 - Math.pow(stateValue / prechargePressure,
                        -1.0 / polytropicIndex)));

        // apply prepare method again to fix the initial wrong setting.
        heatHandler.prepareHeatCalculation();
    }

    /**
     * Sets the geometry and fluid properties for the characteristic of the
     * fluid tank.
     *
     * @param totalVolume in m³
     * @param polytropicIndex Air: 1.4
     * @param density Water: 997
     * @param prechargePressure Can be 1e5 Pa
     */
    public void initCharacteristic(double totalVolume, double polytropicIndex,
            double density, double prechargePressure) {
        this.totalVolume = totalVolume;
        this.polytropicIndex = polytropicIndex;
        this.density = density;
        this.prechargePressure = prechargePressure;
        
        // have some pressure initialized to no let the element start with an
        // exception - such things need to be set with intital effort method.
        stateValue = prechargePressure * 1.05;
    }
}
