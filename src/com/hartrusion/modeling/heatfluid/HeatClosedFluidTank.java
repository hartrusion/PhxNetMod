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
 * as state value, its initial condition can be set either as pressure with the
 * setInitialEffort method or as mass with setInitialMass, which converts the
 * mass to the pressure by using the characteristic of the tank. Therefore,
 * initCharacteristic has to be called before setting the initial mass.
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
            double currentMass = getMass();

            // Add mass flows from node
            double nextMass = currentMass + flowSum * stepTime;

            // check that this tank does neither run completely full nor empty
            if (nextMass >= density * totalVolume) {
                throw new ModelErrorException("Overfill occured");
            } else if (nextMass < 0.0) {
                throw new ModelErrorException("Fluid completely empty");
            }

            double nextPressure = getPressureForMass(nextMass);

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

    /**
     * Calculates the pressure of the compressed gas above the fluid for a
     * given fluid mass, using the polytropic compression of the precharged gas
     * volume.
     *
     * @param mass Fluid mass in kg
     * @return Pressure in Pa
     */
    private double getPressureForMass(double mass) {
        return prechargePressure
                * Math.pow(totalVolume / (totalVolume - (mass / density)),
                        polytropicIndex);
    }

    /**
     * Sets the initial mass of the fluid in the tank by converting it to the
     * pressure, which is the state value of this tank. The characteristic has
     * to be set with initCharacteristic before.
     *
     * @param mass Mass in kg
     */
    @Override
    public void setInitialMass(double mass) {
        if (mass < 0.0 || mass >= density * totalVolume) {
            throw new ModelErrorException("Initial mass must be positive and "
                    + "less than the mass of a completely filled tank.");
        }
        stateValue = getPressureForMass(mass);
    }

    /**
     * Returns the fluid mass in the tank, which is calculated from the
     * pressure as the inverse of the polytropic compression. This replaces the
     * mass calculation of the open tank, so the heat handler of the super
     * class gets the correct mass.
     *
     * @return Mass in kg
     */
    @Override
    public double getMass() {
        return density * totalVolume
                * (1.0 - Math.pow(stateValue / prechargePressure,
                        -1.0 / polytropicIndex));
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
