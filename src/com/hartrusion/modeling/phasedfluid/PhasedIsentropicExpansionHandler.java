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
package com.hartrusion.modeling.phasedfluid;

import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedIsentropicExpansionHandler implements PhasedHandler {

    /**
     * Holds a list of all nodes connected to the element where this phased
     * handler is assigned.
     */
    private final List<PhasedNode> phasedNodes = new ArrayList<>();

    /**
     * Reference to the element which is using this phased handler instance.
     */
    private final PhasedTurbineStage element;
    private double factor = 0.85; // Standardwert für isentropen Wirkungsgrad
    private double excessEnergy = 0.0;

    public PhasedIsentropicExpansionHandler(PhasedTurbineStage element) {
        this.element = element;
    }

    public void setIsentropicFactor(double k) {
        this.factor = k;
    }

    public double getExcessEnergy() {
        return excessEnergy;
    }

    /**
     * Calculates the out heat energy for given in heat energy and both
     * pressures.
     *
     * @param heatEnergyIn Energy set on in node
     * @param pressureIn Pressure on in node (Pa)
     * @param pressureOut Pressure on out node (Pa)
     * @return Out energy
     */
    public double getOutHeatEnergy(double heatEnergyIn,
            double pressureIn, double pressureOut) {
        // Entropy at inlet (not normalized to 0°C)
        double entropyIn = calculateEntropy(heatEnergyIn, pressureIn);

        // ideal isentropic energy that would leave the element
        double isentropicEnergyOut = calculateIsentropicEnergy(
                entropyIn, pressureOut);

        // Use factor to obtain the real energy value
        // h_out = h_in - eta_is * (h_in - h_out_is)
        return heatEnergyIn - factor * (heatEnergyIn - isentropicEnergyOut);

    }

    private double calculateEntropy(
            double heatEnergy, double pressure) {
        double tSat = element.getPhasedFluidProperties()
                .getSaturationTemperature(pressure);
        double cp = element.getPhasedFluidProperties()
                .getSpecificHeatCapacity();
        double hVap = element.getPhasedFluidProperties()
                .getVaporizationHeatEnergy();
        double hLiquid = element.getPhasedFluidProperties()
                .getLiquidHeatCapacity(pressure);

        double sFluid = cp * Math.log(tSat);
        double sEvap = hVap / tSat;

        if (heatEnergy <= hLiquid) { // liquid, noone knows why that ever
            double t = heatEnergy / cp;
            return cp * Math.log(t);
        } else if (heatEnergy >= hLiquid + hVap) { // superheated
            double t = (heatEnergy - hVap) / cp;
            return sFluid + sEvap + cp * Math.log(t / tSat);
        } else { // saturated steam
            double x = element.getPhasedFluidProperties()
                    .getVapourFraction(heatEnergy, pressure);
            return sFluid + x * sEvap;
        }
    }

    private double calculateIsentropicEnergy(double entropy, double pressure) {
        double tSat = element.getPhasedFluidProperties()
                .getSaturationTemperature(pressure);
        double cp = element.getPhasedFluidProperties()
                .getSpecificHeatCapacity();
        double hVap = element.getPhasedFluidProperties()
                .getVaporizationHeatEnergy();
        double hLiquid = element.getPhasedFluidProperties()
                .getLiquidHeatCapacity(pressure);

        double sFluid = cp * Math.log(tSat);
        double sEvap = hVap / tSat;
        double sGas = sFluid + sEvap;

        if (entropy <= sFluid) { // liquid
            double t = Math.exp(entropy / cp);
            return t * cp;
        } else if (entropy >= sGas) { // superheated
            double t = tSat * Math.exp((entropy - sGas) / cp);
            return t * cp + hVap;
        } else { // saturated
            double x = (entropy - sFluid) / sEvap;
            return hLiquid + x * hVap;
        }
    }

    @Override
    public boolean registerPhasedNode(PhasedNode pn) {
        if (!phasedNodes.contains(pn)) {
            phasedNodes.add(pn);
            return true;
        }
        return false;
    }

    @Override
    public void preparePhasedCalculation() {
        // nothing to do here
    }

    @Override
    public boolean doPhasedCalculation() {
        boolean didSomething = false;
        double flow;
        boolean allFlowsZero = true;
        PhasedNode inNode = null, outNode = null;
        double inHeatEnergy, outHeatEnergy;

        if (allFlowsUpdated()) {
            // First of all, if there is no flow at all (valves closed etc),
            // update the model properly to handle this situation.
            for (PhasedNode hn : phasedNodes) { // all nodes connected to elem
                allFlowsZero = allFlowsZero
                        && hn.getFlow((AbstractElement) element) == 0.0;
            }
            if (allFlowsZero) {
                // check non-updated heat nodes and set them to noHeatEnergy.
                for (PhasedNode pn : phasedNodes) { // all nodes connected
                    if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                        pn.setNoHeatEnergy((AbstractElement) element);
                        excessEnergy = 0.0;
                        didSomething = true;
                    }
                }
                return didSomething; // terminate further execution of mehtod
            }

            // Get both nodes which are the in and out nodes regarding flow
            for (PhasedNode pn : phasedNodes) { // all nodes connected to elem
                flow = pn.getFlow((AbstractElement) element);
                if (flow <= 0.0) {
                    outNode = pn;
                } else if (flow >= 0.0) {
                    inNode = pn;
                }
            }

            if (outNode == null || inNode == null) {
                return didSomething; // prevent warnings here
            }

            if (outNode.heatEnergyUpdated((AbstractElement) element)) {
                return didSomething; // finished, nothing more to do here.
            }

            if (!inNode.heatEnergyUpdated((AbstractElement) element)) {
                return didSomething; // nothing to do if this is unknown yet
            }

            if (inNode.noHeatEnergy((AbstractElement) element)) {
                // Problem: There might be no heat energy set even with a set 
                // flow direction, this is a result of numeric calculation 
                // errors and a failure to detect them. In this case, we just
                // propagate the no-energy state to keep the model running.
                outNode.setNoHeatEnergy((AbstractElement) element);
                excessEnergy = 0.0;
                return true;
            }

            inHeatEnergy = inNode.getHeatEnergy((AbstractElement) element);

            outHeatEnergy = getOutHeatEnergy(inHeatEnergy, inNode.getEffort(), outNode.getEffort());

            outNode.setHeatEnergy(outHeatEnergy, (AbstractElement) element);
            excessEnergy = inHeatEnergy - outHeatEnergy;
            didSomething = true;
        }
        return didSomething;
    }

    /**
     * Checks if all flow values from nodes toward the element which is assigned
     * to this handler are updated. This private method just provides a small
     * shortcut for used internally.
     *
     * @return
     */
    private boolean allFlowsUpdated() {
        boolean allUpdated = true;
        for (PhasedNode pn : phasedNodes) {
            allUpdated = allUpdated
                    && pn.flowUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    @Override
    public boolean isPhasedCalulationFinished() {
        boolean allUpdated = true;
        for (PhasedNode pn : phasedNodes) {
            allUpdated = allUpdated
                    && pn.heatEnergyUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    @Override
    public void setInitialHeatEnergy(double q) {
        throw new NonexistingStateVariableException(
                "Simple phased handler does not support heat volume.");
    }

    @Override
    public double getHeatEnergy() {
        throw new NonexistingStateVariableException(
                "Simple phased handler does not support heat volume.");
    }

    @Override
    public void setInnerHeatedMass(double heatedMass) {
        throw new NonexistingStateVariableException(
                "Simple phased handler does not support heat volume.");
    }
}
