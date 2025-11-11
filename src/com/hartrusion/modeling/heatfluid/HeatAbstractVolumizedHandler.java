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

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;

/**
 * Base class for handling heat with inner volume. Can be extended to add a more
 * specific calculation method. It handles everything except the calculation
 * for handling a volume that can be heated up, including all the node
 * management and reset features.
 *
 * Some extra variables are used to make sure a calculation run applied the full
 * calculation before applying the state variable for the next step.
 *
 * @author Viktor Alexander Hartung
 */
public abstract class HeatAbstractVolumizedHandler implements HeatHandler {

    /**
     * Contains all nodes that the element which uses this heat handler has.
     */
    protected final List<HeatNode> heatNodes = new ArrayList<>();

    /**
     * A reference to a heat element which has this handler assigned.
     */
    protected final HeatElement element; // reference to element assigning this

    /**
     * Heated volume inside the element in the unit of the integral of the flow.
     * If flow is given in kg/s, this is likely to be kilogram. Its an abstract
     * value that can be interpreted differently, depending on how the model is
     * built. There is no need to use specific heat capacity if you can remove
     * it from the equations.
     */
    protected double innerThermalMass = 10;

    /**
     * Temperature that is present in the volume of the element. By default, the
     * temperature is 298.15 K (25 deg Celsius), which is standard for
     * thermodynamic experiments by NIST. This is a state variable.
     */
    protected double temperature = 298.15;

    protected double stepTime = 0.1;

    /**
     * The ultimate goal of this handler is to calculate the temperature which
     * it will have during the next step. The prepareCalulation call will set
     * this as the new temperature for the upcoming calculation run.
     */
    protected double nextTemperature = 298.15;

    /**
     * Value of nextTemperature is calculated and can be used for next cycle.
     * This will be set to true when the calculation run is complete and mark it
     * as such. When resetting the calculation by calling prepareCalculation,
     * the variable will be reset after the temperature for the next cycle was
     * set as the current temperature.
     */
    protected boolean temperaturePrepared = false;

    /**
     * Marks the value temperature as valid and updated, which basically means
     * it was either set externally as first initial condition (thats why we
     * init it with true) or the temperature was successfully set from the
     * nextTemperature variable in prepareCalculation.
     */
    protected boolean temperatureUpdated = true;

    /**
     * To determine the first call of the prepare function, this can be called
     * multiple times by the solver. Will be initialized with false to not 
     * trigger the updated/prepared-check on the first call.
     */
    protected boolean firstPrepareCalcCall = false;

    public HeatAbstractVolumizedHandler(HeatElement parent) {
        this.element = parent;
    }

    @Override
    public boolean registerHeatNode(HeatNode hn) {
        if (!heatNodes.contains(hn)) {
            heatNodes.add(hn);
            return true;
        }
        return false;
    }

    @Override
    public void prepareHeatCalculation() {
        // It is expected to hava a temperature that can be applied on first
        // cycle, otherwise the model won't work.
//        if (firstPrepareCalcCall && !temperaturePrepared) {
//            throw new ModelErrorException("No nextTemperature set in previous"
//                    + " calculation cycle.");
//        }
        firstPrepareCalcCall = false;
        if (temperaturePrepared) {
            temperature = nextTemperature;
        }
        temperaturePrepared = false;
        // reset temperature updated is done by overriding prepareCalculation
        // of GeneralNode in HeatNode, so no need to do it here again.
    }

    @Override
    public boolean doThermalCalculation() {
        firstPrepareCalcCall = true; // reset this
        return false;
    }

    /**
     * Checks if all temperature values from nodes connected to the element
     * which is assigned to this handler are updated.
     *
     * @return
     */
    protected boolean allTempsUpdated() {
        for (HeatNode tp : heatNodes) {
            if (!tp.temperatureUpdated((AbstractElement) element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if all temperature properties on flows that come into this element
     * are updated.
     *
     * @return
     */
    protected boolean allInboundTempsUpdated() {
        for (HeatNode tp : heatNodes) {
            if (!tp.flowUpdated((AbstractElement) element)) {
                return false; // prevent exception if flow is not updated
            }
            if (tp.getFlow((AbstractElement) element) <= 0.0) {
                continue; // zero or outgoing flows irrelevant here
            }
            if (!tp.temperatureUpdated((AbstractElement) element)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if all flow values from nodes toward the element which is assigned
     * to this handler are updated. This private method just provides a small
     * shortcut that is used internally.
     *
     * @return
     */
    protected boolean allFlowsUpdated() {
        for (HeatNode tp : heatNodes) {
            if (!tp.flowUpdated((AbstractElement) element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isHeatCalulationFinished() {
        return temperaturePrepared && allTempsUpdated();
    }

    @Override
    public void setInitialTemperature(double t) {
        temperature = t;
        nextTemperature = t; // this gets written to thermal sources
    }

    @Override
    public double getTemperature() {
        return temperature;
    }

    @Override
    public void setInnerThermalMass(double thermalMass) {
        if (thermalMass < 0) {
            throw new ModelErrorException("Thermal mass must never be a "
                    + "negative value, this is physically impossible.");
        }
        this.innerThermalMass = thermalMass;
    }

    public void setStepTime(double dt) {
        stepTime = dt;
    }
}
