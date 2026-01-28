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

import com.hartrusion.modeling.converters.PhasedEnergyExchangerHandler;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedNoMassExchangerHandler
        implements PhasedHandler, PhasedEnergyExchangerHandler {

    /**
     * Holds a list of all nodes connected to the element where this heat
     * handler is assigned.
     */
    private final List<PhasedNode> phasedNodes;

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final PhasedElement element;

    /**
     * A heat exchanger is always made of two sides, this simply references the
     * interface of the other heat handler from the other side.
     */
    private PhasedEnergyExchangerHandler otherSide;

    private PhasedFluidProperties fluidProperties;

    /**
     * Marks the calculation state as finished, this is faster than checking a
     * lot of node states all the time and allows one handler to make the
     * calculation run for both sides.
     */
    private boolean calculationFinished;

    PhasedNoMassExchangerHandler(PhasedFluidProperties fluidProperties,
            PhasedElement parent) {
        this.fluidProperties = fluidProperties;
        phasedNodes = new ArrayList<>();
        element = parent;
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
        calculationFinished = false;
    }

    @Override
    public boolean doPhasedCalculation() {
        if (calculationFinished) {
            return false; // nothing more to do
        }
        // check that this side and the other one is in a state where the 
        // calculation is now possible and all prerequisites are met.
        if (!isNodeStateMet() || !otherSide.isNodeStateMet()) {
            return false;
        }
        calculationFinished = true; // set this here already
        AbstractElement aElement = (AbstractElement) element;
        // Special: No temperature - nothing happens. Either detect this by 
        // a zero-flow or the no-temperature property.
        boolean didSomething = false;
        if (isNoFlowCondition()) {
            // zero-flow condition: set no-temperature on remaining nodes if 
            // somehow this was not done before.
            for (PhasedNode pn : phasedNodes) {
                if (!pn.heatEnergyUpdated(aElement)) {
                    pn.setNoHeatEnergy(aElement);
                    didSomething = true;
                }
            }
            return didSomething;
        }
        // If we reached here, we have one temperature updated and one is 
        // missing. The other part of the heat exchanger is in the same 
        // condition at least and a calculation is possible.
        PhasedNode inNode = null, outNode = null;
        for (PhasedNode pn : phasedNodes) { // get nodes by flow direction
            if (pn.getFlow(aElement) > 0.0) {
                inNode = pn;
            } else if (pn.getFlow(aElement) < 0.0) {
                outNode = pn;
            }
        }
        if (otherSide.isNoFlowCondition()) {
            if (!outNode.heatEnergyUpdated(aElement)) {
                outNode.setHeatEnergy(
                        inNode.getHeatEnergy(aElement), aElement);
                return true;
            }
            return false; // already done, but how was this even possible
        }
        // Here: All prerequisites are met, we can now calculate the thermal
        // energy transfer on both sides. First, get the maximum thermal power
        // that could be transfered when having the other sides inlet 
        // temperature as the own outlet temperature.
        double maxDeltaThis = getMaxEnergyDelta(otherSide.getInTemperature());
        double maxDeltaOhter = otherSide.getMaxEnergyDelta(getInTemperature());
        
        // Determine direction of thermal transfer:
        boolean fromThis;
        if (maxDeltaThis > 0.0 && maxDeltaOhter < 0.0) {
            fromThis = false;
        } else if (maxDeltaThis < 0.0 && maxDeltaOhter > 0.0) {
            fromThis = true;
        } else {
            throw new CalculationException("Undefined energy flow direction");
        }
        
        double transfEnergy = 0.99 * Math.min(Math.abs(maxDeltaThis),
                Math.abs(maxDeltaOhter));
        if (fromThis) {
            setPowerTransfer(-transfEnergy);
            otherSide.setPowerTransfer(transfEnergy);
        } else {
            setPowerTransfer(transfEnergy);
            otherSide.setPowerTransfer(-transfEnergy);
        }

        return true;
    }

    @Override
    public double getMaxEnergyDelta(double otherTemperature) {
        double inEnergy = 0.0;
        for (PhasedNode pn : phasedNodes) { // get nodes by flow direction
            if (pn.getFlow((AbstractElement) element) > 0.0) {
                inEnergy = pn.getHeatEnergy((AbstractElement) element);
                break;
            }
        }
        double ownInTemperature = getInTemperature();
        double assumedOutEnergy;
        // Heating up always assumes to go to full vapor state and cooling down
        // also always assumes to go down to full liquid state. In such cases
        // it is likely that the other side will limit the phase changes
        if (otherTemperature == ownInTemperature) {
            return 0.0;
        } else if (ownInTemperature > otherTemperature) {
            // Cool down:
            assumedOutEnergy = otherTemperature 
                    * fluidProperties.getSpecificHeatCapacity();
        } else {
            // heat up (and eventually evaporate):
            assumedOutEnergy = otherTemperature 
                    * fluidProperties.getSpecificHeatCapacity() 
                    + fluidProperties.getVaporizationHeatEnergy();
        }
        return (assumedOutEnergy - inEnergy) * getInFlow();
        // positive: heats up this element
    }
    
    @Override
    public void setPowerTransfer(double power) {
        double inEnergy = 0.0, inFlow = 0.0;
        for (PhasedNode pn : phasedNodes) { // get node by flow direction
            if (pn.getFlow((AbstractElement) element) > 0.0) {
                inFlow = pn.getFlow((AbstractElement) element);
                inEnergy = pn.getHeatEnergy((AbstractElement) element);
                break;
            }
        }
        double deltaEnergy = power / inFlow;
        for (PhasedNode pn : phasedNodes) { // get node by flow direction
            if (pn.getFlow((AbstractElement) element) < 0.0) {
                pn.setHeatEnergy(inEnergy + deltaEnergy,
                        (AbstractElement) element);
                break;
            }
        }
        calculationFinished = true;
    }

    @Override
    public boolean isNodeStateMet() {
        boolean allUpdated = true;
        for (PhasedNode pn : phasedNodes) {
            allUpdated = allUpdated
                    && pn.flowUpdated((AbstractElement) element);
        }
        if (!allUpdated) {
            return false;
        }
        // temperature of flows towards the element must be in 
        // updated state. a zero-flow is also handled and valid.
        for (PhasedNode pn : phasedNodes) {
            if (pn.getFlow((AbstractElement) element) >= 0.0) {
                if (!pn.heatEnergyUpdated((AbstractElement) element)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isNoFlowCondition() {
        for (PhasedNode pn : phasedNodes) {
            if (pn.getFlow((AbstractElement) element) == 0.0) {
                return true;
            }
            if (pn.heatEnergyUpdated((AbstractElement) element)) {
                if (pn.noHeatEnergy((AbstractElement) element)) {
                    return true;
                }
            }
        }
        return false;
    }

    public double getInFlow() {
        for (PhasedNode pn : phasedNodes) { // get nodes by flow direction
            if (pn.getFlow((AbstractElement) element) > 0.0) {
                return pn.getFlow((AbstractElement) element);
            }
        }
        return 0.0;
    }

    @Override
    public double getInTemperature() {
        for (PhasedNode pn : phasedNodes) { // get nodes by flow direction
            if (pn.getFlow((AbstractElement) element) > 0.0) {
                return fluidProperties.getTemperature(
                        pn.getHeatEnergy((AbstractElement) element),
                        pn.getEffort());
            }
        }
        return 0.0;
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
                "A massless heat exchanger does not have its own energy");
    }

    @Override
    public double getHeatEnergy() {
        throw new NonexistingStateVariableException(
                "A massless heat exchanger does not have its own energy");
    }

    @Override
    public void setInnerHeatedMass(double heatedMass) {
        throw new NonexistingStateVariableException(
                "A massless heat exchanger does not support a volume.");
    }

    @Override
    public void setOtherSide(PhasedEnergyExchangerHandler otherSide) {
        this.otherSide = otherSide;
    }
}
