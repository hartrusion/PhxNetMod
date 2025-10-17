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
package com.hartrusion.modeling.phasedfluid;

import java.util.ArrayList;
import java.util.List;
import com.hartrusion.modeling.PhysicalDomain;
import com.hartrusion.modeling.exceptions.CalculationException;
import com.hartrusion.modeling.exceptions.ModelErrorException;
import com.hartrusion.modeling.general.AbstractElement;
import com.hartrusion.modeling.general.GeneralNode;

/**
 * Extends port for capability to hold scalar heat energy value. The heat energy
 * is linked to flow variable and calculated by assuming that all incoming flows
 * from elements (negative sign) mix perfectly having the same specific heat
 * capacity. This allows to simplyfy the whole thing and just use
 * heat energy as one scalar value but does not allow mixing of different
 * fluids.
 *
 * <p>
 * Nodes do in no way store heat energy. They get their heat energies assigned
 * from elements which have flows towards this node and the node will distribute
 * them to all elements where a flow leaves this node.
 *
 * @author Viktor Alexander Hartung
 */
public class PhasedNode extends GeneralNode {

    /**
     * Holds the heat energy properties towards all connected elements, which is
     * basically an extension of the list flowToElements of the GeneralPort. The
     * HeatEnergyPropertiesContainer is defined in this file too.
     */
    private final List<HeatEnergyPropertiesContainer> heatProps
            = new ArrayList<>();

    /**
     * 25 °C = 298.15 K, 298.15 K * 4200 J / kg / K = 1252230 J/kg
     */
    private double avgOutHeatEnergy = 1252230;

    public PhasedNode() {
        super(PhysicalDomain.PHASEDFLUID);
    }

    @Override
    public void registerElement(AbstractElement el) {
        super.registerElement(el);
        heatProps.add(new HeatEnergyPropertiesContainer());
    }

    @Override
    public void prepareCalculation() {
        super.prepareCalculation();
        for (HeatEnergyPropertiesContainer tp : heatProps) {
            tp.resetHeatEnergyUpdated();
        }
    }

    /**
     * Sets the heat energy towards an element which is connected to this port.
     * This is usually called by a phased handler from inside a connected element
     * that has a flow towards this port.
     *
     * @param heatEnergy
     * @param source
     */
    public void setHeatEnergy(double heatEnergy, AbstractElement source) {
        heatProps.get(connectedElements.indexOf(source))
                .setHeatEnergyValue(heatEnergy);
    }

    /**
     * Returns the heat energy from this port towards (or from) an element. This
     * is usually called from the elements heat handler.
     *
     * @param source to identify the element
     * @return HeatEnergy value towards or from source
     */
    public double getHeatEnergy(AbstractElement source) {
        if (!heatProps.get(connectedElements.indexOf(source))
                .isHeatEnergyUpdated()) {
            throw new CalculationException("Heat energy requsted but it is "
                    + "not in required updated state.");
        }
        if (heatProps.get(connectedElements.indexOf(source))
                .hasNoHeatEnergyValue()) {
            throw new CalculationException("Heat energy requsted but currenty "
                    + "it is in no-heat energy state.");
        }
        return heatProps.get(connectedElements.indexOf(source))
                .getHeatEnergyValue();
    }

    /**
     * Returns the heat energy that gets distributed to the connected elements.
     * If there is no heat energy set, the last used heat energy will be
     * returned.
     *
     * @return
     */
    public double getHeatEnergy() {
        return avgOutHeatEnergy;
    }

    /**
     * Set the heat energy to element to invalid or unknown. This is necessary
     * for skipping a calulation, for example, if a valve is closed. This will
     * be called from an element to mark no flow towards this port.
     *
     * @param source
     */
    public void setNoHeatEnergy(AbstractElement source) {
        heatProps.get(connectedElements.indexOf(source))
                .setNoHeatEnergyValue();
    }

    /**
     * Returns wether the heat energy value has been updated for this port for
     * given Element. Elements can call this method to see if the heat energy
     * value on a port is up to date. This is stored here inside the port, not
     * the element itsel.
     *
     * @param e Element to which the heat energy is assigned.
     * @return true: HeatEnergy was updated
     */
    public boolean heatEnergyUpdated(AbstractElement e) {
        if (!connectedElements.contains(e)) {
            throw new ModelErrorException("Requested element not known to "
                    + "node.");
        }
        return heatProps.get(connectedElements.indexOf(e))
                .isHeatEnergyUpdated();
    }

