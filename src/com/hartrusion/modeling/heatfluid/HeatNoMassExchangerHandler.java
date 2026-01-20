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

import com.hartrusion.modeling.exceptions.NonexistingStateVariableException;
import com.hartrusion.modeling.general.AbstractElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Handler for the no-mass heat exchanger. Knows the other side of the heat
 * exchanger or its handler and uses a very simplified formula to determine the
 * output temperature. Both handlers have the same formula but they will choose
 * a different mass flow value each time.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatNoMassExchangerHandler implements HeatHandler {

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
     * other heat handler from the other side
     */
    private HeatNoMassExchangerHandler otherSide;

    /**
     * Specific heat capacity (water) in J/kg/K
     */
    private double specHeatCap = 4186;

    private double ntu = 0.9;

    /**
     * Marks the calculation state as finished, this is faster than checking a
     * lot of node states all the time and allows one handler to make the
     * calculation run for both sides.
     */
    private boolean calculationFinished;

    HeatNoMassExchangerHandler(HeatElement parent) {
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
        // This is the normal behavior.
        // Thermal energy mass flow:
        double CThis = getInFlow() * specHeatCap;
        double COther = otherSide.getInFlow() * otherSide.getSpecHeatCap();

        double Cmin = Math.min(CThis, COther);
        double Cmax = Math.max(CThis, COther);

        double CRel = Cmin / Cmax;

        // Effectiveness for cross or coutner flow heat exchanger
        double epsilon;
        if (Math.abs(CRel - 1.0) < 1e-40) {
            // limit, happens on exaclty equal flows
            epsilon = ntu / (1 + ntu);
        } else {
            epsilon = (1 - Math.exp(-ntu * (1 - CRel)))
                    / (1 - CRel * Math.exp(-ntu * (1 - CRel)));
        }
        // This can be invoked for both sides:
        double Q = epsilon * Cmin
                * (getInTemperature() - otherSide.getInTemperature());
        outNode.setTemperature(getInTemperature() - Q / CThis, aElement);
        
        // however, instead of having to run this two times, simply use the 
        // known reference to the other side and calculate it from here.
        otherSide.setOutTemperature(otherSide.getInTemperature() + Q / COther);
        return true;
    }

    public boolean isCalculationFinished() {
        return calculationFinished;
    }

    /**
     * To be called from the other heat handler of the other side of that
     * heat exchanger. Allows the calculation to be done by one handler only.
     * 
     * @param outTemp 
     */
    public void setOutTemperature(double outTemp) {
        // write on out node
        for (HeatNode hn : heatNodes) { // get node by flow direction
            if (hn.getFlow((AbstractElement) element) < 0.0) {
                hn.setTemperature(outTemp, (AbstractElement) element);
            }
        }
        calculationFinished = true;
    }

    /**
     * Checks if all flow values from nodes toward the element which is assigned
     * to this handler are updated (flow updated and inbound temperature known).
     * Method is public so the other sides handler can call this one too.
     *
     * @return
     */
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

    /**
     * Checks if the flow through the assigned element is zero, either by the
     * flow itself or by the assigned no-temperature condition.
     *
     * @return
     */
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

    /**
     * Requires the calculation to be in the corresponding state. Public as this
     * is also called from the other heat exchanger side.
     *
     * @return flow inbound in kg/s
     */
    public double getInFlow() {
        for (HeatNode hn : heatNodes) { // get nodes by flow direction
            if (hn.getFlow((AbstractElement) element) > 0.0) {
                return hn.getFlow((AbstractElement) element);
            }
        }
        return 0.0;
    }

    /**
     * Requires the calculation to be in the corresponding state. Public as this
     * is also called from the other heat exchanger side.
     *
     * @return Temperature in K
     */
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

    public void setOtherSideHandler(HeatNoMassExchangerHandler otherSide) {
        this.otherSide = otherSide;
    }

    public double getSpecHeatCap() {
        return specHeatCap;
    }

    public void setSpecHeatCap(double specHeatCap) {
        this.specHeatCap = specHeatCap;
    }
    
    public void setNtu(double ntu) {
        this.ntu = ntu;
    }
}
