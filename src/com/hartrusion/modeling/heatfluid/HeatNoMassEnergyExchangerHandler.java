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
public class HeatNoMassEnergyExchangerHandler implements HeatHandler,
        PhasedEnergyExchangerHandler {

    /**
     * Holds a list of all nodes connected to the element where this heat
     * handler is assigned.
     */
    private final List<HeatNode> heatNodes;

    /**
     * Reference to the element which is using this heat handler instance.
     */
    private final HeatElement element;

    /**
     * A heat exchanger is always made of two sides, this simply references the
     * interface of the other heat handler from the other side.
     */
    private PhasedEnergyExchangerHandler otherSide;

    /**
     * Specific heat capacity (water) in J/kg/K
     */
    private double specHeatCap = 4186;

    private double efficiency = 1.0;

    /**
     * Marks the calculation state as finished, this is faster than checking a
     * lot of node states all the time and allows one handler to make the
     * calculation run for both sides.
     */
    private boolean calculationFinished;

    HeatNoMassEnergyExchangerHandler(HeatElement parent) {
        heatNodes = new ArrayList<>();
        element = parent;
    }

    @Override
    public boolean registerHeatNode(HeatNode tp) {
        if (!heatNodes.contains(tp)) {
            heatNodes.add(tp);
            return true;
        }
        return false;
    }

    @Override
    public void prepareHeatCalculation() {
        calculationFinished = false;
    }

    @Override
    public boolean doThermalCalculation() {
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
            for (HeatNode tp : heatNodes) {
                if (!tp.temperatureUpdated(aElement)) {
                    tp.setNoTemperature(aElement);
                    didSomething = true;
                }
            }
            return didSomething;
        }
        // If we reached here, we have one temperature updated and one is 
        // missing. The other part of the heat exchanger is in the same 
        // condition at least and a calculation is possible.
        HeatNode inNode = null, outNode = null;
        for (HeatNode hn : heatNodes) { // get nodes by flow direction
            if (hn.getFlow(aElement) > 0.0) {
                inNode = hn;
            } else if (hn.getFlow(aElement) < 0.0) {
                outNode = hn;
            }
        }
        if (otherSide.isNoFlowCondition()) {
            if (!outNode.temperatureUpdated(aElement)) {
                outNode.setTemperature(
                        inNode.getTemperature(aElement), aElement);
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
            // throw new CalculationException("Undefined energy flow direction");
            // This can be triggered if there is equal temperatures and no 
            // power transfer due to this.
            setNoPowerTransfer();
            otherSide.setNoPowerTransfer();
            return true;
        }
        
        double transfEnergy = efficiency * Math.min(Math.abs(maxDeltaThis),
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
        // positive: heat up this element (other temperature is higher)
        return (otherTemperature - getInTemperature())
                * specHeatCap * getInFlow();
    }

    @Override
    public void setPowerTransfer(double power) {
        double inTemp = 0.0, inFlow = 0.0;
        for (HeatNode hn : heatNodes) { // get node by flow direction
            if (hn.getFlow((AbstractElement) element) > 0.0) {
                inFlow = hn.getFlow((AbstractElement) element);
                inTemp = hn.getTemperature((AbstractElement) element);
                break;
            }
        }
        double deltaTemp = power / inFlow / specHeatCap;
        for (HeatNode hn : heatNodes) { // get node by flow direction
            if (hn.getFlow((AbstractElement) element) < 0.0) {
                hn.setTemperature(inTemp + deltaTemp,
                        (AbstractElement) element);
                break;
            }
        }
        calculationFinished = true;
    }
    
    @Override
    public void setNoPowerTransfer() {
        double inTemp = 0.0;
        for (HeatNode hn : heatNodes) { // get node by flow direction
            if (hn.getFlow((AbstractElement) element) > 0.0) {
                inTemp = hn.getTemperature((AbstractElement) element);
                break;
            }
        }
        for (HeatNode hn : heatNodes) { // get node by flow direction
            if (hn.getFlow((AbstractElement) element) < 0.0) {
                hn.setTemperature(inTemp, (AbstractElement) element);
                break;
            }
        }
        calculationFinished = true;
    }

    @Override
    public boolean isNodeStateMet() {
        boolean allUpdated = true;
        for (HeatNode tp : heatNodes) {
            allUpdated = allUpdated
                    && tp.flowUpdated((AbstractElement) element);
        }
        if (!allUpdated) {
            return false;
        }
        // temperature of flows towards the element must be in 
        // updated state. a zero-flow is also handled and valid.
        for (HeatNode tp : heatNodes) {
            if (tp.getFlow((AbstractElement) element) >= 0.0) {
                if (!tp.temperatureUpdated((AbstractElement) element)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isNoFlowCondition() {
        for (HeatNode tp : heatNodes) {
            if (tp.getFlow((AbstractElement) element) == 0.0) {
                return true;
            }
            if (tp.temperatureUpdated((AbstractElement) element)) {
                if (tp.noTemperature((AbstractElement) element)) {
                    return true;
                }
            }
        }
        return false;
    }

    public double getInFlow() {
        for (HeatNode hn : heatNodes) { // get nodes by flow direction
            if (hn.getFlow((AbstractElement) element) > 0.0) {
                return hn.getFlow((AbstractElement) element);
            }
        }
        return 0.0;
    }

    public double getInTemperature() {
        for (HeatNode hn : heatNodes) { // get nodes by flow direction
            if (hn.getFlow((AbstractElement) element) > 0.0) {
                return hn.getTemperature((AbstractElement) element);
            }
        }
        return 0.0;
    }

    @Override
    public boolean isHeatCalulationFinished() {
        boolean allUpdated = true;
        for (HeatNode tp : heatNodes) {
            allUpdated = allUpdated
                    && tp.temperatureUpdated((AbstractElement) element);
        }
        return allUpdated;
    }

    // Throw exeptions in case of temperature is set or asked, this is only
    // possible if the handler actually has a temperature, which this one
    // doesnt.
    @Override
    public void setInitialTemperature(double t) {
        throw new NonexistingStateVariableException(
                "A massless heat exchanger does not have its own temperature");
    }

    @Override
    public double getTemperature() {
        throw new NonexistingStateVariableException(
                "A massless heat exchanger does not have its own temperature");
    }

    @Override
    public void setInnerThermalMass(double storedMass) {
        throw new NonexistingStateVariableException(
                "A massless heat exchanger does not support heat volume.");
    }

    @Override
    public void setOtherSide(PhasedEnergyExchangerHandler otherSide) {
        this.otherSide = otherSide;
    }
    
    @Override
    public void setEfficency(double efficiency) {
        this.efficiency = efficiency;
    }

}