    /**
     * Returns, if the calculation is in an updated state, wehter there is a
     * heat energy value or not (zero flow).
     *
     * @param e
     * @return true: No heat energy available.
     */
    public boolean noHeatEnergy(AbstractElement e) {
        if (!heatProps.get(connectedElements.indexOf(e))
                .isHeatEnergyUpdated()) {
            throw new CalculationException("Heat energy existance requsted but "
                    + "heat energy itself is not in required updated state.");
        }
        if (!connectedElements.contains(e)) {
            throw new ModelErrorException("Requested element not known to "
                    + "node.");
        }
        return heatProps.get(connectedElements.indexOf(e))
                .hasNoHeatEnergyValue();
    }

    /**
     * Calculate heat energy distribution according to current flow values. This
     * can be called after all mass flows have updated values. If the port has
     * just two connections, this method will just pass the heat energy through,
     * in case it is more, heat energy will be mixed ideally inside port and
     * distributed over flows leaving this port.
     *
     * <p>
     * Usually, this function gets called from the connected elements as they
     * try to calculate themselfes.
     *
     * @return true: Something was calulated.
     */
    public boolean doCalculateHeatEnergy() {
        double heat; // prodcut of flow times specific heat value
        double inFlow; // sum of flows into the port
        boolean allInHeatEnergyUpdated; // all heat energies into port are updated
        boolean allFlowsZero; // no flow, valves closed or smthn.
        boolean didSomething = false;

        if (allFlowsUpdated()) { // ...it is possible to calculate heat energies.
            // Thermal energy that goes in must leave the node with same temps
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
            // heat energies are updated.
            heat = 0.0; // init
            inFlow = 0.0;
            allInHeatEnergyUpdated = true;
            for (int idx = 0; idx < connectedElements.size(); idx++) {
                if (getFlow(idx) < 0.0) { // negative flow means it goes in
                    allInHeatEnergyUpdated = allInHeatEnergyUpdated
                            && heatProps.get(idx).isHeatEnergyUpdated();
                    if (!allInHeatEnergyUpdated) {
                        // no need to continue here as these values will not be
                        // of any use when allInTempsUpdated is false.
                        break;
                    }
                    // Do some calulation here already to spare additional
                    // loops
                    heat = heat - getFlow(idx) // sum up, but var is neg.
                            * heatProps.get(idx).getHeatEnergyValue();
                    inFlow = inFlow - getFlow(idx);
                }
            }
            if (allFlowsZero) {
                for (int idx = 0; idx < connectedElements.size(); idx++) {
                    if (!heatProps.get(idx).isHeatEnergyUpdated()) {
                        heatProps.get(idx).setNoHeatEnergyValue();
                        didSomething = true;
                    }
                }
                return didSomething;
            } else if (allInHeatEnergyUpdated) {
                // thermal now holds a sum of all inbound flows * heat energy.
                // calculate average heat energy:
                avgOutHeatEnergy = heat / inFlow;
                // all connections leaving the port will have this heat energy:
                for (int idx = 0; idx < connectedElements.size(); idx++) {
                    if (!heatProps.get(idx).isHeatEnergyUpdated()) {
                        heatProps.get(idx).setHeatEnergyValue(
                                avgOutHeatEnergy);
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
 * Holds heat energy properties towards elements on this node. The same way flow
 * and effort values are stored in port, the heat energy properties towards and
 * from the element are also consequently stored in node here.
 *
 * <p>
 * There is a condition that is called "no Value". This will happen if there is
 * no flow towards or from an element, it cant be determined what to do there.
 * As the node iself does never store heat energy, there will be no heat energy
 * towards that element. It is up to the connected element if it can then store
 * heat energy by itself or not. The heat energy here is always assigned to a
 * flow so without flow, there is no heat energy.
 *
 * @author Viktor Alexander Hartung
 */
class HeatEnergyPropertiesContainer {

    private boolean heatEnergyUpdated;
    private boolean noHeatEnergyValue;
    private double heatEnergyValue;

    public boolean isHeatEnergyUpdated() {
        return heatEnergyUpdated;
    }

    public void resetHeatEnergyUpdated() {
        heatEnergyUpdated = false;
        noHeatEnergyValue = false;
    }

    public double getHeatEnergyValue() {
        return heatEnergyValue;
    }

    public void setHeatEnergyValue(double heatEnergyValue) {
        if (!Double.isFinite(heatEnergyValue)) {
            throw new CalculationException("Tried to set non-finite "
                    + "heat energy value.");
        }
        this.heatEnergyValue = heatEnergyValue;
        heatEnergyUpdated = true;
    }

    public boolean hasNoHeatEnergyValue() {
        return noHeatEnergyValue;
    }

    public void setNoHeatEnergyValue() {
        heatEnergyUpdated = true;
        noHeatEnergyValue = true;
    }

}
