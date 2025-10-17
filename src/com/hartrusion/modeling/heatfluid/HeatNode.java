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
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * Extends port for capability to hold scalar temperature value. The temperature
 * is linked to flow variable and calculated by assuming that all incoming flows
 * from elements (negative sign) mix perfectly having the same specific heat
 * capacity and density. This allows to simplyfy the whole thing and just use
 * temperature as one scalar value but does not allow mixing of different
 * fluids.
 *
 * <p>
 * Nodes do in no way store temperatures. They get their temperatures assigned
 * from elements which have flows towards this port and the port will distribute
 * them to all elements where a flow leaves this port.
 *
 * @author Viktor Alexander Hartung
 */
public class HeatNode extends GeneralNode {

    /**
     * Holds the temperature properties towards all connected elements, which is
     * basically an extension of the list flowToElements of the GeneralPort. The
     * TemperaturePropertiesContainer is defined in this file too.
     */
    private final List<TemperaturePropertiesContainer> tempProps
            = new ArrayList<>();
    
    private double avgOutTemperature = 298.15;

    public HeatNode() {
        super(PhysicalDomain.HEATFLUID);
    }

    @Override
    public void registerElement(AbstractElement el) {
        super.registerElement(el);
        tempProps.add(new TemperaturePropertiesContainer());
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        for (TemperaturePropertiesContainer tp : tempProps) {
            tp.resetTemperatureUpdated();
        }
    }

    /**
     * Sets the temperature towards an element which is connected to this port.
     * This is usually called by a heat handler from inside a connected element
     * that has a flow towards this port.
     *
     * @param temperature
     * @param source
     */
    public void setTemperature(double temperature, AbstractElement source) {
        tempProps.get(connectedElements.indexOf(source))
                .setTemperatureValue(temperature);
    }

    /**
     * Returns the temperature from this port towards (or from) an element. This
     * is usually called from the elements heat handler.
     *
     * @param source to identify the element
     * @return Temperature value towards or from source
     */
    public double getTemperature(AbstractElement source) {
        if (!tempProps.get(connectedElements.indexOf(source))
                .isTemperatureUpdated()) {
            throw new CalculationException("Temperature requsted but it is "
                    + "not in required updated state.");
        }
        if (tempProps.get(connectedElements.indexOf(source))
                .hasNoTemperatureValue()) {
            throw new CalculationException("Temperature requsted but currenty "
                    + "it is in no-temperature state.");
        }
        return tempProps.get(connectedElements.indexOf(source))
                .getTemperatureValue();
    }
    
    /**
     * Returns the temperature that gets distributed to the connected elements.
     * If there is no temperature set, the last used temperature will be 
     * returned.
     * 
     * @return 
     */
    public double getTemperature() {
        return avgOutTemperature;
    }

    /**
     * Set the temperature to element to invalid or unknown. This is necessary
     * for skipping a calulation, for example, if a valve is closed. This will
     * be called from an element to mark no flow towards this port.
     *
     * @param source
     */
    public void setNoTemperature(AbstractElement source) {
        tempProps.get(connectedElements.indexOf(source))
                .setNoTemperatureValue();
    }

    /**
     * Returns wether the temperature value has been updated for this port for
     * given Element. Elements can call this method to see if the temperature
     * value on a port is up to date. This is stored here inside the port, not
     * the element itsel.
     *
     * @param e Element to which the temperature is assigned.
     * @return true: Temperature was updated
     */
    public boolean temperatureUpdated(AbstractElement e) {
        if (!connectedElements.contains(e)) {
            throw new ModelErrorException("Requested element not known to "
                    + "port.");
        }
        return tempProps.get(connectedElements.indexOf(e))
                .isTemperatureUpdated();
    }

    /**
     * Returns, if the calculation is in an updated state, wehter there is a
     * temperature value or not (zero flow).
     *
     * @param e
     * @return true: No temperature available.
     */
    public boolean noTemperature(AbstractElement e) {
        if (!tempProps.get(connectedElements.indexOf(e))
                .isTemperatureUpdated()) {
            throw new CalculationException("Temperature existance requsted but "
                    + "temperature itself is not in required updated state.");
        }
        if (!connectedElements.contains(e)) {
            throw new ModelErrorException("Requested element not known to "
                    + "port.");
        }
        return tempProps.get(connectedElements.indexOf(e))
                .hasNoTemperatureValue();
    }

    /**
     * Calculate temperature distribution according to current flow values. This
     * can be called after all mass flows have updated values. If the port has
     * just two connections, this method will just pass the temperature through,
     * in case it is more, temperature will be mixed ideally inside port and
     * distributed over flows leaving this port.
     *
     * <p>
     * Usually, this function gets called from the connected elements as they
     * try to calculate themselfes.
     *
     * @return true: Something was calulated.
     */
    public boolean doCalculateTemperature() {
        double thermal; // prodcut of flow times tmeperature value
        double inFlow; // sum of flows into the port
        boolean allInTempsUpdated; // all temperatures into port are updated
        boolean allFlowsZero; // no flow, valves closed or smthn.
        boolean didSomething = false;

        if (allFlowsUpdated()) { // ...it is possible to calculate temperatures.
            // Thermal energy that goes in must leave the port with same temps
            // towards all elements. Use connectedElements for iteration as we
            // do not know about FlowProperties here.
            // First, a rare case with all flows exactly 0.0 - this can happen
            // if model was just started or if flow is forced to zero by a
            // closed valve. Closed valves will force a double value of 0.0
            // so it is valid to compare a double with an exact value here.
            allFlowsZero = true; // init
            for (int idx = 0; idx < connectedElements.size(); idx++) {
                // allFlowsZero = allFlowsZero && (getFlow(idx) == 0.0);
                allFlowsZero = allFlowsZero && Math.abs(getFlow(idx)) < 1e-10;
            }
            // Now, get total incoming thermal energy and check if those
            // temperatures are updated.
            thermal = 0.0; // init
            inFlow = 0.0;
            allInTempsUpdated = true;
            for (int idx = 0; idx < connectedElements.size(); idx++) {
                if (getFlow(idx) < 0.0) { // negative flow means it goes into port
                    allInTempsUpdated = allInTempsUpdated
                            && tempProps.get(idx).isTemperatureUpdated();
                    if (!allInTempsUpdated) {
                        // no need to continue here as these values will not be
                        // of any use when allInTempsUpdated is false.
                        break;
                    }
                    // Do some calulation here already to spare additional
                    // loops
                    thermal = thermal - getFlow(idx) // sum up, but var is neg.
                            * tempProps.get(idx).getTemperatureValue();
                    inFlow = inFlow - getFlow(idx);
                }
            }
            if (allFlowsZero) {
                for (int idx = 0; idx < connectedElements.size(); idx++) {
                    if (!tempProps.get(idx).isTemperatureUpdated()) {
                        tempProps.get(idx).setNoTemperatureValue();
                        didSomething = true;
                    }
                }
                return didSomething;
            } else if (allInTempsUpdated) {
                // thermal now holds a sum of all inbound flows * temperature.
                // calculate average temperature:
                avgOutTemperature = thermal / inFlow;
                // all connections leaving the port will have this temperature:
                for (int idx = 0; idx < connectedElements.size(); idx++) {
                    if (!tempProps.get(idx).isTemperatureUpdated()) {
                        tempProps.get(idx).setTemperatureValue(
                                avgOutTemperature);
                        didSomething = true;
                    }
                }
                return didSomething;
            }
        }
        return false;
    }
}

/**
 * Holds temperature properties towards elements on this port. The same way flow
 * and effort values are stored in port, the temperature properties towards and
 * from the element are also consequently stored in port here.
 *
 * <p>
 * There is a condition that is called "no Value". This will happen if there is
 * no flow towards or from an element, it cant be determined what to do there.
 * As the port iself does never store temperature, there will be no temperature
 * towards that element. It is up to the connected element if it can then store
 * temperature by itself or not. The temperature here is always assigned to a
 * flow so without flow, there is no temperature.
 *
 * @author Viktor Alexander Hartung
 */
class TemperaturePropertiesContainer {

    private boolean temperatureUpdated;
    private boolean noTemperatureValue;
    private double temperatureValue;

    public boolean isTemperatureUpdated() {
        return temperatureUpdated;
    }

    public void resetTemperatureUpdated() {
        temperatureUpdated = false;
        noTemperatureValue = false;
    }

    public double getTemperatureValue() {
        return temperatureValue;
    }

    public void setTemperatureValue(double temperatureValue) {
        if (!Double.isFinite(temperatureValue)) {
            throw new CalculationException("Tried to set non-finite "
                    + "temperature value.");
        }
        this.temperatureValue = temperatureValue;
        temperatureUpdated = true;
    }

    public boolean hasNoTemperatureValue() {
        return noTemperatureValue;
    }

    public void setNoTemperatureValue() {
        temperatureUpdated = true;
        noTemperatureValue = true;
    }

}
